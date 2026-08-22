package com.luafabric.studio.falling.ui.editor.ai

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material Design Icons (MDI) arrow-collapse-up / arrow-collapse-down，
// 不在 material-icons-extended 1.7.8 中，用官方 path 数据自建
val ArrowCollapseUpIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowCollapseUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M4.08,11.92L12,4L19.92,11.92L18.5,13.33L13,7.83V22H11V7.83L5.5,13.33L4.08,11.92M12,4H22V2H2V4H12Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

val ArrowCollapseDownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowCollapseDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M19.92,12.08L12,20L4.08,12.08L5.5,10.67L11,16.17V2H13V16.17L18.5,10.66L19.92,12.08M12,20H2V22H22V20H12Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}
