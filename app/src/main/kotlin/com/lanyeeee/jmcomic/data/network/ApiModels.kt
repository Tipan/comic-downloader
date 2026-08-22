package com.lanyeeee.jmcomic.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** 兼容 JM 接口里"字符串或数字"的 Long 字段 */
object StringOrNumberLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNumberLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        return when (element) {
            is JsonPrimitive -> element.content.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}

/** 兼容 JM 接口里"字符串或数字"的 String 字段（如 redirect_aid 返回数字） */
object StringOrNumberStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNumberString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())
        return when (element) {
            is JsonPrimitive -> element.content
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class JmResp(
    val code: Long,
    val data: JsonElement,
    @SerialName("error_msg") val errorMsg: String = "",
)

@Serializable
data class SeriesRespData(
    val id: String,
    val name: String,
    val sort: String = "",
)

@Serializable
data class CategoryRespData(
    val id: String? = null,
    val title: String? = null,
)

@Serializable
data class CategorySubRespData(
    val id: String? = null,
    val title: String? = null,
)

// ---------- 漫画详情 / 章节 ----------

@Serializable
data class RelatedListRespData(
    val id: String,
    val author: String,
    val name: String,
    val image: String,
)

@Serializable
data class GetComicRespData(
    val id: Long,
    val name: String,
    val addtime: String,
    val description: String,
    @SerialName("total_views") val totalViews: String,
    val likes: String,
    val series: List<SeriesRespData> = emptyList(),
    @SerialName("series_id") val seriesId: String = "",
    @SerialName("comment_total") val commentTotal: String = "",
    val author: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val works: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    @SerialName("related_list") val relatedList: List<RelatedListRespData> = emptyList(),
    val liked: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_aids") val isAids: Boolean = false,
)

@Serializable
data class GetChapterRespData(
    val id: Long,
    val series: List<SeriesRespData> = emptyList(),
    val tags: String = "",
    val name: String = "",
    val images: List<String> = emptyList(),
    val addtime: String = "",
    @SerialName("series_id") val seriesId: String = "",
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val liked: Boolean = false,
)

// ---------- 搜索 ----------

@Serializable
data class RedirectRespData(
    @SerialName("search_query") val searchQuery: String,
    val total: Long,
    @SerialName("redirect_aid")
    @Serializable(with = StringOrNumberStringSerializer::class)
    val redirectAid: String,
)

@Serializable
data class ComicInSearchRespData(
    val id: String,
    val author: String,
    val name: String,
    val image: String,
    val category: CategoryRespData = CategoryRespData(),
    @SerialName("category_sub") val categorySub: CategorySubRespData = CategorySubRespData(),
    val liked: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("update_at") val updateAt: Long = 0,
)

@Serializable
data class SearchRespData(
    @SerialName("search_query") val searchQuery: String = "",
    @Serializable(with = StringOrNumberLongSerializer::class) val total: Long = 0,
    val content: List<ComicInSearchRespData> = emptyList(),
)

// ---------- 收藏夹 ----------

@Serializable
data class ComicInFavoriteRespData(
    val id: String,
    val author: String,
    val description: String? = null,
    val name: String,
    @SerialName("latest_ep") val latestEp: String? = null,
    @SerialName("latest_ep_aid") val latestEpAid: String? = null,
    val image: String,
    val category: CategoryRespData = CategoryRespData(),
    @SerialName("category_sub") val categorySub: CategorySubRespData = CategorySubRespData(),
)

@Serializable
data class FavoriteFolderRespData(
    @SerialName("FID") val fid: String,
    @SerialName("UID") val uid: String,
    val name: String,
)

@Serializable
data class GetFavoriteRespData(
    val list: List<ComicInFavoriteRespData> = emptyList(),
    @SerialName("folder_list") val folderList: List<FavoriteFolderRespData> = emptyList(),
    val total: String = "",
    val count: Long = 0,
)

@Serializable
data class ToggleFavoriteRespData(
    val status: String = "",
    val msg: String = "",
    @SerialName("type") val toggleType: ToggleType = ToggleType.Add,
)

@Serializable
enum class ToggleType {
    @SerialName("add") Add,
    @SerialName("remove") Remove,
}

// ---------- 每周必看 ----------

@Serializable
data class CategoryInWeeklyInfo(
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
data class GetWeeklyInfoRespData(
    val categories: List<CategoryInWeeklyInfo> = emptyList(),
    @SerialName("type") val types: List<WeeklyType> = emptyList(),
)

@Serializable
data class ComicInWeeklyRespData(
    @Serializable(with = StringOrNumberLongSerializer::class) val id: Long = 0,
    val author: String = "",
    val description: String = "",
    val name: String = "",
    val image: String = "",
    val category: CategoryRespData = CategoryRespData(),
    @SerialName("category_sub") val categorySub: CategorySubRespData = CategorySubRespData(),
    val liked: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("update_at") val updateAt: Long = 0,
)

@Serializable
data class GetWeeklyRespData(
    val total: Long = 0,
    val list: List<ComicInWeeklyRespData> = emptyList(),
)

// ---------- 用户 ----------

@Serializable
data class GetUserProfileRespData(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val emailverified: String = "",
    val photo: String = "",
    val fname: String = "",
    val gender: String = "",
    val message: String? = null,
    @Serializable(with = StringOrNumberLongSerializer::class) val coin: Long = 0,
    @SerialName("album_favorites") val albumFavorites: Long = 0,
    val s: String = "",
    @SerialName("level_name") val levelName: String = "",
    val level: Long = 0,
    @SerialName("next_level_exp") val nextLevelExp: Long = 0,
    val exp: String = "",
    @SerialName("exp_percent") val expPercent: Double = 0.0,
    @SerialName("album_favorites_max") val albumFavoritesMax: Long = 0,
    @SerialName("ad_free") val adFree: Boolean = false,
    val charge: String = "",
    val jar: String = "",
    @SerialName("invitation_qrcode") val invitationQrcode: String = "",
    @SerialName("invitation_url") val invitationUrl: String = "",
    @SerialName("invited_cnt") val invitedCnt: String = "",
)
