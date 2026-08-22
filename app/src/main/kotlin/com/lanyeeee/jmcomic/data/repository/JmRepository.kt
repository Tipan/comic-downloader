package com.lanyeeee.jmcomic.data.repository

import com.lanyeeee.jmcomic.data.local.MetadataStore
import com.lanyeeee.jmcomic.data.network.GetComicRespData
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.data.network.SearchRespRaw
import com.lanyeeee.jmcomic.domain.model.Category
import com.lanyeeee.jmcomic.domain.model.CategorySub
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.domain.model.ComicInFavorite
import com.lanyeeee.jmcomic.domain.model.ComicInSearch
import com.lanyeeee.jmcomic.domain.model.ComicInWeekly
import com.lanyeeee.jmcomic.domain.model.Config
import com.lanyeeee.jmcomic.domain.model.FavoriteResult
import com.lanyeeee.jmcomic.domain.model.FavoriteSort
import com.lanyeeee.jmcomic.domain.model.RelatedList
import com.lanyeeee.jmcomic.domain.model.SearchResp
import com.lanyeeee.jmcomic.domain.model.SearchSort
import com.lanyeeee.jmcomic.domain.model.UserProfile
import com.lanyeeee.jmcomic.domain.model.WeeklyInfo
import com.lanyeeee.jmcomic.domain.model.WeeklyResult
import java.io.File

/**
 * 仓库层：把 API 原始响应转换为领域模型，并用本地库存打 isDownloaded 标记。
 */
