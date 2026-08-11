package com.vrc.friendtracker.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vrc.friendtracker.ui.AppViewModelFactory
import com.vrc.friendtracker.ui.appFrom

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val app = appFrom(LocalContext.current)
    val vm: LoginViewModel = viewModel(factory = AppViewModelFactory(app))
    val state by vm.uiState.collectAsState()
    val sessionActive by app.sessionActive.collectAsState()

    LaunchedEffect(sessionActive) {
        if (sessionActive) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("VRC好友状态", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "查看好友在线状态、所在房间、简介与信任等级变化",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = vm::onUsernameChange,
            label = { Text("VRChat 账号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.twoFactorRequired) {
            Spacer(Modifier.height(12.dp))
            if (state.twoFactorMethods.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    state.twoFactorMethods.forEach { m ->
                        FilterChip(
                            selected = state.selectedMethod == m,
                            onClick = { vm.onMethodChange(m) },
                            label = { Text(methodLabel(m)) },
                            enabled = !state.loading,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = state.code,
                onValueChange = vm::onCodeChange,
                label = { Text(methodLabel(state.selectedMethod)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (state.twoFactorRequired) vm.verifyCode() else vm.login() },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("请稍候…")
            } else {
                Text(if (state.twoFactorRequired) "验证并登录" else "登录")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "登录信息仅用于获取 VRChat 官方非公开接口数据，令牌加密保存在本机。\n使用第三方 API 存在账号风险，请自用并知悉。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun methodLabel(method: String?): String = when (method) {
    "totp" -> "验证器 App 验证码"
    "emailOtp" -> "邮箱验证码"
    "otp" -> "恢复代码"
    else -> "验证码"
}