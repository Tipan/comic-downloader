package com.lanyeeee.jmcomic.data.network

import com.lanyeeee.jmcomic.domain.model.FavoriteSort
import com.lanyeeee.jmcomic.domain.model.ProxyMode
import com.lanyeeee.jmcomic.domain.model.SearchSort
import com.lanyeeee.jmcomic.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class JmApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JM API 客户端，与 legacy Rust 版 (jm_client.rs) 协议一致。
 * 配置变化（域名/代理）通过 provider 实时读取，无需重建 client。
 */
class JmApi(
    private val json: Json,
    private val apiDomain: () -> String,
    private val proxyMode: () -> ProxyMode,
    private val proxyHost: () -> String,
    private val proxyPort: () -> Int,
) {
    companion object {
        const val IMAGE_DOMAIN = "cdn-msp2.jmapiproxy2.cc"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        /** 封面大图 URL（旧域名 cdn-msp3.18comic.vip 已失效返回 403，改用图片 CDN） */
        fun coverUrl(comicId: Long) = "https://$IMAGE_DOMAIN/media/albums/$comicId.jpg"

        /** 封面缩略图 URL */
        fun coverThumbUrl(comicId: Long) = "https://$IMAGE_DOMAIN/media/albums/${comicId}_3x4.jpg"

        /** 章节图片 URL */
        fun chapterImageUrl(chapterId: Long, filename: String) =
            "https://$IMAGE_DOMAIN/media/photos/$chapterId/$filename"

        fun userPhotoUrl(photo: String) = "https://$IMAGE_DOMAIN/media/users/$photo"
    }

    private enum class ApiPath(val path: String, val scrambleSecret: Boolean = false) {
        Login("/login"),
        GetUserProfile("/login"),
        Search("/search"),
        GetComic("/album"),
        GetChapter("/chapter"),
        GetScrambleId("/chapter_view_template", scrambleSecret = true),
        GetFavoriteFolder("/favorite"),
        GetWeeklyInfo("/week"),
        GetWeekly("/week/filter"),
    }

    private class JmRawResponse(val status: Int, val ts: Long, val body: String)

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> = when (proxyMode()) {
                    ProxyMode.NoProxy -> listOf(Proxy.NO_PROXY)
                    ProxyMode.Custom -> listOf(
                        Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost(), proxyPort()))
                    )
                    ProxyMode.System -> {
                        val def = ProxySelector.getDefault()
                        if (def != null) def.select(uri) else listOf(Proxy.NO_PROXY)
                    }
                }

                override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {}
            })
        // 简易内存 Cookie 存储（登录态）
        val cookies = mutableMapOf<String, List<okhttp3.Cookie>>()
        builder.cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookieList: List<okhttp3.Cookie>) {
                cookies[url.host] = cookieList
            }

            override fun loadForRequest(url: HttpUrl): List<okhttp3.Cookie> =
                cookies[url.host] ?: emptyList()
        })
        builder.build()
    }

    // ---------- 通用请求 ----------

    private suspend fun jmRequest(
        method: String,
        path: ApiPath,
        query: Map<String, String>?,
        form: Map<String, String>?,
    ): JmRawResponse {
        val ts = System.currentTimeMillis() / 1000
        val token = if (path.scrambleSecret) JmCrypto.scrambleToken(ts) else JmCrypto.apiToken(ts)
        val tokenparam = "$ts,${JmCrypto.APP_VERSION}"

        val urlBuilder = HttpUrl.Builder().scheme("https").host(apiDomain())
        val pathStr = path.path.trimStart('/')
        pathStr.split('/').filter { it.isNotEmpty() }.forEach { urlBuilder.addPathSegment(it) }
        query?.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()

        val reqBuilder = Request.Builder()
            .url(url)
            .header("token", token)
            .header("tokenparam", tokenparam)
            .header("user-agent", UA)

        val request = if (form != null) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            reqBuilder.post(body).build()
        } else {
            reqBuilder.build()
        }

        // API 请求：指数退避重试，总时长约 5 秒
        val resp = withRetry(maxAttempts = 5, baseDelayMs = 1000) {
            execute(request).let { r ->
                val body = r.body?.string() ?: ""
                val status = r.code
                r.close()
                JmRawResponse(status, ts, body)
            }
        }
        return resp
    }

    private suspend fun execute(request: Request): Response =
        withContext(Dispatchers.IO) { client.newCall(request).execute() }

    private suspend fun <T> withRetry(maxAttempts: Int, baseDelayMs: Long, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw JmApiException(e.message ?: "网络请求失败", e)
                delay(baseDelayMs * attempt)
            }
        }
    }

    private fun decodeJmData(ts: Long, body: String): JsonElement {
        val jm = runCatching { json.decodeFromString(JmResp.serializer(), body) }
            .getOrElse { throw JmApiException("解析 JmResp 失败: $body") }
        if (jm.code != 200L) {
            throw JmApiException("接口错误 code=${jm.code}, msg=${jm.errorMsg}")
        }
        val dataStr = jm.data.jsonPrimitive.content
        val decrypted = JmCrypto.decryptData(ts, dataStr)
        return json.parseToJsonElement(decrypted)
    }

    private inline fun <reified T> parseDecrypted(ts: Long, body: String): T {
        val element = decodeJmData(ts, body)
        return json.decodeFromString<T>(element.toString())
    }

    private fun requireOk(status: Int, action: String, body: String) {
        if (status != 200) throw JmApiException("$action 失败，状态码($status): $body")
    }

    // ---------- 账号 ----------

    suspend fun login(username: String, password: String): UserProfile {
        val resp = jmRequest(
            "POST", ApiPath.Login, null,
            mapOf("username" to username, "password" to password),
        )
        requireOk(resp.status, "使用账号密码登录", resp.body)
        val profile = parseDecrypted<GetUserProfileRespData>(resp.ts, resp.body)
        return profile.toUserProfile()
    }

    suspend fun getUserProfile(): UserProfile {
        val resp = jmRequest("POST", ApiPath.GetUserProfile, null, null)
        if (resp.status == 401) throw JmApiException("获取用户信息失败，Cookie 无效或已过期，请重新登录")
        requireOk(resp.status, "获取用户信息", resp.body)
        val profile = parseDecrypted<GetUserProfileRespData>(resp.ts, resp.body)
        return profile.toUserProfile()
    }

    // ---------- 浏览 ----------

    suspend fun search(keyword: String, page: Long, sort: SearchSort): SearchRespRaw {
        val resp = jmRequest(
            "GET", ApiPath.Search,
            mapOf(
                "main_tag" to "0",
                "search_query" to keyword,
                "page" to page.toString(),
                "o" to sort.apiValue,
            ),
            null,
        )
        requireOk(resp.status, "搜索", resp.body)
        val element = decodeJmData(resp.ts, resp.body)
        // 优先尝试直接命中单本（RedirectRespData），否则为列表
        val redirect = runCatching {
            json.decodeFromString(RedirectRespData.serializer(), element.toString())
        }.getOrNull()
        if (redirect != null && redirect.redirectAid.isNotBlank()) {
            val comic = getComic(redirect.redirectAid.toLong())
            return SearchRespRaw.Comic(comic)
        }
        val data = json.decodeFromString(SearchRespData.serializer(), element.toString())
        return SearchRespRaw.Search(data)
    }

    suspend fun getComic(aid: Long): GetComicRespData {
        val resp = jmRequest("GET", ApiPath.GetComic, mapOf("id" to aid.toString()), null)
        requireOk(resp.status, "获取漫画", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    suspend fun getChapter(id: Long): GetChapterRespData {
        val resp = jmRequest("GET", ApiPath.GetChapter, mapOf("id" to id.toString()), null)
        requireOk(resp.status, "获取章节", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    /** 从 /chapter_view_template 的 HTML 里提取 scramble_id，失败时用默认值 */
    suspend fun getScrambleId(id: Long): Long {
        val ts = System.currentTimeMillis() / 1000
        val token = JmCrypto.scrambleToken(ts)
        val tokenparam = "$ts,${JmCrypto.APP_VERSION}"
        val urlBuilder = HttpUrl.Builder().scheme("https").host(apiDomain())
        urlBuilder.addPathSegment("chapter_view_template")
        urlBuilder.addQueryParameter("id", id.toString())
        urlBuilder.addQueryParameter("v", ts.toString())
        urlBuilder.addQueryParameter("mode", "vertical")
        urlBuilder.addQueryParameter("page", "0")
        urlBuilder.addQueryParameter("app_img_shunt", "1")
        urlBuilder.addQueryParameter("express", "off")

        val request = Request.Builder().url(urlBuilder.build())
            .header("token", token)
            .header("tokenparam", tokenparam)
            .header("user-agent", UA)
            .build()

        val body = withRetry(maxAttempts = 5, baseDelayMs = 1000) {
            execute(request).use { r ->
                if (r.code != 200) throw IOException("获取 scramble_id 失败，状态码(${r.code})")
                r.body?.string() ?: ""
            }
        }
        val scramble = body.split("var scramble_id = ").getOrNull(1)
            ?.split(';')?.firstOrNull()?.trim()?.toLongOrNull()
        return scramble ?: 220_980
    }

    suspend fun getFavoriteFolder(folderId: Long, page: Long, sort: FavoriteSort): GetFavoriteRespData {
        val resp = jmRequest(
            "GET", ApiPath.GetFavoriteFolder,
            mapOf("page" to page.toString(), "o" to sort.apiValue, "folder_id" to folderId.toString()),
            null,
        )
        requireOk(resp.status, "获取收藏夹", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    suspend fun toggleFavorite(aid: Long): ToggleFavoriteRespData {
        val resp = jmRequest("POST", ApiPath.GetFavoriteFolder, null, mapOf("aid" to aid.toString()))
        requireOk(resp.status, "收藏/取消收藏", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    suspend fun getWeeklyInfo(): GetWeeklyInfoRespData {
        val resp = jmRequest("GET", ApiPath.GetWeeklyInfo, null, null)
        requireOk(resp.status, "获取每周必看信息", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    suspend fun getWeekly(categoryId: String, typeId: String): GetWeeklyRespData {
        val resp = jmRequest(
            "GET", ApiPath.GetWeekly,
            mapOf("id" to categoryId, "type" to typeId),
            null,
        )
        requireOk(resp.status, "获取每周必看", resp.body)
        return parseDecrypted(resp.ts, resp.body)
    }

    // ---------- 图片下载 ----------

    /** 下载图片字节，空 body 时带 ?ts= 重试一次防缓存 */
    suspend fun downloadImage(url: String): ByteArray {
        val request = Request.Builder().url(url).header("user-agent", UA).build()
        val data = withRetry(maxAttempts = 3, baseDelayMs = 500) {
            execute(request).use { r ->
                if (r.code != 200) throw IOException("下载图片失败，状态码(${r.code})")
                r.body?.bytes() ?: throw IOException("下载图片失败，响应为空")
            }
        }
        if (data.isNotEmpty()) return data
        // 空 body 说明 JM 缓存失效，带时间戳重试
        val ts = System.currentTimeMillis() / 1000
        val retryUrl = url.toHttpUrl().newBuilder().addQueryParameter("ts", ts.toString()).build()
        return withRetry(maxAttempts = 3, baseDelayMs = 500) {
            execute(Request.Builder().url(retryUrl).header("user-agent", UA).build()).use { r ->
                if (r.code != 200) throw IOException("下载图片失败，状态码(${r.code})")
                r.body?.bytes() ?: throw IOException("下载图片失败，响应为空")
            }
        }
    }
}

sealed interface SearchRespRaw {
    data class Search(val data: SearchRespData) : SearchRespRaw
    data class Comic(val comic: GetComicRespData) : SearchRespRaw
}

private fun GetUserProfileRespData.toUserProfile(): UserProfile = UserProfile(
    uid = uid,
    username = username,
    email = email,
    emailverified = emailverified,
    photo = if (photo.isBlank()) "" else JmApi.userPhotoUrl(photo),
    fname = fname,
    gender = gender,
    message = message,
    coin = coin,
    albumFavorites = albumFavorites,
    s = s,
    levelName = levelName,
    level = level,
    nextLevelExp = nextLevelExp,
    exp = exp,
    expPercent = expPercent,
    albumFavoritesMax = albumFavoritesMax,
    adFree = adFree,
    charge = charge,
    jar = jar,
    invitationQrcode = invitationQrcode,
    invitationUrl = invitationUrl,
    invitedCnt = invitedCnt,
)
