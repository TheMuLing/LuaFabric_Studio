package com.luafabric.studio.falling.ui.login

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.luafabric.studio.falling.R
import kotlinx.coroutines.launch
import muling.views.tool.utils.NonBlockingToastState

/** 云居登录/注册全屏界面 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: (YunJuResponse) -> Unit,
    toast: NonBlockingToastState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var qq by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var confirmPass by remember { mutableStateOf("") }
    var confirmPassVisible by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var keepLoggedIn by remember { mutableStateOf(true) }
    var rememberAccount by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sendingCode by remember { mutableStateOf(false) }

    var qqError by remember { mutableStateOf<String?>(null) }
    var passError by remember { mutableStateOf<String?>(null) }
    var confirmPassError by remember { mutableStateOf<String?>(null) }
    var nicknameError by remember { mutableStateOf<String?>(null) }
    var codeError by remember { mutableStateOf<String?>(null) }

    val qqShake = remember { Animatable(0f) }
    val passShake = remember { Animatable(0f) }
    val confirmShake = remember { Animatable(0f) }
    val nicknameShake = remember { Animatable(0f) }
    val codeShake = remember { Animatable(0f) }

    val qqLabel = stringResource(R.string.login_qq_label)
    val passLabel = stringResource(R.string.login_pass_label)
    val confirmPassLabel = stringResource(R.string.login_confirm_pass_label)
    val nicknameLabel = stringResource(R.string.login_nickname_label)
    val codeLabel = stringResource(R.string.login_code_label)
    val passMismatch = stringResource(R.string.login_pass_mismatch)

    // 左右摇晃动画 + 短振一次
    suspend fun shake(anim: Animatable<Float, AnimationVector1D>) {
        for (target in listOf(-12f, 12f, -8f, 8f, -4f, 4f, 0f)) {
            anim.animateTo(target, tween(durationMillis = 70, easing = LinearEasing))
        }
    }

    fun vibrate() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // 登录：从上到下每次只查错一个编辑框，命中即停止
    val onLoginClick: () -> Unit = loginAction@{
        if (qq.isBlank()) {
            qqError = qqLabel
            vibrate()
            scope.launch { shake(qqShake) }
            return@loginAction
        }
        if (pass.isBlank()) {
            passError = passLabel
            vibrate()
            scope.launch { shake(passShake) }
            return@loginAction
        }
        qqError = null
        passError = null
        val trimmedQq = qq.trim()
        scope.launch {
            loading = true
            try {
                val result = LoginRepository.login(trimmedQq, pass)
                if (result.success && result.user != null) {
                    LoginStore.saveLogin(
                        context, trimmedQq, pass,
                        keepLoggedIn, rememberAccount, result.user
                    )
                    onLoginSuccess(result.user)
                } else {
                    toast.showToast(result.message.ifBlank { context.getString(R.string.login_failed_default) })
                }
            } catch (e: Exception) {
                toast.showToast(context.getString(R.string.login_network_error))
            } finally {
                loading = false
            }
        }
    }

    // 注册：QQ → 密码 → 再输入一次密码(非空且一致) → 昵称 → 验证码
    val onRegisterClick: () -> Unit = registerAction@{
        if (qq.isBlank()) {
            qqError = qqLabel
            vibrate()
            scope.launch { shake(qqShake) }
            return@registerAction
        }
        if (pass.isBlank()) {
            passError = passLabel
            vibrate()
            scope.launch { shake(passShake) }
            return@registerAction
        }
        if (confirmPass.isBlank()) {
            confirmPassError = confirmPassLabel
            vibrate()
            scope.launch { shake(confirmShake) }
            return@registerAction
        }
        if (confirmPass != pass) {
            confirmPassError = passMismatch
            vibrate()
            scope.launch { shake(passShake) }
            scope.launch { shake(confirmShake) }
            return@registerAction
        }
        if (nickname.isBlank()) {
            nicknameError = nicknameLabel
            vibrate()
            scope.launch { shake(nicknameShake) }
            return@registerAction
        }
        if (code.isBlank()) {
            codeError = codeLabel
            vibrate()
            scope.launch { shake(codeShake) }
            return@registerAction
        }
        qqError = null
        passError = null
        confirmPassError = null
        nicknameError = null
        codeError = null
        val trimmedQq = qq.trim()
        scope.launch {
            loading = true
            try {
                val reg = LoginRepository.register(
                    trimmedQq, pass, nickname.trim(), "$trimmedQq@qq.com", code.trim()
                )
                if (reg.first) {
                    // 注册成功 → 自动登录；登录失败则退回登录模式
                    val login = LoginRepository.login(trimmedQq, pass)
                    if (login.success && login.user != null) {
                        LoginStore.saveLogin(
                            context, trimmedQq, pass,
                            keepLoggedIn, rememberAccount, login.user
                        )
                        onLoginSuccess(login.user)
                    } else {
                        toast.showToast(login.message.ifBlank { context.getString(R.string.login_failed_default) })
                        isRegisterMode = false
                    }
                } else {
                    toast.showToast(reg.second.ifBlank { context.getString(R.string.login_register_failed) })
                }
            } catch (e: Exception) {
                toast.showToast(context.getString(R.string.login_network_error))
            } finally {
                loading = false
            }
        }
    }

    // 获取验证码：发送到 QQ号@qq.com
    val onGetCodeClick: () -> Unit = codeAction@{
        if (qq.isBlank()) {
            qqError = qqLabel
            vibrate()
            scope.launch { shake(qqShake) }
            return@codeAction
        }
        qqError = null
        scope.launch {
            sendingCode = true
            try {
                val res = LoginRepository.sendCode("${qq.trim()}@qq.com")
                toast.showToast(
                    res.second.ifBlank {
                        context.getString(
                            if (res.first) R.string.login_send_code_success
                            else R.string.login_send_code_failed
                        )
                    }
                )
            } catch (e: Exception) {
                toast.showToast(context.getString(R.string.login_network_error))
            } finally {
                sendingCode = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部栏：无容器色返回按钮 + 同行折叠标题（登录 ↔ 注册 淡入淡出切换）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.login_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            AnimatedContent(
                targetState = isRegisterMode,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "login_title"
            ) { registerMode ->
                Text(
                    text = stringResource(
                        if (registerMode) R.string.register_title else R.string.login_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // QQ号：仅数字，md3 Chat 图标，末尾清空
            OutlinedTextField(
                value = qq,
                onValueChange = { new ->
                    if (new.all(Char::isDigit)) {
                        qq = new
                        qqError = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = qqShake.value },
                label = { Text(qqLabel) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (qq.isNotEmpty()) {
                        IconButton(onClick = { qq = ""; qqError = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                isError = qqError != null,
                supportingText = { qqError?.let { Text(it) } },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )

            // 密码：md3 Lock 图标，末尾可视/不可视小眼睛，默认不可视
            OutlinedTextField(
                value = pass,
                onValueChange = { new ->
                    pass = new
                    passError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = passShake.value },
                label = { Text(passLabel) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (passVisible) R.string.login_hide_password
                                else R.string.login_show_password
                            )
                        )
                    }
                },
                singleLine = true,
                isError = passError != null,
                supportingText = { passError?.let { Text(it) } },
                visualTransformation =
                    if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            // 注册模式专属三编辑框：默认隐藏，进入注册模式后滑出
            AnimatedVisibility(
                visible = isRegisterMode,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 再输入一次密码：md3 LockReset，小眼睛默认不可视
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { new ->
                            confirmPass = new
                            confirmPassError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = confirmShake.value },
                        label = { Text(confirmPassLabel) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.LockReset,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                Icon(
                                    if (confirmPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (confirmPassVisible) R.string.login_hide_password
                                        else R.string.login_show_password
                                    )
                                )
                            }
                        },
                        singleLine = true,
                        isError = confirmPassError != null,
                        supportingText = { confirmPassError?.let { Text(it) } },
                        visualTransformation =
                            if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        )
                    )

                    // 昵称：md3 Edit 铅笔，末尾清空
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { new ->
                            nickname = new
                            nicknameError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = nicknameShake.value },
                        label = { Text(nicknameLabel) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (nickname.isNotEmpty()) {
                                IconButton(onClick = { nickname = ""; nicknameError = null }) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        isError = nicknameError != null,
                        supportingText = { nicknameError?.let { Text(it) } },
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                    )

                    // 验证码：md3 VerifiedUser 盾+对勾，末尾清空，同排右侧边框按钮「获取验证码」
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { new ->
                                if (new.all(Char::isDigit)) {
                                    code = new
                                    codeError = null
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { translationX = codeShake.value },
                            label = { Text(codeLabel) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (code.isNotEmpty()) {
                                    IconButton(onClick = { code = ""; codeError = null }) {
                                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                                    }
                                }
                            },
                            singleLine = true,
                            isError = codeError != null,
                            supportingText = { codeError?.let { Text(it) } },
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = onGetCodeClick,
                            enabled = !sendingCode
                        ) {
                            if (sendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.login_get_code))
                            }
                        }
                    }
                }
            }

            // 主按钮：登录模式=登录，注册模式=注册
            Button(
                onClick = if (isRegisterMode) onRegisterClick else onLoginClick,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (isRegisterMode) R.string.login_register_submit else R.string.login_button
                        ),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 双复选框上下堆叠（宽度最大），进入注册模式后淡出
            AnimatedVisibility(
                visible = !isRegisterMode,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(250))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoginCheckboxRow(
                        checked = keepLoggedIn,
                        onCheckedChange = {
                            keepLoggedIn = it
                            vibrate()
                        },
                        text = stringResource(R.string.login_keep_login)
                    )
                    LoginCheckboxRow(
                        checked = rememberAccount,
                        onCheckedChange = {
                            rememberAccount = it
                            vibrate()
                        },
                        text = stringResource(R.string.login_remember_account)
                    )
                }
            }
        }

        // 底部：注册账号(注册模式=返回登录) 与 忘记密码 各占半行分别居中，中间分割竖线
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = {
                        vibrate()
                        isRegisterMode = !isRegisterMode
                        confirmPassError = null
                        nicknameError = null
                        codeError = null
                    }
                ) {
                    Text(
                        stringResource(
                            if (isRegisterMode) R.string.login_back_to_login
                            else R.string.login_register
                        )
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { vibrate() }
                ) {
                    Text(stringResource(R.string.login_forgot_password))
                }
            }
        }
    }
}

/** 左文本右框复选框：整行可点，点文本或框均切换，宽度撑满 */
@Composable
private fun LoginCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
