package com.luafabric.studio.falling.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.ui.settings.SettingsManager
import io.github.tarifchakder.ktoast.ToastData

@Composable
fun Toast(toastData: ToastData) {
    val borderEnabled = SettingsManager.currentSettings.toastBorderEnabled

    val containerColor = lerp(
        start = MaterialTheme.colorScheme.surfaceContainerHighest,
        stop = MaterialTheme.colorScheme.primary,
        fraction = 0.05f
    )

    Card(
        modifier = if (borderEnabled) {
            Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
        } else {
            Modifier
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = toastData.visuals.message,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}