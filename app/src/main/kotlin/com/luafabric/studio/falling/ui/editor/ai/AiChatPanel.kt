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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.luafabric.studio.falling.ui.editor.ai.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class AiPage { CHAT, SETTINGS, HISTORY }

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

// ========== Main Panel ==========

@Composable
fun AiChatPanel(
    projectPath: String,
    codeReference: CodeReference?,
    onClearReference: () -> Unit,
    onOpenFile: (filePath: String, startLine: Int, endLine: Int) -> Unit,
    onAskUser: (title: String, options: List<String>, callback: (String?) -> Unit) -> Unit,
    onConfirmInMain: (title: String, message: String, callback: (Boolean) -> Unit) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Config
    val config = remember { mutableStateOf(AiSettingsManager.loadConfig(context)) }

    // Chat state (rememberSaveable to survive tab switches via SaveableStateHolder)
    var messages by rememberSaveable(stateSaver = ChatMessageListSaver) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputValue by rememberSaveable(stateSaver = TextFieldValueFullSaver) { mutableStateOf(TextFieldValue("")) }
    var isStreaming by rememberSaveable { mutableStateOf(false) }
    var currentConversationId by rememberSaveable { mutableStateOf(AiChatHistoryStore.createNewId()) }
    var streamingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentPage by rememberSaveable { mutableStateOf(AiPage.CHAT) }
    var showWelcome by remember { mutableStateOf(!AiSettingsManager.isWelcomeDismissed(context)) }
    var shareMode by rememberSaveable { mutableStateOf(false) }
    var selectedShareMessages by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sendJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var errorBanners by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var wasInterrupted by rememberSaveable { mutableStateOf(false) }
    var selectedProviderIndex by rememberSaveable { mutableStateOf(
        if (config.value.providers.isNotEmpty() && config.value.selectedProviderIndex in config.value.providers.indices)
            config.value.selectedProviderIndex
        else 0
    ) }
    var summary by rememberSaveable { mutableStateOf("") }

    // Tool registry
    val toolRegistry = remember {
        ToolRegistry().apply {
            register(ShellTool())
            register(FileIOTool())
            register(SearchTool())
            register(AskUserTool())
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
        }
    }

    // Auto-scroll
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty() && isStreaming) {
            listState.scrollToBottom(animate = false)
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
            AiChatHistoryStore.saveConversation(context, data)
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
            selectedProviderIndex = selectedProviderIndex,
            onBackClick = { currentPage = AiPage.CHAT },
            onSettingsClick = { currentPage = AiPage.SETTINGS },
            onHistoryClick = { currentPage = AiPage.HISTORY },
            onSelectProvider = { idx ->
                selectedProviderIndex = idx
                config.value = config.value.copy(selectedProviderIndex = idx)
                AiSettingsManager.saveConfig(context, config.value)
            },
            onSelectModel = { providerIdx, model ->
                val providers = config.value.providers.toMutableList()
                providers[providerIdx] = providers[providerIdx].copy(model = model)
                val newConfig = config.value.copy(providers = providers, selectedProviderIndex = providerIdx)
                config.value = newConfig
                selectedProviderIndex = providerIdx
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
                            sendJob = scope.launch {
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
                                    onAskUser = onAskUser,
                                    onConfirmInMain = onConfirmInMain,
                                    onOpenFile = onOpenFile,
                                    onError = { errorBanners = errorBanners + it; wasInterrupted = true }
                                )
                            }
                        },
                        onStop = {
                            sendJob?.cancel()
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
                        onResend = {
                            val lastUserIdx = messages.lastIndexOf(messages.lastOrNull { it.role == ChatRole.USER })
                            if (lastUserIdx >= 0) {
                                val lastUserMsg = messages[lastUserIdx]
                                messages = messages.take(lastUserIdx)
                                inputValue = TextFieldValue(lastUserMsg.content, selection = TextRange(lastUserMsg.content.length))
                                wasInterrupted = false
                                sendJob = scope.launch {
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
                                        onAskUser = onAskUser,
                                        onConfirmInMain = onConfirmInMain,
                                        onOpenFile = onOpenFile,
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
                        currentId = currentConversationId,
                        onSelectConversation = { data ->
                            messages = data.messages
                            summary = data.summary
                            currentConversationId = data.id
                            currentPage = AiPage.CHAT
                        },
                        onNewChat = {
                            messages = emptyList()
                            summary = ""
                            currentConversationId = AiChatHistoryStore.createNewId()
                            currentPage = AiPage.CHAT
                        }
                    )
                }
            }
        }
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
                        AiPage.HISTORY -> "历史记录"
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
                        Icon(Icons.Filled.History, contentDescription = "历史记录", modifier = Modifier.size(20.dp))
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
    onResend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { "${it.id}-${it.timestamp}" }) { msg ->
                    val isLastUser = msg.role == ChatRole.USER && messages.lastOrNull { it.role == ChatRole.USER }?.id == msg.id
                    ChatMessageBubble(
                        message = msg,
                        isStreaming = msg.id == streamingMessageId,
                        shareMode = shareMode,
                        isSelected = msg.id in selectedShareMessages,
                        onToggleSelect = { onToggleShareMessage(msg.id) },
                        onOpenFile = onOpenFile,
                        onCopy = onCopy,
                        onRegenerate = onRegenerate,
                        onEnterShareMode = onEnterShareMode,
                        showResendIcon = isLastUser && wasInterrupted && !isStreaming,
                        onResend = onResend
                    )
                }
            }

            // Scroll-to-top / scroll-to-bottom buttons (bottom-right)
            if (messages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(ArrowCollapseUpIcon, contentDescription = "回到顶部", modifier = Modifier.size(16.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { scope.launch { listState.scrollToBottom(animate = true) } },
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

        // Input area
        Surface(tonalElevation = 1.dp, shadowElevation = 2.dp) {
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

// ========== Chat Message Bubble ==========

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isStreaming: Boolean,
    shareMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenFile: (String, Int, Int) -> Unit,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onEnterShareMode: () -> Unit,
    showResendIcon: Boolean = false,
    onResend: () -> Unit = {}
) {
    val isUser = message.role == ChatRole.USER
    val isTool = message.role == ChatRole.TOOL
    var showResendConfirm by remember { mutableStateOf(false) }

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
            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 12.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Code reference in message
                    message.codeReference?.let { ref ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = ref.preview,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Tool calls
                    if (message.toolCalls.isNotEmpty()) {
                        message.toolCalls.forEach { tc ->
                            CollapsibleSection(
                                title = "工具：${tc.name}",
                                content = tc.arguments.take(200)
                            )
                        }
                    }

                    // Content with SelectionContainer for system long-press copy
                    if (message.content.isNotBlank()) {
                        if (isUser) {
                            SelectionContainer {
                                Text(
                                    text = message.content + if (isStreaming) " ▌" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            AiMessageContent(
                                content = message.content,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (isStreaming && !isUser) {
                        AndroidView(
                            factory = { ctx ->
                                com.google.android.material.loadingindicator.LoadingIndicator(ctx).apply {
                                    show()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Action buttons + timestamp for assistant messages
            if (!isUser && !isTool) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timestamp
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    // Token consumption
                    Text(
                        text = "~${message.content.length / 4} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // Action buttons
                    ActionIconButton(Icons.Filled.ContentCopy, "复制", onClick = { onCopy(message.content) })
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

// ========== Collapsible Section ==========

@Composable
private fun CollapsibleSection(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ========== Settings Collapsible Entry ==========

@Composable
private fun SettingsCollapsibleEntry(
    title: String,
    icon: ImageVector,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column {
        Surface(
            onClick = { expanded = !expanded },
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
    var skills by remember { mutableStateOf(config.skills.ifEmpty {
        val bundledSkillPath = File(context.filesDir, ".agent/skills/luafabric-studio/SKILL.md").absolutePath
        listOf(
            SkillConfig(
                path = "C:\\Users\\mingm\\.agents\\skills\\caveman",
                enabled = true,
                title = "caveman",
                readme = "Ultra-compressed communication mode. Cuts token usage ~75%."
            ),
            SkillConfig(
                path = bundledSkillPath,
                enabled = true,
                title = "luafabric-studio",
                readme = "LuaFabric Studio、AndroLua、LuaFabric 项目开发专用 skill。"
            )
        )
    })}
    var memories by remember { mutableStateOf(config.memories.toMutableList()) }
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
            temperature = config.temperature
        ))
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
        SettingsCollapsibleEntry(title = "API 提供商", icon = Icons.Filled.Cloud, defaultExpanded = true) {
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
        SettingsCollapsibleEntry(title = "技能", icon = Icons.Filled.Extension, defaultExpanded = false) {
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
        SettingsCollapsibleEntry(title = "记忆", icon = Icons.Filled.Psychology, defaultExpanded = false) {
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; newNameError = false },
                        label = { Text("提供商名称", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = newNameError,
                        supportingText = { if (newNameError) Text("请输入提供商名称") },
                        shape = RoundedCornerShape(shapeSize),
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
                        trailingIcon = {
                            if (newBaseUrl.isNotEmpty()) {
                                IconButton(onClick = { newBaseUrl = ""; newBaseUrlError = false }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    // Model ID with dropdown + cloud-search
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    newApiKeyError = newApiKey.isBlank()
                    newBaseUrlError = newBaseUrl.isBlank()
                    newModelError = newModel.isBlank()
                    if (!newNameError && !newApiKeyError && !newBaseUrlError && !newModelError) {
                        providers = (providers + ApiProvider(
                            name = newName.trim(),
                            protocol = newProtocol,
                            apiKey = newApiKey.trim(),
                            baseUrl = newBaseUrl.trim(),
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("提供商名称", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(shapeSize),
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
                        trailingIcon = {
                            if (editBaseUrl.isNotEmpty()) {
                                IconButton(onClick = { editBaseUrl = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = editModel,
                                onValueChange = { editModel = it },
                                label = { Text("模型 ID", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(shapeSize),
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
    currentId: String,
    onSelectConversation: (ConversationData) -> Unit,
    onNewChat: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        conversations = AiChatHistoryStore.listConversations(context)
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
                Text("暂无历史对话", style = MaterialTheme.typography.bodyMedium,
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
                                    AiChatHistoryStore.loadConversation(context, conv.id)?.let { data ->
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
                        AiChatHistoryStore.deleteConversation(context, id)
                        conversations = AiChatHistoryStore.listConversations(context)
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

// 滚动到列表真正的最底部：先滚到最后一个 item，若该 item 高于视口
// 再按 item 高度计算偏移，让 item 底部与视口底部对齐，避免长消息被截断
private suspend fun LazyListState.scrollToBottom(animate: Boolean) {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    if (animate) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
    if (lastVisible != null) {
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val scrollOffset = viewportHeight - lastVisible.size
        if (animate) animateScrollToItem(lastIndex, scrollOffset) else scrollToItem(lastIndex, scrollOffset)
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
    config: AiConfig
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
    onAskUser: (title: String, options: List<String>, callback: (String?) -> Unit) -> Unit,
    onConfirmInMain: (title: String, message: String, callback: (Boolean) -> Unit) -> Unit,
    onOpenFile: (filePath: String, startLine: Int, endLine: Int) -> Unit,
    onError: ((String) -> Unit)? = null
) {
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
            setMessages(kept)
        }
    }

    val userMsgId = UUID.randomUUID().toString()
    val userMessage = ChatMessage(
        id = userMsgId,
        role = ChatRole.USER,
        content = inputText,
        codeReference = codeReference
    )

    val updatedMessages = effectiveMessages + userMessage
    setMessages(updatedMessages)
    setInputText("")
    onClearReference()

    // Build API messages: system prompt (skills + memories) + rolling summary + full context for code references
    val toolDefs = toolRegistry.getDefinitions()
    val systemPrompt = buildSystemPrompt(context, config)
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

    var assistantMsgId = UUID.randomUUID().toString()
    var assistantContent = ""
    var pendingToolCalls = mutableListOf<ToolCallInfo>()
    var currentAssistantMessage = ChatMessage(
        id = assistantMsgId,
        role = ChatRole.ASSISTANT,
        content = "",
        isStreaming = true
    )
    setMessages(updatedMessages + currentAssistantMessage)
    setStreaming(true)
    setStreamingMessageId(assistantMsgId)

    val toolContext = ToolContext(
        projectPath = projectPath,
        onAskUser = { title, options ->
            var result: String? = null
            kotlinx.coroutines.runBlocking {
                val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
                onAskUser(title, options) { deferred.complete(it) }
                deferred.await()
            }.also { result = it }
            result
        },
        onOpenFile = { path, startLine, endLine ->
            onOpenFile(path, startLine, endLine)
            true
        },
        onConfirmInMain = { title, message ->
            var result = false
            kotlinx.coroutines.runBlocking {
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                onConfirmInMain(title, message) { deferred.complete(it) }
                deferred.await()
            }.also { result = it }
            result
        }
    )

    // Main agent loop
    var conversationMessages = updatedMessages
    var lastApiError: String? = null

    try {
        while (true) {
            assistantContent = ""
            pendingToolCalls.clear()

            val apiMessages = buildApiMessages(conversationMessages)

            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                AiChatRepository.streamChat(
                    config = config,
                    messages = apiMessages,
                    tools = toolDefs,
                    onChunk = { chunk ->
                        assistantContent += chunk
                        val msg = currentAssistantMessage.copy(
                            content = assistantContent,
                            isStreaming = true
                        )
                        setMessages(conversationMessages + msg)
                    },
                    onToolCall = { tc ->
                        pendingToolCalls.add(tc)
                    },
                    onComplete = { error ->
                        if (error != null) {
                            lastApiError = error
                            // Don't set assistantContent with error - just mark error
                        }
                        cont.resume(error == null, null)
                    }
                )
            }

            val finalAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = ChatRole.ASSISTANT,
                content = assistantContent,
                toolCalls = pendingToolCalls.toList(),
                isStreaming = false
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
                val result = toolRegistry.execute(tc, toolContext)
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

            // Regenerate assistantMsgId for next loop iteration to prevent duplicate LazyColumn keys
            assistantMsgId = UUID.randomUUID().toString()
        }
        if (lastApiError != null) {
            android.util.Log.w("AiChat", "sendMessage api error: $lastApiError")
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