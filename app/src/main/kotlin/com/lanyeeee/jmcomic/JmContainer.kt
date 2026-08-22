package com.lanyeeee.jmcomic

import android.content.Context
import com.lanyeeee.jmcomic.data.local.ConfigStore
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

/** 应用级依赖容器（简易 ServiceLocator） */
class JmContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val configStore = ConfigStore(context)
    private val _config = MutableStateFlow(configStore.load())
    val config: StateFlow<Config> = _config

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

    val repository = JmRepository(api) { _config.value }

    val downloadManager = DownloadManager(
        api = api,
        configProvider = { _config.value },
        comicFetcher = { aid -> repository.getComic(aid) },
        scope = appScope,
    )

    fun updateConfig(config: Config) {
        _config.value = config
        configStore.save(config)
        downloadManager.reloadConcurrency()
    }
}
