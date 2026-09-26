package com.luafabric.studio.falling.ui.sponsor

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.settings.SettingsManager

/**
 * 构建次数赞助提示的业务逻辑与弹窗。
 */
object Sponsorship {

    // 第 r 轮阈值：threshold(r) = 5 * r * (r + 1) / 2
    fun threshold(round: Int): Int = 5 * round * (round + 1) / 2

    /**
     * 每次构建开始时调用：累计构建次数并评估是否需要弹出赞助提示。
     */
    fun recordBuild(context: Context) {
        val current = SettingsManager.currentSettings
        val newCount = current.buildCount + 1
        var round = if (current.sponsorRound > 0) current.sponsorRound else 1
        var skipNext = current.skipNextSponsor
        var showAt: Int? = null

        while (true) {
            if (newCount < threshold(round)) break
            if (skipNext) {
                // 上一轮已赞助，本轮跳过并推进到下一轮
                skipNext = false
                round += 1
                continue
            }
            // 需要弹窗，记录轮次并中止
            showAt = round
            break
        }

        SettingsManager.updateSettings(
            current.copy(
                buildCount = newCount,
                sponsorRound = round,
                skipNextSponsor = skipNext
            )
        )
        SettingsManager.saveSettings(context)

        if (showAt != null) {
            SettingsManager.pendingSponsorPrompt = newCount
        }
    }

    /**
     * 用户关闭赞助弹窗（无论赞助/拒绝）后：推进轮次并清掉悬停弹窗。
     * 不再借此设置"跳过一轮"——跳过只能由赞助页的"投喂我"按钮触发。
     */
    fun onAnswer(context: Context) {
        val current = SettingsManager.currentSettings
        val baseRound = if (current.sponsorRound > 0) current.sponsorRound else 1
        SettingsManager.updateSettings(
            current.copy(
                sponsorRound = baseRound + 1,
                skipNextSponsor = false
            )
        )
        SettingsManager.saveSettings(context)
        SettingsManager.pendingSponsorPrompt = null
    }

    /**
     * 点击赞助页"投喂我"按钮：标记跳过下一轮（幂等，多次点击只记一次）。
     */
    fun onFeed(context: Context) {
        val current = SettingsManager.currentSettings
        if (current.skipNextSponsor) {
            // 已标记过，重复点击不叠加、不再落库
            return
        }
        SettingsManager.updateSettings(
            current.copy(skipNextSponsor = true)
        )
        SettingsManager.saveSettings(context)
    }
}

/**
 * 赞助提示弹窗。正/负按钮都先调用 onAnswer 清理 pendingSponsorPrompt，再关闭弹窗避免重复弹出。
 */
@Composable
fun SponsorshipDialog(
    onSponsor: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val buildCountText = (SettingsManager.pendingSponsorPrompt ?: 0).toString()
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* 点击空白区域不关闭，仅能通过按钮关闭 */ },
            shape = MaterialTheme.shapes.medium,
            title = {
                Text("求赞助啦......")
            },
            text = {
                Text(
                    "$appName 已经为你构建 $buildCountText 次软件啦!\n" +
                        "如果觉得 $appName 还算好用的话，也可以考虑小小地赞助一下作者呢qwq......\n" +
                        "毕竟一个人开发也不容易(小声)......"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        Sponsorship.onAnswer(context)
                        onSponsor()
                    }
                ) {
                    Text("这就赞助！")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        Sponsorship.onAnswer(context)
                    }
                ) {
                    Text("但是我拒绝")
                }
            }
        )
    }
}