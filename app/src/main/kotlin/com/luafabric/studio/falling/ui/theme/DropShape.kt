package com.luafabric.studio.falling.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 水滴形状：四边圆角，底部中间有尖角
 * @param topStart 左上角圆角
 * @param topEnd 右上角圆角
 * @param bottomStart 左下角圆角
 * @param bottomEnd 右下角圆角
 * @param spikeWidth 底部尖角的宽度
 * @param spikeHeight 底部尖角的高度
 */
class DropShape(
    private val topStart: CornerSize = CornerSize(12.dp),
    private val topEnd: CornerSize = CornerSize(12.dp),
    private val bottomStart: CornerSize = CornerSize(12.dp),
    private val bottomEnd: CornerSize = CornerSize(12.dp),
    private val spikeWidth: Dp = 20.dp,
    private val spikeHeight: Dp = 12.dp
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val centerX = width / 2

        return with(density) {
            val topStartPx = topStart.toPx(size, density)
            val topEndPx = topEnd.toPx(size, density)
            val bottomStartPx = bottomStart.toPx(size, density)
            val bottomEndPx = bottomEnd.toPx(size, density)
            val spikeWidthPx = spikeWidth.toPx()
            val spikeHeightPx = spikeHeight.toPx()

            val path = Path().apply {
                moveTo(0f, topStartPx)

                // 上边
                arcTo(
                    rect = Rect(0f, 0f, topStartPx * 2, topStartPx * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(width - topEndPx, 0f)
                arcTo(
                    rect = Rect(width - topEndPx * 2, 0f, width, topEndPx * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )

                // 右边
                lineTo(width, height - bottomEndPx - spikeHeightPx)
                arcTo(
                    rect = Rect(
                        width - bottomEndPx * 2,
                        height - bottomEndPx * 2 - spikeHeightPx,
                        width,
                        height - spikeHeightPx
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )

                // 底边右侧 -> 尖角 -> 底边左侧
                lineTo(centerX + spikeWidthPx / 2, height - spikeHeightPx)
                lineTo(centerX, height)  // 尖角顶点
                lineTo(centerX - spikeWidthPx / 2, height - spikeHeightPx)

                // 左边
                lineTo(bottomStartPx, height - spikeHeightPx)
                arcTo(
                    rect = Rect(
                        0f,
                        height - bottomStartPx * 2 - spikeHeightPx,
                        bottomStartPx * 2,
                        height - spikeHeightPx
                    ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(0f, topStartPx)

                close()
            }

            Outline.Generic(path)
        }
    }
}

// 便捷创建函数
fun DropShape(
    cornerSize: Dp = 12.dp,
    spikeWidth: Dp = 20.dp,
    spikeHeight: Dp = 12.dp
): DropShape = DropShape(
    topStart = CornerSize(cornerSize),
    topEnd = CornerSize(cornerSize),
    bottomStart = CornerSize(cornerSize),
    bottomEnd = CornerSize(cornerSize),
    spikeWidth = spikeWidth,
    spikeHeight = spikeHeight
)
