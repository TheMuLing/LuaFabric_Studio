@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.luafabric.studio.falling.ui.editor.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.luafabric.studio.falling.R
import com.luafabric.studio.falling.ui.editor.ai.tools.*
import com.luafabric.studio.falling.ui.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class AiPage { CHAT, SETTINGS, HISTORY }

/** AI 提问（横幅/弹窗共用）：AI 询问用户，用户作答后经 callback 回传。 */
private data class AskUserPromptState(
    val title: String,
    val description: String,
    val options: List<String>,
    val multi: Boolean,
    val callback: (List<String>?) -> Unit
)

/** 编辑框/容器圆角跟随 luafabric 主题 shapeSizeIndex（0-3 → 4/8/12/16dp）。 */
private fun askUiThemeRadius(): Dp = when (SettingsManager.currentSettings.shapeSizeIndex) {
    0 -> 4.dp
    1 -> 8.dp
    2 -> 12.dp
    3 -> 16.dp
    else -> 12.dp
}

/**
 * AI 会话内存态：大块数据（对话消息等）放这里而非 rememberSaveable，
 * 避免 Activity 重建（息屏/横竖屏）时 instance state 超 Binder 1MB 限制
 * 抛 TransactionTooLargeException。
 *
 * 全部状态用真实 MutableState（而非普通 var + commitSession 同步）：
 * - 重建后新 AiChatPanel 直接订阅同一份状态，消息/流式进度不丢；
 * - streamScope 为进程级 scope，旋转/重建不取消正在进行的流式输出；
 * - sendJob 也存这里，重建后「停止」按钮仍能取消同一个流。
 *
 * 按项目隔离：对话消息/流式/提问/摘要以项目路径为 key 分区存储，
 * 切换项目不串记录（修复跨项目共享同一份会话的问题）。
 */
private object AiChatSessionState {
    private val projectStates = mutableMapOf<String, ProjectChatState>()

    fun forProject(projectPath: String): ProjectChatState = synchronized(this) {
        projectStates.getOrPut(projectPath) { ProjectChatState() }
    }

    /** 导航页签全局共享（不按项目区分）。 */
    val currentPage = mutableStateOf(AiPage.CHAT)
}

/** 单个项目的 AI 会话内存态。 */
private class ProjectChatState {
    val messages = mutableStateOf<List<ChatMessage>>(emptyList())
    val summary = mutableStateOf("")
    val isStreaming = mutableStateOf(false)
    val streamingMessageId = mutableStateOf<String?>(null)
    val inputValue = mutableStateOf(TextFieldValue(""))
    val wasInterrupted = mutableStateOf(false)
    val shareMode = mutableStateOf(false)
    val selectedShareMessages = mutableStateOf<Set<String>>(emptySet())

    /** 进程中待回答的 AI 提问（横幅/弹窗共源，重建不丢）。 */
    val pendingAsk = mutableStateOf<AskUserPromptState?>(null)

    /** 进程级流式 scope：横竖屏重建不取消，保证流式输出续走。 */
    val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前进行中的流式任务，存单例使重建后「停止」仍可取消它。 */
    var sendJob: kotlinx.coroutines.Job? = null
}

