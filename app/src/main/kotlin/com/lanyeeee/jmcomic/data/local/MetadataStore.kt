package com.lanyeeee.jmcomic.data.local

import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 元数据 JSON 的读写 + 本地库存扫描。
 * 磁盘格式与 legacy Rust 版完全兼容（camelCase、空字段省略）。
 */
object MetadataStore {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    val prettyJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    /** 写入漫画 `元数据.json`（序列化时省略 is_downloaded / comic_download_dir） */
    fun saveComicMetadata(comic: Comic): Boolean {
        val dir = comic.comicDownloadDir ?: return false
        val file = File(dir, FileNames.COMIC_METADATA)
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(prettyJson.encodeToString(Comic.serializer(), comic.stripped()))
        }.isSuccess
    }

    /** 写入章节 `章节元数据.json` */
    fun saveChapterMetadata(chapter: ChapterInfo): Boolean {
        val dir = chapter.chapterDownloadDir ?: return false
        val file = File(dir, FileNames.CHAPTER_METADATA)
        return runCatching {
            file.parentFile?.mkdirs()
            val stripped = chapter.copy(isDownloaded = null, chapterDownloadDir = null)
            file.writeText(prettyJson.encodeToString(ChapterInfo.serializer(), stripped))
        }.isSuccess
    }

    /** 从 `元数据.json` 读取漫画，并回填下载目录与已下载章节信息 */
    fun loadComicFromMetadata(metadataPath: File): Comic? {
        val comic = runCatching {
            json.decodeFromString(Comic.serializer(), metadataPath.readText())
        }.getOrNull() ?: return null
        val comicDir = metadataPath.parentFile ?: return null

        createChapterMetadataForOldVersion(comic, comicDir)

        val chapterDirs = findChapterMetadataDirs(comicDir)
        val updated = comic.copy(
            comicDownloadDir = comicDir.absolutePath,
            isDownloaded = true,
            chapterInfos = comic.chapterInfos.map { ch ->
                val dir = chapterDirs[ch.chapterId]
                if (dir != null) ch.copy(isDownloaded = true, chapterDownloadDir = dir.absolutePath)
                else ch
            },
        )
        return updated
    }

    /** 扫描漫画目录下所有 `章节元数据.json`，得到 chapterId -> 章节目录 映射 */
    fun findChapterMetadataDirs(comicDir: File): Map<Long, File> {
        val map = LinkedHashMap<Long, File>()
        comicDir.walkTopDown().forEach { f ->
            if (f.isFile && f.name == FileNames.CHAPTER_METADATA) {
                val chapterId = runCatching {
                    json.decodeFromString(ChapterInfo.serializer(), f.readText()).chapterId
                }.getOrNull()
                if (chapterId != null) {
                    val parent = f.parentFile
                    if (parent != null) map[chapterId] = parent
                }
            }
        }
        return map
    }

    /**
     * 兼容旧版本：若章节目录存在但缺少 `章节元数据.json`，自动补齐。
     */
    private fun createChapterMetadataForOldVersion(comic: Comic, comicDir: File) {
        val chapterDirs = comicDir.listFiles()?.filter { it.isDirectory }?.toHashSet() ?: return
        for (chapter in comic.chapterInfos) {
            val oldDir = File(comicDir, chapter.chapterTitle)
            if (chapterDirs.contains(oldDir) && !File(oldDir, FileNames.CHAPTER_METADATA).exists()) {
                saveChapterMetadata(chapter.copy(isDownloaded = true, chapterDownloadDir = oldDir.absolutePath))
            }
        }
    }

    /**
     * 扫描下载目录，建立 漫画ID -> 漫画下载目录 映射（用于给搜索结果打 isDownloaded 标记）。
     */
    fun createIdToDirMap(downloadDir: File): Map<Long, File> {
        if (!downloadDir.exists()) return emptyMap()
        val map = LinkedHashMap<Long, File>()
        downloadDir.walkTopDown().forEach { f ->
            if (f.isFile && f.name == FileNames.COMIC_METADATA) {
                val id = runCatching {
                    json.decodeFromString(Comic.serializer(), f.readText()).id
                }.getOrNull()
                if (id != null && !map.containsKey(id)) {
                    val parent = f.parentFile
                    if (parent != null) map[id] = parent
                }
            }
        }
        return map
    }

    /**
     * 列出已下载漫画（读取全部 `元数据.json`，按修改时间倒序，按 id 去重）。
     */
    fun listDownloadedComics(downloadDir: File): List<Comic> {
        if (!downloadDir.exists()) return emptyList()
        val byId = LinkedHashMap<Long, Pair<Comic, Long>>()
        downloadDir.walkTopDown().forEach { f ->
            if (f.isFile && f.name == FileNames.COMIC_METADATA) {
                val comic = loadComicFromMetadata(f) ?: return@forEach
                val mtime = f.lastModified()
                val existing = byId[comic.id]
                if (existing == null || mtime > existing.second) {
                    byId[comic.id] = comic to mtime
                }
            }
        }
        return byId.values.sortedByDescending { it.second }.map { it.first }
    }
}
