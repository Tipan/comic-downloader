package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.domain.model.DownloadTaskState
import com.lanyeeee.jmcomic.domain.model.ProgressData
import com.lanyeeee.jmcomic.ui.MainViewModel

@Composable
fun DownloadScreen(vm: MainViewModel) {
    val progresses by vm.downloadManager.progresses.collectAsState()
    val speed by vm.downloadManager.speed.collectAsState()
    val sleeping by vm.downloadManager.sleeping.collectAsState()

    val active = progresses.values
        .filter { it.state in setOf(DownloadTaskState.Pending, DownloadTaskState.Downloading, DownloadTaskState.Paused) }
        .sortedBy { it.chapterInfo.chapterId }
    val completed = progresses.values.filter { it.state == DownloadTaskState.Completed }
    val failed = progresses.values
        .filter { it.state in setOf(DownloadTaskState.Failed, DownloadTaskState.Cancelled) }
        .sortedBy { it.chapterInfo.chapterId }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("下载速度", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text(speed, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text("进行中 ${active.size}", style = MaterialTheme.typography.labelMedium)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (active.isEmpty() && completed.isEmpty() && failed.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("暂无下载任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (active.isNotEmpty()) {
                item { SectionHeader("进行中") }
                items(active, key = { it.chapterInfo.chapterId }) { p ->
                    ActiveProgressCard(
                        p, sleeping[p.chapterInfo.chapterId],
                        onPause = { vm.downloadManager.pause(p.chapterInfo.chapterId) },
                        onResume = { vm.downloadManager.resume(p.chapterInfo.chapterId) },
                        onCancel = { vm.downloadManager.cancel(p.chapterInfo.chapterId) },
                    )
                }
            }

            if (failed.isNotEmpty()) {
                item { SectionHeader("失败/已取消") }
                items(failed, key = { it.chapterInfo.chapterId }) { p ->
                    FailedCard(
                        p,
                        onRetry = { vm.downloadManager.createDownloadTask(p.comic, p.chapterInfo.chapterId) },
                        onRemove = { vm.downloadManager.removeTask(p.chapterInfo.chapterId) },
                    )
                }
            }

            if (completed.isNotEmpty()) {
                item { SectionHeader("已完成") }
                items(completed, key = { it.chapterInfo.chapterId }) { p ->
                    CompletedCard(
                        p,
                        onOpen = { vm.navigate(com.lanyeeee.jmcomic.ui.Screen.Reader(p.comic, p.chapterInfo)) },
                        onRemove = { vm.downloadManager.removeTask(p.chapterInfo.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun ActiveProgressCard(
    p: ProgressData,
    sleepingSec: Long?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${p.comic.name} - ${p.chapterInfo.chapterTitle}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.indicator, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                if (sleepingSec != null) {
                    Text("休息中 ${sleepingSec}s", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                }
                when (p.state) {
                    DownloadTaskState.Downloading -> IconButton(onClick = onPause) {
                        Icon(Icons.Filled.PlayArrow, "暂停", tint = MaterialTheme.colorScheme.primary)
                    }
                    DownloadTaskState.Paused -> IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, "继续", tint = MaterialTheme.colorScheme.primary)
                    }
                    DownloadTaskState.Pending -> IconButton(onClick = onPause, enabled = false) {
                        Icon(Icons.Filled.PlayArrow, "排队中", tint = MaterialTheme.colorScheme.outline)
                    }
                    else -> {}
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Clear, "取消", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (p.totalImgCount > 0) {
                LinearProgressIndicator(
                    progress = { p.percentage },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FailedCard(p: ProgressData, onRetry: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.comic.name} - ${p.chapterInfo.chapterTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(p.indicator, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, "重试", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Clear, "移除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun CompletedCard(p: ProgressData, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.comic.name} - ${p.chapterInfo.chapterTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("已下载 ${p.totalImgCount} 张", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.Refresh, "阅读", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Clear, "移除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
