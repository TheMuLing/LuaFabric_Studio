package com.luafabric.studio.falling.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DraggablePanelState(
    initialHeight: Float = 88.dp.value,
    minHeight: Float = 88.dp.value,
    var maxHeight: Float = Float.MAX_VALUE
) {
    var height by mutableStateOf(initialHeight)

    // [MODIFIED] 将 minHeight 改为可变状态，支持动态调整
    var minHeight by mutableStateOf(minHeight)
        private set

    var isDragging by mutableStateOf(false)
        private set

    /**
     * 更新最小高度，并确保当前高度符合新的约束
     */
    fun updateMinHeight(newMinHeight: Float) {
        minHeight = newMinHeight
        // 如果当前高度小于新的最小高度，调整高度
        if (height < minHeight) {
            height = minHeight
        }
    }

    /**
     * 计算面板展开比例（从底部向上）
     * 0% = minHeight（收起状态）
     * 100% = maxHeight（完全展开）
     */
    val expansionRatio: Float
        get() = if (maxHeight > minHeight) {
            ((height - minHeight) / (maxHeight - minHeight)).coerceIn(0f, 1f)
        } else {
            0f
        }

    /**
     * 检查是否超过 40% 阈值
     * 当面板高度超过最大高度的 40% 时返回 true
     */
    val isAboveThreshold: Boolean
        get() = expansionRatio >= 0.4f

    fun updateHeight(newHeight: Float) {
        height = newHeight.coerceIn(minHeight, maxHeight)
    }

    fun onDragStart() {
        isDragging = true
    }

    fun onDragEnd(scope: CoroutineScope) {
        isDragging = false

        scope.launch {
            // 只有当拖拽到非常接近最小高度时才自动收起
            if (height < minHeight * 1.5) {
                animateToHeight(minHeight)
            }
        }
    }

    fun animateToHeight(targetHeight: Float) {
        height = targetHeight.coerceIn(minHeight, maxHeight)
    }

    // 更新最大高度
    fun updateMaxHeight(newMaxHeight: Float) {
        maxHeight = newMaxHeight
        // 如果当前高度超过新的最大高度，调整高度
        if (height > maxHeight) {
            height = maxHeight
        }
    }
}

@Composable
fun rememberDraggablePanelState(
    minHeight: Float = 80.dp.value
): DraggablePanelState {
    return remember {
        DraggablePanelState(
            initialHeight = minHeight,
            minHeight = minHeight,
            maxHeight = Float.MAX_VALUE
        )
    }
}
