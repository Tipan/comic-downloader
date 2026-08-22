package com.lanyeeee.jmcomic.data.network

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * JM API 协议加解密，与 legacy Rust 版 (jm_client.rs) 完全一致：
 * - token = md5(ts + secret)
 * - 响应 data 字段 = Base64(AES-256-ECB 加密, key = md5(ts + APP_DATA_SECRET) 的 hex 字节, PKCS#7 填充)
 */
object JmCrypto {
    const val APP_TOKEN_SECRET = "18comicAPP"
    const val APP_TOKEN_SECRET_2 = "18comicAPPContent"
    const val APP_DATA_SECRET = "185Hcomic3PAPP7R"
    const val APP_VERSION = "2.0.13"

    fun md5Hex(data: String): String =
        MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 普通接口的 token，GetScrambleId 用 [scrambleToken] */
    fun apiToken(ts: Long): String = md5Hex("$ts$APP_TOKEN_SECRET")

    fun scrambleToken(ts: Long): String = md5Hex("$ts$APP_TOKEN_SECRET_2")

    fun decryptData(ts: Long, data: String): String {
        val encrypted = Base64.getDecoder().decode(data)
        val key = md5Hex("$ts$APP_DATA_SECRET").toByteArray(Charsets.US_ASCII) // 32 字节 hex = AES-256 key
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        val decrypted = cipher.doFinal(encrypted)
        val padding = decrypted.last().toInt()
        return String(decrypted, 0, decrypted.size - padding, Charsets.UTF_8)
    }
}
