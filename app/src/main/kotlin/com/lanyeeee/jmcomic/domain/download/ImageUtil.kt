package com.lanyeeee.jmcomic.domain.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lanyeeee.jmcomic.domain.model.DownloadFormat
import java.io.ByteArrayOutputStream

object ImageUtil {
    enum class Format { JPEG, PNG, WEBP, GIF, UNKNOWN }

    /** 通过魔数判断图片格式（等价 legacy `image::guess_format`） */
    fun detectFormat(bytes: ByteArray): Format {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) return Format.JPEG
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) return Format.PNG
        if (bytes.size >= 4 &&
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()
        ) return Format.GIF
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) return Format.WEBP
        return Format.UNKNOWN
    }

    fun decode(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    /** 编码为指定格式（PNG/WebP 无损，JPEG 有损质量 90），与 legacy image crate 输出一致 */
    fun encode(bitmap: Bitmap, format: DownloadFormat): ByteArray {
        val out = ByteArrayOutputStream()
        val (cf, quality) = when (format) {
            DownloadFormat.Jpeg -> Bitmap.CompressFormat.JPEG to 90
            DownloadFormat.Png -> Bitmap.CompressFormat.PNG to 100
            DownloadFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY to 90
        }
        bitmap.compress(cf, quality, out)
        return out.toByteArray()
    }
}
