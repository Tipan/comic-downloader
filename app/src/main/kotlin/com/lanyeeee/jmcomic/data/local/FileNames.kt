package com.lanyeeee.jmcomic.data.local

import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.domain.model.DownloadFormat

object FileNames {
    const val COMIC_METADATA = "元数据.json"
    const val CHAPTER_METADATA = "章节元数据.json"
    const val COVER_NAME = "cover.jpg"
    const val TEMP_PREFIX = ".下载中-"

    /** 目录/文件名的非法字符过滤，与 legacy `utils::filename_filter` 完全一致 */
    fun filenameFilter(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\\', '/', '\n' -> sb.append(' ')
                ':' -> sb.append('：')
                '*' -> sb.append('⭐')
                '?' -> sb.append('？')
                '"' -> sb.append('\'')
                '<' -> sb.append('《')
                '>' -> sb.append('》')
                '|' -> sb.append('丨')
                else -> sb.append(c)
            }
        }
        var result = sb.toString().trim()
        result = result.trimEnd('.')
        result = result.trim()
        return result
    }

    /** 渲染 `{key}` 目录模板，等价 legacy 的 strfmt */
    fun renderFmt(fmt: String, params: Comic.DirFmtParams): String {
        var out = fmt
        out = out.replace("{comic_id}", params.comic_id.toString())
        out = out.replace("{comic_title}", params.comic_title)
        out = out.replace("{author}", params.author)
        out = out.replace("{chapter_id}", params.chapter_id.toString())
        out = out.replace("{chapter_title}", params.chapter_title)
        out = out.replace("{order}", params.order.toString())
        return out
    }

    /**
     * 根据 dirFmt 计算章节下载目录，返回 null 表示目录层级不足（至少两级）。
     */
    fun chapterDownloadDir(
        downloadDir: String,
        dirFmt: String,
        params: Comic.DirFmtParams,
    ): String? {
        val parts = dirFmt.split('/').map { filenameFilter(renderFmt(it, params)) }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        return parts.fold(downloadDir) { acc, name ->
            if (acc.isEmpty()) name else "$acc/$name"
        }
    }

    /** 漫画下载目录 = 第一个章节目录的父目录 */
    fun comicDownloadDirOf(firstChapterDir: String): String {
        val idx = firstChapterDir.lastIndexOf('/')
        return if (idx > 0) firstChapterDir.substring(0, idx) else firstChapterDir
    }

    /** 临时下载目录：`.下载中-{章节目录名}`，位于正式目录的父目录下 */
    fun tempDownloadDir(chapterDownloadDir: String): String {
        val idx = chapterDownloadDir.lastIndexOf('/')
        val parent = if (idx > 0) chapterDownloadDir.substring(0, idx) else ""
        val name = chapterDownloadDir.substring(idx + 1)
        return "$parent/$TEMP_PREFIX$name"
    }

    fun extensionOf(format: DownloadFormat): String = format.extension
}
