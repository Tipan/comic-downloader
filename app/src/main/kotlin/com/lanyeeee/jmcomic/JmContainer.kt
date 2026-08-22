package com.lanyeeee.jmcomic

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lanyeeee.jmcomic.data.local.ConfigStore
import com.lanyeeee.jmcomic.data.local.DownloadIndex
import com.lanyeeee.jmcomic.data.local.MetadataStore
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.data.repository.JmRepository
import com.lanyeeee.jmcomic.domain.download.DownloadManager
import com.lanyeeee.jmcomic.domain.model.ApiDomainMode
import com.lanyeeee.jmcomic.domain.model.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 应用级依赖容器（简易 ServiceLocator） */
class JmContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val configStore = ConfigStore(context)
    private val _config = MutableStateFlow(configStore.load())
    val config: StateFlow<Config> = _config

    /** 已下载漫画内存索引：启动扫一次，之后 O(1) 查询，下载后增量更新 */
    val downloadIndex = DownloadIndex()

    private val apiDomains = listOf(
        "www.cdnzack.cc",
        "www.cdnhth.cc",
        "www.cdnhth.net",
        "www.cdnbea.net",
        "www.cdn-mspjmapiproxy.xyz",
    )

    private fun currentApiDomain(): String = when (_config.value.apiDomainMode) {
        ApiDomainMode.Domain1 -> apiDomains[0]
        ApiDomainMode.Domain2 -> apiDomains[1]
        ApiDomainMode.Domain3 -> apiDomains[2]
        ApiDomainMode.Domain4 -> apiDomains[3]
        ApiDomainMode.Domain5 -> apiDomains[4]
        ApiDomainMode.Custom -> _config.value.customApiDomain
    }

    val api = JmApi(
        json = MetadataStore.json,
        apiDomain = { currentApiDomain() },
        proxyMode = { _config.value.proxyMode },
        proxyHost = { _config.value.proxyHost },
        proxyPort = { _config.value.proxyPort },
    )

    val repository = JmRepository(api, { _config.value }, downloadIndex)

    val downloadManager = DownloadManager(
        api = api,
        configProvider = { _config.value },
        comicFetcher = { aid -> repository.getComic(aid) },
        scope = appScope,
        onDownloadActivityChanged = { active ->
            if (active) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, DownloadForegroundService::class.java)
                    )
                }
            } else {
                runCatching {
                    context.stopService(Intent(context, DownloadForegroundService::class.java))
                }
            }
        },
        onChapterCompleted = { comic, chapter, chapterDir, comicDir ->
            downloadIndex.markChapterDownloaded(comic.id, chapter.chapterId, chapterDir, comicDir)
        },
    )

    init {
        // 启动时后台扫描一次下载目录，重建已下载索引
        refreshDownloadIndex()
    }

    fun refreshDownloadIndex() {
        appScope.launch {
            val dir = File(_config.value.downloadDir)
            runCatching { downloadIndex.refresh(dir) }
        }
    }

    fun updateConfig(config: Config) {
        val dirChanged = config.downloadDir != _config.value.downloadDir
        _config.value = config
        configStore.save(config)
        downloadManager.reloadConcurrency()
        if (dirChanged) refreshDownloadIndex()
    }
}
