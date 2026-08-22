package com.lanyeeee.jmcomic.data.local

import android.net.Uri
import android.provider.DocumentsContract

/** 存储路径工具 */
object StoragePaths {
    /**
     * 把 SAF 文件夹选择器返回的 tree Uri 转成可直接读写的文件路径。
     * 支持 primary 内置存储（/storage/emulated/0/...）和外部 SD 卡（/storage/<卷>/...）。
     */
    fun treeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) // 形如 "primary:Download/漫画下载"
            val idx = docId.indexOf(':')
            if (idx < 0) return null
            val volume = docId.substring(0, idx)
            val relative = docId.substring(idx + 1)
            if (volume == "primary") "/storage/emulated/0/$relative" else "/storage/$volume/$relative"
        } catch (e: Exception) {
            null
        }
    }
}
