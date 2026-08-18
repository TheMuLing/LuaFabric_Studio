package com.luafabric.studio.falling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 支持边缘滑动的抽屉组件
 * 只在屏幕左侧边缘触发抽屉手势，避免与编辑器冲突
 */
@Composable
fun EdgeSwipeDismissibleDrawer(
    drawerState: DrawerState,  // 这里应该是 DrawerState，不是 DrawState
    modifier: Modifier = Modifier,
    edgeWidth: Float = 20f,
    gesturesEnabled: Boolean = true,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { edgeWidth.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DismissibleNavigationDrawer(
            drawerState = drawerState,
            modifier = Modifier.fillMaxSize(),
            gesturesEnabled = gesturesEnabled && drawerState.isOpen,
            drawerContent = drawerContent,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(drawerState, edgeWidthPx, gesturesEnabled) {
                            if (!gesturesEnabled) return@pointerInput

                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    val downX = down.position.x

                                    if (downX <= edgeWidthPx && drawerState.isClosed) {
                                        down.consume()

                                        var accumulatedX = 0f
                                        var shouldOpen = false
                                        var lastX = downX

                                        while (down.pressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull()

                                            if (change != null) {
                                                val currentX = change.position.x
                                                val deltaX = currentX - lastX
                                                accumulatedX += deltaX
                                                lastX = currentX

                                                if (accumulatedX > 30f) {
                                                    shouldOpen = true
                                                }

                                                change.consume()
                                            }

                                            if (!down.pressed) break
                                        }

                                        if (shouldOpen) {
                                            scope.launch { drawerState.open() }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // 先渲染用户传入的内容
                    content()

                    // 然后在最上层添加透明点击层（当抽屉打开时）
                    if (drawerState.isOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable(
                                    indication = null,
                                    interactionSource = null
                                ) {
                                    scope.launch { drawerState.close() }
                                }
                        )
                    }
                }
            }
        )
    }
}