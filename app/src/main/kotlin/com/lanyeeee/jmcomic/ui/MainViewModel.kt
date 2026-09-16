package com.lanyeeee.jmcomic.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanyeeee.jmcomic.JmApplication
import com.lanyeeee.jmcomic.data.local.AppLogger
import com.lanyeeee.jmcomic.data.local.LocalFavoritesStore
import com.lanyeeee.jmcomic.data.local.StoragePermissions
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.domain.model.ComicInFavorite
import com.lanyeeee.jmcomic.domain.model.ComicInSearch
import com.lanyeeee.jmcomic.domain.model.ComicInWeekly
import com.lanyeeee.jmcomic.domain.model.Config
import com.lanyeeee.jmcomic.domain.model.FavoriteFolder
import com.lanyeeee.jmcomic.domain.model.FavoriteSort
import com.lanyeeee.jmcomic.domain.model.SearchResp
import com.lanyeeee.jmcomic.domain.model.SearchSort
import com.lanyeeee.jmcomic.domain.model.UserProfile
import com.lanyeeee.jmcomic.domain.model.WeeklyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 屏幕导航 */
sealed interface Screen {
    data object Main : Screen
    data class ComicDetail(val comicId: Long) : Screen
    data class Reader(val comic: Comic, val chapter: ChapterInfo) : Screen
    data object Downloaded : Screen
    data object JmFavorites : Screen
}

enum class MainTab(val title: String) {
    Search("搜索"), Favorite("收藏"), Weekly("每周必看"), Download("下载"), Mine("我的")
}

