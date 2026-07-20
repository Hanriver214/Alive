package com.alive.alive.ui

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
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()

    var draft by remember(cfg) { mutableStateOf(cfg) }
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

    LaunchedEffect(exportResult) {
        if (exportResult != null) {
            delay(5000)
            viewModel.clearExportResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SMTP 应用密码使用 Android KeyStore 在本机加密保存。" +
                        "重置配置将清除所有设置，导出的配置可在另一台设备导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportSmtpConfig() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出配置")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入配置")
                    }
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重置")
                    }
                }
                exportResult?.let {
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

        Text("SMTP 邮件配置", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider()

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
                onCheckedChange = { draft = draft.copy(enabled = it) }
            )
        }

        Text("快速选择服务商", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.gmail.com", port = 587) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Gmail")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.qq.com", port = 465) },
                modifier = Modifier.weight(1f)
            ) {
                Text("QQ")
            }
            OutlinedButton(
                onClick = { draft = draft.copy(host = "smtp.163.com", port = 465) },
                modifier = Modifier.weight(1f)
            ) {
                Text("163")
            }
        }

        OutlinedTextField(
            value = draft.host,
            onValueChange = { draft = draft.copy(host = it) },
            label = { Text("SMTP 服务器 (如 smtp.qq.com)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = draft.port.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { draft = draft.copy(port = it.coerceIn(1, 65535)) }
            },
            label = { Text("端口 (465=SSL, 587=STARTTLS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
            singleLine = true
        )

        OutlinedTextField(
            value = draft.pass,
            onValueChange = { draft = draft.copy(pass = it) },
            label = { Text("应用密码 / 授权码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        OutlinedTextField(
            value = draft.to,
            onValueChange = { draft = draft.copy(to = it) },
            label = { Text("收件邮箱地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = draft.subject,
            onValueChange = { draft = draft.copy(subject = it) },
            label = { Text("邮件标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = draft.body,
            onValueChange = { draft = draft.copy(body = it) },
            label = { Text("邮件正文") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        OutlinedTextField(
            value = draft.resendHours.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { draft = draft.copy(resendHours = it.coerceIn(1, 24)) }
            },
            label = { Text("重发间隔（小时，1-24）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { viewModel.saveConfig(draft) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
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
            modifier = Modifier.fillMaxWidth()
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

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置配置") },
            text = {
                Text("将清除所有 SMTP 配置，无法恢复。确定继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveConfig(SmtpConfig())
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