class JmRepository(
    private val api: JmApi,
    private val configProvider: () -> Config,
) {
    private fun downloadDir(): File = File(configProvider().downloadDir)

    private fun idToDirMap(): Map<Long, File> = MetadataStore.createIdToDirMap(downloadDir())

    // ---------- 模型转换 ----------

    fun convertComic(dto: GetComicRespData): Comic {
        val chapterInfos = dto.series.mapIndexedNotNull { index, s ->
            val chapterId = s.id.toLongOrNull() ?: return@mapIndexedNotNull null
            val order = (index + 1).toLong()
            val title = if (s.name.isBlank()) "第${order}话" else "第${order}话 ${s.name}"
            ChapterInfo(chapterId = chapterId, chapterTitle = title, order = order)
        }
        val infos = if (chapterInfos.isEmpty()) {
            listOf(ChapterInfo(chapterId = dto.id, chapterTitle = "第1话", order = 1))
        } else chapterInfos
        return Comic(
            id = dto.id,
            name = dto.name,
            addtime = dto.addtime,
            description = dto.description,
            totalViews = dto.totalViews,
            likes = dto.likes,
            chapterInfos = infos,
            seriesId = dto.seriesId,
            commentTotal = dto.commentTotal,
            author = dto.author,
            tags = dto.tags,
            works = dto.works,
            actors = dto.actors,
            relatedList = dto.relatedList.map {
                RelatedList(it.id, it.author, it.name, it.image)
            },
            liked = dto.liked,
            isFavorite = dto.isFavorite,
            isAids = dto.isAids,
        )
    }

    /** 根据本地库存给漫画打 isDownloaded / chapterDownloadDir 标记 */
    fun applyDownloadStatus(comic: Comic): Comic {
        val dir = idToDirMap()[comic.id] ?: return comic
        val chapterDirs = MetadataStore.findChapterMetadataDirs(dir)
        return comic.copy(
            comicDownloadDir = dir.absolutePath,
            isDownloaded = true,
            chapterInfos = comic.chapterInfos.map { ch ->
                val cd = chapterDirs[ch.chapterId]
                if (cd != null) ch.copy(isDownloaded = true, chapterDownloadDir = cd.absolutePath) else ch
            },
        )
    }

    private fun toComicInSearch(raw: com.lanyeeee.jmcomic.data.network.ComicInSearchRespData): ComicInSearch {
        val dir = idToDirMap()[raw.id.toLongOrNull() ?: -1]
        return ComicInSearch(
            id = raw.id.toLongOrNull() ?: 0,
            author = raw.author,
            name = raw.name,
            image = raw.image,
            category = Category(raw.category.id, raw.category.title),
            categorySub = CategorySub(raw.categorySub.id, raw.categorySub.title),
            liked = raw.liked,
            isFavorite = raw.isFavorite,
            updateAt = raw.updateAt,
            isDownloaded = dir != null,
            comicDownloadDir = dir?.absolutePath,
        )
    }

    private fun toComicInFavorite(raw: com.lanyeeee.jmcomic.data.network.ComicInFavoriteRespData): ComicInFavorite {
        val dir = idToDirMap()[raw.id.toLongOrNull() ?: -1]
        return ComicInFavorite(
            id = raw.id.toLongOrNull() ?: 0,
            author = raw.author,
            description = raw.description,
            name = raw.name,
            latestEp = raw.latestEp,
            latestEpAid = raw.latestEpAid,
            image = raw.image,
            category = Category(raw.category.id, raw.category.title),
            categorySub = CategorySub(raw.categorySub.id, raw.categorySub.title),
            isDownloaded = dir != null,
            comicDownloadDir = dir?.absolutePath,
        )
    }

    private fun toComicInWeekly(raw: com.lanyeeee.jmcomic.data.network.ComicInWeeklyRespData): ComicInWeekly {
        val dir = idToDirMap()[raw.id]
        return ComicInWeekly(
            id = raw.id,
            author = raw.author,
            description = raw.description,
            name = raw.name,
            image = raw.image,
            category = Category(raw.category.id, raw.category.title),
            categorySub = CategorySub(raw.categorySub.id, raw.categorySub.title),
            liked = raw.liked,
            isFavorite = raw.isFavorite,
            updateAt = raw.updateAt,
            isDownloaded = dir != null,
            comicDownloadDir = dir?.absolutePath,
        )
    }

    // ---------- 数据接口 ----------

    suspend fun login(username: String, password: String): UserProfile =
        api.login(username, password)

    suspend fun getUserProfile(): UserProfile = api.getUserProfile()

    suspend fun search(keyword: String, page: Long, sort: SearchSort): SearchResp =
        when (val raw = api.search(keyword, page, sort)) {
            is SearchRespRaw.Search -> SearchResp.Result(
                com.lanyeeee.jmcomic.domain.model.SearchResult(
                    searchQuery = raw.data.searchQuery,
                    total = raw.data.total,
                    content = raw.data.content.map { toComicInSearch(it) },
                )
            )
            is SearchRespRaw.Comic -> SearchResp.Comic(applyDownloadStatus(convertComic(raw.comic)))
        }

    suspend fun getComic(aid: Long): Comic =
        applyDownloadStatus(convertComic(api.getComic(aid)))

    suspend fun getFavoriteFolder(folderId: Long, page: Long, sort: FavoriteSort): FavoriteResult {
        val resp = api.getFavoriteFolder(folderId, page, sort)
        return FavoriteResult(
            list = resp.list.map { toComicInFavorite(it) },
            folderList = resp.folderList.map {
                com.lanyeeee.jmcomic.domain.model.FavoriteFolder(fid = it.fid, uid = it.uid, name = it.name)
            },
            total = resp.total.toLongOrNull() ?: 0,
            count = resp.count,
        )
    }

    suspend fun toggleFavorite(aid: Long): Boolean =
        api.toggleFavorite(aid).toggleType == com.lanyeeee.jmcomic.data.network.ToggleType.Remove

    suspend fun getWeeklyInfo(): WeeklyInfo {
        val resp = api.getWeeklyInfo()
        return WeeklyInfo(
            categories = resp.categories.map { com.lanyeeee.jmcomic.domain.model.WeeklyCategory(it.id, it.title, it.time) },
            types = resp.types.map { com.lanyeeee.jmcomic.domain.model.WeeklyType(it.id, it.title) },
        )
    }

    suspend fun getWeekly(categoryId: String, typeId: String): WeeklyResult {
        val resp = api.getWeekly(categoryId, typeId)
        return WeeklyResult(
            total = resp.total,
            list = resp.list.map { toComicInWeekly(it) },
        )
    }

    fun getDownloadedComics(): List<Comic> = MetadataStore.listDownloadedComics(downloadDir())
}
