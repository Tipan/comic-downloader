package com.lanyeeee.jmcomic

import com.lanyeeee.jmcomic.domain.download.Stitch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StitchTest {

    @Test
    fun calculateBlockNum_newerChapterGivesEvenBlockCount() {
        // id >= 421926: block_num = (last_hex % x) * 2 + 2, x=8 → 2..16 之间的偶数
        val scrambleId = 500_000L
        val chapterId = 600_000L
        repeat(50) { i ->
            val n = Stitch.calculateBlockNum(scrambleId, chapterId, "img_$i")
            assertTrue("block=$n", n >= 2 && n <= 16 && n % 2 == 0)
        }
    }

    @Test
    fun calculateBlockNum_oldChapterAlwaysTen() {
        // scrambleId < id < 268850 → 10
        val n = Stitch.calculateBlockNum(200_000L, 250_000L, "anything")
        assertEquals(10, n)
    }

    @Test
    fun calculateBlockNum_beforeScrambleIdZero() {
        val n = Stitch.calculateBlockNum(500_000L, 100_000L, "anything")
        assertEquals(0, n)
    }

    @Test
    fun computeBlocks_coverWholeHeightWithoutGap() {
        val height = 100
        val blockNum = 7
        val blocks = Stitch.computeBlocks(height, blockNum)
        // dst 行号集合恰好覆盖 0..height
        val rows = HashSet<Int>()
        for ((_, blockHeight, dstYStart) in blocks) {
            for (y in 0 until blockHeight) rows.add(dstYStart + y)
        }
        assertEquals(height, rows.size)
        for (y in 0 until height) assertTrue("row $y", rows.contains(y))
    }

    @Test
    fun stitch_restoresReversedBlocks() {
        val width = 4
        val height = 13 // 余数场景
        val blockNum = 5

        // 原始图 O：每行像素值 = 行号
        val original = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) original[y * width + x] = y
        }

        // 用 stitch 映射的逆映射构造乱序图 S：
        // dst 的第 i 块来自 src 的第 i 块，所以 scrambled[srcYStart_i..] = original[dstYStart_i..]
        val blocks = Stitch.computeBlocks(height, blockNum)
        val scrambled = IntArray(width * height)
        for ((srcYStart, blockHeight, dstYStart) in blocks) {
            for (y in 0 until blockHeight) {
                System.arraycopy(original, (dstYStart + y) * width, scrambled, (srcYStart + y) * width, width)
            }
        }
        // blockNum>1 时确实发生了乱序
        assertTrue(!scrambled.contentEquals(original))

        val restored = Stitch.stitchPixels(scrambled, width, height, blockNum)
        assertArrayEquals(original, restored)
    }

    @Test
    fun stitch_identityWhenBlockNumOne() {
        val width = 3
        val height = 9
        val pixels = IntArray(width * height) { it }
        assertArrayEquals(pixels, Stitch.stitchPixels(pixels, width, height, 1))
    }
}
