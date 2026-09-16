package com.lanyeeee.jmcomic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanyeeee.jmcomic.data.local.StoragePermissions
import com.lanyeeee.jmcomic.ui.components.JmIcons
import com.lanyeeee.jmcomic.ui.screens.ComicDetailScreen
import com.lanyeeee.jmcomic.ui.screens.DownloadScreen
import com.lanyeeee.jmcomic.ui.screens.DownloadedScreen
import com.lanyeeee.jmcomic.ui.screens.FavoriteScreen
import com.lanyeeee.jmcomic.ui.screens.LocalFavoritesScreen
import com.lanyeeee.jmcomic.ui.screens.MineScreen
import com.lanyeeee.jmcomic.ui.screens.ReaderScreen
import com.lanyeeee.jmcomic.ui.screens.SearchScreen
import com.lanyeeee.jmcomic.ui.screens.WeeklyScreen

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val screen by vm.screen.collectAsState()
    val hasPermission by vm.hasStoragePermission.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = screen != Screen.Main) {
        vm.back()
    }

    if (!hasPermission) {
        PermissionGate()
    } else {
        when (screen) {
            is Screen.Main -> MainScaffold(vm)
            is Screen.ComicDetail -> ComicDetailScreen(vm, (screen as Screen.ComicDetail).comicId)
            is Screen.Reader -> ReaderScreen(vm, (screen as Screen.Reader).comic, (screen as Screen.Reader).chapter)
            is Screen.Downloaded -> DownloadedScreen(vm)
            is Screen.JmFavorites -> FavoriteScreen(vm)
        }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel) {
    val tab by vm.currentTab.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    MainTab.Search to Icons.Filled.Search,
                    MainTab.Favorite to Icons.Filled.Favorite,
                    MainTab.Weekly to Icons.AutoMirrored.Filled.List,
                    MainTab.Download to JmIcons.Download,
                    MainTab.Mine to Icons.Filled.Person,
                )
                items.forEach { (t, icon) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.setCurrentTab(t) },
                        icon = { Icon(icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                MainTab.Search -> SearchScreen(vm)
                MainTab.Favorite -> LocalFavoritesScreen(vm)
                MainTab.Weekly -> WeeklyScreen(vm)
                MainTab.Download -> DownloadScreen(vm)
                MainTab.Mine -> MineScreen(vm)
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            JmIcons.Download,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "需要存储权限",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            "下载的漫画保存在公共下载目录（/Download/comics）。\n" +
                "请点击下方按钮，到系统设置中开启「允许访问所有文件」权限，然后返回本应用。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(
            onClick = { StoragePermissions.launchPermissionSettings(context) },
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        ) {
            Text("去授权")
        }
        Text(
            "授权完成后会自动检测并进入应用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
