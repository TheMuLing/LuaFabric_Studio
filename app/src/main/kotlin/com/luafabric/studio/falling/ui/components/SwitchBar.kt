package com.luafabric.studio.falling.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 风格的开关条组件
 * 点击整个条区域均可切换开关状态，波纹完美限制在圆角内
 */
@Composable
fun SwitchBar(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    colors: SwitchBarColors = SwitchBarDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    // ✅ 核心修复：使用 Card 的 onClick 参数处理点击和波纹
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.containerColor),
        shape = MaterialTheme.shapes.medium,
        onClick = { onCheckedChange(!checked) }, // ✅ 自动处理带圆角的波纹
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding), // 不再需要 clickable 和 clip
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = text,
                style = textStyle,
                color = colors.textColor,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = null, // 点击由 Card 处理
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.checkedThumbColor,
                    checkedTrackColor = colors.checkedTrackColor,
                    uncheckedThumbColor = colors.uncheckedThumbColor,
                    uncheckedTrackColor = colors.untrackedTrackColor,
                ),
            )
        }
    }
}

/**
 * 颜色配置类（保持不变）
 */
data class SwitchBarColors(
    val containerColor: Color,
    val textColor: Color,
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedThumbColor: Color,
    val untrackedTrackColor: Color,
)

/**
 * 默认配置（保持不变）
 */
object SwitchBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surface,
        textColor: Color = MaterialTheme.colorScheme.onSurface,
        checkedThumbColor: Color = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor: Color = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor: Color = MaterialTheme.colorScheme.outline,
        uncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ): SwitchBarColors = SwitchBarColors(
        containerColor = containerColor,
        textColor = textColor,
        checkedThumbColor = checkedThumbColor,
        checkedTrackColor = checkedTrackColor,
        uncheckedThumbColor = uncheckedThumbColor,
        untrackedTrackColor = uncheckedTrackColor,
    )
}
