package com.lanyeeee.jmcomic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterInfo(
    val chapterId: Long,
    val chapterTitle: String,
    val order: Long,
    val isDownloaded: Boolean? = null,
    val chapterDownloadDir: String? = null,
)

@Serializable
data class RelatedList(
    val id: String,
    val author: String,
    val name: String,
    val image: String,
)

/**
 * 漫画领域模型，也是 `元数据.json` 的磁盘格式（camelCase，可空字段省略）。
 * 与 legacy Rust 版 `types/comic.rs` 的 Comic 序列化保持一致，兼容已有下载数据。
 */
@Serializable
data class Comic(
    val id: Long,
    val name: String,
    val addtime: String = "",
    val description: String = "",
    @SerialName("total_views") val totalViews: String = "",
    val likes: String = "",
    val chapterInfos: List<ChapterInfo> = emptyList(),
    @SerialName("series_id") val seriesId: String = "",
    @SerialName("comment_total") val commentTotal: String = "",
    val author: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val works: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    @SerialName("related_list") val relatedList: List<RelatedList> = emptyList(),
    val liked: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_aids") val isAids: Boolean = false,
    val isDownloaded: Boolean? = null,
    val comicDownloadDir: String? = null,
) {
    /** 目录模板参数（与 legacy `DirFmtParams` 字段一致） */
    data class DirFmtParams(
        val comic_id: Long,
        val comic_title: String,
        val author: String,
        val chapter_id: Long,
        val chapter_title: String,
        val order: Long,
    )

    val authorDisplay: String get() = author.joinToString(", ")

    /** 返回去掉 is_downloaded / comic_download_dir 字段的副本（用于写元数据） */
    fun stripped(): Comic = copy(
        isDownloaded = null,
        comicDownloadDir = null,
        chapterInfos = chapterInfos.map {
            it.copy(isDownloaded = null, chapterDownloadDir = null)
        },
    )
}

@Serializable
data class ComicInSearch(
    val id: Long,
    val author: String,
    val name: String,
    val image: String,
    val category: Category = Category(),
    val categorySub: CategorySub = CategorySub(),
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val updateAt: Long = 0,
    val isDownloaded: Boolean = false,
    val comicDownloadDir: String? = null,
)

@Serializable
data class SearchResult(
    val searchQuery: String = "",
    val total: Long = 0,
    val content: List<ComicInSearch> = emptyList(),
)

sealed interface SearchResp {
    data class Result(val result: SearchResult) : SearchResp
    data class Comic(val comic: com.lanyeeee.jmcomic.domain.model.Comic) : SearchResp
}

@Serializable
data class ComicInFavorite(
    val id: Long,
    val author: String = "",
    val description: String? = null,
    val name: String = "",
    val latestEp: String? = null,
    val latestEpAid: String? = null,
    val image: String = "",
    val category: Category = Category(),
    val categorySub: CategorySub = CategorySub(),
    val isDownloaded: Boolean = false,
    val comicDownloadDir: String? = null,
)

@Serializable
data class FavoriteFolder(
    @SerialName("FID") val fid: String = "",
    @SerialName("UID") val uid: String = "",
    val name: String = "",
)

@Serializable
data class FavoriteResult(
    val list: List<ComicInFavorite> = emptyList(),
    val folderList: List<FavoriteFolder> = emptyList(),
    val total: Long = 0,
    val count: Long = 0,
)

@Serializable
data class Category(
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class CategorySub(
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class ComicInWeekly(
    val id: Long = 0,
    val author: String = "",
    val description: String = "",
    val name: String = "",
    val image: String = "",
    val category: Category = Category(),
    val categorySub: CategorySub = CategorySub(),
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val updateAt: Long = 0,
    val isDownloaded: Boolean = false,
    val comicDownloadDir: String? = null,
)

@Serializable
data class WeeklyInfo(
    val categories: List<WeeklyCategory> = emptyList(),
    val types: List<WeeklyType> = emptyList(),
)

@Serializable
data class WeeklyCategory(
    val id: String,
    val title: String,
    val time: String,
)

@Serializable
data class WeeklyType(
    val id: String,
    val title: String,
)

@Serializable
data class WeeklyResult(
    val total: Long = 0,
    val list: List<ComicInWeekly> = emptyList(),
)

@Serializable
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val emailverified: String = "",
    val photo: String = "",
    val fname: String = "",
    val gender: String = "",
    val message: String? = null,
    val coin: Long = 0,
    val albumFavorites: Long = 0,
    val s: String = "",
    val levelName: String = "",
    val level: Long = 0,
    val nextLevelExp: Long = 0,
    val exp: String = "",
    val expPercent: Double = 0.0,
    val albumFavoritesMax: Long = 0,
    val adFree: Boolean = false,
    val charge: String = "",
    val jar: String = "",
    val invitationQrcode: String = "",
    val invitationUrl: String = "",
    val invitedCnt: String = "",
)

enum class DownloadTaskState {
    Pending, Downloading, Paused, Cancelled, Completed, Failed
}

enum class DownloadFormat(val extension: String) {
    Jpeg("jpg"), Png("png"), Webp("webp");

    companion object {
        fun fromExtension(ext: String): DownloadFormat? =
            entries.firstOrNull { it.extension == ext }
    }
}

enum class ProxyMode {
    System, NoProxy, Custom
}

enum class ApiDomainMode {
    Domain1, Domain2, Domain3, Domain4, Domain5, Custom
}

enum class SearchSort(val apiValue: String) {
    Latest("mr"), View("mv"), Picture("mp"), Like("tf")
}

enum class FavoriteSort(val apiValue: String) {
    FavoriteTime("mr"), UpdateTime("mp")
}

/** 单个章节下载任务的进度数据 */
data class ProgressData(
    val state: DownloadTaskState,
    val comic: Comic,
    val chapterInfo: ChapterInfo,
    val downloadedImgCount: Int,
    val totalImgCount: Int,
) {
    val percentage: Float
        get() = if (totalImgCount <= 0) 0f
        else downloadedImgCount.toFloat() / totalImgCount

    val indicator: String
        get() = when (state) {
            DownloadTaskState.Pending -> "排队中"
            DownloadTaskState.Downloading ->
                if (totalImgCount <= 0) "下载中"
                else "$downloadedImgCount/$totalImgCount"
            DownloadTaskState.Paused -> "已暂停"
            DownloadTaskState.Cancelled -> "已取消"
            DownloadTaskState.Completed -> "已完成"
            DownloadTaskState.Failed -> "下载失败"
        }
}
