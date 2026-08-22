package com.lanyeeee.jmcomic

import com.lanyeeee.jmcomic.data.network.JmCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class JmCryptoTest {

    @Test
    fun md5Hex_matchesKnownValue() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", JmCrypto.md5Hex("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", JmCrypto.md5Hex(""))
    }

    @Test
    fun token_isHex32() {
        val t = JmCrypto.apiToken(1234567890)
        assertEquals(32, t.length)
        assertEquals(JmCrypto.md5Hex("1234567890${JmCrypto.APP_TOKEN_SECRET}"), t)
    }

    @Test
    fun scrambleToken_usesDifferentSecret() {
        val ts = 987654321L
        assertEquals(JmCrypto.md5Hex("$ts${JmCrypto.APP_TOKEN_SECRET_2}"), JmCrypto.scrambleToken(ts))
    }

    @Test
    fun decryptData_roundTrip() {
        val ts = 1700000000L
        val plain = "{\"code\":200,\"list\":[\"a\",\"b\"]}"
        // 用 key = md5(ts + APP_DATA_SECRET) 做 AES-256-ECB PKCS7 加密，模拟 JM 服务端
        val key = JmCrypto.md5Hex("$ts${JmCrypto.APP_DATA_SECRET}").toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val data = Base64.getEncoder().encodeToString(encrypted)

        assertEquals(plain, JmCrypto.decryptData(ts, data))
    }

    @Test
    fun decryptData_usesTsDependentKey() {
        // 用错误的 ts 解密应失败（乱码或异常），证明 key 与 ts 相关
        val ts = 1700000000L
        val key = JmCrypto.md5Hex("$ts${JmCrypto.APP_DATA_SECRET}").toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val encrypted = cipher.doFinal("hello".toByteArray())
        val data = Base64.getEncoder().encodeToString(encrypted)
        // 用错误 ts 解密不会得到原文字符串（大概率异常或乱码）
        val result = runCatching { JmCrypto.decryptData(ts + 1, data) }
        assertTrue(result.isFailure || result.getOrNull() != "hello")
    }
}
