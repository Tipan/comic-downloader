package com.lanyeeee.jmcomic.data.local

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 简易日志：写 logcat + 落盘到公共下载目录（无需 root，用户可直接查看/分享）。
 * 崩溃时由 crash handler 单独存一份 crash-<时间>.log。
 */
object AppLogger {
    private val lock = ReentrantLock()
    private var logDir: File? = null
    private const val MAX_LOG_SIZE = 512 * 1024

    fun init(context: Context) {
        val publicBase = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull()
        val publicDir = publicBase?.let { File(it, "jmcomic-logs") }
        val ok = publicDir?.let { runCatching { it.mkdirs() }.getOrDefault(false) } == true
        logDir = if (ok) publicDir else File(context.filesDir, "jmcomic-logs").apply { mkdirs() }
    }

    /** 当前日志目录（用户可访问的公共目录优先） */
    fun currentLogDir(): String? = logDir?.absolutePath

    fun info(tag: String, msg: String) = log(android.util.Log.INFO, "I", tag, msg)

    fun warn(tag: String, msg: String) = log(android.util.Log.WARN, "W", tag, msg)

    fun error(tag: String, msg: String, e: Throwable? = null) {
        log(android.util.Log.ERROR, "E", tag, if (e == null) msg else "$msg\n${stackTrace(e)}")
    }

    private fun log(level: Int, levelChar: String, tag: String, msg: String) {
        android.util.Log.println(level, "JmApp", "[$tag] $msg")
        write("$ts $levelChar [$tag] $msg")
    }

    /** 崩溃处理器专用：写一条完整的崩溃记录 */
    fun logCrash(thread: Thread, throwable: Throwable) {
        val sb = StringBuilder()
        sb.append("\n===== CRASH START =====\n")
        sb.append("time  : ").append(fullTs).append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        sb.append("stack :\n").append(stackTrace(throwable))
        sb.append("===== CRASH END =====\n")
        write(sb.toString())
        // 单独存一份，方便定位
        runCatching {
            val dir = logDir ?: return
            val f = File(dir, "crash-${fullTs.replace(' ', '_').replace(':', '-')}.log")
            f.writeText(sb.toString())
        }
    }

    private fun stackTrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private val ts: String
        get() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private val fullTs: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun write(line: String) {
        val dir = logDir ?: return
        runCatching {
            lock.withLock {
                val f = File(dir, "app.log")
                // 超 512KB 时滚动
                if (f.exists() && f.length() > MAX_LOG_SIZE) {
                    val backup = File(dir, "app.log.old")
                    backup.delete()
                    f.renameTo(backup)
                }
                f.appendText(line + "\n")
            }
        }
    }
}
