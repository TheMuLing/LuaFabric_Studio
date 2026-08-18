@file:OptIn(ExperimentalMaterial3Api::class)

package com.luafabric.studio.falling.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.editor.persistence.EditorStateUtil
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import com.luafabric.studio.falling.ui.settings.SettingsManager
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.NonBlockingToastState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EditorTopBar(
    projectName: String,
    currentFileName: String,
    drawerState: DrawerState,
    onDrawerToggle: () -> Unit,
    viewModel: EditorViewModel,
    toast: NonBlockingToastState,
    context: Context,
    projectPath: String,
    isMoreMenuExpanded: Boolean,
    onMoreMenuExpandedChange: (Boolean) -> Unit,
    isCompilingFile: Boolean,
    onCompileFile: () -> Unit,
    onBuildProject: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val appName = getAppNameFromSettings(projectPath) ?: projectName

    TopAppBar(
        title = {
            Column {
                Text(appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentFileName.isNotEmpty()) {
                    Text(
                        text = currentFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            AnimatedDrawerToggle(
                isOpen = drawerState.isOpen,
                onClick = onDrawerToggle
            )
        },
        actions = {
            IconButton(onClick = { viewModel.undo() }) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.code_editor_undo))
            }
            IconButton(onClick = { viewModel.redo() }) {
                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.code_editor_redo))
            }

            IconButton(onClick = {
                scope.launch {
                    viewModel.saveAllFilesSilently()

                    val mainLuaFile = File(projectPath, "main.lua")

                    if (!mainLuaFile.exists() || !mainLuaFile.isFile) {
                        toast.showToast(context.getString(R.string.code_editor_main_lua_not_found))
                        return@launch
                    }

                    try {
                        val intent = Intent(context, com.androlua.LuaActivity::class.java)
                        intent.data = Uri.fromFile(mainLuaFile)
                        context.startActivity(intent)

                    } catch (_: ActivityNotFoundException) {
                        toast.showToast(context.getString(R.string.code_editor_install_not_found))
                    } catch (_: SecurityException) {
                        toast.showToast(context.getString(R.string.code_editor_install_permission_denied))
                    } catch (e: Exception) {
                        LogCatcher.e("CodeEditScreen", "启动失败", e)
                        toast.showToast(context.getString(R.string.code_editor_run_failed, e.message))
                    }
                }
            }) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.code_editor_run))
            }

            Box {
                IconButton(onClick = { onMoreMenuExpandedChange(true) }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.code_editor_more))
                }
                EditorMoreMenu(
                    expanded = isMoreMenuExpanded,
                    onDismiss = { onMoreMenuExpandedChange(false) },
                    viewModel = viewModel,
                    toast = toast,
                    context = context,
                    projectPath = projectPath,
                    isCompilingFile = isCompilingFile,
                    onCompileFile = onCompileFile,
                    onBuildProject = onBuildProject
                )
            }
        }
    )
}

@Composable
fun EditorMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: EditorViewModel,
    toast: NonBlockingToastState,
    context: Context,
    projectPath: String,
    isCompilingFile: Boolean,
    onCompileFile: () -> Unit,
    onBuildProject: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings = SettingsManager.currentSettings
    var wordWrap by remember { mutableStateOf(settings.editorWordWrap) }
    var isEditSubmenu by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (!expanded) {
            isEditSubmenu = false
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (isEditSubmenu) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.code_editor_edit))
                    }
                },
                onClick = {
                    isEditSubmenu = false
                }
            )
            Divider()
            // 自动换行
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = wordWrap,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.code_editor_auto_wrap))
                    }
                },
                onClick = {
                    val newValue = !wordWrap
                    wordWrap = newValue
                    val newSettings = settings.copy(editorWordWrap = newValue)
                    SettingsManager.updateSettings(newSettings)
                    SettingsManager.saveSettings(context)
                    onDismiss()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_editor_edit)) },
                onClick = {
                    isEditSubmenu = true
                }
            )

            if (viewModel.activeFileState?.file?.let {
                    it.name.endsWith(".lua", ignoreCase = true) ||
                            it.name.endsWith(".aly", ignoreCase = true)
                } == true) {
                DropdownMenuItem(
                    text = {
                        if (isCompilingFile) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(stringResource(R.string.code_editor_compiling))
                            }
                        } else {
                            Text(stringResource(R.string.code_editor_compile_file))
                        }
                    },
                    onClick = {
                        onCompileFile()
                        onDismiss()
                    }
                )
                Divider()
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_editor_save_all)) },
                onClick = {
                    scope.launch {
                        viewModel.saveAllModifiedFiles(toast)
                    }
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_editor_format_code)) },
                onClick = {
                    viewModel.formatCode()
                    onDismiss()
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_editor_build)) },
                onClick = {
                    onBuildProject()
                    onDismiss()
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.code_editor_clear_cache)) },
                onClick = {
                    scope.launch {
                        try {
                            EditorStateUtil.cleanProjectState(context, projectPath)
                            toast.showToast(context.getString(R.string.code_editor_cache_cleared))
                        } catch (e: Exception) {
                            LogCatcher.e("CodeEditScreen", "清理失败", e)
                            toast.showToast(context.getString(R.string.code_editor_clear_cache_failed, e.message))
                        }
                    }
                    onDismiss()
                }
            )
        }
    }
}