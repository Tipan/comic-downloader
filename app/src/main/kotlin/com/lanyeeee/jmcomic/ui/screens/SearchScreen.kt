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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.domain.model.SearchSort
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.components.ComicCard
import kotlinx.coroutines.flow.collect

private val sortLabels = mapOf(
    SearchSort.Latest to "最新",
    SearchSort.View to "最多浏览",
    SearchSort.Picture to "最多图片",
    SearchSort.Like to "最多点赞",
)

@Composable
fun SearchScreen(vm: MainViewModel) {
    val state by vm.search.collectAsState()
    val gridState = vm.searchGridState

    // 新搜索（关键词/排序变化）滚回顶部；从详情返回时保留位置（首次/返回不重置）
    var lastSearchEpoch by remember { mutableStateOf(vm.searchEpoch) }
    LaunchedEffect(vm.searchEpoch) {
        if (vm.searchEpoch != lastSearchEpoch) {
            lastSearchEpoch = vm.searchEpoch
            runCatching { vm.searchGridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(gridState) {
        // 滚动到底部附近时加载更多
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            last >= total - 6 && total > 0
        }.collect { nearEnd ->
            if (nearEnd && state.results.isNotEmpty() && !state.loading && !state.loadingMore) {
                vm.search(false)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = vm::setSearchKeyword,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("搜索漫画或 JM 号") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = { vm.setSearchKeyword("") }) {
                            Icon(Icons.Filled.Clear, "清空")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(true) }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.search(true) }) {
                Text("搜索")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = {
                        if (state.sort != sort) vm.setSearchSort(sort)
                    },
                    label = { Text(sortLabels[sort] ?: sort.name) },
                )
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
            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("输入关键词搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
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
                        onClick = { vm.navigate(com.lanyeeee.jmcomic.ui.Screen.ComicDetail(comic.id)) },
                    )
                }
                if (state.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
