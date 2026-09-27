package com.luafabric.studio.falling.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 快捷功能数据类（onClick 放最后，便于 trailing lambda）
data class QuickAction(
    val labelResId: Int,
    val key: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

@Composable
fun QuickActionToolbar(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    iconOnly: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                QuickActionButton(action = action, iconOnly = iconOnly)
            }
        }
    }
}

@Composable
fun QuickActionButton(
    action: QuickAction,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        onClick = action.onClick
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = if (iconOnly) 10.dp else 12.dp,
                    vertical = 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconOnly && action.icon != null) {
                // 无字模式：仅显示图标（避免过大）
                Icon(
                    imageVector = action.icon,
                    contentDescription = stringResource(action.labelResId),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = stringResource(action.labelResId),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}