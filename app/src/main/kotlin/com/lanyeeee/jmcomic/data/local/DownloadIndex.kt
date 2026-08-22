package com.lanyeeee.jmcomic.data.local

import com.lanyeeee.jmcomic.domain.model.Comic
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Serializable
private data class PersistedIndex(
    val comicDirs: Map<Long, String> = emptyMap(),
    val chapterDirs: Map<Long, Map<Long, String>> = emptyMap(),
)

/**
 * 已下载漫画内存索引，落盘 + 增量更新。
 *
 * 设计：
 * - 索引持久化到应用私有目录 `download_index.json`，启动读入（单文件，快）
 * - 启动/回前台时做"目录核对"：只对**新增**的漫画读 JSON，已有条目仅检查目录是否存在，
 *   避免每次全量重读所有元数据文件（库再大启动也几乎瞬时）
 * - 下载完成/删除时增量更新内存并写回磁盘，无需重新扫描
 */
class DownloadIndex(private val indexFile: File) {
    data class State(
        val comicDirs: Map<Long, String>,
        val chapterDirs: Map<Long, Map<Long, String>>,
    )

    private val state = AtomicReference(State(emptyMap(), emptyMap()))

    /** 启动时从磁盘读入索引（单文件，快，可在主线程） */
    fun loadFromDisk() {
        val loaded = runCatching {
            if (!indexFile.exists()) State(emptyMap(), emptyMap())
            else {
                val p = MetadataStore.json.decodeFromString(PersistedIndex.serializer(), indexFile.readText())
                State(p.comicDirs, p.chapterDirs)
            }
        }.getOrDefault(State(emptyMap(), emptyMap()))
        state.set(loaded)
    }

    /**
     * 目录核对（后台调用）：
     * - 遍历下载目录找所有含 `元数据.json` 的目录
     * - 已在索引里的：只校验目录存在（不读 JSON 内容）
     * - 新增的：读元数据建索引
     * - 被外部删除的：从索引移除
     */
    fun refresh(downloadDir: File) {
        if (!downloadDir.exists()) {
            state.set(State(emptyMap(), emptyMap()))
            persist()
            return
        }

        val current = state.get()
        val pathToId = current.comicDirs.entries.associate { (id, dir) -> dir to id }
        val newComicDirs = linkedMapOf<Long, String>()
        val newChapterDirs = linkedMapOf<Long, Map<Long, String>>()

        downloadDir.walkTopDown().forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val meta = File(dir, FileNames.COMIC_METADATA)
            if (!meta.isFile) return@forEach

            val existingId = pathToId[dir.absolutePath]
            if (existingId != null) {
                // 已有漫画：仅校验章节目录仍存在
                newComicDirs[existingId] = dir.absolutePath
                val chDirs = current.chapterDirs[existingId] ?: emptyMap()
                val alive = chDirs.filterValues { File(it).isDirectory }
                if (alive.isNotEmpty()) newChapterDirs[existingId] = alive
            } else {
                // 新增漫画：读取元数据
                val comic = runCatching {
                    MetadataStore.json.decodeFromString(Comic.serializer(), meta.readText())
                }.getOrNull() ?: return@forEach
                newComicDirs[comic.id] = dir.absolutePath
                val ch = MetadataStore.findChapterMetadataDirs(dir)
                if (ch.isNotEmpty()) newChapterDirs[comic.id] = ch.mapValues { it.value.absolutePath }
            }
        }

        val next = State(newComicDirs, newChapterDirs)
        val changed = next != current
        state.set(next)
        if (changed) persist()
    }

    fun comicDir(comicId: Long): String? = state.get().comicDirs[comicId]

    fun isDownloaded(comicId: Long): Boolean = state.get().comicDirs.containsKey(comicId)

    fun chapterDir(comicId: Long, chapterId: Long): String? =
        state.get().chapterDirs[comicId]?.get(chapterId)

    fun chapterIsDownloaded(comicId: Long, chapterId: Long): Boolean =
        state.get().chapterDirs[comicId]?.containsKey(chapterId) == true

    /** 下载完成后增量标记并落盘 */
    fun markChapterDownloaded(comicId: Long, chapterId: Long, chapterDir: String, comicDir: String) {
        val before = state.get()
        val after = state.updateAndGet { s ->
            val chapters = (s.chapterDirs[comicId] ?: emptyMap()) + (chapterId to chapterDir)
            s.copy(
                comicDirs = s.comicDirs + (comicId to comicDir),
                chapterDirs = s.chapterDirs + (comicId to chapters),
            )
        }
        if (after != before) persist()
    }

    fun removeComic(comicId: Long) {
        val before = state.get()
        val after = state.updateAndGet { s ->
            s.copy(
                comicDirs = s.comicDirs - comicId,
                chapterDirs = s.chapterDirs - comicId,
            )
        }
        if (after != before) persist()
    }

    private fun persist() {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val s = state.get()
            indexFile.writeText(
                MetadataStore.json.encodeToString(
                    PersistedIndex.serializer(),
                    PersistedIndex(s.comicDirs, s.chapterDirs),
                )
            )
        }
    }
}
