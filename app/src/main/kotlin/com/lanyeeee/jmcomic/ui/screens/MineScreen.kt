package com.lanyeeee.jmcomic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import com.lanyeeee.jmcomic.ui.components.JmIcons
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lanyeeee.jmcomic.data.network.JmApi
import com.lanyeeee.jmcomic.ui.MainViewModel
import com.lanyeeee.jmcomic.ui.Screen
import com.lanyeeee.jmcomic.ui.components.LoginDialog
import com.lanyeeee.jmcomic.ui.components.SettingsDialog

@Composable
fun MineScreen(vm: MainViewModel) {
    val user by vm.userProfile.collectAsState()
    val config by vm.config.collectAsState()
    val updateProgress by vm.updateProgress.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    if (showLogin) LoginDialog(vm) { showLogin = false }
    if (showSettings) SettingsDialog(vm) { showSettings = false }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 用户卡片
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (user != null) {
                    AsyncImage(
                        model = user!!.photo,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(user!!.username, style = MaterialTheme.typography.titleMedium)
                        Text("Lv.${user!!.level} ${user!!.levelName}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = vm::logout) { Text("退出") }
                } else {
                    Icon(Icons.Filled.Person, null, Modifier.size(48.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(if (config.username.isBlank()) "未登录" else "已保存账号：${config.username}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("登录后可查看收藏夹", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showLogin = true }) { Text("登录") }
                }
            }
        }

        Spacer(Modifier.size(16.dp))

        // 功能入口
        Card(Modifier.fillMaxWidth()) {
            MenuRow(Icons.Filled.Star, "收藏夹（需登录）", "云端收藏的漫画") { vm.navigate(Screen.JmFavorites) }
            HorizontalDivider()
            MenuRow(JmIcons.Storage, "本地库存", "查看已下载的漫画") { vm.navigate(Screen.Downloaded) }
            HorizontalDivider()
            MenuRow(Icons.Filled.Settings, "设置", "下载路径 / 格式 / 线路 / 代理 / 并发") { showSettings = true }
            HorizontalDivider()
            MenuRow(Icons.Filled.Info, "关于", "漫画下载器 · Android") { showAbout = true }
        }

        if (updateProgress != null) {
            Text(
                "更新库存中 ${updateProgress!!.first}/${updateProgress!!.second}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.size(16.dp))
        Text(
            "下载目录：${config.downloadDir}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "当前线路：${if (config.apiDomainMode.name == "Custom") config.customApiDomain else config.apiDomainMode.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showAbout) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("关于") },
                text = {
                    Text(
                        "漫画下载器\n\nAndroid native 重构版（Kotlin + Jetpack Compose）。\n" +
                            "支持搜索、收藏夹、每周必看、本地库存、多线程下载、反切片还原、阅读器与 CBZ 导出。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAbout = false }) { Text("知道了") }
                },
            )
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
    }
}
