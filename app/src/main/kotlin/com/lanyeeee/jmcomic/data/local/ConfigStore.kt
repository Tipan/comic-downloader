package com.lanyeeee.jmcomic.data.local

import android.content.Context
import android.os.Environment
import com.lanyeeee.jmcomic.domain.model.Config
import java.io.File

/** 配置持久化（JSON 文件，与 legacy config.json 字段一致，向前兼容合并默认值） */
class ConfigStore(private val context: Context) {
    private val file get() = File(context.filesDir, "config.json")

    private fun defaultDownloadDir(): String {
        val base = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }.getOrElse { "/storage/emulated/0/Download" }
        return "$base/漫画下载"
    }

    private fun defaultExportDir(): String {
        val base = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }.getOrElse { "/storage/emulated/0/Download" }
        return "$base/漫画导出"
    }

    fun load(): Config {
        val config = if (file.exists()) {
            runCatching {
                MetadataStore.json.decodeFromString(Config.serializer(), file.readText())
            }.getOrElse { Config() }
        } else {
            Config()
        }
        if (config.downloadDir.isBlank()) config.downloadDir = defaultDownloadDir()
        if (config.exportDir.isBlank()) config.exportDir = defaultExportDir()
        return config
    }

    fun save(config: Config) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(MetadataStore.prettyJson.encodeToString(Config.serializer(), config))
        }
    }
}
