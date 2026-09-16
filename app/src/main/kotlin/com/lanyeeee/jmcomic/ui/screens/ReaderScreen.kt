package com.lanyeeee.jmcomic.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.lanyeeee.jmcomic.domain.model.ChapterInfo
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.ui.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val imageExts = setOf("jpg", "png", "webp", "gif")

/**
 * 阅读器：进入 5 秒或上下滑动进入全屏，单击唤出顶部章节栏 + 底部进度条。
 * 按章节 key 包裹内容：切上一话/下一话时整个阅读子树完全重建，进度从头开始。
 */
@Composable
fun ReaderScreen(vm: MainViewModel, comic: Comic, chapter: ChapterInfo) {
    key(chapter.chapterId) {
        ReaderContent(vm, comic, chapter)
    }
}

@Composable
private fun ReaderContent(vm: MainViewModel, comic: Comic, chapter: ChapterInfo) {
    val images = remember(comic, chapter.chapterDownloadDir) {
        chapter.chapterDownloadDir?.let { dir ->
            File(dir).listFiles { f -> f.isFile && f.extension.lowercase() in imageExts }
                ?.sortedBy { it.name }
        } ?: emptyList()
    }
    val listState = remember { LazyListState() }
    val currentIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    var showControls by remember { mutableStateOf(true) }

    val chapters = comic.chapterInfos.filter { it.chapterDownloadDir != null }
    val curIdx = chapters.indexOfFirst { it.chapterId == chapter.chapterId }
    val prevChapter = chapters.getOrNull(curIdx - 1)
    val nextChapter = chapters.getOrNull(curIdx + 1)

    // 5 秒无操作自动进入全屏
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    // 上下滑动进入全屏（只响应用户手势）
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) showControls = false
        }
    }

    // 全屏：隐藏/显示系统栏
    val activity = LocalContext.current as? Activity
    val controller = remember {
        activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
    }
    LaunchedEffect(showControls) {
        if (showControls) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val contentInsets =
        if (showControls) WindowInsets.safeDrawing else WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(contentInsets),
    ) {
        // 顶部章节选择栏
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Column {
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
                        TextButton(onClick = { vm.replaceReader(comic, prevChapter) }) { Text("上一话") }
                    }
                    if (nextChapter != null) {
                        TextButton(onClick = { vm.replaceReader(comic, nextChapter) }) { Text("下一话") }
                    }
                }
                HorizontalDivider()
            }
        }

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该章节没有本地图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 图片区：单击切换控件显示/隐藏
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { showControls = !showControls })
                    },
            ) {
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

            // 底部进度条
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                val scope = rememberCoroutineScope()
                var scrollJob by remember { mutableStateOf<Job?>(null) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${currentIndex + 1}/${images.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Slider(
                        value = currentIndex.toFloat(),
                        valueRange = 0f..(images.size - 1).coerceAtLeast(1).toFloat(),
                        onValueChange = { v ->
                            val target = v.roundToInt().coerceIn(0, images.size - 1)
                            scrollJob?.cancel()
                            scrollJob = scope.launch { listState.scrollToItem(target) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${images.size}页",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
