package com.lanyeeee.jmcomic

import android.app.Application
import android.os.Build
import com.lanyeeee.jmcomic.data.local.AppLogger

class JmApplication : Application() {
    lateinit var container: JmContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        installCrashHandler()
        AppLogger.info(
            "App",
            "启动 version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                "logDir=${AppLogger.currentLogDir()}",
        )
        container = JmContainer(this)
    }

    /** 捕获未处理异常，写入日志文件后交给系统默认处理（弹出崩溃提示并退出） */
    private fun installCrashHandler() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.logCrash(thread, throwable)
            } catch (_: Throwable) {
                // 日志写入失败也不能影响崩溃流程
            }
            original?.uncaughtException(thread, throwable) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
