package com.lanyeeee.jmcomic.data.local

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * 已下载漫画的内存索引。
 *
 * 解决"每次打开漫画都要全盘扫描下载目录"导致的卡顿，以及重启后已下载状态丢失/重复下载问题：
 * - 启动时后台扫一次，之后查询都是 O(1) 内存查找
 * - 下载完成时增量更新，无需重新扫描
 * - 重启后自动重建
 */
class DownloadIndex {
    data class State(
        val comicDirs: Map<Long, String>,
        val chapterDirs: Map<Long, Map<Long, String>>,
    )

    private val state = AtomicReference(State(emptyMap(), emptyMap()))

    /** 扫描下载目录，重建索引（后台调用，一次性） */
    fun refresh(downloadDir: File) {
        val comicDirMap = MetadataStore.createIdToDirMap(downloadDir)
        val chapterMap = mutableMapOf<Long, Map<Long, String>>()
        comicDirMap.forEach { (id, dir) ->
            val chapters = MetadataStore.findChapterMetadataDirs(dir)
            if (chapters.isNotEmpty()) {
                chapterMap[id] = chapters.mapValues { it.value.absolutePath }
            }
        }
        state.set(
            State(
                comicDirs = comicDirMap.mapValues { it.value.absolutePath },
                chapterDirs = chapterMap,
            )
        )
    }

    fun comicDir(comicId: Long): String? = state.get().comicDirs[comicId]

    fun isDownloaded(comicId: Long): Boolean = state.get().comicDirs.containsKey(comicId)

    fun chapterDir(comicId: Long, chapterId: Long): String? =
        state.get().chapterDirs[comicId]?.get(chapterId)

    fun chapterIsDownloaded(comicId: Long, chapterId: Long): Boolean =
        state.get().chapterDirs[comicId]?.containsKey(chapterId) == true

    /** 下载完成后增量标记（无需重扫） */
    fun markChapterDownloaded(comicId: Long, chapterId: Long, chapterDir: String, comicDir: String) {
        state.updateAndGet { s ->
            val chapters = (s.chapterDirs[comicId] ?: emptyMap()) + (chapterId to chapterDir)
            s.copy(
                comicDirs = s.comicDirs + (comicId to comicDir),
                chapterDirs = s.chapterDirs + (comicId to chapters),
            )
        }
    }

    fun removeComic(comicId: Long) {
        state.updateAndGet { s ->
            s.copy(
                comicDirs = s.comicDirs - comicId,
                chapterDirs = s.chapterDirs - comicId,
            )
        }
    }
}
