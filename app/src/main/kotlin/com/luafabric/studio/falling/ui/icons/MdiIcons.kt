package com.luafabric.studio.falling.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material Design Icons (MDI) 7.4.47 官方 path 数据（MIT 许可），
// 不在 material-icons-extended 1.7.8 中，故自建 ImageVector。
// 来源：https://pictogrammers.com/library/mdi/

val GithubIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M12,2A10,10 0 0,0 2,12C2,16.42 4.87,20.17 8.84,21.5C9.34,21.58 9.5,21.27 9.5,21C9.5,20.77 9.5,20.14 9.5,19.31C6.73,19.91 6.14,17.97 6.14,17.97C5.68,16.81 5.03,16.5 5.03,16.5C4.12,15.88 5.1,15.9 5.1,15.9C6.1,15.97 6.63,16.93 6.63,16.93C7.5,18.45 8.97,18 9.54,17.76C9.63,17.11 9.89,16.67 10.17,16.42C7.95,16.17 5.62,15.31 5.62,11.5C5.62,10.39 6,9.5 6.65,8.79C6.55,8.54 6.2,7.5 6.75,6.15C6.75,6.15 7.59,5.88 9.5,7.17C10.29,6.95 11.15,6.84 12,6.84C12.85,6.84 13.71,6.95 14.5,7.17C16.41,5.88 17.25,6.15 17.25,6.15C17.8,7.5 17.45,8.54 17.35,8.79C18,9.5 18.38,10.39 18.38,11.5C18.38,15.32 16.04,16.16 13.81,16.41C14.17,16.72 14.5,17.33 14.5,18.26C14.5,19.6 14.5,20.68 14.5,21C14.5,21.27 14.66,21.59 15.17,21.5C19.14,20.16 22,16.42 22,12A10,10 0 0,0 12,2Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

val AndroidStudioIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AndroidStudio",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M11,2H13V4H13.5A1.5,1.5 0 0,1 15,5.5V9L14.56,9.44L16.2,12.28C17.31,11.19 18,9.68 18,8H20C20,10.42 18.93,12.59 17.23,14.06L20.37,19.5L20.5,21.72L18.63,20.5L15.56,15.17C14.5,15.7 13.28,16 12,16C10.72,16 9.5,15.7 8.44,15.17L5.37,20.5L3.5,21.72L3.63,19.5L9.44,9.44L9,9V5.5A1.5,1.5 0 0,1 10.5,4H11V2M9.44,13.43C10.22,13.8 11.09,14 12,14C12.91,14 13.78,13.8 14.56,13.43L13.1,10.9H13.09C12.47,11.5 11.53,11.5 10.91,10.9H10.9L9.44,13.43M12,6A1,1 0 0,0 11,7A1,1 0 0,0 12,8A1,1 0 0,0 13,7A1,1 0 0,0 12,6Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

val MicroscopeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Microscope",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M9.46,6.28L11.05,9C8.47,9.26 6.5,11.41 6.5,14A5,5 0 0,0 11.5,19C13.55,19 15.31,17.77 16.08,16H13.5V14H21.5V16H19.25C18.84,17.57 17.97,18.96 16.79,20H19.5V22H3.5V20H6.21C4.55,18.53 3.5,16.39 3.5,14C3.5,10.37 5.96,7.2 9.46,6.28M12.74,2.07L13.5,3.37L14.36,2.87L17.86,8.93L14.39,10.93L10.89,4.87L11.76,4.37L11,3.07L12.74,2.07Z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}