data class SearchUiState(
    val keyword: String = "",
    val sort: SearchSort = SearchSort.Latest,
    val results: List<ComicInSearch> = emptyList(),
    val page: Long = 0,
    val total: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

data class FavoriteUiState(
    val folderId: Long = 0,
    val folders: List<FavoriteFolder> = emptyList(),
    val results: List<ComicInFavorite> = emptyList(),
    val sort: FavoriteSort = FavoriteSort.FavoriteTime,
    val page: Long = 0,
    val total: Long = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

data class WeeklyUiState(
    val info: WeeklyInfo? = null,
    val categoryId: String = "",
    val typeId: String = "",
    val results: List<ComicInWeekly> = emptyList(),
    val loading: Boolean = false,
    val loadingComics: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val container = (app as JmApplication).container
    val repository = container.repository
    val downloadManager = container.downloadManager

    private val _config = MutableStateFlow(container.config.value)
    val config: StateFlow<Config> = _config.asStateFlow()
    fun updateConfig(c: Config) {
        container.updateConfig(c)
        _config.value = c
    }

    // ---------- 存储权限 ----------
    private val _hasStoragePermission = MutableStateFlow(
        StoragePermissions.hasPermission(app) &&
            StoragePermissions.canWriteDir(java.io.File(container.config.value.downloadDir))
    )
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()
    fun refreshStoragePermission() {
        _hasStoragePermission.value = StoragePermissions.hasPermission(getApplication())
        // 回到前台时同步一次已下载索引（防外部删改导致的过期状态）
        container.refreshDownloadIndex()
    }

    // ---------- 导航 ----------
    private val _screen = MutableStateFlow<Screen>(Screen.Main)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val backStack = ArrayDeque<Screen>()

    fun navigate(s: Screen) {
        if (s == _screen.value) return
        backStack.addLast(_screen.value)
        _screen.value = s
    }

    fun back() {
        if (backStack.isEmpty()) {
            _screen.value = Screen.Main
        } else {
            _screen.value = backStack.removeLast()
        }
    }

    // ---------- 列表滚动位置（从详情页返回时恢复，避免回到顶部） ----------
    val searchGridState = LazyGridState()
    val weeklyGridState = LazyGridState()
    val localFavoritesGridState = LazyGridState()
    val jmFavoritesGridState = LazyGridState()
    val downloadedListState = LazyListState()
    val comicDetailListState = LazyListState()

    // 各列表"重置轮次"：搜索/排序/分类变化时 +1，界面据此滚回顶部
    private var _searchEpoch = 0
    val searchEpoch: Int get() = _searchEpoch
    private var _favoriteEpoch = 0
    val favoriteEpoch: Int get() = _favoriteEpoch
    private var _weeklyEpoch = 0
    val weeklyEpoch: Int get() = _weeklyEpoch

    // 当前底部 tab（从详情页返回时保留，不重置回搜索）
    private val _currentTab = MutableStateFlow(MainTab.Search)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()
    fun setCurrentTab(tab: MainTab) {
        if (_currentTab.value != tab) _currentTab.value = tab
    }

    // ---------- 搜索 ----------
    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    fun setSearchKeyword(keyword: String) {
        _search.value = _search.value.copy(keyword = keyword)
    }

    fun setSearchSort(sort: SearchSort) {
        _search.value = _search.value.copy(sort = sort)
        search(reset = true)
    }

    fun search(reset: Boolean) {
        val state = _search.value
        if (state.keyword.isBlank()) return
        if (state.loading || state.loadingMore) return
        val page = if (reset) 1L else state.page + 1
        if (reset) _searchEpoch++
        viewModelScope.launch {
            _search.value = if (reset) state.copy(loading = true, error = null) else state.copy(loadingMore = true)
            try {
                val resp = withContext(Dispatchers.IO) { repository.search(state.keyword, page, state.sort) }
                when (resp) {
                    is SearchResp.Result -> {
                        val result = resp.result
                        val all = if (reset) result.content else state.results + result.content
                        _search.value = _search.value.copy(
                            results = all,
                            page = page,
                            total = result.total,
                            loading = false,
                            loadingMore = false,
                        )
                    }
                    is SearchResp.Comic -> {
                        _search.value = _search.value.copy(loading = false, loadingMore = false)
                        navigate(Screen.ComicDetail(resp.comic.id))
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("VM", "搜索失败 keyword=${state.keyword}", e)
                _search.value = _search.value.copy(
                    loading = false, loadingMore = false, error = e.message ?: "搜索失败"
                )
            }
        }
    }

    // ---------- 收藏夹 ----------
    private val _favorite = MutableStateFlow(FavoriteUiState())
    val favorite: StateFlow<FavoriteUiState> = _favorite.asStateFlow()

    fun refreshFavorite(reset: Boolean = true) {
        val state = _favorite.value
        if (state.loading || state.loadingMore) return
        val page = if (reset) 1L else state.page + 1
        if (reset) _favoriteEpoch++
        viewModelScope.launch {
            _favorite.value = if (reset) state.copy(loading = true, error = null) else state.copy(loadingMore = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getFavoriteFolder(state.folderId, page, state.sort)
                }
                val all = if (reset) result.list else state.results + result.list
                _favorite.value = _favorite.value.copy(
                    folders = result.folderList,
                    results = all,
                    page = page,
                    total = result.total,
                    loading = false,
                    loadingMore = false,
                )
            } catch (e: Exception) {
                AppLogger.error("VM", "获取收藏夹失败 folder=${state.folderId}", e)
                _favorite.value = _favorite.value.copy(
                    loading = false, loadingMore = false, error = e.message ?: "获取收藏夹失败"
                )
            }
        }
    }

    fun setFavoriteFolder(folderId: Long) {
        _favorite.value = _favorite.value.copy(folderId = folderId)
        refreshFavorite(true)
    }

    fun setFavoriteSort(sort: FavoriteSort) {
        _favorite.value = _favorite.value.copy(sort = sort)
        refreshFavorite(true)
    }

    // 下载整个收藏夹进度
    private val _batchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchProgress: StateFlow<Pair<Int, Int>?> = _batchProgress.asStateFlow()

    fun downloadAllFavorites() {
        viewModelScope.launch {
            _batchProgress.value = 0 to 0
            runCatching {
                downloadManager.downloadAllFavorites { current, total ->
                    _batchProgress.value = current to total
                }
            }
            _batchProgress.value = null
        }
    }

    // ---------- 每周必看 ----------
    private val _weekly = MutableStateFlow(WeeklyUiState())
    val weekly: StateFlow<WeeklyUiState> = _weekly.asStateFlow()

    fun loadWeeklyInfo() {
        if (_weekly.value.info != null) return
        viewModelScope.launch {
            _weekly.value = _weekly.value.copy(loading = true, error = null)
            try {
                val info = withContext(Dispatchers.IO) { repository.getWeeklyInfo() }
                _weekly.value = _weekly.value.copy(info = info, loading = false)
                // 自动选中一个有内容的 (分类, 类型) 组合（最新一期常为空）
                val initial = withContext(Dispatchers.IO) { findFirstNonEmptyWeekly(info) }
                if (initial != null) {
                    loadWeekly(initial.first, initial.second)
                } else {
                    val c = info.categories.firstOrNull()?.id ?: ""
                    val t = info.types.firstOrNull()?.id ?: ""
                    if (c.isNotBlank() && t.isNotBlank()) loadWeekly(c, t)
                }
            } catch (e: Exception) {
                AppLogger.error("VM", "获取每周必看信息失败", e)
                _weekly.value = _weekly.value.copy(loading = false, error = e.message ?: "获取每周必看失败")
            }
        }
    }

    /** 找最新一期有内容的 (分类, 类型)，限制尝试次数避免打太多请求 */
    private suspend fun findFirstNonEmptyWeekly(info: WeeklyInfo): Pair<String, String>? {
        var attempts = 0
        for (cat in info.categories.take(3)) {
            for (t in info.types) {
                if (++attempts > 8) return null
                val r = runCatching { repository.getWeekly(cat.id, t.id) }.getOrNull() ?: continue
                if (r.list.isNotEmpty()) return cat.id to t.id
            }
        }
        return null
    }

    fun loadWeekly(categoryId: String, typeId: String) {
        _weeklyEpoch++
        viewModelScope.launch {
            _weekly.value = _weekly.value.copy(
                categoryId = categoryId, typeId = typeId, loadingComics = true, error = null
            )
            try {
                val result = withContext(Dispatchers.IO) { repository.getWeekly(categoryId, typeId) }
                _weekly.value = _weekly.value.copy(results = result.list, loadingComics = false)
            } catch (e: Exception) {
                AppLogger.error("VM", "获取每周必看列表失败 $categoryId/$typeId", e)
                _weekly.value = _weekly.value.copy(
                    loadingComics = false, error = e.message ?: "获取每周必看失败"
                )
            }
        }
    }

    // ---------- 本地库存 ----------
    private val _downloaded = MutableStateFlow<List<Comic>>(emptyList())
    val downloaded: StateFlow<List<Comic>> = _downloaded.asStateFlow()
    private val _downloadedLoading = MutableStateFlow(false)
    val downloadedLoading: StateFlow<Boolean> = _downloadedLoading.asStateFlow()
    private var lastLibraryVersion = -1

    /** 读取本地库存；索引版本没变时用缓存（避免每次打开都全量重扫） */
    fun refreshDownloaded(force: Boolean = false) {
        val indexVersion = container.downloadIndex.version
        if (!force && indexVersion == lastLibraryVersion) return
        lastLibraryVersion = indexVersion
        viewModelScope.launch {
            _downloadedLoading.value = true
            val list = withContext(Dispatchers.IO) { repository.getDownloadedComics() }
            _downloaded.value = list
            _downloadedLoading.value = false
        }
    }

    /** 删除本地已下载的漫画（只删下载目录内的目录，安全） */
    fun deleteDownloadedComics(comics: List<Comic>) {
        val downloadDir = java.io.File(_config.value.downloadDir).absolutePath
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (comic in comics) {
                    val dir = comic.comicDownloadDir
                    if (dir != null && dir.startsWith(downloadDir)) {
                        runCatching { java.io.File(dir).deleteRecursively() }
                    }
                    container.downloadIndex.removeComic(comic.id)
                }
            }
            refreshDownloaded(force = true)
        }
    }

    // 更新库存（联网补下新章节）进度
    private val _updateProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val updateProgress: StateFlow<Pair<Int, Int>?> = _updateProgress.asStateFlow()

    fun updateDownloadedComics() {
        viewModelScope.launch {
            _updateProgress.value = 0 to 0
            runCatching {
                downloadManager.updateDownloadedComics(
                    java.io.File(_config.value.downloadDir),
                ) { current, total ->
                    _updateProgress.value = current to total
                }
            }
            _updateProgress.value = null
        }
    }

    // ---------- 漫画详情 ----------
    private val _selectedComic = MutableStateFlow<Comic?>(null)
    val selectedComic: StateFlow<Comic?> = _selectedComic.asStateFlow()
    private val _comicLoading = MutableStateFlow(false)
    val comicLoading: StateFlow<Boolean> = _comicLoading.asStateFlow()
    private val _comicError = MutableStateFlow<String?>(null)
    val comicError: StateFlow<String?> = _comicError.asStateFlow()
    private val _selectedChapterIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChapterIds: StateFlow<Set<Long>> = _selectedChapterIds.asStateFlow()

    fun loadComic(aid: Long) {
        viewModelScope.launch {
            _comicLoading.value = true
            _comicError.value = null
            try {
                val comic = withContext(Dispatchers.IO) { repository.getComic(aid) }
                _selectedComic.value = comic
            } catch (e: Exception) {
                AppLogger.error("VM", "获取漫画失败 aid=$aid", e)
                _comicError.value = e.message ?: "获取漫画失败"
            }
            _comicLoading.value = false
        }
    }

    /** 回到前台时重新应用一次已下载状态（不重新联网，直接更新章节下载路径） */
    fun refreshSelectedComicStatus() {
        val comic = _selectedComic.value ?: return
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { repository.applyDownloadStatus(comic) }
            _selectedComic.value = fresh
        }
    }

    fun toggleChapter(chapterId: Long, selected: Boolean) {
        _selectedChapterIds.value =
            if (selected) _selectedChapterIds.value + chapterId
            else _selectedChapterIds.value - chapterId
    }

    fun selectAllChapters() {
        val comic = _selectedComic.value ?: return
        _selectedChapterIds.value = comic.chapterInfos
            .filter { it.isDownloaded != true }
            .map { it.chapterId }
            .toSet()
    }

    fun clearChapterSelection() {
        _selectedChapterIds.value = emptySet()
    }

    fun downloadSelected() {
        val comic = _selectedComic.value ?: return
        viewModelScope.launch {
            // 下载前重新应用一次已下载状态（索引可能刚完成扫描），避免重复下载
            val fresh = withContext(Dispatchers.IO) { repository.applyDownloadStatus(comic) }
            _selectedChapterIds.value.forEach { id ->
                downloadManager.createDownloadTask(fresh, id)
            }
        }
    }

    fun downloadWholeComic() {
        val comic = _selectedComic.value ?: return
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { repository.applyDownloadStatus(comic) }
            downloadManager.downloadComic(fresh)
        }
    }

    fun retryDownload(comic: Comic, chapterId: Long) {
        downloadManager.createDownloadTask(comic, chapterId)
    }

    /** 打开阅读器前从实时索引解析章节下载路径（刚下载完立刻进入也能读到图） */
    fun readerChapter(comic: Comic, chapter: ChapterInfo): ChapterInfo {
        val cd = container.downloadIndex.chapterDir(comic.id, chapter.chapterId)
        return if (cd != null) chapter.copy(isDownloaded = true, chapterDownloadDir = cd) else chapter
    }

    // ---------- 本地收藏（无需登录） ----------
    private val _localFavorites = MutableStateFlow(LocalFavoritesStore.load(app))
    val localFavorites: StateFlow<List<Comic>> = _localFavorites.asStateFlow()

    fun isLocalFavorite(comicId: Long): Boolean =
        _localFavorites.value.any { it.id == comicId }

    /** 添加/取消本地收藏，返回是否已收藏 */
    fun toggleLocalFavorite(comic: Comic): Boolean {
        val list = _localFavorites.value.toMutableList()
        val existing = list.indexOfFirst { it.id == comic.id }
        if (existing >= 0) {
            list.removeAt(existing)
        } else {
            list.add(0, comic)
        }
        _localFavorites.value = list
        LocalFavoritesStore.save(getApplication(), list)
        return existing < 0
    }

    fun removeLocalFavorite(comicId: Long) {
        val list = _localFavorites.value.filterNot { it.id == comicId }
        _localFavorites.value = list
        LocalFavoritesStore.save(getApplication(), list)
    }

    // ---------- 登录 ----------
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()
    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            try {
                val profile = withContext(Dispatchers.IO) { repository.login(username, password) }
                _userProfile.value = profile
                updateConfig(_config.value.copy(username = username, password = password))
            } catch (e: Exception) {
                AppLogger.error("VM", "登录失败 user=$username", e)
                _loginError.value = e.message ?: "登录失败"
            }
            _loginLoading.value = false
        }
    }

    fun logout() {
        _userProfile.value = null
        updateConfig(_config.value.copy(username = "", password = ""))
    }

    fun tryAutoLogin() {
        val c = _config.value
        if (c.username.isNotBlank() && c.password.isNotBlank() && _userProfile.value == null) {
            login(c.username, c.password)
        }
    }
}
