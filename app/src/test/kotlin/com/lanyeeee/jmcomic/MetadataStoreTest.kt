package com.lanyeeee.jmcomic

import com.lanyeeee.jmcomic.data.local.FileNames
import com.lanyeeee.jmcomic.data.local.MetadataStore
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MetadataStoreTest {
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "jmtest_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun sampleComic(): Comic = Comic(
        id = 999,
        name = "测试漫画",
        addtime = "2024-01-01",
        description = "desc",
        totalViews = "12345",
        likes = "100",
        chapterInfos = listOf(
            ChapterInfo(chapterId = 1001, chapterTitle = "第1话", order = 1),
            ChapterInfo(chapterId = 1002, chapterTitle = "第2话", order = 2),
        ),
        seriesId = "999",
        commentTotal = "5",
        author = listOf("作者A", "作者B"),
        tags = listOf("tag1", "tag2"),
        works = emptyList(),
        actors = emptyList(),
        relatedList = emptyList(),
        liked = true,
        isFavorite = true,
        isAids = false,
        isDownloaded = true,
        comicDownloadDir = tempDir.absolutePath,
    )

    @Test
    fun saveAndLoadComicMetadata_roundTrip() {
        val comic = sampleComic()
        assertTrue(MetadataStore.saveComicMetadata(comic))

        val file = File(tempDir, FileNames.COMIC_METADATA)
        assertTrue(file.exists())
        val text = file.readText()
        // 序列化时省略了 is_downloaded / comic_download_dir / 章节下载路径
        assertTrue("should not contain is_downloaded", !text.contains("is_downloaded"))
        assertTrue("should not contain comic_download_dir", !text.contains("comic_download_dir"))
        assertTrue("should not contain chapter_download_dir", !text.contains("chapter_download_dir"))
        // camelCase 字段
        assertTrue(text.contains("\"chapterInfos\""))
        assertTrue(text.contains("\"total_views\""))

        val loaded = MetadataStore.loadComicFromMetadata(file)
        assertNotNull(loaded)
        assertEquals(999L, loaded!!.id)
        assertEquals("测试漫画", loaded.name)
        assertEquals(2, loaded.chapterInfos.size)
        assertEquals(1002L, loaded.chapterInfos[1].chapterId)
        // 加载后回填下载状态
        assertEquals(true, loaded.isDownloaded)
        assertEquals(tempDir.absolutePath, loaded.comicDownloadDir)
    }

    @Test
    fun saveChapterMetadata_roundTrip() {
        val chapterDir = File(tempDir, "第1话")
        val chapter = ChapterInfo(
            chapterId = 1001,
            chapterTitle = "第1话",
            order = 1,
            isDownloaded = true,
            chapterDownloadDir = chapterDir.absolutePath,
        )
        assertTrue(MetadataStore.saveChapterMetadata(chapter))
        val file = File(chapterDir, FileNames.CHAPTER_METADATA)
        val text = file.readText()
        assertTrue(!text.contains("is_downloaded"))
        assertTrue(!text.contains("chapter_download_dir"))
        assertTrue(text.contains("\"chapterId\""))
    }

    @Test
    fun createIdToDirMap_and_listDownloadedComics() {
        val comic = sampleComic()
        MetadataStore.saveComicMetadata(comic)

        val map = MetadataStore.createIdToDirMap(tempDir)
        assertEquals(1, map.size)
        assertEquals(tempDir.absolutePath, map[999L]?.absolutePath)

        val list = MetadataStore.listDownloadedComics(tempDir)
        assertEquals(1, list.size)
        assertEquals("测试漫画", list[0].name)
    }

    @Test
    fun loadComic_ignoresUnknownFields() {
        // 模拟旧版本元数据多出字段
        val comic = sampleComic()
        val json = MetadataStore.prettyJson.encodeToString(
            com.lanyeeee.jmcomic.domain.model.Comic.serializer(), comic.stripped()
        ).replace("}", ",\"unknownFutureField\":123}")
        val file = File(tempDir, FileNames.COMIC_METADATA)
        file.writeText(json)
        val loaded = MetadataStore.loadComicFromMetadata(file)
        assertNotNull(loaded)
        assertEquals(999L, loaded!!.id)
    }

    @Test
    fun oldVersionChapterDirWithoutMetadata_isBackfilled() {
        val comic = sampleComic()
        val chapterDir = File(tempDir, "第1话")
        chapterDir.mkdirs()
        // 旧版本：章节目录存在但没有 章节元数据.json
        MetadataStore.saveComicMetadata(comic)
        assertTrue(!File(chapterDir, FileNames.CHAPTER_METADATA).exists())

        val loaded = MetadataStore.loadComicFromMetadata(File(tempDir, FileNames.COMIC_METADATA))
        assertNotNull(loaded)
        // 回填后章节标记为已下载
        val ch = loaded!!.chapterInfos.firstOrNull { it.chapterId == 1001L }
        assertNotNull(ch)
        assertEquals(true, ch!!.isDownloaded)
        assertEquals(chapterDir.absolutePath, ch.chapterDownloadDir)
    }
}
