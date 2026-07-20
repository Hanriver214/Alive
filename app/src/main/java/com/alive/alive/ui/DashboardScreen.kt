package com.alive.alive.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alive.alive.R
import com.alive.alive.state.DayState
import java.time.Instant
import java.time.ZoneId

@Composable
fun DashboardScreen(
    viewModel: MainViewModel
) {
    val state by viewModel.dayState.collectAsStateWithLifecycle()
    val cfg by viewModel.config.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "今天，${state.dayKey.ifEmpty { "—" }}",
            style = MaterialTheme.typography.headlineMedium
        )
        HorizontalDivider()

        StatusCard(state)

        EventRow(
            label = stringResource(R.string.event_a_label),
            done = state.eventA
        )
        EventRow(
            label = stringResource(R.string.event_b_label),
            done = state.eventB
        )
        EventRow(
            label = stringResource(R.string.event_c_label),
            done = state.eventC
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.checkedIn) {
            Button(
                onClick = { viewModel.triggerActiveCheckIn() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("我挺好（手动签到）")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "今日已签到 ✓",
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        MailStatusCard(cfg = cfg, state = state, onSendNow = { viewModel.sendAlertMail() })
    }
}

@Composable
private fun StatusCard(state: DayState) {
    val statusText = when {
        state.checkedIn -> "今日已签到 ✓"
        state.markedCount > 0 -> "监测中…（${state.markedCount}/3）"
        else -> "尚未签到，等待 12:00 关怀"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.checkedIn)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "状态",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.passiveCheckIn) Text("· 被动签到", fontSize = 12.sp)
            if (state.activeCheckIn) Text("· 主动签到", fontSize = 12.sp)
            if (state.careNotificationShown) Text("· 12:00 关怀通知已推送", fontSize = 12.sp)
            if (state.emailSent) Text("· 邮件已发送", fontSize = 12.sp)
        }
    }
}

@Composable
private fun EventRow(label: String, done: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else Color.LightGray,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MailStatusCard(
    cfg: com.alive.alive.data.SmtpConfig,
    state: DayState,
    onSendNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Email,
                    contentDescription = null,
                    tint = if (cfg.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "邮件通知",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = when {
                    !cfg.enabled -> "未启用"
                    cfg.to.isBlank() || cfg.host.isBlank() -> "配置不完整"
                    state.emailSent -> "今日已发送 ✓"
                    else -> "监控中，未签到时将发送"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (cfg.enabled && cfg.to.isNotBlank()) {
                Text(
                    text = "收件人: ${cfg.to}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cfg.enabled && cfg.host.isNotBlank() && cfg.user.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSendNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即发送提醒邮件")
                }
            }
        }
    }
}
