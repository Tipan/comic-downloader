package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import java.io.File

private val imageExts = setOf("jpg", "png", "webp", "gif")

@Composable
fun ReaderScreen(vm: MainViewModel, comic: Comic, chapter: ChapterInfo) {
    val images = remember(comic, chapter.chapterDownloadDir) {
        chapter.chapterDownloadDir?.let { dir ->
            File(dir).listFiles { f -> f.isFile && f.extension.lowercase() in imageExts }
                ?.sortedBy { it.name }
        } ?: emptyList()
    }
    val listState = rememberLazyListState()
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val chapters = comic.chapterInfos.filter { it.chapterDownloadDir != null }
    val curIdx = chapters.indexOfFirst { it.chapterId == chapter.chapterId }
    val prevChapter = chapters.getOrNull(curIdx - 1)
    val nextChapter = chapters.getOrNull(curIdx + 1)

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
            Column(Modifier.weight(1f)) {
                Text(
                    "${comic.name} - ${chapter.chapterTitle}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (images.isEmpty()) "无图片" else "${currentIndex + 1}/${images.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (prevChapter != null) {
                TextButton(onClick = { vm.navigate(Screen.Reader(comic, prevChapter)) }) { Text("上一话") }
            }
            if (nextChapter != null) {
                TextButton(onClick = { vm.navigate(Screen.Reader(comic, nextChapter)) }) { Text("下一话") }
            }
        }
        HorizontalDivider()

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该章节没有本地图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(images, key = { it.absolutePath }) { img ->
                    AsyncImage(
                        model = img,
                        contentDescription = img.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }
    }

    LaunchedEffect(chapter.chapterId) {
        listState.scrollToItem(0)
    }
}
