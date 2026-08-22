package com.lanyeeee.jmcomic.domain.download

import com.lanyeeee.jmcomic.data.local.FileNames
import com.lanyeeee.jmcomic.data.local.MetadataStore
import com.lanyeeee.jmcomic.data.network.GetChapterRespData
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.domain.model.Config
import com.lanyeeee.jmcomic.domain.model.DownloadFormat
import com.lanyeeee.jmcomic.domain.model.DownloadTaskState
import com.lanyeeee.jmcomic.domain.model.FavoriteSort
import com.lanyeeee.jmcomic.domain.model.ProgressData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** 计算漫画/章节的下载目录（基于 dirFmt），与 legacy `update_download_dir_fields_by_fmt` 一致 */
object DirResolver {
    fun resolve(comic: Comic, config: Config): Comic {
        var firstChapterDir: String? = null
        val chapters = comic.chapterInfos.map { ch ->
            val params = Comic.DirFmtParams(
                comic_id = comic.id,
                comic_title = comic.name,
                author = comic.authorDisplay,
                chapter_id = ch.chapterId,
                chapter_title = ch.chapterTitle,
                order = ch.order,
            )
            val dir = FileNames.chapterDownloadDir(config.downloadDir, config.dirFmt, params)
            if (dir == null) ch else {
                if (firstChapterDir == null) firstChapterDir = dir
                ch.copy(chapterDownloadDir = dir)
            }
        }
        val comicDir = firstChapterDir?.let { FileNames.comicDownloadDirOf(it) }
        return comic.copy(chapterInfos = chapters, comicDownloadDir = comicDir)
    }
}

/**
 * 下载管理器，移植 legacy `download_manager.rs`：
 * - 章节级并发(默认3) + 图片级并发(默认20) 信号量
 * - 每章节一个 [DownloadTask]，状态机 Pending/Downloading/Paused/Cancelled/Completed/Failed
 * - 临时目录 `.下载中-xxx` 下载完成后原子 rename 为正式目录（断点续传 + 避免半成品）
 */
