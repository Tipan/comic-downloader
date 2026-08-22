package com.lanyeeee.jmcomic.domain.export

import com.lanyeeee.jmcomic.data.local.FileNames
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CBZ 导出（zip + ComicInfo.xml），对应 legacy `export.rs` 的 CBZ 部分 */
object CbzExporter {
    private val imageExts = setOf("jpg", "png", "webp", "gif")

    /**
     * 导出一本漫画的选中章节为 CBZ。
     * @return 导出的文件列表
     */
    fun exportCbz(comic: Comic, chapters: List<ChapterInfo>, outDir: File): List<File> {
        val result = mutableListOf<File>()
        for (chapter in chapters) {
            val chapterDir = chapter.chapterDownloadDir?.let { File(it) } ?: continue
            if (!chapterDir.exists()) continue
            val images = chapterDir.listFiles { f -> f.isFile && f.extension.lowercase() in imageExts }
                ?.sortedBy { it.name } ?: continue
            if (images.isEmpty()) continue

            val outFile = File(outDir, "${FileNames.filenameFilter(chapter.chapterTitle)}.cbz")
            runCatching {
                outFile.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                    ZipOutputStream(bos).use { zip ->
                        zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                        zip.write(buildComicInfoXml(comic, chapter).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        for (img in images) {
                            zip.putNextEntry(ZipEntry(img.name))
                            img.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                result.add(outFile)
            }
        }
        return result
    }

    private fun buildComicInfoXml(comic: Comic, chapter: ChapterInfo): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<ComicInfo>\n")
        sb.append("  <Manga>Yes</Manga>\n")
        sb.append("  <Series>").append(escapeXml(comic.name)).append("</Series>\n")
        sb.append("  <Publisher>禁漫天堂</Publisher>\n")
        sb.append("  <Writer>").append(escapeXml(comic.author.joinToString(", "))).append("</Writer>\n")
        sb.append("  <Tags>").append(escapeXml(comic.tags.joinToString(", "))).append("</Tags>\n")
        sb.append("  <Summary>").append(escapeXml(comic.description)).append("</Summary>\n")
        sb.append("  <Title>").append(escapeXml(chapter.chapterTitle)).append("</Title>\n")
        sb.append("  <Number>").append(chapter.order).append("</Number>\n")
        sb.append("  <Count>").append(comic.chapterInfos.size).append("</Count>\n")
        sb.append("</ComicInfo>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
