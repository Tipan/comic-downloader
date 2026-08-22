package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import com.lanyeeee.jmcomic.ui.components.ComicCard

@Composable
fun WeeklyScreen(vm: MainViewModel) {
    val state by vm.weekly.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadWeeklyInfo()
    }

    Column(Modifier.fillMaxSize()) {
        val info = state.info
        if (info != null) {
            // 分类
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.categories.forEach { cat ->
                    FilterChip(
                        selected = state.categoryId == cat.id,
                        onClick = {
                            if (state.categoryId != cat.id) {
                                val typeId = info.types.firstOrNull()?.id ?: return@FilterChip
                                vm.loadWeekly(cat.id, typeId)
                            }
                        },
                        label = { Text(cat.title) },
                    )
                }
            }
            // 类型
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.types.forEach { t ->
                    FilterChip(
                        selected = state.typeId == t.id,
                        onClick = {
                            if (state.typeId != t.id && state.categoryId.isNotBlank()) {
                                vm.loadWeekly(state.categoryId, t.id)
                            }
                        },
                        label = { Text(t.title) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        when {
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.results.size) { i ->
                    val comic = state.results[i]
                    ComicCard(
                        title = comic.name,
                        author = comic.author,
                        coverUrl = JmApi.coverThumbUrl(comic.id),
                        isDownloaded = comic.isDownloaded,
                        onClick = { vm.navigate(Screen.ComicDetail(comic.id)) },
                    )
                }
            }
        }
    }
}
