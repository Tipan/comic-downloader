package com.lanyeeee.jmcomic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.lanyeeee.jmcomic.domain.model.DownloadTaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 下载前台服务：保持进程前台 + 持唤醒锁，锁屏/熄屏/切后台时下载不中断。
 * 有活动下载时启动，全部结束后自动停止。
 */
class DownloadForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "download"
        private const val NOTIF_ID = 1
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var watchJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        ensureWakeLock()
        ensureWatcher()
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification(0, "准备下载…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(activeCount: Int, status: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载漫画")
            .setContentText(if (activeCount > 0) "进行中 $activeCount 个任务 · $status" else status)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun ensureWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jmcomic:download").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun ensureWatcher() {
        if (watchJob?.isActive == true) return
        val dm = (application as JmApplication).container.downloadManager
        watchJob = serviceScope.launch {
            dm.progresses.collect { progresses ->
                val active = progresses.values.count {
                    it.state in setOf(DownloadTaskState.Pending, DownloadTaskState.Downloading)
                }
                if (active == 0) {
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, buildNotification(active, "速度 ${dm.speed.value}"))
                }
            }
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        serviceScope.cancel()
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
        wakeLock = null
        super.onDestroy()
    }
}
