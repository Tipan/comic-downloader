package com.lanyeeee.jmcomic.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/** 存储权限处理（API 30+ 用「所有文件访问」，API 29- 用运行时写权限） */
object StoragePermissions {
    fun hasAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager()
        return true
    }

    fun hasLegacyWritePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return true
        return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasPermission(context: Context): Boolean =
        hasAllFilesAccess(context) && hasLegacyWritePermission(context)

    /** 实际写文件探测（比权限 API 更严格，等价 legacy `check_storage_permission`） */
    fun canWriteDir(dir: File): Boolean {
        val test = File(dir, ".jmcomic_perm_test")
        return runCatching {
            test.parentFile?.mkdirs()
            test.writeText("test")
            test.delete()
        }.isSuccess
    }

    /** 跳转系统授权页（native 可直接跳转，替代旧版 WebView 里只能文字提示的痛点） */
    fun launchPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
