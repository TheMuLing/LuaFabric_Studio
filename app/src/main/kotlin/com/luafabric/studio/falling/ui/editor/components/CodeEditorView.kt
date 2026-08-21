package com.luafabric.studio.falling.ui.editor.components

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.luafabric.studio.falling.ui.editor.SwipeDirection
import com.luafabric.studio.falling.ui.editor.viewmodel.CodeEditorState
import com.luafabric.studio.falling.ui.editor.viewmodel.EditorViewModel
import com.luafabric.studio.falling.ui.settings.SettingsManager
import com.luafabric.studio.falling.ui.theme.DropShape
import com.luafabric.studio.falling.ui.theme.ThemeType
import muling.views.tool.utils.JsonUtil
import muling.views.tool.utils.LogCatcher
import muling.views.tool.utils.LuaParserUtil
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.getComponent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers as KotlinDispatchers
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CodeEditorView(
    modifier: Modifier = Modifier,
    state: CodeEditorState,
    viewModel: EditorViewModel,
    isActiveFile: Boolean = false,
    expansionRatio: Float = 0f,
    // 滑动手势回调
    onSwipe: ((SwipeDirection) -> Unit)? = null,
    // AI 代码引用回调
    onAiCodeReference: ((filePath: String, fileName: String, startLine: Int, endLine: Int, content: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var isEditorReady by remember { mutableStateOf(false) }

    val settingsState = SettingsManager.currentSettings
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (settingsState.darkMode) {
        com.luafabric.studio.falling.ui.settings.DarkMode.FOLLOW_SYSTEM -> systemDark
        com.luafabric.studio.falling.ui.settings.DarkMode.LIGHT -> false
        com.luafabric.studio.falling.ui.settings.DarkMode.DARK -> true
    }

    val seedColor = remember(settingsState.themeType) {
        when (settingsState.themeType) {
            ThemeType.GREEN -> androidx.compose.ui.graphics.Color(0xFF2E6A44)
            ThemeType.BLUE -> androidx.compose.ui.graphics.Color(0xFF36618E)
            ThemeType.PINK -> androidx.compose.ui.graphics.Color(0xFF8D4A5A)
        }
    }

    val materialColors = MaterialTheme.colorScheme
    val primaryColor = materialColors.primary.toArgb()
    val backgroundColor = materialColors.background.toArgb()
    val onBackgroundColor = materialColors.onBackground.toArgb()
    val outlineColor = materialColors.outline.toArgb()
    val surfaceColor = materialColors.surface.toArgb()
    val onSurfaceColor = materialColors.onSurface.toArgb()

    var syntaxError by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var lastSyntaxError by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var isHiddenByExpansion by remember { mutableStateOf(false) }

    val currentState by rememberUpdatedState(state)

    val editor = remember(state.file.absolutePath) {
        viewModel.getOrCreateEditor(context, currentState)
    }

    val scope = rememberCoroutineScope()
    var parseJob by remember { mutableStateOf<Job?>(null) }

    // 滑动检测阈值（px）
    val swipeThreshold = with(LocalDensity.current) { 30.dp.toPx() }

    LaunchedEffect(editor) {
        var lastTouchY = 0f
        var totalDeltaY = 0f
        var isSwiping = false

        editor.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.y
                    totalDeltaY = 0f
                    isSwiping = false
                    false // 不消费事件，让编辑器继续处理
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - lastTouchY
                    // 累加垂直滑动距离
                    totalDeltaY += deltaY
                    lastTouchY = event.y

                    // 只有当设置开启、累积距离超过阈值且尚未判定方向时才触发
                    if (settingsState.enableSwipeGesture && !isSwiping && abs(totalDeltaY) > swipeThreshold) {
                        isSwiping = true
                        val direction = if (totalDeltaY > 0) SwipeDirection.DOWN else SwipeDirection.UP
                        onSwipe?.invoke(direction)
                        // 触发后重置累积距离，避免连续触发
                        totalDeltaY = 0f
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSwiping = false
                    false
                }
                else -> false
            }
        }
    }

    // AI 代码引用按钮点击监听
    LaunchedEffect(editor) {
        val textActionWindow = editor.getComponent<EditorTextActionWindow>()
        textActionWindow.setCustomButtonClickListener { buttonId ->
            if (buttonId == 100) { // BTN_AI_REFERENCE
                val cursor = editor.cursor
                if (cursor.isSelected) {
                    val leftLine = cursor.leftLine
                    val leftColumn = cursor.leftColumn
                    val rightLine = cursor.rightLine
                    val rightColumn = cursor.rightColumn
                    val leftIdx = cursor.left
                    val rightIdx = cursor.right
                    val selectedText = editor.text.substring(leftIdx, rightIdx)
                    val filePath = currentState.file.absolutePath
                    val fileName = currentState.file.name
                    onAiCodeReference?.invoke(filePath, fileName, leftLine + 1, rightLine + 1, selectedText)
                    // 清除选取（用行/列坐标，避免索引越界）
                    editor.setSelection(cursor.leftLine, cursor.leftColumn)
                }
            }
        }
    }

    LaunchedEffect(expansionRatio, lastSyntaxError) {
        when {
            expansionRatio >= 0.4f -> {
                if (syntaxError != null) {
                    isHiddenByExpansion = true
                    syntaxError = null
                }
            }

            expansionRatio < 0.4f && isHiddenByExpansion && lastSyntaxError != null -> {
                isHiddenByExpansion = false
                syntaxError = lastSyntaxError
            }
        }
    }

    val parseLuaCode = { code: String ->
        parseJob?.cancel()
        parseJob = scope.launch(KotlinDispatchers.IO) {
            delay(500)
            val fileName = currentState.file.name.lowercase()
            if (!fileName.endsWith(".lua") && !fileName.endsWith(".aly")) {
                withContext(KotlinDispatchers.Main) {
                    lastSyntaxError = null
                    syntaxError = null
                }
                return@launch
            }
            try {
                val resultJson = LuaParserUtil.parse(code)
                val result = JsonUtil.parseObject(resultJson)
                withContext(KotlinDispatchers.Main) {
                    val status = result["status"] as? Boolean ?: false
                    if (status) {
                        lastSyntaxError = null
                        syntaxError = null
                    } else {
                        val line = (result["line"] as? Number)?.toInt() ?: 1
                        val message = result["message"] as? String ?: "UnknownError"
                        val errorPair = line to message
                        lastSyntaxError = errorPair
                        if (expansionRatio < 0.4f) {
                            syntaxError = errorPair
                        }
                    }
                }
            } catch (e: Exception) {
                LogCatcher.e("CodeEditorView", "Lua解析失败", e)
                withContext(KotlinDispatchers.Main) {
                    lastSyntaxError = null
                    syntaxError = null
                }
            }
        }
    }

    LaunchedEffect(
        seedColor, isDark, primaryColor, backgroundColor, onBackgroundColor,
        outlineColor, surfaceColor, onSurfaceColor
    ) {
        if (isEditorReady) {
            viewModel.updateEditorTheme(
                seedColor = seedColor,
                isDark = isDark,
                primaryColor = primaryColor,
                backgroundColor = backgroundColor,
                onBackgroundColor = onBackgroundColor,
                outlineColor = outlineColor,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor
            )
        }
    }

    LaunchedEffect(settingsState.editorFontType, settingsState.customFontPath) {
        if (isEditorReady) {
            viewModel.updateEditorFonts()
        }
    }

    LaunchedEffect(
        settingsState.classNameColor,
        settingsState.localVariableColor,
        settingsState.keywordColor,
        settingsState.functionNameColor,
        settingsState.literalColor,
        settingsState.commentColor,
        settingsState.selectedLineColor
    ) {
        if (isEditorReady) {
            viewModel.updateEditorSyntaxColors(settingsState)
        }
    }

    LaunchedEffect(settingsState.indentGuideEnabled) {
        if (isEditorReady) {
            viewModel.updateEditorIndentGuides()
        }
    }

    // 一次性初始化：设置编辑器就绪标志、执行初次语法解析、同步内容
    LaunchedEffect(Unit) {
        delay(50)
        isEditorReady = true
        parseLuaCode(currentState.content)

        editor.post {
            val currentText = editor.text.toString()
            if (currentText != currentState.content) {
                editor.setText(currentState.content)
                LogCatcher.d("CodeEditorView", "编辑器内容不同步，已修正: ${currentState.file.name}")
            }
        }

        viewModel.updateEditorTheme(
            seedColor = seedColor,
            isDark = isDark,
            primaryColor = primaryColor,
            backgroundColor = backgroundColor,
            onBackgroundColor = onBackgroundColor,
            outlineColor = outlineColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor
        )
    }

    LaunchedEffect(isActiveFile, isEditorReady) {
        if (isActiveFile && isEditorReady) {
            delay(50)
            editor.post {
                if (!editor.hasFocus()) {
                    editor.requestFocus()
                }
                editor.ensureSelectionVisible()
            }
        } else if (!isActiveFile) {
            editor.post {
                if (editor.hasFocus()) {
                    editor.clearFocus()
                    val inputMethodManager =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(editor.windowToken, 0)
                }
            }
        }
    }

    LaunchedEffect(settingsState.editorWordWrap) {
        if (isEditorReady) {
            editor.isWordwrap = settingsState.editorWordWrap
        }
    }

    DisposableEffect(editor) {
        val listener = object : ContentListener {
            override fun beforeReplace(content: io.github.rosemoe.sora.text.Content) {}
            override fun afterInsert(
                content: io.github.rosemoe.sora.text.Content,
                startLine: Int, startColumn: Int,
                endLine: Int, endColumn: Int,
                inserted: CharSequence
            ) {
                parseLuaCode(content.toString())
            }

            override fun afterDelete(
                content: io.github.rosemoe.sora.text.Content,
                startLine: Int, startColumn: Int,
                endLine: Int, endColumn: Int,
                deleted: CharSequence
            ) {
                parseLuaCode(content.toString())
            }
        }
        editor.text.addContentListener(listener)

        // 添加选择变化监听器
        val selectionReceipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            val left = event.left
            val right = event.right
            if (left != right) {
                // 有选中文本
                val selectedText = editor.text.substring(left.index, right.index)
                viewModel.checkAndSetSelectedClass(selectedText)
            } else {
                // 无选中
                viewModel.clearSelectedClass()
            }
        }

        onDispose {
            parseJob?.cancel()
            try {
                editor.text.removeContentListener(listener)
                selectionReceipt.unsubscribe()
            } catch (e: Exception) {
                LogCatcher.e("CodeEditorView", "移除监听器失败", e)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isEditorReady) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AndroidView(
                    factory = { _ ->
                        (editor.parent as? ViewGroup)?.removeView(editor)
                        editor.isFocusable = true
                        editor.isFocusableInTouchMode = true
                        editor
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { viewEditor ->
                        val currentEditorContent = viewEditor.text.toString()
                        if (currentEditorContent != currentState.content) {
                            val cursor = viewEditor.cursor
                            val cursorLine = cursor.leftLine
                            val cursorColumn = cursor.leftColumn
                            viewEditor.setText(currentState.content)
                            LogCatcher.d(
                                "CodeEditorView",
                                "AndroidView update 同步内容: ${currentState.file.name}"
                            )
                            try {
                                val lineCount = viewEditor.text.lineCount
                                val targetLine = cursorLine.coerceIn(0, lineCount - 1)
                                val lineLength = viewEditor.text.getColumnCount(targetLine)
                                val targetColumn = cursorColumn.coerceIn(0, lineLength)
                                viewEditor.setSelection(targetLine, targetColumn)
                            } catch (e: Exception) {
                                LogCatcher.e("CodeEditorView", "设置光标位置失败", e)
                            }
                        }

                        viewEditor.isEnabled = true
                        viewEditor.visibility = android.view.View.VISIBLE
                        viewEditor.requestLayout()
                    }
                )

                AnimatedContent(
                    targetState = syntaxError,
                    transitionSpec = {
                        if (targetState != null && initialState == null) {
                            (scaleIn(
                                initialScale = 0f,
                                animationSpec = tween(durationMillis = 350),
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 200, delayMillis = 50)
                            )).togetherWith(
                                scaleOut(
                                    targetScale = 0.3f,
                                    animationSpec = tween(durationMillis = 200),
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 150)
                                )
                            )
                        } else if (targetState == null && initialState != null) {
                            (scaleIn(
                                initialScale = 0.3f,
                                animationSpec = tween(durationMillis = 200),
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 150)
                            )).togetherWith(
                                scaleOut(
                                    targetScale = 0f,
                                    animationSpec = tween(durationMillis = 300),
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 200, delayMillis = 50)
                                )
                            )
                        } else {
                            fadeIn(animationSpec = tween(durationMillis = 150)).togetherWith(fadeOut(animationSpec = tween(durationMillis = 150)))
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { currentError ->
                    if (currentError != null) {
                        val (line, message) = currentError
                        var animationFinished by remember { mutableStateOf(false) }
                        LaunchedEffect(currentError) {
                            animationFinished = false
                            delay(350)
                            animationFinished = true
                        }
                        DisposableEffect(Unit) {
                            onDispose { animationFinished = false }
                        }
                        val elevation by animateFloatAsState(
                            targetValue = if (animationFinished) 1f else 0f,
                            animationSpec = tween(durationMillis = 150),
                            label = "shadow_elevation"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .shadow(
                                    elevation = elevation.dp,
                                    shape = DropShape(
                                        cornerSize = 12.dp,
                                        spikeWidth = 24.dp,
                                        spikeHeight = 10.dp
                                    ),
                                    clip = true
                                )
                        ) {
                            Text(
                                text = "Line $line: $message",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(
                                        start = 16.dp,
                                        top = 12.dp,
                                        end = 16.dp,
                                        bottom = 20.dp
                                    )
                            )
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.dp)
                        )
                    }
                }
            }
        }
    }
}