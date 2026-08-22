package com.lanyeeee.jmcomic

import com.lanyeeee.jmcomic.data.local.FileNames
import com.lanyeeee.jmcomic.domain.model.Comic
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test
    fun filenameFilter_mapsIllegalChars() {
        val input = "a:b*c?d\"e<f>g|h\\i/j\nk..  "
        val expected = "a：b⭐c？d'e《f》g丨h i j k"
        assertEquals(expected, FileNames.filenameFilter(input))
    }

    @Test
    fun filenameFilter_keepsNormalChars() {
        assertEquals("第12话 有趣的日常", FileNames.filenameFilter("第12话 有趣的日常"))
    }

    @Test
    fun chapterDownloadDir_defaultFmt() {
        val dir = FileNames.chapterDownloadDir(
            downloadDir = "/storage/emulated/0/Download/漫画下载",
            dirFmt = "{comic_title}/{chapter_title}",
            params = Comic.DirFmtParams(
                comic_id = 12345,
                comic_title = "我的漫画",
                author = "作者A",
                chapter_id = 678,
                chapter_title = "第1话 开始",
                order = 1,
            ),
        )
        assertEquals("/storage/emulated/0/Download/漫画下载/我的漫画/第1话 开始", dir)
    }

    @Test
    fun chapterDownloadDir_usesAuthorFmt() {
        val dir = FileNames.chapterDownloadDir(
            downloadDir = "/x",
            dirFmt = "{author}/[{author}] {comic_title}({comic_id})/{order} - {chapter_title}",
            params = Comic.DirFmtParams(
                comic_id = 12345,
                comic_title = "T",
                author = "作者A",
                chapter_id = 678,
                chapter_title = "第一章",
                order = 2,
            ),
        )
        assertEquals("/x/作者A/[作者A] T(12345)/2 - 第一章", dir)
    }

    @Test
    fun chapterDownloadDir_rejectsSingleLevel() {
        val dir = FileNames.chapterDownloadDir(
            downloadDir = "/x",
            dirFmt = "{comic_title}",
            params = Comic.DirFmtParams(1, "T", "A", 2, "C", 1),
        )
        assertEquals(null, dir)
    }

    @Test
    fun tempDownloadDir_usesDotPrefix() {
        val temp = FileNames.tempDownloadDir("/a/漫画/第1话")
        assertEquals("/a/漫画/.下载中-第1话", temp)
    }

    @Test
    fun comicDownloadDirOf_returnsParent() {
        assertEquals("/a/漫画", FileNames.comicDownloadDirOf("/a/漫画/第1话"))
    }
}
