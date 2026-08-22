package com.lanyeeee.jmcomic.domain.download

import android.graphics.Bitmap
import com.lanyeeee.jmcomic.data.network.JmCrypto

/**
 * JM 图片反切片（防爬分块乱序）算法，与 legacy `download_manager.rs` 完全一致：
 * - [calculateBlockNum]：计算一张 webp 图片被切成了几块
 * - [stitch]：把乱序分块逆序拼接还原整图
 */
object Stitch {
    /**
     * 计算分块数（与 legacy `calculate_block_num` 一致）。
     * @param scrambleId 章节的 scramble_id
     * @param chapterId 章节 id
     * @param filenameWithoutExt 图片文件名（不含扩展名）
     */
    fun calculateBlockNum(scrambleId: Long, chapterId: Long, filenameWithoutExt: String): Int {
        if (chapterId < scrambleId) return 0
        if (chapterId < 268_850) return 10
        val x = if (chapterId < 421_926) 10 else 8
        val s = JmCrypto.md5Hex("$chapterId$filenameWithoutExt")
        val blockNum = s.last().code
        return (blockNum % x) * 2 + 2
    }

    /**
     * 计算每个分块的 (源图起始Y, 块高, 目标图起始Y)。
     * 纯逻辑，可单测。
     */
    fun computeBlocks(height: Int, blockNum: Int): List<IntArray> {
        val blocks = ArrayList<IntArray>(blockNum)
        val remainder = height % blockNum
        val baseBlock = height / blockNum
        for (i in 0 until blockNum) {
            var blockHeight = baseBlock
            val srcYStart = height - (baseBlock * (i + 1)) - remainder
            var dstYStart = baseBlock * i
            if (i == 0) {
                blockHeight += remainder
            } else {
                dstYStart += remainder
            }
            blocks.add(intArrayOf(srcYStart, blockHeight, dstYStart))
        }
        return blocks
    }

    /**
     * 纯像素级反切片拼接（可 JVM 单测）：srcPixels 是乱序图，返回还原图。
     */
    fun stitchPixels(srcPixels: IntArray, width: Int, height: Int, blockNum: Int): IntArray {
        val dstPixels = IntArray(width * height)
        for ((srcYStart, blockHeight, dstYStart) in computeBlocks(height, blockNum)) {
            for (y in 0 until blockHeight) {
                val srcOff = (srcYStart + y) * width
                val dstOff = (dstYStart + y) * width
                System.arraycopy(srcPixels, srcOff, dstPixels, dstOff, width)
            }
        }
        return dstPixels
    }

    /**
     * 将乱序分块的位图逆序拼接还原。src 是乱序图，dst 是还原图。
     */
    fun stitch(src: Bitmap, blockNum: Int): Bitmap {
        if (blockNum <= 0) return src
        val width = src.width
        val height = src.height
        val dst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)
        val dstPixels = stitchPixels(srcPixels, width, height, blockNum)
        dst.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return dst
    }
}
