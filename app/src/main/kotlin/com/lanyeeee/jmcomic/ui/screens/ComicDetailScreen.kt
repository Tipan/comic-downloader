package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.DownloadTaskState
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import com.lanyeeee.jmcomic.ui.components.JmIcons

@Composable
fun ComicDetailScreen(vm: MainViewModel, comicId: Long) {
    val comic by vm.selectedComic.collectAsState()
    val loading by vm.comicLoading.collectAsState()
    val error by vm.comicError.collectAsState()
    val selected by vm.selectedChapterIds.collectAsState()
    val progresses by vm.downloadManager.progresses.collectAsState()

    LaunchedEffect(comicId) {
        vm.loadComic(comicId)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                comic?.name ?: "漫画详情",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            loading && comic == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && comic == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.loadComic(comicId) }) { Text("重试") }
                }
            }
            comic != null -> {
                val c = comic!!
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ComicHeader(c) }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = vm::downloadSelected,
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(JmIcons.Download, null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (selected.isEmpty()) "下载选中章节" else "下载选中章节(${selected.size})")
                            }
                            OutlinedButton(onClick = vm::downloadWholeComic, modifier = Modifier.weight(1f)) {
                                Text("下载整本")
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = vm::selectAllChapters) {
                                Icon(JmIcons.CheckBox, null, Modifier.width(16.dp).height(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("全选未下载")
                            }
                            OutlinedButton(onClick = vm::clearChapterSelection) {
                                Icon(JmIcons.CheckBoxOutlineBlank, null, Modifier.width(16.dp).height(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("清空选择")
                            }
                        }
                    }
                    item {
                        Text(
                            "章节（${c.chapterInfos.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(c.chapterInfos, key = { it.chapterId }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            progress = progresses[chapter.chapterId],
                            selected = selected.contains(chapter.chapterId),
                            onToggle = { sel -> vm.toggleChapter(chapter.chapterId, sel) },
                            onClick = { vm.navigate(Screen.Reader(c, chapter)) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ComicHeader(comic: com.lanyeeee.jmcomic.domain.model.Comic) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AsyncImage(
            model = JmApi.coverUrl(comic.id),
            contentDescription = comic.name,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(comic.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "作者：${comic.authorDisplay}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                comic.tags.joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (comic.isDownloaded == true) {
                Text(
                    "已下载",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                )
            }
        }
    }
    if (comic.description.isNotBlank()) {
        Text(
            comic.description,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterInfo,
    progress: com.lanyeeee.jmcomic.domain.model.ProgressData?,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val isDownloaded = chapter.isDownloaded == true || progress?.state == DownloadTaskState.Completed
    val isActive = progress != null && progress.state in setOf(
        DownloadTaskState.Pending, DownloadTaskState.Downloading, DownloadTaskState.Paused
    )
    val isFailed = progress != null && progress.state in setOf(
        DownloadTaskState.Failed, DownloadTaskState.Cancelled
    )

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (!isDownloaded) onToggle(!selected) }, enabled = !isDownloaded) {
                if (selected) {
                    Icon(Icons.Filled.Check, "已选", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(JmIcons.CheckBoxOutlineBlank, "未选", tint = MaterialTheme.colorScheme.outline)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    isDownloaded -> Text("已下载", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                    isActive && progress != null -> Text(
                        progress.indicator,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    isFailed -> Text(progress?.indicator ?: "失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> Text("", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isActive && progress != null && progress.totalImgCount > 0) {
                LinearProgressIndicator(
                    progress = { progress.percentage },
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}
