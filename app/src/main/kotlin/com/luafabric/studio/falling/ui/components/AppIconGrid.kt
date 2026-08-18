package com.luafabric.studio.falling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luafabric.studio.falling.R
import muling.views.tool.utils.IconManager

@Composable
fun AppIconGrid(
    selectedIcon: IconManager.AppIcon,
    onIconSelected: (IconManager.AppIcon) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start, // 从左开始排列
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 第一个图标：默认
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, // 图标和文字居中对齐
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(end = 32.dp) // 添加右侧间距
        ) {
            AppIconOption(
                modifier = Modifier.size(60.dp),
                iconResId = R.drawable.ic_launcher_playstore,
                isSelected = selectedIcon == IconManager.AppIcon.PLAY_STORE,
                onClick = { onIconSelected(IconManager.AppIcon.PLAY_STORE) }
            )
            Text(
                text = stringResource(R.string.settings_icon_default),
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedIcon == IconManager.AppIcon.PLAY_STORE)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selectedIcon == IconManager.AppIcon.PLAY_STORE)
                    FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center // 文字居中对齐
            )
        }

        // 第二个图标：adaptive
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, // 图标和文字居中对齐
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIconOption(
                modifier = Modifier.size(60.dp),
                iconResId = R.mipmap.ic_launcher,
                isSelected = selectedIcon == IconManager.AppIcon.ADAPTIVE,
                onClick = { onIconSelected(IconManager.AppIcon.ADAPTIVE) }
            )
            Text(
                text = stringResource(R.string.settings_icon_adaptive),
                style = MaterialTheme.typography.labelMedium,
                color = if (selectedIcon == IconManager.AppIcon.ADAPTIVE)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selectedIcon == IconManager.AppIcon.ADAPTIVE)
                    FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center // 文字居中对齐
            )
        }
    }
}

@Composable
fun AppIconOption(
    modifier: Modifier = Modifier,
    iconResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(iconResId)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}