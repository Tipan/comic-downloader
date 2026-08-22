package com.lanyeeee.jmcomic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    var username: String = "",
    var password: String = "",
    var downloadDir: String = "",
    var exportDir: String = "",
    var downloadFormat: DownloadFormat = DownloadFormat.Jpeg,
    var dirFmt: String = "{comic_title}/{chapter_title}",
    var proxyMode: ProxyMode = ProxyMode.System,
    var proxyHost: String = "127.0.0.1",
    var proxyPort: Int = 7890,
    var enableFileLogger: Boolean = true,
    var chapterConcurrency: Int = 3,
    var chapterDownloadIntervalSec: Long = 0,
    var imgConcurrency: Int = 20,
    var imgDownloadIntervalSec: Long = 0,
    var downloadAllFavoritesIntervalSec: Long = 0,
    var updateDownloadedComicsIntervalSec: Long = 0,
    var apiDomainMode: ApiDomainMode = ApiDomainMode.Domain2,
    var customApiDomain: String = "www.cdnhth.cc",
    var shouldDownloadCover: Boolean = true,
)
