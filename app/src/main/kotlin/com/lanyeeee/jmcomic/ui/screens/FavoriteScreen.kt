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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.domain.model.FavoriteSort
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import com.lanyeeee.jmcomic.ui.components.ComicCard
import kotlinx.coroutines.flow.collect

@Composable
fun FavoriteScreen(vm: MainViewModel) {
    val state by vm.favorite.collectAsState()
    val batch by vm.batchProgress.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (state.results.isEmpty() && !state.loading) vm.refreshFavorite(true)
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            last >= total - 6 && total > 0
        }.collect { nearEnd ->
            if (nearEnd && state.results.isNotEmpty() && !state.loading && !state.loadingMore) {
                vm.refreshFavorite(false)
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
            FolderSelector(state.folders, state.folderId, vm::setFavoriteFolder)
            Spacer(Modifier.weight(1f))
            if (batch != null) {
                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp))
                Text(" ${batch!!.first}/${batch!!.second}", style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedButton(onClick = vm::downloadAllFavorites, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("下载整个收藏夹")
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FavoriteSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = {
                        if (state.sort != sort) {
                            vm.setFavoriteSort(sort)
                        }
                    },
                    label = { Text(if (sort == FavoriteSort.FavoriteTime) "收藏时间" else "更新时间") },
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        when {
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.refreshFavorite(true) }) { Text("重试") }
                }
            }
            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("收藏夹为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onClick = { vm.navigate(Screen.ComicDetail(comic.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderSelector(
    folders: List<com.lanyeeee.jmcomic.domain.model.FavoriteFolder>,
    currentId: Long,
    onSelect: (Long) -> Unit,
) {
    if (folders.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val current = folders.firstOrNull { it.fid == currentId.toString() }
    Box {
        OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(current?.name ?: "全部收藏")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部收藏") }, onClick = {
                expanded = false
                onSelect(0)
            })
            folders.forEach { f ->
                DropdownMenuItem(text = { Text(f.name) }, onClick = {
                    expanded = false
                    f.fid.toLongOrNull()?.let { onSelect(it) }
                })
            }
        }
    }
}
