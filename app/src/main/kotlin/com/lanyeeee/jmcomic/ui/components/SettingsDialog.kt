package com.lanyeeee.jmcomic.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.data.local.StoragePaths
import com.lanyeeee.jmcomic.domain.model.ApiDomainMode
import com.lanyeeee.jmcomic.domain.model.Config
import com.lanyeeee.jmcomic.domain.model.DownloadFormat
import com.lanyeeee.jmcomic.domain.model.ProxyMode
import com.lanyeeee.jmcomic.ui.MainViewModel

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val config = vm.config.value
    var d by remember { mutableStateOf(config) }
    val context = LocalContext.current

    val downloadDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { StoragePaths.treeUriToPath(it)?.let { p -> d = d.copy(downloadDir = p) } }
    }
    val exportDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { StoragePaths.treeUriToPath(it)?.let { p -> d = d.copy(exportDir = p) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingLabel("下载格式")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadFormat.entries.forEach { fmt ->
                        OutlinedButton(
                            onClick = { d = d.copy(downloadFormat = fmt) },
                            modifier = Modifier.weight(1f),
                        ) {
                            val selected = d.downloadFormat == fmt
                            Text(
                                when (fmt) {
                                    DownloadFormat.Jpeg -> "JPG"
                                    DownloadFormat.Png -> "PNG"
                                    DownloadFormat.Webp -> "WebP"
                                },
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else androidx.compose.ui.graphics.Color.Unspecified,
                            )
                        }
                    }
                }

                SettingLabel("目录格式（至少两级）")
                OutlinedTextField(
                    value = d.dirFmt,
                    onValueChange = { d = d.copy(dirFmt = it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "可用变量：{comic_title} {author} {comic_id} {chapter_title} {chapter_id} {order}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingLabel("下载目录")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = d.downloadDir,
                        onValueChange = { d = d.copy(downloadDir = it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { downloadDirLauncher.launch(null) }) { Text("选择") }
                }
                Text(
                    "更改后新下载使用该目录，已有文件不会移动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingLabel("导出目录（CBZ）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = d.exportDir,
                        onValueChange = { d = d.copy(exportDir = it) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { exportDirLauncher.launch(null) }) { Text("选择") }
                }

                SettingLabel("API 线路")
                DropdownSelector(
                    items = ApiDomainMode.entries.map { it.name },
                    selected = d.apiDomainMode.name,
                    onSelect = { name -> d = d.copy(apiDomainMode = ApiDomainMode.valueOf(name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d.apiDomainMode == ApiDomainMode.Custom) {
                    OutlinedTextField(
                        value = d.customApiDomain,
                        onValueChange = { d = d.copy(customApiDomain = it) },
                        singleLine = true,
                        label = { Text("自定义域名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingLabel("代理")
                DropdownSelector(
                    items = listOf("系统", "直连", "自定义"),
                    selected = when (d.proxyMode) {
                        ProxyMode.System -> "系统"
                        ProxyMode.NoProxy -> "直连"
                        ProxyMode.Custom -> "自定义"
                    },
                    onSelect = { name ->
                        d = d.copy(proxyMode = when (name) {
                            "系统" -> ProxyMode.System
                            "直连" -> ProxyMode.NoProxy
                            else -> ProxyMode.Custom
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d.proxyMode == ProxyMode.Custom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = d.proxyHost,
                            onValueChange = { d = d.copy(proxyHost = it) },
                            singleLine = true,
                            label = { Text("代理地址") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = d.proxyPort.toString(),
                            onValueChange = { d = d.copy(proxyPort = it.toIntOrNull() ?: 0) },
                            singleLine = true,
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                        )
                    }
                }

                SettingLabel("并发")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.chapterConcurrency.toString(),
                        onValueChange = { d = d.copy(chapterConcurrency = it.toIntOrNull() ?: 1) },
                        singleLine = true,
                        label = { Text("章节并发") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = d.imgConcurrency.toString(),
                        onValueChange = { d = d.copy(imgConcurrency = it.toIntOrNull() ?: 1) },
                        singleLine = true,
                        label = { Text("图片并发") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("下载封面", modifier = Modifier.weight(1f))
                    Switch(
                        checked = d.shouldDownloadCover,
                        onCheckedChange = { d = d.copy(shouldDownloadCover = it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateConfig(d)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun DropdownSelector(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    onSelect(item)
                    expanded = false
                })
            }
        }
    }
}
