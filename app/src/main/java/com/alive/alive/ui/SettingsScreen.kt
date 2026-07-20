package com.alive.alive.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alive.alive.BuildConfig
import com.alive.alive.data.SmtpConfig
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val cfg by viewModel.config.collectAsStateWithLifecycle()
    val isMasterSet by viewModel.isMasterPasswordSet.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val cryptoMessage by viewModel.cryptoMessage.collectAsStateWithLifecycle()

    var draft by remember(cfg) { mutableStateOf(cfg) }
    var showSetMasterDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().readText()
                    viewModel.importSmtpConfig(json)
                }
            }
        }
    }

    LaunchedEffect(cryptoMessage) {
        if (cryptoMessage != null) {
            delay(5000)
            viewModel.clearCryptoMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 加密存储区 =====
        Text("加密存储", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        !isMasterSet -> "尚未设置加密密码"
                        isUnlocked -> "已解锁 · 可编辑应用密码"
                        else -> "已锁定 · 应用密码已加密保护"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "SMTP 应用密码使用您自定义的加密密码在本机加密保存。" +
                        "忘记加密密码只能重置（清除所有 SMTP 配置），无法恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isMasterSet) {
                        Button(
                            onClick = { showSetMasterDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("设置加密密码")
                        }
                    } else if (!isUnlocked) {
                        Button(
                            onClick = { showUnlockDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("解锁")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.lock() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("锁定")
                        }
                    }
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重置")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportSmtpConfig() },
                        modifier = Modifier.weight(1f),
                        enabled = isMasterSet
                    ) {
                        Text("导出配置")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入配置")
                    }
                }
                cryptoMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("成功") || it.contains("已") && !it.contains("失败"))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ===== SMTP 配置区 =====
        Text("SMTP 邮件配置", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider()

        val editable = !isMasterSet || isUnlocked
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "启用邮件通知",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = draft.enabled,
                onCheckedChange = { draft = draft.copy(enabled = it) },
                enabled = editable
            )
        }

        Text("快速选择服务商", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.gmail.com", port = 587) },
                modifier = Modifier.weight(1f),
                enabled = editable
            ) {
                Text("Gmail")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.qq.com", port = 465) },
                modifier = Modifier.weight(1f),
                enabled = editable
            ) {
                Text("QQ")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.163.com", port = 465) },
                modifier = Modifier.weight(1f),
                enabled = editable
            ) {
                Text("163")
            }
        }

        OutlinedTextField(
            value = draft.host,
            onValueChange = { draft = draft.copy(host = it) },
            label = { Text("SMTP 服务器 (如 smtp.qq.com)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable
        )

        OutlinedTextField(
            value = draft.port.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { draft = draft.copy(port = it.coerceIn(1, 65535)) }
            },
            label = { Text("端口 (465=SSL, 587=STARTTLS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = editable
        )
        if (draft.host.contains("gmail", true) && draft.port == 587) {
            Text(
                text = "提示：Gmail 的 587 端口在某些网络下会被拦截，如遇 [EOF] 错误请改为 465 端口",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = draft.user,
            onValueChange = { draft = draft.copy(user = it) },
            label = { Text("发件邮箱地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable
        )

        OutlinedTextField(
            value = draft.pass,
            onValueChange = { draft = draft.copy(pass = it) },
            label = { Text("应用密码 / 授权码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = editable,
            readOnly = isMasterSet && !isUnlocked
        )
        if (isMasterSet && !isUnlocked) {
            Text(
                text = "应用密码已加密保存，需先解锁才能查看或修改",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = draft.to,
            onValueChange = { draft = draft.copy(to = it) },
            label = { Text("收件邮箱地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable
        )

        OutlinedTextField(
            value = draft.subject,
            onValueChange = { draft = draft.copy(subject = it) },
            label = { Text("邮件标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = editable
        )

        OutlinedTextField(
            value = draft.body,
            onValueChange = { draft = draft.copy(body = it) },
            label = { Text("邮件正文") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            enabled = editable
        )

        OutlinedTextField(
            value = draft.resendHours.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { draft = draft.copy(resendHours = it.coerceIn(1, 24)) }
            },
            label = { Text("重发间隔（小时，1-24）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = editable
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                if (!isMasterSet) {
                    showSetMasterDialog = true
                } else {
                    viewModel.saveConfig(draft)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = editable || !isMasterSet
        ) {
            Text(if (!isMasterSet) "设置加密密码并保存" else "保存")
        }

        Spacer(modifier = Modifier.height(8.dp))

        val testResult by viewModel.testMailResult.collectAsStateWithLifecycle()
        LaunchedEffect(testResult) {
            if (testResult != null) {
                delay(8000)
                viewModel.clearTestMailResult()
            }
        }

        Button(
            onClick = { viewModel.sendTestMail(draft) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isUnlocked || !isMasterSet
        ) {
            Text("发送测试邮件")
        }

        OutlinedButton(
            onClick = { viewModel.diagnoseSmtp(draft) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("诊断网络连接")
        }

        testResult?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("成功")) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "版本 ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ===== 设置加密密码对话框 =====
    if (showSetMasterDialog) {
        var pwd by remember { mutableStateOf("") }
        var pwdConfirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetMasterDialog = false },
            title = { Text("设置加密密码") },
            text = {
                Column {
                    Text(
                        text = "此密码用于加密 SMTP 应用密码。忘记后只能重置删除配置，无法恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("加密密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pwdConfirm,
                        onValueChange = { pwdConfirm = it },
                        label = { Text("再次输入") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pwd.isNotBlank() && pwd == pwdConfirm) {
                            viewModel.setMasterPassword(pwd, draft)
                            showSetMasterDialog = false
                        }
                    },
                    enabled = pwd.isNotBlank() && pwd == pwdConfirm
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetMasterDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ===== 解锁对话框 =====
    if (showUnlockDialog) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("输入加密密码解锁") },
            text = {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("加密密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unlock(pwd)
                        showUnlockDialog = false
                    },
                    enabled = pwd.isNotBlank()
                ) {
                    Text("解锁")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ===== 重置确认对话框 =====
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置加密配置") },
            text = {
                Text("将清除所有 SMTP 配置和加密密码，无法恢复。确定继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllCrypto()
                        showResetConfirm = false
                    }
                ) {
                    Text("确定重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
