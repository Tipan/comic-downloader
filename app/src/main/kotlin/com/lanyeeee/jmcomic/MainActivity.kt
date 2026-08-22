package com.lanyeeee.jmcomic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lanyeeee.jmcomic.ui.AppRoot
import com.lanyeeee.jmcomic.ui.theme.JmComicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JmComicTheme {
                AppRoot()
            }
        }
    }
}