// ========== 自定义 md3 引号图标 ==========
// 基于 Google Material Icons 官方 format_quote 24px path 数据：
//   "M6,17h3l2-4V6H4v7h4L6,17z M15,17h3l2-4V6h-7v7h4L15,17z"
// 开引号（format-quote-open）取第一段左引号原样；
// 关引号（format-quote-close）取第二段右引号并做水平镜像（x' = 24 - x），
// 使开口方向相反成对。
private val FormatQuoteOpenIcon: ImageVector = ImageVector.Builder(
    name = "FormatQuoteOpen",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path(fill = SolidColor(Color.Black)) {
    moveTo(6f, 17f)
    horizontalLineToRelative(3f)
    lineToRelative(2f, -4f)
    verticalLineTo(6f)
    horizontalLineTo(4f)
    verticalLineToRelative(7f)
    horizontalLineToRelative(4f)
    lineTo(6f, 17f)
    close()
}.build()

private val FormatQuoteCloseIcon: ImageVector = ImageVector.Builder(
    name = "FormatQuoteClose",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).path(fill = SolidColor(Color.Black)) {
    moveTo(9f, 17f)
    horizontalLineToRelative(-3f)
    lineToRelative(-2f, -4f)
    verticalLineTo(6f)
    horizontalLineToRelative(7f)
    verticalLineToRelative(7f)
    horizontalLineToRelative(-4f)
    lineTo(9f, 17f)
    close()
}.build()

private val gson = Gson()

private val ChatMessageListSaver = listSaver<List<ChatMessage>, String>(
    save = { list -> list.map { gson.toJson(it) } },
    restore = { saved ->
        saved.mapNotNull { json ->
            runCatching { gson.fromJson(json, ChatMessage::class.java) }.getOrNull()
        }
    }
)

// 默认 TextFieldValue.Saver 不保存 composition（输入法组合区），
// 恢复后会导致输入法组合状态丢失、括号等符号被吞。这里把 composition 一并保存。
private val TextFieldValueFullSaver = listSaver<TextFieldValue, Any>(
    save = { value ->
        listOf(
            value.text,
            value.selection.start,
            value.selection.end,
            value.composition?.start ?: -1,
            value.composition?.end ?: -1
        )
    },
    restore = { list ->
        val selStart = list[1] as Int
        val selEnd = list[2] as Int
        val compStart = list[3] as Int
        val compEnd = list[4] as Int
        TextFieldValue(
            text = list[0] as String,
            selection = TextRange(selStart, selEnd),
            composition = if (compStart >= 0 && compEnd >= 0) TextRange(compStart, compEnd) else null
        )
    }
)

// 上下文压缩：消息超过阈值时，把最旧的压缩进滚动摘要，只保留最近窗口
private const val COMPRESS_THRESHOLD = 30
private const val KEEP_WINDOW = 20

/** 从工具调用参数 JSON 中提取 path（相对项目目录展示用），取不到返回 null。 */
private fun toolCallPath(arguments: String): String? {
    if (arguments.isBlank()) return null
    val m = Regex("\"(?:path|file_path)\"\\s*:\\s*\"([^\"]+)\"").find(arguments)
    return m?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

// ========== Main Panel ==========

@Composable
fun AiChatPanel(
    projectPath: String,
    codeReference: CodeReference?,
    onClearReference: () -> Unit,
    onOpenFile: (filePath: String, startLine: Int, endLine: Int) -> Unit,
    onConfirmInMain: (title: String, message: String, callback: (Boolean) -> Unit) -> Unit,
    onNavigateToSettings: () -> Unit,
    projectOps: ProjectOps
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Config
    val config = remember { mutableStateOf(AiSettingsManager.loadConfig(context)) }

    // Chat state：按项目隔离（AiChatSessionState.forProject），重建后新 AiChatPanel
    // 订阅同一份状态，消息/流式进度不丢；流式任务在该项目 streamScope（进程级）执行，不随重建取消。
    val session = AiChatSessionState.forProject(projectPath)
    var messages by session.messages
    var inputValue by session.inputValue
    var isStreaming by session.isStreaming
    var currentConversationId by rememberSaveable { mutableStateOf(AiChatHistoryStore.createNewId()) }
    var streamingMessageId by session.streamingMessageId
    var currentPage by AiChatSessionState.currentPage
    var showWelcome by remember { mutableStateOf(!AiSettingsManager.isWelcomeDismissed(context)) }
    var shareMode by session.shareMode
    var selectedShareMessages by session.selectedShareMessages
    var errorBanners by remember { mutableStateOf<List<String>>(emptyList()) }
    var wasInterrupted by session.wasInterrupted
    var summary by session.summary
    var pendingAsk by session.pendingAsk

    // Tool registry
    val toolRegistry = remember {
        ToolRegistry().apply {
            register(ShellTool())
            register(FileIOTool())
            register(SearchTool())
            register(AskUserChoiceTool())
            register(AskUserMultiChoiceTool())
            register(OpenFileTool())
            register(MemoryTool { content ->
                val memId = UUID.randomUUID().toString()
                val newMemories = config.value.memories + MemoryItem(id = memId, content = content)
                config.value = config.value.copy(memories = newMemories)
                AiSettingsManager.saveConfig(context, config.value)
            })
            register(GetMemoriesTool {
                config.value.memories.map { it.content }
            })
            register(DebugRunProjectTool())
            register(BuildProjectTool())
            register(ImportAnalysisTool())
            register(GetSyntaxErrorsTool())
        }
    }

    // Scroll to bottom when a conversation is opened (from history or new chat)
    LaunchedEffect(currentConversationId) {
        if (messages.isNotEmpty()) {
            listState.scrollToBottom(animate = false)
        }
    }

    // Save conversation when messages change
    LaunchedEffect(messages, summary) {
        if (messages.isNotEmpty() && !isStreaming) {
            val title = AiChatHistoryStore.generateTitle(messages)
            val data = ConversationData(
                id = currentConversationId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = messages,
                summary = summary
            )
            AiChatHistoryStore.saveConversation(context, projectPath, data)
        }
    }

    // Welcome dialog
    if (showWelcome) {
        AlertDialog(
            onDismissRequest = { showWelcome = false },
            title = { Text("AI 助手") },
            text = { Text("配置 AI 提供商以开始使用。你可以使用默认密钥或自行提供。") },
            confirmButton = {
                TextButton(onClick = {
                    showWelcome = false
                    AiSettingsManager.setWelcomeDismissed(context, false)
                    currentPage = AiPage.SETTINGS
                }) { Text("配置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWelcome = false
                    AiSettingsManager.setWelcomeDismissed(context, true)
                }) { Text("取消") }
            }
        )
    }

    val canSend = config.value.providers.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar
        AiTitleBar(
            page = currentPage,
            providers = config.value.providers,
            // 与发送链路同源（config.value.selectedProviderIndex），避免顶栏显示与真实路由不一致
            selectedProviderIndex = config.value.selectedProviderIndex,
            onBackClick = { currentPage = AiPage.CHAT },
            onSettingsClick = { currentPage = AiPage.SETTINGS },
            onHistoryClick = { currentPage = AiPage.HISTORY },
            onSelectProvider = { idx ->
                config.value = config.value.copy(selectedProviderIndex = idx)
                AiSettingsManager.saveConfig(context, config.value)
            },
            onSelectModel = { providerIdx, model ->
                val providers = config.value.providers.toMutableList()
                providers[providerIdx] = providers[providerIdx].copy(model = model)
                val newConfig = config.value.copy(providers = providers, selectedProviderIndex = providerIdx)
                config.value = newConfig
                AiSettingsManager.saveConfig(context, newConfig)
            }
        )

        // Page content with animation
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth } +
                    fadeIn(animationSpec = tween(250)) togetherWith
                    slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth } +
                    fadeOut(animationSpec = tween(250))
            },
            label = "page_transition",
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                AiPage.CHAT -> {
                    ChatContent(
                        messages = messages,
                        isStreaming = isStreaming,
                        streamingMessageId = streamingMessageId,
                        scope = scope,
                        listState = listState,
                        inputValue = inputValue,
                        codeReference = codeReference,
                        config = config.value,
                        toolRegistry = toolRegistry,
                        context = context,
                        projectPath = projectPath,
                        shareMode = shareMode,
                        selectedShareMessages = selectedShareMessages,
                        onToggleShareMessage = { id ->
                            selectedShareMessages = if (id in selectedShareMessages) {
                                selectedShareMessages - id
                            } else {
                                selectedShareMessages + id
                            }
                        },
                        onOpenFile = onOpenFile,
                        onInputChange = { inputValue = it },
                        onSend = {
                            session.sendJob = session.streamScope.launch {
                                sendMessage(
                                    inputText = inputValue.text,
                                    config = config.value,
                                    messages = messages,
                                    toolRegistry = toolRegistry,
                                    context = context,
                                    projectPath = projectPath,
                                    codeReference = codeReference,
                                    onClearReference = onClearReference,
                                    setMessages = { messages = it },
                                    setStreaming = { isStreaming = it },
                                    setStreamingMessageId = { streamingMessageId = it },
                                    setInputText = { inputValue = TextFieldValue(it) },
                                    currentConversationId = currentConversationId,
                                    setCurrentConversationId = { currentConversationId = it },
                                    summary = summary,
                                    setSummary = { summary = it },
                                    onAskUser = { title, description, options, multi, callback ->
                                        session.pendingAsk.value = AskUserPromptState(title, description, options, multi, callback)
                                    },
                                    onConfirmInMain = onConfirmInMain,
                                    onOpenFile = onOpenFile,
                                    projectOps = projectOps,
                                    onError = { errorBanners = errorBanners + it; wasInterrupted = true }
                                )
                            }
                        },
                        onStop = {
                            session.sendJob?.cancel()
                            session.sendJob = null
                            wasInterrupted = true
                        },
                        onCopy = { content ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AI", content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        onRegenerate = {
                            val lastUserIdx = messages.lastIndexOf(messages.lastOrNull { it.role == ChatRole.USER })
                            if (lastUserIdx >= 0) {
                                val lastUserMsg = messages[lastUserIdx]
                                messages = messages.take(lastUserIdx)
                                inputValue = TextFieldValue(lastUserMsg.content, selection = TextRange(lastUserMsg.content.length))
                            }
                        },
                        onEnterShareMode = {
                            // Select current message pair by default
                            val lastAiIdx = messages.lastIndexOf(messages.lastOrNull { it.role == ChatRole.ASSISTANT })
                            val lastUserIdx = messages.lastIndexOf(messages.lastOrNull { it.role == ChatRole.USER })
                            val selected = mutableSetOf<String>()
                            if (lastAiIdx >= 0) selected.add(messages[lastAiIdx].id)
                            if (lastUserIdx >= 0) selected.add(messages[lastUserIdx].id)
                            selectedShareMessages = selected
                            shareMode = true
                        },
                        onShareConfirm = {
                            val text = messages.filter { it.id in selectedShareMessages }
                                .joinToString("\n\n") { if (it.role == ChatRole.USER) "用户: ${it.content}" else "AI: ${it.content}" }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享对话"))
                            shareMode = false
                            selectedShareMessages = emptySet()
                        },
                        onCancelShareMode = {
                            shareMode = false
                            selectedShareMessages = emptySet()
                        },
                        onClearReference = onClearReference,
                        errorBanners = errorBanners,
                        onDismissError = { errorBanners = emptyList() },
                        canSend = canSend,
                        wasInterrupted = wasInterrupted,
                        pendingAsk = pendingAsk,
                        onAskUserRespond = { result ->
                            pendingAsk?.callback?.invoke(result)
                            session.pendingAsk.value = null
                        },
                        onResend = {
                            val lastUserIdx = messages.lastIndexOf(messages.lastOrNull { it.role == ChatRole.USER })
                            if (lastUserIdx >= 0) {
                                val lastUserMsg = messages[lastUserIdx]
                                messages = messages.take(lastUserIdx)
                                inputValue = TextFieldValue(lastUserMsg.content, selection = TextRange(lastUserMsg.content.length))
                                wasInterrupted = false
                                session.sendJob = session.streamScope.launch {
                                    sendMessage(
                                        inputText = lastUserMsg.content,
                                        config = config.value,
                                        messages = messages,
                                        toolRegistry = toolRegistry,
                                        context = context,
                                        projectPath = projectPath,
                                        codeReference = lastUserMsg.codeReference,
                                        onClearReference = onClearReference,
                                        setMessages = { messages = it },
                                        setStreaming = { isStreaming = it },
                                        setStreamingMessageId = { streamingMessageId = it },
                                        setInputText = { inputValue = TextFieldValue(it) },
                                        currentConversationId = currentConversationId,
                                        setCurrentConversationId = { currentConversationId = it },
                                        summary = summary,
                                        setSummary = { summary = it },
                                        onAskUser = { title, description, options, multi, callback ->
                                            session.pendingAsk.value = AskUserPromptState(title, description, options, multi, callback)
                                        },
                                        onConfirmInMain = onConfirmInMain,
                                        onOpenFile = onOpenFile,
                                        projectOps = projectOps,
                                        onError = { errorBanners = errorBanners + it; wasInterrupted = true }
                                    )
                                }
                            }
                        },
                    )
                }
                AiPage.SETTINGS -> {
                    AiSettingsPage(
                        config = config.value,
                        onConfigChanged = { newConfig ->
                            config.value = newConfig
                            AiSettingsManager.saveConfig(context, newConfig)
                        }
                    )
                }
                AiPage.HISTORY -> {
                    AiHistoryPage(
                        context = context,
                        projectPath = projectPath,
                        currentId = currentConversationId,
                        onSelectConversation = { data ->
                            session.sendJob?.cancel()
                            session.sendJob = null
                            messages = data.messages
                            summary = data.summary
                            isStreaming = false
                            streamingMessageId = null
                            currentConversationId = data.id
                            currentPage = AiPage.CHAT
                        },
                        onNewChat = {
                            session.sendJob?.cancel()
                            session.sendJob = null
                            messages = emptyList()
                            summary = ""
                            isStreaming = false
                            streamingMessageId = null
                            wasInterrupted = false
                            currentConversationId = AiChatHistoryStore.createNewId()
                            currentPage = AiPage.CHAT
                        }
                    )
                }
            }
        }
    }

    // AI 提问弹窗（设置「用弹窗展示AI询问」开时；横幅模式已在 ChatContent 内，输入框圆角均跟随主题）
    val pendingAskState = pendingAsk
    if (pendingAskState != null && SettingsManager.currentSettings.askUserInDialog) {
        AlertDialog(
            onDismissRequest = {
                pendingAskState.callback(null)
                session.pendingAsk.value = null
            },
            title = { Text(pendingAskState.title) },
            text = {
                Column {
                    if (pendingAskState.description.isNotBlank()) {
                        Text(
                            text = pendingAskState.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    AskUserOptionsContent(
                        state = pendingAskState,
                        onRespond = { result ->
                            pendingAskState.callback(result)
                            session.pendingAsk.value = null
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    pendingAskState.callback(null)
                    session.pendingAsk.value = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ========== Title Bar ==========

@Composable
private fun AiTitleBar(
    page: AiPage,
    providers: List<ApiProvider>,
    selectedProviderIndex: Int,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSelectProvider: (Int) -> Unit,
    onSelectModel: (providerIndex: Int, model: String) -> Unit
) {
    var showProviderMenu by remember { mutableStateOf(false) }
    var modelMenuProviderIdx by remember { mutableStateOf<Int?>(null) }

    val currentProvider = providers.getOrNull(selectedProviderIndex)
    val currentModel = currentProvider?.model ?: ""

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page != AiPage.CHAT) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                }
            }
            if (page == AiPage.CHAT) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showProviderMenu = !showProviderMenu }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (providers.isEmpty()) "新聊天"
                                       else currentProvider?.name?.ifBlank { "未命名" } ?: "新聊天",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentModel.isNotBlank()) {
                                Text(
                                    text = currentModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    // Provider dropdown - primary color
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = {
                            showProviderMenu = false
                            modelMenuProviderIdx = null
                        }
                    ) {
                        val modelProviderIdx = modelMenuProviderIdx
                        if (modelProviderIdx != null) {
                            // Model sub-menu (drill-down to avoid nested popup positioning bugs)
                            val provider = providers.getOrNull(modelProviderIdx)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${provider?.name?.ifBlank { "未命名" }} · 选择模型",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = { modelMenuProviderIdx = null }
                            )
                            if (provider != null) {
                                val models = provider.customModels
                                if (models.isNotEmpty()) {
                                    models.forEach { modelEntry ->
                                        DropdownMenuItem(
                                            text = { Text(modelEntry.displayName.ifBlank { modelEntry.modelId }, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                onSelectModel(modelProviderIdx, modelEntry.modelId)
                                                showProviderMenu = false
                                                modelMenuProviderIdx = null
                                            }
                                        )
                                    }
                                } else if (provider.model.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(provider.model, style = MaterialTheme.typography.bodySmall) },
                                        onClick = {
                                            onSelectModel(modelProviderIdx, provider.model)
                                            showProviderMenu = false
                                            modelMenuProviderIdx = null
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("暂无模型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        onClick = { modelMenuProviderIdx = null }
                                    )
                                }
                            }
                        } else {
                            providers.forEachIndexed { idx, provider ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(provider.name.ifBlank { "未命名" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .clickable { modelMenuProviderIdx = idx }
                                                    .padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.ChevronRight, contentDescription = "选择模型", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectProvider(idx)
                                        showProviderMenu = false
                                    }
                                )
                            }
                            if (providers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无提供商", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { showProviderMenu = false }
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = when (page) {
                        AiPage.SETTINGS -> "AI 设置"
                        AiPage.HISTORY -> "对话记录"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (page == AiPage.CHAT) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onHistoryClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.History, contentDescription = "对话记录", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ========== Chat Content ==========

@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    streamingMessageId: String?,
    scope: CoroutineScope,
    listState: LazyListState,
    inputValue: TextFieldValue,
    codeReference: CodeReference?,
    config: AiConfig,
    toolRegistry: ToolRegistry,
    context: Context,
    projectPath: String,
    shareMode: Boolean,
    selectedShareMessages: Set<String>,
    onToggleShareMessage: (String) -> Unit,
    onOpenFile: (String, Int, Int) -> Unit,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEnterShareMode: () -> Unit,
    onShareConfirm: () -> Unit,
    onCancelShareMode: () -> Unit,
    onClearReference: () -> Unit,
    errorBanners: List<String>,
    onDismissError: () -> Unit,
    canSend: Boolean,
    wasInterrupted: Boolean,
    onResend: () -> Unit,
    pendingAsk: AskUserPromptState?,
    onAskUserRespond: (List<String>?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 滚动跟随：流式内容增长时，仅当末项完整可见（真贴底）才自动滚动；
        // 超高气泡中段滚动不算贴底 → 不打断手动浏览；点「回到底部」恢复
        val streamAnchor = messages.lastOrNull()?.let { Triple(it.id, it.content.length, it.reasoning?.length ?: 0) }
        LaunchedEffect(messages.size, isStreaming, streamAnchor) {
            if (messages.isNotEmpty() && isStreaming) {
                val layout = listState.layoutInfo
                val total = layout.totalItemsCount
                val last = layout.visibleItemsInfo.lastOrNull()
                val atBottom = total > 0 && last != null && last.index == total - 1 &&
                    last.offset + last.size <= layout.viewportEndOffset + 4f
                if (atBottom) {
                    listState.scrollToBottom(animate = false)
                }
            }
        }

        // Messages list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !isStreaming) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "有什么想问的？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 回合聚合渲染：用户消息独立气泡；其后的 assistant/tool 连续消息合并为一个助手气泡；
            // SYSTEM 消息不渲染
            val groups = remember(messages, streamingMessageId) {
                buildList {
                    val assists = mutableListOf<ChatMessage>()
                    fun flushAssists() {
                        if (assists.isNotEmpty()) {
                            add(ChatGroup(null, assists.toList()))
                            assists.clear()
                        }
                    }
                    for (m in messages) {
                        when (m.role) {
                            ChatRole.USER -> { flushAssists(); add(ChatGroup(m, emptyList())) }
                            ChatRole.SYSTEM -> { /* 聚合边界提示不显示 */ }
                            else -> assists.add(m)
                        }
                    }
                    flushAssists()
                }.also {
                    android.util.Log.d("AiChat", "groups=${it.size} msgs=${messages.size} roles=${messages.map { m -> m.role.name }.joinToString(",")}")
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { g -> (g.user?.id ?: (g.assists.firstOrNull()?.id ?: "")) + "-" + (g.user?.timestamp ?: g.assists.firstOrNull()?.timestamp ?: 0L) }) { g ->
                    val isLastUser = g.user != null &&
                        messages.lastOrNull { it.role == ChatRole.USER }?.id == g.user.id
                    val msgId = g.user?.id ?: g.assists.firstOrNull()?.id ?: ""
                    val streamingGroup = g.assists.any { it.id == streamingMessageId }
                    // 工具执行/降级重试等「全局流式但 streamingMessageId 已清空」的等待期，
                    // 最后组仍需以流式态渲染，气泡末尾的加载指示器才可见
                    val lastGroupStreaming = isStreaming && groups.lastOrNull() == g
                    ChatMessageBubble(
                        user = g.user,
                        assists = g.assists,
                        groupId = msgId,
                        isStreaming = streamingGroup || lastGroupStreaming,
                        shareMode = shareMode,
                        isSelected = msgId in selectedShareMessages,
                        onToggleSelect = { onToggleShareMessage(msgId) },
                        onOpenFile = onOpenFile,
                        onCopy = onCopy,
                        onRegenerate = onRegenerate,
                        onEnterShareMode = onEnterShareMode,
                        showResendIcon = isLastUser && wasInterrupted && !isStreaming,
                        onResend = onResend,
                        projectPath = projectPath
                    )
                }
            }

            // Scroll-to-top / scroll-to-bottom buttons (right-center)
            if (messages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(ArrowCollapseUpIcon, contentDescription = "回到顶部", modifier = Modifier.size(16.dp))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch { listState.scrollToBottom(animate = false) }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(ArrowCollapseDownIcon, contentDescription = "回到底部", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Error banner collection
        if (errorBanners.isNotEmpty()) {
            AiErrorBannerCollection(
                errors = errorBanners,
                onDismiss = onDismissError,
                onClick = {
                    if (messages.isNotEmpty()) {
                        scope.launch {
                            listState.scrollToBottom(animate = true)
                        }
                    }
                }
            )
        }

        // Code reference banner (above input area)
        codeReference?.let { ref ->
            CodeReferenceBanner(
                reference = ref,
                onDismiss = onClearReference
            )
        }

        // Share mode pills
        if (shareMode) {
            Surface(tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelShareMode,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("取消", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(
                        onClick = onShareConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("分享", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // AI 提问横幅（非弹窗模式：默认在编辑框上方显示，同 error banner 位置；弹窗模式另行渲染）
        if (pendingAsk != null && !SettingsManager.currentSettings.askUserInDialog) {
            AiAskUserBanner(
                state = pendingAsk,
                onRespond = onAskUserRespond
            )
        }

        // Input area（编辑框去阴影：无 shadowElevation）
        Surface(tonalElevation = 1.dp) {
            Column {
                // Slash-command skill suggestions
                val enabledSkills = config.skills.filter { it.enabled }
                val slashToken = inputValue.text.substringBefore(' ')
                val slashQuery = if (slashToken.startsWith("/")) slashToken.removePrefix("/").trim() else ""
                val showSlashMenu = slashToken.startsWith("/") && enabledSkills.isNotEmpty()
                val slashSuggestions = if (showSlashMenu) {
                    enabledSkills.filter { slashQuery.isEmpty() || it.title.contains(slashQuery, ignoreCase = true) }
                } else emptyList()

                if (showSlashMenu && slashSuggestions.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column {
                            slashSuggestions.forEach { skill ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newText = inputValue.text.replaceFirst(slashToken, "/${skill.title}")
                                            val finalText = "$newText "
                                            onInputChange(TextFieldValue(finalText, selection = TextRange(finalText.length)))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "/${skill.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(120.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        skill.readme.ifBlank { "使用 ${skill.title} 技能" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 受控输入框本地状态化：输入时立即更新本地值并同步通知父级，
                    // 避免经过父级重组往返导致输入法组合状态丢失（括号被吞/光标左移）
                    var localInput by remember { mutableStateOf(inputValue) }
                    LaunchedEffect(inputValue) {
                        if (inputValue.text != localInput.text || inputValue.selection != localInput.selection) {
                            localInput = inputValue
                        }
                    }
                    OutlinedTextField(
                        value = localInput,
                        onValueChange = { newValue ->
                            localInput = newValue
                            onInputChange(newValue)
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问 AI...", style = MaterialTheme.typography.bodySmall) },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (inputValue.text.isNotBlank() && !isStreaming) onSend() }),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(
                        onClick = { if (isStreaming) onStop() else if (inputValue.text.isNotBlank()) onSend() },
                        enabled = (canSend && inputValue.text.isNotBlank()) || isStreaming,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape
                    ) {
                        if (isStreaming) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "发送", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ========== AI 提问横幅 ==========

/** AI 提问横幅（非弹窗）：显示在 AI 侧边栏编辑框上方，圆角跟随 luafabric 主题。 */
@Composable
private fun AiAskUserBanner(
    state: AskUserPromptState,
    onRespond: (List<String>?) -> Unit
) {
    val radius = askUiThemeRadius()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(radius),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.description.isNotBlank()) {
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            AskUserOptionsContent(
                state = state,
                onRespond = onRespond
            )
        }
    }
}

/** 共用选项区：单选用自绘圆点（点击即回）；多选自绘方框勾选后确认；「其他」展开单横线编辑框。
 *  判断基于选项索引（AskUserToolBase 恒把「其他」追加在末尾），避免字符串匹配。 */
@Composable
private fun AskUserOptionsContent(
    state: AskUserPromptState,
    onRespond: (List<String>?) -> Unit
) {
    var othersText by remember(state) { mutableStateOf("") }
    var selectedIndices by remember(state) { mutableStateOf(setOf<Int>()) }
    val otherIndex = state.options.lastIndex

    state.options.forEachIndexed { index, option ->
        val isOther = index == otherIndex
        val label = if (isOther) "其他" else option
        if (state.multi) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
                    }
                    .heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 自绘紧凑复选框：紧贴左侧（替代带默认 48dp 触控盒的 M3 Checkbox）
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index in selectedIndices) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index in selectedIndices) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            if (isOther && index in selectedIndices) {
                UnderlineTextField(
                    value = othersText,
                    onValueChange = { othersText = it },
                    placeholder = "输入您的回答",
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isOther) {
                            selectedIndices = setOf(index)
                        } else {
                            onRespond(listOf(option))
                        }
                    }
                    .heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 自绘紧凑单选圆点：紧贴左侧（替代带默认内边距的 M3 RadioButton）
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(
                            width = 2.dp,
                            color = if (index in selectedIndices) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index in selectedIndices) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
            if (isOther && index in selectedIndices) {
                UnderlineTextField(
                    value = othersText,
                    onValueChange = { othersText = it },
                    placeholder = "输入您的回答",
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
    // 确认行：多选恒显示；单选选了「其他」也显示
    if (state.multi || selectedIndices.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onRespond(null) }) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = {
                // 按索引组装结果：排除末尾的「其他」，未填的自定义文本时视为取消
                val chosen = selectedIndices.mapNotNull { idx ->
                    if (idx == otherIndex) null else state.options[idx]
                }
                val withCustom = if (othersText.isNotBlank()) chosen + othersText else chosen
                onRespond(if (withCustom.isEmpty()) null else withCustom)
            }) { Text(stringResource(R.string.ok)) }
        }
    }
}

/** 单横线编辑框：无外框，仅底部一条线（高信息密度输入样式）。 */
@Composable
private fun UnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ========== Code Reference Banner ==========

@Composable
private fun CodeReferenceBanner(
    reference: CodeReference,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.FormatQuote,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = reference.preview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ========== Error Banner Collection ==========

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiErrorBannerCollection(
    errors: List<String>,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    if (errors.isEmpty()) return

    var currentIndex by remember { mutableStateOf(errors.size - 1) }
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Sync index when errors change (new error added or list shrunk)
    LaunchedEffect(errors.size) {
        if (currentIndex >= errors.size) {
            currentIndex = errors.size - 1
        }
    }

    val currentError = errors.getOrNull(currentIndex) ?: return
    val hasMultiple = errors.size > 1

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand/collapse icon (left side, replaces warning)
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                // Error text: current error (truncated)
                Text(
                    text = currentError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Left arrow — previous error
                IconButton(
                    onClick = {
                        currentIndex = if (currentIndex > 0) currentIndex - 1 else errors.size - 1
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = hasMultiple
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "上一个",
                        modifier = Modifier.size(18.dp),
                        tint = if (hasMultiple) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f)
                    )
                }

                // Right arrow — next error
                IconButton(
                    onClick = {
                        currentIndex = if (currentIndex < errors.size - 1) currentIndex + 1 else 0
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = hasMultiple
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "下一个",
                        modifier = Modifier.size(18.dp),
                        tint = if (hasMultiple) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f)
                    )
                }

                // Copy — click copy current, long press copy all
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentError))
                                Toast.makeText(context, "已复制当前错误", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = {
                                val allText = errors.joinToString("\n---\n")
                                clipboardManager.setText(AnnotatedString(allText))
                                Toast.makeText(context, "已复制全部 ${errors.size} 个错误", Toast.LENGTH_SHORT).show()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                // Close — dismiss all errors
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Expanded: show full current error details
            AnimatedVisibility(visible = expanded) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ========== Chat Message Bubble（回合聚合渲染） ==========

/** 一次用户提问及其引发的助手回合（思考/工具调用/工具结果/多段回复）。 */
private data class ChatGroup(
    val user: ChatMessage?,
    val assists: List<ChatMessage>
)

/** 助手气泡内的展平渲染条目（保持原始顺序）。 */
private sealed class BubbleItem {
    data class Think(val text: String) : BubbleItem()
    data class Tool(val name: String, val args: String, val result: String?) : BubbleItem()
    data class Text(val text: String, val streaming: Boolean) : BubbleItem()
}

/** 把助手回合消息展平为有序条目：思考→工具(紧接其结果)→文本。 */
private fun flattenGroup(assists: List<ChatMessage>): List<BubbleItem> {
    val out = mutableListOf<BubbleItem>()
    var pendingResult: String? = null
    for (m in assists) {
        if (m.role == ChatRole.TOOL) {
            pendingResult = m.content
            continue
        }
        if (m.reasoning.isNotBlank()) out += BubbleItem.Think(m.reasoning)
        for (tc in m.toolCalls) {
            out += BubbleItem.Tool(tc.name, tc.arguments, pendingResult)
            pendingResult = null
        }
        if (m.content.isNotBlank()) out += BubbleItem.Text(m.content, m.isStreaming)
    }
    return out
}

@Composable
private fun ChatMessageBubble(
    user: ChatMessage?,
    assists: List<ChatMessage>,
    groupId: String,
    isStreaming: Boolean,
    shareMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenFile: (String, Int, Int) -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEnterShareMode: () -> Unit,
    showResendIcon: Boolean = false,
    onResend: () -> Unit = {},
    projectPath: String = ""
) {
    val isUser = user != null
    var showResendConfirm by remember { mutableStateOf(false) }

    // 圆角跟随 LuaFabric 主题配置（形状圆角）
    val baseSize = when (SettingsManager.currentSettings.shapeSizeIndex) {
        0 -> 4f  // 小
        1 -> 8f  // 中小
        2 -> 12f // 中（默认）
        3 -> 16f // 大
        else -> 12f
    }.dp

    // Resend confirmation dialog
    if (showResendConfirm) {
        AlertDialog(
            onDismissRequest = { showResendConfirm = false },
            title = { Text("重新发送", style = MaterialTheme.typography.titleSmall) },
            text = { Text("确认重新发送此消息？", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    showResendConfirm = false
                    onResend()
                }) {
                    Text("确认", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResendConfirm = false }) {
                    Text("取消", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Share mode checkbox
        if (shareMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Refresh icon for interrupted user messages - click to resend with confirmation
        if (showResendIcon && isUser) {
            IconButton(
                onClick = { showResendConfirm = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "重新发送",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                // ===== 用户单气泡 =====
                val u = user
                Surface(
                    shape = RoundedCornerShape(
                        topStart = baseSize, topEnd = baseSize,
                        bottomStart = baseSize, bottomEnd = 4.dp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        u?.codeReference?.let { ref ->
                            var refExpanded by remember { mutableStateOf(false) }
                            val relativePath = run {
                                val p = projectPath.trimEnd('/', '\\')
                                val fp = ref.filePath.replace('\\', '/')
                                val rel = fp.removePrefix(p.trimEnd('/', '\\').replace('\\', '/') + "/")
                                if (rel.isNotBlank() && rel != fp) rel else ref.fileName
                            }
                            val lineText = if (ref.startLine == ref.endLine) "第${ref.startLine}行"
                            else "第${ref.startLine}~${ref.endLine}行"
                            val cleaned = ref.content
                                .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                                .replace(Regex("""\s+"""), " ")
                                .trim()
                            val excerpt = if (cleaned.length > 200) cleaned.take(200).trimEnd() + "..." else cleaned
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { refExpanded = !refExpanded }
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            FormatQuoteOpenIcon,
                                            contentDescription = "代码引用",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$relativePath：$lineText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            if (refExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowLeft,
                                            contentDescription = if (refExpanded) "收起" else "展开",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    AnimatedVisibility(visible = refExpanded) {
                                        Text(
                                            text = excerpt,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        // 用户文本：覆盖选择手柄色避免与 primary 同色不可见
                        val userContent = u?.content ?: ""
                        val userSelectionColors = TextSelectionColors(
                            handleColor = MaterialTheme.colorScheme.onPrimary,
                            backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                        )
                        CompositionLocalProvider(LocalTextSelectionColors provides userSelectionColors) {
                            SelectionContainer {
                                Text(
                                    text = userContent + if (isStreaming) " ▌" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                // ===== 助手回合聚合气泡 =====
                val items = remember(assists, groupId) { flattenGroup(assists) }
                Surface(
                    shape = RoundedCornerShape(
                        topStart = baseSize, topEnd = baseSize,
                        bottomStart = 4.dp, bottomEnd = baseSize
                    ),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // 流式占位：真实内容（思考/工具/文本）尚未产出时显示循环加载指示器
                        if (isStreaming && items.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        items.forEach { item ->
                            when (item) {
                                is BubbleItem.Think -> {
                                    var exp by remember(groupId, item.text.length) { mutableStateOf(false) }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { exp = !exp }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.Psychology,
                                                    contentDescription = "思考过程",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "思考过程",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    if (exp) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowLeft,
                                                    contentDescription = if (exp) "收起" else "展开",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            AnimatedVisibility(visible = exp) {
                                                Text(
                                                    text = item.text,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                is BubbleItem.Tool -> {
                                    var exp by remember(groupId, item.name, item.args.length) { mutableStateOf(false) }
                                    val relPath = run {
                                        val ap = toolCallPath(item.args)
                                        if (ap.isNullOrBlank() || projectPath.isBlank()) null
                                        else {
                                            val p = projectPath.trimEnd('/', '\\').replace('\\', '/')
                                            val f = ap.replace('\\', '/')
                                            val r = f.removePrefix(p + "/")
                                            if (r.isNotBlank() && r != f) r else ap
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { exp = !exp }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "调用 ${item.name}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    if (exp) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowLeft,
                                                    contentDescription = if (exp) "收起" else "展开",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            AnimatedVisibility(visible = exp) {
                                                Column(modifier = Modifier.padding(top = 3.dp)) {
                                                    Text(
                                                        text = relPath ?: item.args.take(200),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                    )
                                                    // 工具结果并入同一折叠区，不再单独气泡
                                                    if (!item.result.isNullOrBlank()) {
                                                        Text(
                                                            text = item.result,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                is BubbleItem.Text -> {
                                    AiMessageContent(
                                        content = item.text + if (item.streaming) " ▌" else "",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                        // 工具调用后等待期：工具结果已回传、模型正在处理下一轮，显示小号加载指示
                        if (isStreaming && items.lastOrNull() is BubbleItem.Tool) {
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Action buttons + timestamp for assistant groups
            if (!isUser) {
                val contentLen = assists.map { it.content.length }.sum()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(assists.firstOrNull()?.timestamp ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    // Token consumption
                    Text(
                        text = "~${maxOf(0, contentLen / 4)} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // Action buttons
                    val copyText = assists.map { it.content }.filter { it.isNotBlank() }.joinToString("\n\n")
                    ActionIconButton(Icons.Filled.ContentCopy, "复制", onClick = { onCopy(copyText) })
                    ActionIconButton(Icons.Filled.Refresh, "重新生成", onClick = onRegenerate)
                    ActionIconButton(Icons.Filled.Share, "分享", onClick = onEnterShareMode)
                }
            }
        }
    }
}

@Composable
private fun ActionIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ========== Settings Collapsible Entry ==========

@Composable
private fun SettingsCollapsibleEntry(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ========== Settings Page ==========

@Composable
private fun AiSettingsPage(
    config: AiConfig,
    onConfigChanged: (AiConfig) -> Unit
) {
    val context = LocalContext.current
    // AI 询问展示方式（编辑区上横幅 / 弹窗）
    var askUserDialogMode by remember { mutableStateOf(SettingsManager.currentSettings.askUserInDialog) }
    fun updateAskUserDialogMode(v: Boolean) {
        askUserDialogMode = v
        SettingsManager.updateSettings(SettingsManager.currentSettings.copy(askUserInDialog = v))
        SettingsManager.saveSettings(context)
    }

    // Extract bundled skills from raw resources to private directory
    LaunchedEffect(Unit) {
        try {
            val skillsDir = File(context.filesDir, ".agent/skills")
            val rawResId = context.resources.getIdentifier("luafabric_studio_skill", "raw", context.packageName)
            if (rawResId != 0) {
                val inputStream = context.resources.openRawResource(rawResId)
                val content = inputStream.bufferedReader().readText()
                inputStream.close()
                val nameMatch = Regex("""^name:\s*(.+)$""", RegexOption.MULTILINE).find(content)
                val name = nameMatch?.groupValues?.getOrNull(1)?.trim() ?: "luafabric-studio"
                val skillFolder = File(skillsDir, name)
                val skillFile = File(skillFolder, "SKILL.md")
                if (!skillFile.exists()) {
                    skillFolder.mkdirs()
                    skillFile.writeText(content)
                }
            }
        } catch (_: Exception) { }
    }

    var providers by remember { mutableStateOf(config.providers.toMutableList()) }
    var selectedIndex by remember { mutableStateOf(
        if (config.providers.isNotEmpty() && config.selectedProviderIndex in config.providers.indices) config.selectedProviderIndex
        else -1
    )}
    // 默认技能列表：首次使用或持久化配置缺失默认技能时兜底。
    // 持久化过旧版本配置（仅含 luafabric-studio）时，缺失的默认技能按 title 补齐合并。
    val defaultSkills = {
        val bundledSkillPath = File(context.filesDir, ".agent/skills/luafabric-studio/SKILL.md").absolutePath
        listOf(
            SkillConfig(
                path = "C:\\Users\\WuLiang\\.agents\\skills\\caveman",
                enabled = true,
                title = "caveman",
                readme = "Ultra-compressed communication mode. Cuts token usage ~75%."
            ),
            SkillConfig(
                path = "C:\\Users\\WuLiang\\.agents\\skills\\caveman-commit",
                enabled = true,
                title = "caveman-commit",
                readme = "Ultra-compressed commit message generator. Cuts noise from commit messages while preserving intent."
            ),
            SkillConfig(
                path = "C:\\Users\\WuLiang\\.agents\\skills\\grilling",
                enabled = true,
                title = "grilling",
                readme = "Interview the user relentlessly about a plan or design. Use when the user wants to stress-test a plan before building."
            ),
            SkillConfig(
                path = "C:\\Users\\WuLiang\\.agents\\skills\\ponytail-audit",
                enabled = true,
                title = "ponytail-audit",
                readme = "Whole-repo audit for over-engineering: a ranked list of what to delete, simplify, or replace."
            ),
            SkillConfig(
                path = "C:\\Users\\WuLiang\\.agents\\skills\\no-negative-echo",
                enabled = true,
                title = "no-negative-echo",
                readme = "Reduce negative-constraint and session-history leakage when a discarded proposal or user correction is echoed into final artifacts."
            ),
            SkillConfig(
                path = bundledSkillPath,
                enabled = true,
                title = "luafabric-studio",
                readme = "LuaFabric Studio、AndroLua、LuaFabric 项目开发专用 skill。"
            )
        )
    }
    var skills by remember { mutableStateOf(
        if (config.skills.isEmpty()) defaultSkills()
        else {
            val existingTitles = config.skills.mapTo(mutableSetOf()) { it.title }
            config.skills + defaultSkills().filter { it.title !in existingTitles }
        }
    ) }
    var memories by remember { mutableStateOf(config.memories.toMutableList()) }
    // 折叠菜单状态：已折叠的标题集合，变化时随配置持久化
    var collapsedSections by remember { mutableStateOf(config.collapsedSections.toMutableSet()) }
    var showAddProvider by remember { mutableStateOf(false) }
    var showEditProvider by remember { mutableStateOf<Int?>(null) }
    var showDeleteSkill by remember { mutableStateOf<Int?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var showDeleteMemory by remember { mutableStateOf<Int?>(null) }
    var providerDeleteMode by remember { mutableStateOf(false) }
    var showDeleteProvider by remember { mutableStateOf<Int?>(null) }

    fun save() {
        onConfigChanged(AiConfig(
            providers = providers,
            selectedProviderIndex = selectedIndex,
            skills = skills,
            memories = memories,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            collapsedSections = collapsedSections.toList()
        ))
    }

    // 切换某菜单的折叠状态并持久化
    fun toggleSection(title: String) {
        if (!collapsedSections.remove(title)) collapsedSections.add(title)
        save()
    }

    // SKILL.md front matter parser
    fun parseSkillFrontMatter(content: String): Triple<String, String, String>? {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) return null
        val endIdx = trimmed.indexOf("---", 3)
        if (endIdx == -1) return null
        val front = trimmed.substring(3, endIdx).trim()
        val name = Regex("""^name:\s*(.+)$""", RegexOption.MULTILINE).find(front)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val desc = Regex("""^description:\s*(.+)$""", RegexOption.MULTILINE).find(front)?.groupValues?.getOrNull(1)?.trim() ?: ""
        if (name.isBlank()) return null
        return Triple(name, desc, trimmed.substring(endIdx + 3).trim())
    }

    // File picker for skill (SKILL.md)
    val skillFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { srcUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(srcUri)
                val content = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val fileName = srcUri.lastPathSegment ?: ""
                if (!fileName.endsWith("SKILL.md", ignoreCase = true)) {
                    Toast.makeText(context, "文件名必须为 SKILL.md", Toast.LENGTH_SHORT).show()
                    return@let
                }

                val parsed = parseSkillFrontMatter(content)
                if (parsed == null) {
                    Toast.makeText(context, "SKILL.md 格式无效：缺少 name 或格式错误", Toast.LENGTH_SHORT).show()
                    return@let
                }

                val (name, desc, _) = parsed
                val readme = desc.take(200)
                skills = skills + SkillConfig(path = srcUri.toString(), enabled = true, title = name, readme = readme)
                save()
                Toast.makeText(context, "已添加技能：$name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ===== API 提供商列表 =====
        SettingsCollapsibleEntry(
            title = "API 提供商",
            icon = Icons.Filled.Cloud,
            expanded = "API 提供商" !in collapsedSections,
            onExpandedChange = { toggleSection("API 提供商") }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Add provider + delete mode button row
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showAddProvider = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加提供商", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedIconButton(
                        onClick = { providerDeleteMode = !providerDeleteMode },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            if (providerDeleteMode) Icons.Filled.Close else Icons.Filled.Delete,
                            contentDescription = "删除模式",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Provider list
                providers.forEachIndexed { idx, provider ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (idx == selectedIndex) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.name.ifBlank { "未命名" }, style = MaterialTheme.typography.bodySmall)
                                if (provider.model.isNotBlank()) {
                                    Text(
                                        provider.model,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Protocol chip
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 0.dp
                            ) {
                                Text(
                                    text = when (provider.protocol) {
                                        ApiProtocol.OPENAI -> "OpenAI"
                                        ApiProtocol.ANTHROPIC -> "Anthro"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (providerDeleteMode) {
                                IconButton(
                                    onClick = { showDeleteProvider = idx },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { showEditProvider = idx },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "编辑",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ===== 技能列表 =====
        SettingsCollapsibleEntry(
            title = "技能",
            icon = Icons.Filled.Extension,
            expanded = "技能" !in collapsedSections,
            onExpandedChange = { toggleSection("技能") }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Add skill + delete mode button row
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { skillFilePicker.launch(arrayOf("text/*", "*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加技能", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedIconButton(
                        onClick = { deleteMode = !deleteMode },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            if (deleteMode) Icons.Filled.Close else Icons.Filled.Delete,
                            contentDescription = "删除模式",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Skill list
                skills.forEachIndexed { idx, skill ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = skill.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (skill.readme.isNotBlank()) {
                                    Text(
                                        skill.readme.take(80),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            if (deleteMode) {
                                IconButton(
                                    onClick = { showDeleteSkill = idx },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Switch(
                                    checked = skill.enabled,
                                    onCheckedChange = {
                                        skills = skills.toMutableList().apply { this[idx] = this[idx].copy(enabled = it) }
                                        save()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // ===== 记忆列表 =====
        SettingsCollapsibleEntry(
            title = "记忆",
            icon = Icons.Filled.Psychology,
            expanded = "记忆" !in collapsedSections,
            onExpandedChange = { toggleSection("记忆") }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (memories.isEmpty()) {
                    Text(
                        "暂无记忆。AI 会在对话中自动记住重要信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                memories.forEachIndexed { idx, mem ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mem.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(mem.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showDeleteMemory = idx },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        SettingsCollapsibleEntry(
            title = "询问",
            icon = Icons.Filled.Help,
            expanded = "询问" !in collapsedSections,
            onExpandedChange = { toggleSection("询问") }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { updateAskUserDialogMode(!askUserDialogMode) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("用弹窗展示AI询问", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "默认在编辑区上方询问您，开启后以弹窗方式显示AI对您的询问",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = askUserDialogMode,
                    onCheckedChange = { updateAskUserDialogMode(it) }
                )
            }
        }
    }

    // ===== Add Provider Dialog =====
    if (showAddProvider) {
        var newName by remember { mutableStateOf("") }
        var newApiKey by remember { mutableStateOf("") }
        var newBaseUrl by remember { mutableStateOf("") }
        var newModel by remember { mutableStateOf("") }
        var newProtocol by remember { mutableStateOf(ApiProtocol.OPENAI) }
        var newModelDropdown by remember { mutableStateOf(false) }
        var newFetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
        var newNameError by remember { mutableStateOf(false) }
        var newApiKeyError by remember { mutableStateOf(false) }
        var newBaseUrlError by remember { mutableStateOf(false) }
        var newModelError by remember { mutableStateOf(false) }
        var newApiKeyVisible by remember { mutableStateOf(false) }
        val shapeSize = with(LocalDensity.current) {
            val sizes = listOf(4.dp, 8.dp, 12.dp, 16.dp)
            sizes.getOrElse(com.luafabric.studio.falling.ui.settings.SettingsManager.currentSettings.shapeSizeIndex) { 12.dp }
        }

        val addContext = LocalContext.current

        AlertDialog(
            onDismissRequest = { showAddProvider = false },
            title = { Text("添加 API 提供商") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; newNameError = false },
                        label = { Text("提供商名称", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = newNameError,
                        supportingText = { if (newNameError) Text("请输入提供商名称") },
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (newName.isNotEmpty()) {
                                IconButton(onClick = { newName = ""; newNameError = false }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newProtocol == ApiProtocol.OPENAI,
                            onClick = { newProtocol = ApiProtocol.OPENAI },
                            label = { Text("OpenAI", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = newProtocol == ApiProtocol.ANTHROPIC,
                            onClick = { newProtocol = ApiProtocol.ANTHROPIC },
                            label = { Text("Anthropic", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it; newApiKeyError = false },
                        label = { Text("API Key", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = newApiKeyError,
                        supportingText = { if (newApiKeyError) Text("请输入 API Key") },
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (newApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { newApiKeyVisible = !newApiKeyVisible }) {
                                Icon(
                                    if (newApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (newApiKeyVisible) "隐藏" else "显示",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    OutlinedTextField(
                        value = newBaseUrl,
                        onValueChange = { newBaseUrl = it; newBaseUrlError = false },
                        label = { Text("API 请求地址", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = newBaseUrlError,
                        supportingText = { if (newBaseUrlError) Text("请输入请求地址") },
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (newBaseUrl.isNotEmpty()) {
                                IconButton(onClick = { newBaseUrl = ""; newBaseUrlError = false }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    // Model ID with dropdown + cloud-search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newModel,
                                onValueChange = { newModel = it; newModelError = false },
                                label = { Text("模型 ID", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                isError = newModelError,
                                supportingText = { if (newModelError) Text("请输入模型 ID") },
                                shape = RoundedCornerShape(shapeSize),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { newModelDropdown = !newModelDropdown }) {
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = newModelDropdown && newFetchedModels.isNotEmpty(),
                                onDismissRequest = { newModelDropdown = false }
                            ) {
                                newFetchedModels.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = { Text(modelId, style = MaterialTheme.typography.bodySmall) },
                                        onClick = { newModel = modelId; newModelDropdown = false }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                if (newBaseUrl.isBlank()) {
                                    Toast.makeText(addContext, "请先填写 API 请求地址", Toast.LENGTH_SHORT).show()
                                } else {
                                    kotlinx.coroutines.MainScope().launch {
                                        try {
                                            val fetched = AiChatRepository.fetchModels(
                                                baseUrl = newBaseUrl.trim(),
                                                apiKey = newApiKey.trim(),
                                                protocol = newProtocol
                                            )
                                            newFetchedModels = fetched
                                            if (fetched.isNotEmpty()) newModel = fetched.first()
                                            Toast.makeText(addContext, "获取到 ${fetched.size} 个模型", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(addContext, "获取模型失败：${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "获取模型列表", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    newNameError = newName.isBlank()
                    newBaseUrlError = newBaseUrl.isBlank()
                    newModelError = newModel.isBlank()
                    if (!newNameError && !newBaseUrlError && !newModelError) {
                        providers = (providers + ApiProvider(
                            name = newName.trim(),
                            protocol = newProtocol,
                            apiKey = newApiKey.trim(),
                            baseUrl = AiChatRepository.normalizeBaseUrl(newBaseUrl.trim()),
                            model = newModel.trim(),
                            useDefaultKey = false
                        )).toMutableList()
                        showAddProvider = false
                        save()
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddProvider = false }) { Text("取消") }
            }
        )
    }

    // Edit provider dialog
    showEditProvider?.let { editIdx ->
        val provider = providers[editIdx]
        var editName by remember(editIdx) { mutableStateOf(provider.name) }
        var editApiKey by remember(editIdx) { mutableStateOf(provider.apiKey) }
        var editBaseUrl by remember(editIdx) { mutableStateOf(provider.baseUrl) }
        var editModel by remember(editIdx) { mutableStateOf(provider.model) }
        var editProtocol by remember(editIdx) { mutableStateOf(provider.protocol) }
        var editModelDropdown by remember(editIdx) { mutableStateOf(false) }
        var editFetchedModels by remember(editIdx) { mutableStateOf<List<String>>(emptyList()) }
        var editNameError by remember(editIdx) { mutableStateOf(false) }
        var editApiKeyError by remember(editIdx) { mutableStateOf(false) }
        var editBaseUrlError by remember(editIdx) { mutableStateOf(false) }
        var editModelError by remember(editIdx) { mutableStateOf(false) }
        var editApiKeyVisible by remember(editIdx) { mutableStateOf(false) }
        val shapeSize = with(LocalDensity.current) {
            val sizes = listOf(4.dp, 8.dp, 12.dp, 16.dp)
            sizes.getOrElse(com.luafabric.studio.falling.ui.settings.SettingsManager.currentSettings.shapeSizeIndex) { 12.dp }
        }

        val editContext = LocalContext.current

        AlertDialog(
            onDismissRequest = { showEditProvider = null },
            title = { Text("编辑 API 提供商") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("提供商名称", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (editName.isNotEmpty()) {
                                IconButton(onClick = { editName = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editProtocol == ApiProtocol.OPENAI,
                            onClick = { editProtocol = ApiProtocol.OPENAI },
                            label = { Text("OpenAI", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = editProtocol == ApiProtocol.ANTHROPIC,
                            onClick = { editProtocol = ApiProtocol.ANTHROPIC },
                            label = { Text("Anthropic", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    OutlinedTextField(
                        value = editApiKey,
                        onValueChange = { editApiKey = it },
                        label = { Text("API Key", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (editApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { editApiKeyVisible = !editApiKeyVisible }) {
                                Icon(
                                    if (editApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (editApiKeyVisible) "隐藏" else "显示",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    OutlinedTextField(
                        value = editBaseUrl,
                        onValueChange = { editBaseUrl = it },
                        label = { Text("API 请求地址", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(shapeSize),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (editBaseUrl.isNotEmpty()) {
                                IconButton(onClick = { editBaseUrl = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editModel,
                                onValueChange = { editModel = it },
                                label = { Text("模型 ID", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(shapeSize),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { editModelDropdown = !editModelDropdown }) {
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = editModelDropdown && editFetchedModels.isNotEmpty(),
                                onDismissRequest = { editModelDropdown = false }
                            ) {
                                editFetchedModels.forEach { modelId ->
                                    DropdownMenuItem(
                                        text = { Text(modelId, style = MaterialTheme.typography.bodySmall) },
                                        onClick = { editModel = modelId; editModelDropdown = false }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                if (editBaseUrl.isBlank()) {
                                    Toast.makeText(editContext, "请先填写 API 请求地址", Toast.LENGTH_SHORT).show()
                                } else if (editApiKey.isBlank()) {
                                    Toast.makeText(editContext, "请先填写 API Key", Toast.LENGTH_SHORT).show()
                                } else {
                                    kotlinx.coroutines.MainScope().launch {
                                        try {
                                            val fetched = AiChatRepository.fetchModels(
                                                baseUrl = editBaseUrl.trim(),
                                                apiKey = editApiKey.trim(),
                                                protocol = editProtocol
                                            )
                                            editFetchedModels = fetched
                                            if (fetched.isNotEmpty()) editModel = fetched.first()
                                            Toast.makeText(editContext, "获取到 ${fetched.size} 个模型", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(editContext, "获取模型失败：${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = "获取模型列表", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    providers = providers.toMutableList().apply {
                        this[editIdx] = this[editIdx].copy(
                            name = editName.trim(),
                            protocol = editProtocol,
                            apiKey = editApiKey.trim(),
                            baseUrl = editBaseUrl.trim(),
                            model = editModel.trim(),
                            useDefaultKey = false
                        )
                    }
                    showEditProvider = null
                    save()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProvider = null }) { Text("取消") }
            }
        )
    }

    // Delete skill confirmation dialog
    showDeleteSkill?.let { skillIdx ->
        AlertDialog(
            onDismissRequest = { showDeleteSkill = null },
            title = { Text("删除技能") },
            text = { Text("确定删除「${skills[skillIdx].title}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    skills = skills.toMutableList().apply { removeAt(skillIdx) }
                    showDeleteSkill = null
                    deleteMode = false
                    save()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSkill = null }) { Text("取消") }
            }
        )
    }

    // Delete provider confirmation dialog
    showDeleteProvider?.let { providerIdx ->
        AlertDialog(
            onDismissRequest = { showDeleteProvider = null },
            title = { Text("删除提供商") },
            text = { Text("确定删除「${providers[providerIdx].name}」？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    providers = providers.toMutableList().apply { removeAt(providerIdx) }
                    showDeleteProvider = null
                    providerDeleteMode = false
                    save()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProvider = null }) { Text("取消") }
            }
        )
    }

    // Delete memory confirmation dialog
    showDeleteMemory?.let { memIdx ->
        AlertDialog(
            onDismissRequest = { showDeleteMemory = null },
            title = { Text("删除记忆") },
            text = { Text("确定删除此条记忆？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    memories = memories.toMutableList().apply { removeAt(memIdx) }
                    showDeleteMemory = null
                    save()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMemory = null }) { Text("取消") }
            }
        )
    }
}

// ========== History Page ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiHistoryPage(
    context: Context,
    projectPath: String,
    currentId: String,
    onSelectConversation: (ConversationData) -> Unit,
    onNewChat: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectPath) {
        conversations = AiChatHistoryStore.listConversations(context, projectPath)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // New chat button
        Surface(
            onClick = onNewChat,
            shape = RoundedCornerShape(0.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("新建对话", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无对话记录", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(conversations, key = { it.id }) { conv ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                showDeleteConfirm = conv.id
                                false // Don't dismiss yet, wait for confirmation
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFE53935), RoundedCornerShape(0.dp))
                                    .padding(start = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false
                    ) {
                        Surface(
                            onClick = {
                                kotlinx.coroutines.MainScope().launch {
                                    AiChatHistoryStore.loadConversation(context, projectPath, conv.id)?.let { data ->
                                        onSelectConversation(data)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${conv.messageCount} 条消息",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除对话") },
            text = { Text("确定删除此对话？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        AiChatHistoryStore.deleteConversation(context, projectPath, id)
                        conversations = AiChatHistoryStore.listConversations(context, projectPath)
                    }
                    showDeleteConfirm = null
                }) { Text("删除", color = Color(0xFFE53935)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

// ========== Helper Functions ==========

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3600_000} 小时前"
        else -> java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

// 滚动到底：末项顶部对齐后，若末项高于视口，用正 scrollOffset 把其底部对齐视口底
//（避免长气泡停在开头；正 offset 不会触发该版本 clamp 到顶的漂移问题）
private suspend fun LazyListState.scrollToBottom(animate: Boolean) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    if (animate) {
        animateScrollToItem(lastIndex)
        return
    }
    scrollToItem(lastIndex)
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    if (last.index == lastIndex) {
        val viewportH = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val pos = last.size - viewportH
        if (pos > 0) scrollToItem(lastIndex, pos)
    }
}

private fun getPathFromUri(uri: Uri): String? {
    // Try to get the actual file path from content URI
    val docId = uri.lastPathSegment ?: return null
    return docId.split(":").getOrNull(1)?.let { "/storage/emulated/0/$it" }
        ?: docId
}

// ========== Send Message Logic ==========

private fun readSkillContent(context: Context, skill: SkillConfig): String? {
    return try {
        val path = skill.path
        when {
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            path.startsWith("file://") -> {
                File(Uri.parse(path).path ?: return null).takeIf { it.exists() }?.readText()
            }
            else -> File(path).takeIf { it.exists() }?.readText()
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun buildSystemPrompt(
    context: Context,
    config: AiConfig,
    editorSnapshot: String = ""
): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    sb.appendLine("你是运行在 LuaFabric Studio 中的 AI 助手。")
    sb.appendLine()
    sb.appendLine("## 当前环境")
    sb.appendLine("- 运行环境：LuaFabric Studio（运行在 Android 上的 Lua/Android 开发工具）")
    sb.appendLine("- 你可以通过工具函数执行 Shell 命令、读写项目文件、搜索代码、打开文件、调用用户确认、以及使用记忆功能")
    sb.appendLine("- 用户可能引用代码片段，引用时会附带文件名和行号，请结合引用内容回答")
    sb.appendLine("- 回答使用与用户相同的语言，保持简洁准确")
    sb.appendLine()

    // 编辑器快照（当前文件元信息 + 已打开文件路径，不注入内容）：让 AI 即知当前文件、
    // 语法状态、修改时间、MD5、行数；行首带 * 者为当前活动文件。无活动文件时标注「未打开任何文件」。
    if (editorSnapshot.isNotBlank()) {
        sb.appendLine("## 当前编辑器状态")
        sb.appendLine(editorSnapshot)
        sb.appendLine("文件内容不在此处展开；需要查看/修改某文件时，使用 file_io 工具（相对路径基于项目根）读取或写入，可用 read_lines 精确定位文件某行或某 N~M 行，可用 get_syntax_errors（可传 path 参数）检查任意文件的语法错误。")
        sb.appendLine()
    }

    val enabledSkills = config.skills.filter { it.enabled }
    if (enabledSkills.isNotEmpty()) {
        sb.appendLine("## 可用技能（Skills）")
        sb.appendLine("以下技能定义了特定场景下的工作方式。当任务与某技能相关时，遵循该技能的指示。")
        sb.appendLine("当用户消息以 /技能名 开头时，表示用户明确要求使用该技能，必须严格遵循该技能的规则。")
        sb.appendLine("可用技能：${enabledSkills.joinToString("、") { "/${it.title}" }}")
        sb.appendLine()
        enabledSkills.forEach { skill ->
            sb.appendLine("### ${skill.title}")
            val content = readSkillContent(context, skill) ?: skill.readme
            if (content.isNotBlank()) {
                sb.appendLine(content)
            }
            sb.appendLine()
        }
    }

    if (config.memories.isNotEmpty()) {
        sb.appendLine("## 记忆")
        sb.appendLine("以下是你记住的关于用户的信息，回答时可参考：")
        config.memories.forEach { mem ->
            sb.appendLine("- ${mem.content}")
        }
        sb.appendLine()
    }

    sb.toString().trim()
}

private suspend fun sendMessage(
    inputText: String,
    config: AiConfig,
    messages: List<ChatMessage>,
    toolRegistry: ToolRegistry,
    context: Context,
    projectPath: String,
    codeReference: CodeReference?,
    onClearReference: () -> Unit,
    setMessages: (List<ChatMessage>) -> Unit,
    setStreaming: (Boolean) -> Unit,
    setStreamingMessageId: (String?) -> Unit,
    setInputText: (String) -> Unit,
    currentConversationId: String,
    setCurrentConversationId: (String) -> Unit,
    summary: String,
    setSummary: (String) -> Unit,
    onAskUser: (title: String, description: String, options: List<String>, multi: Boolean, callback: (List<String>?) -> Unit) -> Unit,
    onConfirmInMain: (title: String, message: String, callback: (Boolean) -> Unit) -> Unit,
    onOpenFile: (filePath: String, startLine: Int, endLine: Int) -> Unit,
    projectOps: ProjectOps,
    onError: ((String) -> Unit)? = null
) {
    android.util.Log.d("AiChat", "sendMessage start msgs=${messages.size} threshold=$COMPRESS_THRESHOLD")
    val userMsgId = UUID.randomUUID().toString()
    val userMessage = ChatMessage(
        id = userMsgId,
        role = ChatRole.USER,
        content = inputText,
        codeReference = codeReference
    )
    // 立即反馈：先追加用户消息 + AI 占位气泡并清空输入框，压缩摘要或等待首 token 期间
    // 即显示占位加载气泡，避免 UI 长时间无响应（大上下文压缩需数秒）
    var assistantMsgId = UUID.randomUUID().toString()
    val placeholderAssistant = ChatMessage(
        id = assistantMsgId,
        role = ChatRole.ASSISTANT,
        content = "",
        isStreaming = true,
        reasoning = ""
    )
    setMessages(messages + userMessage + placeholderAssistant)
    setInputText("")
    onClearReference()
    setStreaming(true)
    setStreamingMessageId(assistantMsgId)

    // 上下文压缩：消息过多时把最旧的压缩进滚动摘要，只保留最近窗口
    var effectiveMessages = messages
    var currentSummary = summary
    if (messages.size >= COMPRESS_THRESHOLD) {
        // 窗口边界尽量落在 user 消息上，避免窗口以 tool/assistant 开头导致 Anthropic 拒绝
        var startIdx = messages.size - KEEP_WINDOW
        if (startIdx > 0) {
            while (startIdx < messages.size && messages[startIdx].role != ChatRole.USER) {
                startIdx++
            }
            if (startIdx >= messages.size) startIdx = messages.size - KEEP_WINDOW
        }
        val toCompress = messages.take(startIdx)
        val kept = messages.drop(startIdx)
        val newSummary = AiChatRepository.summarizeMessages(config, toCompress, currentSummary)
        if (newSummary.isNotBlank()) {
            currentSummary = newSummary
            setSummary(newSummary)
            effectiveMessages = kept
            setMessages(kept + userMessage + placeholderAssistant)
        }
    }

    val updatedMessages = effectiveMessages + userMessage

    // Build API messages: system prompt (skills + memories) + rolling summary + full context for code references
    val toolDefs = toolRegistry.getDefinitions()
    // 当前编辑文件快照（单文件毫秒级编译）；失败/无活动文件时为空串，不中断发送
    val editorSnapshot = try {
        projectOps.onGetEditorSnapshot()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        ""
    }
    val systemPrompt = buildSystemPrompt(context, config, editorSnapshot)
    android.util.Log.d("AiChat", "editorSnapshot len=${editorSnapshot.length} head=${editorSnapshot.take(120).replace('\n', '|')}")
    android.util.Log.d("AiChat", "systemPrompt len=${systemPrompt.length} hasSnapshotBlock=${systemPrompt.contains("## 当前编辑器状态")}")
    val baseApiMessages = buildList {
        if (systemPrompt.isNotBlank()) {
            add(ChatMessage(id = UUID.randomUUID().toString(), role = ChatRole.SYSTEM, content = systemPrompt))
        }
        if (currentSummary.isNotBlank()) {
            add(ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.SYSTEM,
                content = "以下是本对话早期内容的摘要（早期消息已被压缩，请以此作为上下文）：\n$currentSummary"
            ))
        }
    }
    // 每轮循环从当前对话消息重建 API 消息列表，确保工具调用与工具结果
    // 能正确回传给模型（否则模型看不到工具结果，记忆等工具无法生效）
    fun buildApiMessages(conversation: List<ChatMessage>): List<ChatMessage> =
        baseApiMessages + conversation.map { msg ->
            if (msg.role == ChatRole.USER && msg.codeReference != null) {
                val ref = msg.codeReference
                msg.copy(
                    content = "文件：${ref.fileName}（第${ref.startLine}~${ref.endLine}行）\n```\n${ref.content}\n```\n\n${msg.content}"
                )
            } else msg
        }

    var assistantContent = ""
    var assistantReasoning = ""
    var pendingToolCalls = mutableListOf<ToolCallInfo>()
    val currentAssistantMessage = ChatMessage(
        id = assistantMsgId,
        role = ChatRole.ASSISTANT,
        content = "",
        isStreaming = true
    )
    // 占位气泡已在发送时随 setMessages 前置，此处仅确保全局流式态（幂等）
    setStreaming(true)
    setStreamingMessageId(assistantMsgId)

    val toolContext = ToolContext(
        projectPath = projectPath,
        // 对话框/横幅回调必须在主线程置状态；等待点击期间只挂起当前协程，禁止 runBlocking（主线程自锁 → ANR）
        onAskUser = { title, description, options, multi ->
            val deferred = kotlinx.coroutines.CompletableDeferred<List<String>?>()
            withContext(Dispatchers.Main) {
                onAskUser(title, description, options, multi) { deferred.complete(it) }
            }
            deferred.await()
        },
        onOpenFile = { path, startLine, endLine ->
            withContext(Dispatchers.Main) {
                onOpenFile(path, startLine, endLine)
            }
            true
        },
        onConfirmInMain = { title, message ->
            val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main) {
                onConfirmInMain(title, message) { deferred.complete(it) }
            }
            deferred.await()
        },
        projectOps = projectOps
    )

    // Main agent loop
    var conversationMessages = updatedMessages
    var lastApiError: String? = null
    android.util.Log.d("AiChat", "sendMessage entering loop msgs=${conversationMessages.size} tools=${toolDefs.size}")

    try {
        while (true) {
            assistantContent = ""
            assistantReasoning = ""
            pendingToolCalls.clear()

            val apiMessages = buildApiMessages(conversationMessages)
            android.util.Log.d("AiChat", "round api msgs=${apiMessages.size} -> calling streamChat")

            val activeCtx = currentCoroutineContext()
            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                // 停止后 OkHttp 回调仍会触发：用协程活跃性守卫，取消后不再写 UI/追加内容
                AiChatRepository.streamChat(
                    config = config,
                    messages = apiMessages,
                    tools = toolDefs,
                    onChunk = { chunk ->
                        if (activeCtx.isActive) {
                            assistantContent += chunk
                            val msg = currentAssistantMessage.copy(
                                content = assistantContent,
                                reasoning = assistantReasoning,
                                isStreaming = true
                            )
                            setMessages(conversationMessages + msg)
                        }
                    },
                    onReasoning = { chunk ->
                        if (activeCtx.isActive) {
                            assistantReasoning += chunk
                            val msg = currentAssistantMessage.copy(
                                content = assistantContent,
                                reasoning = assistantReasoning,
                                isStreaming = true
                            )
                            setMessages(conversationMessages + msg)
                        }
                    },
                    onToolCall = { tc ->
                        if (activeCtx.isActive) {
                            pendingToolCalls.add(tc)
                        }
                    },
                    onComplete = { error ->
                        if (activeCtx.isActive) {
                            if (error != null) {
                                lastApiError = error
                                // Don't set assistantContent with error - just mark error
                            }
                            cont.resume(error == null, null)
                        }
                    }
                )
            }

            // 流式降级重试：部分端点（如 SiliconFlow 对部分模型的流式工具兼容问题）在 stream=true 时
            // 不下发 tool_calls，响应只剩 reasoning 后空结束。本轮无文本且无工具调用（异常空返回）时，
            // 用同参数非流式重试一次，恢复工具调用能力（对正常流式输出零影响）。
            if (
                config.resolvedProtocol == ApiProtocol.OPENAI &&
                lastApiError == null &&
                assistantContent.isEmpty() &&
                pendingToolCalls.isEmpty()
            ) {
                android.util.Log.w("AiChat", "stream returned empty(no text/no tool) -> non-stream retry once")
                assistantContent = ""
                assistantReasoning = ""
                pendingToolCalls.clear()
                val activeCtx2 = currentCoroutineContext()
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    AiChatRepository.streamChat(
                        config = config,
                        messages = apiMessages,
                        tools = toolDefs,
                        stream = false,
                        onChunk = { chunk ->
                            if (activeCtx2.isActive) {
                                assistantContent += chunk
                                setMessages(conversationMessages + currentAssistantMessage.copy(
                                    content = assistantContent,
                                    reasoning = assistantReasoning,
                                    isStreaming = true
                                ))
                            }
                        },
                        onReasoning = { chunk ->
                            if (activeCtx2.isActive) {
                                assistantReasoning += chunk
                                setMessages(conversationMessages + currentAssistantMessage.copy(
                                    content = assistantContent,
                                    reasoning = assistantReasoning,
                                    isStreaming = true
                                ))
                            }
                        },
                        onToolCall = { tc ->
                            if (activeCtx2.isActive) {
                                pendingToolCalls.add(tc)
                            }
                        },
                        onComplete = { error ->
                            if (activeCtx2.isActive) {
                                if (error != null) {
                                    lastApiError = error
                                }
                                cont.resume(error == null, null)
                            }
                        }
                    )
                }
            }

            val finalAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = ChatRole.ASSISTANT,
                content = assistantContent,
                toolCalls = pendingToolCalls.toList(),
                isStreaming = false,
                reasoning = assistantReasoning
            )
            if (lastApiError != null) {
                // Error occurred - don't add the empty assistant message, just show error banner
                break
            }
            conversationMessages = conversationMessages + finalAssistantMsg
            setMessages(conversationMessages)
            setStreamingMessageId(null)

            // Execute tool calls
            if (pendingToolCalls.isEmpty()) break

            for (tc in pendingToolCalls) {
                // 工具读写文件/执行 shell 均为阻塞操作，必须移出主线程（否则大文件读写/等待进程退出直接卡 UI）
                val result = withContext(Dispatchers.IO) {
                    toolRegistry.execute(tc, toolContext)
                }
                val toolMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.TOOL,
                    content = if (result.success) result.data else "错误：${result.error}",
                    toolCalls = listOf(tc)
                )
                conversationMessages = conversationMessages + toolMsg
                setMessages(conversationMessages)
            }

            pendingToolCalls.clear()

            // AI 任意工具（file_io / execute_shell 等）改盘后，统一把已打开文件与磁盘对齐，
            // 避免编辑区仍显示旧 buffer（保存时覆盖 AI 修改）。每轮工具执行完同步一次。
            try {
                projectOps.onSyncEditorsFromDisk()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }

            // Regenerate assistantMsgId for next loop iteration to prevent duplicate LazyColumn keys
            assistantMsgId = UUID.randomUUID().toString()
        }
        if (lastApiError != null) {
            val provider = config.activeProvider
            android.util.Log.w(
                "AiChat",
                "sendMessage api error: $lastApiError | provider=${provider?.name} model=${provider?.model ?: config.model} " +
                    "protocol=${config.resolvedProtocol} messages=${conversationMessages.size} tools=${toolDefs.size}"
            )
            setMessages(conversationMessages)
            onError?.invoke("API 错误: $lastApiError")
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // User cancelled - don't show error, just remove the streaming message
        setMessages(conversationMessages)
        throw e
    } catch (e: Exception) {
        // Remove the empty streaming message and show error banner
        setMessages(conversationMessages)
        onError?.invoke(e.message ?: "未知错误")
    } finally {
        setStreaming(false)
        setStreamingMessageId(null)
    }
}