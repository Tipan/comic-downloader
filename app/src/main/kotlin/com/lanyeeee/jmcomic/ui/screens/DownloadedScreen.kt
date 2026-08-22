package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lanyeeee.jmcomic.domain.export.CbzExporter
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import com.lanyeeee.jmcomic.ui.components.JmIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DownloadedScreen(vm: MainViewModel) {
    val downloaded by vm.downloaded.collectAsState()
    val loading by vm.downloadedLoading.collectAsState()
    val updateProgress by vm.updateProgress.collectAsState()
    val config by vm.config.collectAsState()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.refreshDownloaded()
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text("本地库存 (${downloaded.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refreshDownloaded() }) {
                Icon(Icons.Filled.Refresh, "刷新")
            }
            IconButton(onClick = { vm.updateDownloadedComics() }, enabled = updateProgress == null) {
                if (updateProgress != null) {
                    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                } else {
                    Icon(JmIcons.Update, "更新库存")
                }
            }
        }
        HorizontalDivider()

        // 操作栏
        if (downloaded.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        selectedIds = if (selectedIds.size == downloaded.size) emptySet()
                        else downloaded.map { it.id }.toSet()
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        if (selectedIds.size == downloaded.size) JmIcons.CheckBox else JmIcons.CheckBoxOutlineBlank,
                        null, Modifier.width(16.dp).height(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("全选")
                }
                Spacer(Modifier.weight(1f))
                if (updateProgress != null) {
                    Text("更新中 ${updateProgress!!.first}/${updateProgress!!.second}", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        val comics = downloaded.filter { selectedIds.contains(it.id) }
                        if (comics.isEmpty()) return@Button
                        scope.launch {
                            val dir = File(config.exportDir)
                            var count = 0
                            withContext(Dispatchers.IO) {
                                for (c in comics) {
                                    val chapters = c.chapterInfos.filter { it.isDownloaded == true && it.chapterDownloadDir != null }
                                    count += CbzExporter.exportCbz(c, chapters, dir).size
                                }
                            }
                            exportResult = "已导出 $count 个 CBZ 到 ${dir.absolutePath}"
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Icon(JmIcons.Archive, null)
                    Spacer(Modifier.width(4.dp))
                    Text("导出选中为CBZ(${selectedIds.size})")
                }
            }
        }

        when {
            loading && downloaded.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            downloaded.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已下载的漫画", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloaded, key = { it.id }) { comic ->
                    DownloadedRow(
                        comic = comic,
                        selected = selectedIds.contains(comic.id),
                        onToggleSelect = {
                            selectedIds = if (selectedIds.contains(comic.id)) selectedIds - comic.id else selectedIds + comic.id
                        },
                        onClick = { vm.navigate(Screen.ComicDetail(comic.id)) },
                        onReadFirst = {
                            val first = comic.chapterInfos.firstOrNull { it.isDownloaded == true && it.chapterDownloadDir != null }
                            if (first != null) vm.navigate(Screen.Reader(comic, first))
                        },
                    )
                }
            }
        }
    }

    exportResult?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportResult = null },
            title = { Text("导出完成") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { exportResult = null }) { Text("好的") } },
        )
    }
}

@Composable
private fun DownloadedRow(
    comic: Comic,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onReadFirst: () -> Unit,
) {
    val downloadedChapters = comic.chapterInfos.count { it.isDownloaded == true }
    val cover = comic.comicDownloadDir?.let { File(it, com.lanyeeee.jmcomic.data.local.FileNames.COVER_NAME) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                AsyncImage(
                    model = cover,
                    contentDescription = comic.name,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .clickable(onClick = onToggleSelect)
                        .padding(3.dp),
                ) {
                    Icon(
                        if (selected) JmIcons.CheckBox else JmIcons.CheckBoxOutlineBlank,
                        "选择",
                        tint = if (selected) Color(0xFFFF7A00) else Color.White,
                        modifier = Modifier.width(14.dp).height(14.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(comic.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(comic.authorDisplay, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "已下载 $downloadedChapters/${comic.chapterInfos.size} 章节",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                )
            }
            if (downloadedChapters > 0) {
                TextButton(onClick = onReadFirst) { Text("阅读") }
            }
        }
    }
}