class DownloadManager(
    private val api: JmApi,
    private val configProvider: () -> Config,
    private val comicFetcher: suspend (Long) -> Comic,
    private val scope: CoroutineScope,
) {
    private class Semaphores(val chapter: Semaphore, val img: Semaphore)

    private val semaphores = AtomicReference(
        Semaphores(
            Semaphore(configProvider().chapterConcurrency.coerceAtLeast(1)),
            Semaphore(configProvider().imgConcurrency.coerceAtLeast(1)),
        )
    )

    private val bytePerSec = AtomicLong(0)
    private val _speed = MutableStateFlow("0.00MB/s")
    val speed: StateFlow<String> = _speed

    private val _progresses = MutableStateFlow<Map<Long, ProgressData>>(emptyMap())
    val progresses: StateFlow<Map<Long, ProgressData>> = _progresses

    private val _sleeping = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val sleeping: StateFlow<Map<Long, Long>> = _sleeping

    private val tasks = ConcurrentHashMap<Long, DownloadTask>()

    init {
        scope.launch { speedLoop() }
    }

    /** 配置中的并发数变化后调用，新任务生效 */
    fun reloadConcurrency() {
        semaphores.set(
            Semaphores(
                Semaphore(configProvider().chapterConcurrency.coerceAtLeast(1)),
                Semaphore(configProvider().imgConcurrency.coerceAtLeast(1)),
            )
        )
    }

    private suspend fun speedLoop() {
        while (true) {
            delay(1000)
            val bytes = bytePerSec.getAndSet(0)
            val mb = bytes / 1024.0 / 1024.0
            _speed.value = "%.2fMB/s".format(mb)
        }
    }

    // ---------- 控制 ----------

    fun createDownloadTask(comic: Comic, chapterId: Long): Boolean {
        val existing = tasks[chapterId]
        if (existing != null &&
            existing.state.value in setOf(DownloadTaskState.Pending, DownloadTaskState.Downloading, DownloadTaskState.Paused)
        ) {
            return false
        }
        val resolved = DirResolver.resolve(comic, configProvider())
        val chapter = resolved.chapterInfos.firstOrNull { it.chapterId == chapterId } ?: return false
        if (resolved.comicDownloadDir == null || chapter.chapterDownloadDir == null) return false
        val task = DownloadTask(resolved, chapter)
        tasks[chapterId] = task
        scope.launch { process(task) }
        return true
    }

    fun pause(chapterId: Long) {
        tasks[chapterId]?.let { it.state.value = DownloadTaskState.Paused }
    }

    fun resume(chapterId: Long) {
        tasks[chapterId]?.let { it.state.value = DownloadTaskState.Pending }
    }

    fun cancel(chapterId: Long) {
        tasks[chapterId]?.let { it.state.value = DownloadTaskState.Cancelled }
    }

    fun removeTask(chapterId: Long) {
        tasks.remove(chapterId)
        _progresses.update { it - chapterId }
        _sleeping.update { it - chapterId }
    }

    fun isActive(chapterId: Long): Boolean =
        tasks[chapterId]?.state?.value in setOf(
            DownloadTaskState.Pending, DownloadTaskState.Downloading, DownloadTaskState.Paused
        )

    // ---------- 单任务处理 ----------

    private suspend fun process(task: DownloadTask) {
        emitUpdate(task)
        var hasChapterPermit = false
        val chapterSem = { semaphores.get().chapter }
        while (true) {
            when (task.state.value) {
                DownloadTaskState.Cancelled, DownloadTaskState.Completed, DownloadTaskState.Failed -> break
                DownloadTaskState.Paused -> {
                    task.state.first {
                        it == DownloadTaskState.Pending || it == DownloadTaskState.Cancelled
                    }
                    if (task.state.value == DownloadTaskState.Cancelled) break
                }
                else -> { // Pending
                    if (!hasChapterPermit) {
                        try {
                            chapterSem().acquire()
                            hasChapterPermit = true
                        } catch (e: CancellationException) {
                            break
                        }
                    }
                    if (task.state.value != DownloadTaskState.Pending) {
                        chapterSem().release()
                        hasChapterPermit = false
                        continue
                    }
                    task.state.value = DownloadTaskState.Downloading
                    when (val outcome = runChapter(task)) {
                        Outcome.Completed -> {
                            task.state.value = DownloadTaskState.Completed
                            break
                        }
                        Outcome.Failed -> {
                            task.state.value = DownloadTaskState.Failed
                            break
                        }
                        Outcome.Cancelled -> {
                            task.state.value = DownloadTaskState.Cancelled
                            break
                        }
                        Outcome.Paused -> {
                            if (hasChapterPermit) {
                                chapterSem().release()
                                hasChapterPermit = false
                            }
                            task.state.first {
                                it == DownloadTaskState.Pending || it == DownloadTaskState.Cancelled
                            }
                            if (task.state.value == DownloadTaskState.Cancelled) break
                        }
                    }
                }
            }
        }
        if (hasChapterPermit) {
            chapterSem().release()
            hasChapterPermit = false
        }
        emitUpdate(task)
    }

    private enum class Outcome { Completed, Paused, Cancelled, Failed }

    private suspend fun runChapter(task: DownloadTask): Outcome {
        val comic = task.comic
        val chapter = task.chapterInfo
        val config = configProvider()
        val chapterId = chapter.chapterId

        // 1. 保存漫画元数据
        if (!MetadataStore.saveComicMetadata(comic)) {
            log("`${comic.name}` 保存元数据失败")
            return Outcome.Failed
        }

        // 2. 下载封面（失败不阻断下载）
        if (config.shouldDownloadCover) {
            val coverFile = comic.comicDownloadDir?.let { File(it, FileNames.COVER_NAME) }
            if (coverFile != null && !coverFile.exists()) {
                try {
                    val data = api.downloadImage(JmApi.coverUrl(comic.id))
                    withContext(NonCancellable) { coverFile.writeBytes(data) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("`${comic.name}` 下载封面失败: ${e.message}")
                }
            }
        }

        // 3. 并行获取 scramble_id + 章节图片列表
        val scrambleId: Long
        val chapterResp: GetChapterRespData
        try {
            val (a, b) = coroutineScope {
                val fa = async { api.getScrambleId(chapterId) }
                val fb = async { api.getChapter(chapterId) }
                fa.await() to fb.await()
            }
            scrambleId = a
            chapterResp = b
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("`${comic.name} - ${chapter.chapterTitle}` 获取图片下载链接失败: ${e.message}")
            return Outcome.Failed
        }

        // 4. 构造 (url, blockNum) 列表
        val items = buildImageItems(chapterResp, chapterId, scrambleId)
        if (items.isEmpty()) {
            log("`${comic.name} - ${chapter.chapterTitle}` 没有可下载的图片")
            return Outcome.Failed
        }
        task.totalCount = items.size

        // 5. 创建临时下载目录
        val finalDir = chapter.chapterDownloadDir ?: return Outcome.Failed
        val tempDirPath = FileNames.tempDownloadDir(finalDir)
        val tempDir = File(tempDirPath)
        if (!runCatching { tempDir.mkdirs() }.getOrDefault(false)) {
            log("创建临时下载目录失败: $tempDirPath")
            return Outcome.Failed
        }

        // 6. 清理临时目录中格式不一致的残留文件
        cleanTempDir(tempDir, config.downloadFormat)

        // 7. 下载全部图片
        val imgOutcome = downloadImages(task, items, tempDir, config)
        if (imgOutcome != Outcome.Completed) return imgOutcome

        // 8. 校验图片数量
        if (task.downloadedCount != task.totalCount) {
            log("`${comic.name} - ${chapter.chapterTitle}` 下载不完整: " +
                "共${task.totalCount}张，只下载了${task.downloadedCount}张")
            return Outcome.Failed
        }

        // 9. 临时目录重命名为正式目录（先删旧再 rename）
        val finalFile = File(finalDir)
        if (finalFile.exists()) runCatching { finalFile.deleteRecursively() }
        val renamed = runCatching { tempDir.renameTo(finalFile) }.getOrDefault(false)
        if (!renamed) {
            log("`${comic.name} - ${chapter.chapterTitle}` 重命名临时下载目录失败")
            return Outcome.Failed
        }

        // 10. 保存章节元数据
        MetadataStore.saveChapterMetadata(chapter.copy(chapterDownloadDir = finalDir))

        // 11. 章节间休眠（可被打断）
        if (sleepBetweenChapters(task, config) != Outcome.Completed) return Outcome.Cancelled

        return Outcome.Completed
    }

    private data class ImageItem(val url: String, val blockNum: Int)

    private fun buildImageItems(
        resp: GetChapterRespData,
        chapterId: Long,
        scrambleId: Long,
    ): List<ImageItem> {
        val items = ArrayList<ImageItem>(resp.images.size)
        for (filename in resp.images) {
            val lastDot = filename.lastIndexOf('.')
            if (lastDot < 0) continue
            val ext = filename.substring(lastDot + 1).lowercase()
            val url = JmApi.chapterImageUrl(chapterId, filename)
            if (ext == "gif") {
                items.add(ImageItem(url, 0))
                continue
            }
            if (ext != "webp") continue
            val stem = filename.substring(0, lastDot)
            val blockNum = Stitch.calculateBlockNum(scrambleId, chapterId, stem)
            items.add(ImageItem(url, blockNum))
        }
        return items
    }

    private suspend fun downloadImages(
        task: DownloadTask,
        items: List<ImageItem>,
        tempDir: File,
        config: Config,
    ): Outcome {
        return try {
            coroutineScope {
                val scope = this
                val jobs = items.mapIndexed { index, item ->
                    launch { downloadImg(task, item, index, tempDir, config) }
                }
                val watcher = launch {
                    // 状态离开 Downloading（Paused/Cancelled）时取消图片阶段
                    task.state.first { it != DownloadTaskState.Downloading }
                    scope.cancel()
                }
                jobs.joinAll()
                watcher.cancel()
            }
            Outcome.Completed
        } catch (e: CancellationException) {
            when (task.state.value) {
                DownloadTaskState.Paused -> Outcome.Paused
                DownloadTaskState.Cancelled -> Outcome.Cancelled
                else -> throw e
            }
        }
    }

    private suspend fun downloadImg(
        task: DownloadTask,
        item: ImageItem,
        index: Int,
        tempDir: File,
        config: Config,
    ) {
        semaphores.get().img.withPermit {
            val idx = "%04d".format(index + 1)
            val userPath = File(tempDir, "$idx.${config.downloadFormat.extension}")
            val gifPath = File(tempDir, "$idx.gif")

            // 已存在则跳过（断点续传）
            if (userPath.exists() || gifPath.exists()) {
                task.downloadedCount++
                emitUpdate(task)
                return@withPermit
            }

            val bytes = api.downloadImage(item.url)
            bytePerSec.addAndGet(bytes.size.toLong())

            val savePath = if (ImageUtil.detectFormat(bytes) == ImageUtil.Format.GIF) gifPath else userPath
            withContext(NonCancellable) {
                saveImage(savePath, config.downloadFormat, item.blockNum, bytes)
            }

            task.downloadedCount++
            emitUpdate(task)

            if (config.imgDownloadIntervalSec > 0) {
                if (task.state.value != DownloadTaskState.Downloading) {
                    throw CancellationException("chapter no longer downloading")
                }
                delay(config.imgDownloadIntervalSec * 1000)
            }
        }
    }

    private fun saveImage(savePath: File, format: DownloadFormat, blockNum: Int, bytes: ByteArray) {
        val srcFormat = ImageUtil.detectFormat(bytes)
        if (srcFormat == ImageUtil.Format.GIF) {
            savePath.writeBytes(bytes)
            return
        }
        val bitmap = ImageUtil.decode(bytes) ?: throw IOException("解码图片失败")
        try {
            val stitched = if (blockNum > 0) Stitch.stitch(bitmap, blockNum) else bitmap
            val out = ImageUtil.encode(stitched, format)
            savePath.writeBytes(out)
            if (stitched !== bitmap) stitched.recycle()
        } finally {
            bitmap.recycle()
        }
    }

    private fun cleanTempDir(tempDir: File, format: DownloadFormat) {
        tempDir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val ext = f.extension.lowercase()
            val keep = ext == "gif" || ext == format.extension
            if (!keep) runCatching { f.delete() }
        }
    }

    private suspend fun sleepBetweenChapters(task: DownloadTask, config: Config): Outcome {
        var remaining = config.chapterDownloadIntervalSec
        try {
            while (remaining > 0) {
                when (task.state.value) {
                    DownloadTaskState.Cancelled -> return Outcome.Cancelled
                    DownloadTaskState.Paused -> return Outcome.Paused
                    else -> {}
                }
                _sleeping.update { it + (task.chapterInfo.chapterId to remaining) }
                delay(1000)
                remaining--
            }
        } finally {
            _sleeping.update { it - task.chapterInfo.chapterId }
        }
        return Outcome.Completed
    }

    private fun emitUpdate(task: DownloadTask) {
        _progresses.update {
            it + (task.chapterInfo.chapterId to ProgressData(
                state = task.state.value,
                comic = task.comic,
                chapterInfo = task.chapterInfo,
                downloadedImgCount = task.downloadedCount,
                totalImgCount = task.totalCount,
            ))
        }
    }

    private fun log(msg: String) {
        android.util.Log.e("JmDownload", msg)
    }

    // ---------- 批量操作 ----------

    /** 一键下载整本（跳过已下载章节），返回创建的任务数 */
    suspend fun downloadComic(comic: Comic): Int {
        var created = 0
        for (chapter in comic.chapterInfos) {
            if (chapter.isDownloaded == true) continue
            if (createDownloadTask(comic, chapter.chapterId)) created++
        }
        return created
    }

    /** 下载整个收藏夹（拉全部分页，逐本创建未下载章节的任务） */
    suspend fun downloadAllFavorites(onProgress: (current: Int, total: Int) -> Unit) {
        val first = api.getFavoriteFolder(0, 1, FavoriteSort.FavoriteTime)
        val total = first.total.toLongOrNull() ?: first.list.size.toLong()
        val count = first.count.coerceAtLeast(1)
        val pageCount = ((total / count) + 1).toInt().coerceAtLeast(1)

        val all = first.list.toMutableList()
        if (pageCount > 1) {
            val extra = coroutineScope {
                (2..pageCount).map { page ->
                    async { api.getFavoriteFolder(0, page.toLong(), FavoriteSort.FavoriteTime) }
                }
            }
            extra.forEach { page ->
                runCatching { all.addAll(page.await().list) }
            }
        }

        val intervalMs = configProvider().downloadAllFavoritesIntervalSec * 1000
        all.forEachIndexed { i, fav ->
            onProgress(i + 1, all.size)
            val aid = fav.id.toLongOrNull()
            if (aid != null) {
                runCatching {
                    val comic = comicFetcher(aid)
                    comic.chapterInfos.filter { it.isDownloaded != true }.forEach { ch ->
                        createDownloadTask(comic, ch.chapterId)
                        delay(100)
                    }
                }
            }
            if (intervalMs > 0) delay(intervalMs)
        }
    }

    /** 更新库存：扫描已下载漫画，联网补下新章节 */
    suspend fun updateDownloadedComics(
        downloadDir: File,
        onProgress: (current: Int, total: Int) -> Unit,
    ) {
        val downloaded = MetadataStore.listDownloadedComics(downloadDir)
        val intervalMs = configProvider().updateDownloadedComicsIntervalSec * 1000
        downloaded.forEachIndexed { i, comic ->
            onProgress(i + 1, downloaded.size)
            runCatching {
                val fresh = comicFetcher(comic.id)
                fresh.chapterInfos.filter { it.isDownloaded != true }.forEach { ch ->
                    createDownloadTask(fresh, ch.chapterId)
                    delay(100)
                }
            }
            if (intervalMs > 0) delay(intervalMs)
        }
    }
}

class DownloadTask(
    val comic: Comic,
    val chapterInfo: com.lanyeeee.jmcomic.domain.model.ChapterInfo,
) {
    val state = MutableStateFlow(DownloadTaskState.Pending)
    @Volatile var downloadedCount: Int = 0
    @Volatile var totalCount: Int = 0
}
