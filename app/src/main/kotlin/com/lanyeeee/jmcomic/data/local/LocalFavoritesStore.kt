package com.lanyeeee.jmcomic.data.local

import android.content.Context
import com.lanyeeee.jmcomic.domain.model.Comic
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** 本地收藏（无需登录），存 JSON 到应用私有目录 */
object LocalFavoritesStore {
    private const val FILE_NAME = "favorites.json"

    fun load(context: Context): List<Comic> {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return emptyList()
        return runCatching {
            MetadataStore.json.decodeFromString(ListSerializer(Comic.serializer()), f.readText())
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, favorites: List<Comic>) {
        runCatching {
            val f = File(context.filesDir, FILE_NAME)
            f.parentFile?.mkdirs()
            f.writeText(
                MetadataStore.prettyJson.encodeToString(ListSerializer(Comic.serializer()), favorites)
            )
        }
    }
}
