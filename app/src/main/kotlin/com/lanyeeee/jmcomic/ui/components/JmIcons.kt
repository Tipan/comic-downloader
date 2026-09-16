package com.lanyeeee.jmcomic.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自定义矢量图标（Material Design Icons，Apache 2.0）。
 * 核心 material-icons 没有这些图标，为避免引入庞大的 extended 依赖，自建轻量矢量。
 */
object JmIcons {
    val Download: ImageVector by lazy {
        icon("Download", "M5,20h14v-2H5V20zM19,9h-4V3H9v6H5l7,7L19,9z")
    }
    val CheckBox: ImageVector by lazy {
        icon(
            "CheckBox",
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8L10,17z",
        )
    }
    val CheckBoxOutlineBlank: ImageVector by lazy {
        icon(
            "CheckBoxOutlineBlank",
            "M19,5v14H5V5H19M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z",
        )
    }
    val Archive: ImageVector by lazy {
        icon(
            "Archive",
            "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6C5.53,3 5.12,3.21 4.84,3.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19C3,20.1 3.9,21 5,21h14C20.1,21 21,20.1 21,19V6.5C21,6.02 20.83,5.57 20.54,5.23zM6.24,5H17.76l0.83,1H5.42L6.24,5zM12,17.5L7.5,13H11v-2h2v2h3.5L12,17.5z",
        )
    }
    val Update: ImageVector by lazy {
        icon(
            "Update",
            "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12 20,7.58 16.42,4 12,4zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
        )
    }
    val Storage: ImageVector by lazy {
        icon(
            "Storage",
            "M2,20h20v-4H2V20zM4,17h2v2H4V17zM2,4v4h20V4H2zM6,7H4V5h2V7zM2,14h20v-4H2V14zM4,11h2v2H4V11z",
        )
    }
    val Delete: ImageVector by lazy {
        icon(
            "Delete",
            "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(nodes, fill = SolidColor(Color.Black)).build()
    }
}
