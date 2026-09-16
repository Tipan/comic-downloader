package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lanyeeee.jmcomic.domain.model.Comic
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen

/** 本地收藏（无需登录），数据存应用私有目录 */
@Composable
fun LocalFavoritesScreen(vm: MainViewModel) {
    val favorites by vm.localFavorites.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "本地收藏 (${favorites.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (favorites.isNotEmpty()) {
                TextButton(onClick = { favorites.forEach { vm.removeLocalFavorite(it.id) } }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.padding(start = 2.dp))
                    Text("清空")
                }
            }
        }

        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有本地收藏\n去漫画详情页点 ♥ 即可收藏（无需登录）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = vm.localFavoritesGridState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(favorites.size) { i ->
                    val comic = favorites[i]
                    Card(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.navigate(Screen.ComicDetail(comic.id)) },
                    ) {
                        Column {
                            Box {
                                AsyncImage(
                                    model = JmApi.coverThumbUrl(comic.id),
                                    contentDescription = comic.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCC000000))
                                        .clickable { vm.removeLocalFavorite(comic.id) }
                                        .padding(6.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        "取消收藏",
                                        tint = Color(0xFFFF7A00),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Text(
                                comic.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
