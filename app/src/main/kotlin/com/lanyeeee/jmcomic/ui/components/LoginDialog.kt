package com.lanyeeee.jmcomic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanyeeee.jmcomic.ui.MainViewModel

@Composable
fun LoginDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val loading by vm.loginLoading.collectAsState()
    val error by vm.loginError.collectAsState()
    val config = vm.config.value
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("登录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "登录后可查看收藏夹；不登录也能搜索和下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (loading) {
                CircularProgressIndicator(Modifier.padding(end = 16.dp))
            } else {
                TextButton(
                    onClick = {
                        vm.login(username, password)
                    },
                ) { Text("登录") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
