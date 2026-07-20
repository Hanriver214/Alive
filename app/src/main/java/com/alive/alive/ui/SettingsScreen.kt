package com.alive.alive.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alive.alive.data.SmtpConfig

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val cfg by viewModel.config.collectAsStateWithLifecycle()
    var draft by remember(cfg) { mutableStateOf(cfg) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
    }
}
