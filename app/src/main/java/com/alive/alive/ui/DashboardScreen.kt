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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alive.alive.R
import com.alive.alive.state.DayState
import com.alive.alive.state.ScoreType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel
) {
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Alive") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            actions = {
                Box {
                    IconButton(onClick = { menuExpanded = !menuExpanded }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showSettings = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("日志") },
                            leadingIcon = { Icon(Icons.Filled.List, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showLogs = true
                            }
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "今天，${viewModel.dayState.collectAsStateWithLifecycle().value.dayKey.ifEmpty { "—" }}",
                style = MaterialTheme.typography.headlineMedium
            )
            HorizontalDivider()

            val state by viewModel.dayState.collectAsStateWithLifecycle()
            StatusCard(state)
            ScoreBreakdownCard(state)

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

            val cfg by viewModel.config.collectAsStateWithLifecycle()
            MailStatusCard(cfg = cfg, state = state, onSendNow = { viewModel.sendAlertMail() })
        }
    }

    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            ) {
                SettingsScreen(viewModel)
            }
        }
    }

    if (showLogs) {
        Dialog(
            onDismissRequest = { showLogs = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            ) {
                LogViewerScreen(viewModel)
            }
        }
    }
}

@Composable
private fun StatusCard(state: DayState) {
    val statusText = when {
        state.checkedIn -> "今日已签到 ✓"
        state.score > 0 -> "监测中…（${state.score}/${state.passiveThreshold} 分）"
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
            if (!state.checkedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = (state.score.toFloat() / state.passiveThreshold).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "分数 ≥ ${state.passiveThreshold} 自动签到",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state.passiveCheckIn) Text("· 被动签到", fontSize = 12.sp)
            if (state.activeCheckIn) Text("· 主动签到", fontSize = 12.sp)
            if (state.careNotificationShown12) Text("· 12:00 关怀通知已推送", fontSize = 12.sp)
            if (state.careNotificationShown18) Text("· 18:00 二次提醒已推送", fontSize = 12.sp)
            if (state.emailSent20) Text("· 20:00 首封邮件已发送", fontSize = 12.sp)
            if (state.emailSent22) Text("· 22:00 第二封邮件已发送", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreBreakdownCard(state: DayState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日分数",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${state.score} 分",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.score >= state.passiveThreshold)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ScoreRow(
                label = stringResource(R.string.score_unlock),
                count = state.unlockCount,
                delta = ScoreType.UNLOCK.delta
            )
            ScoreRow(
                label = stringResource(R.string.score_screen_locked),
                count = if (state.screenLockedBonusAdded) 1 else 0,
                delta = ScoreType.SCREEN_LOCKED.delta,
                extra = "累计 ${state.screenOnLockedMs / 1000}s / 120s"
            )
            ScoreRow(
                label = stringResource(R.string.score_screen_unlocked),
                count = if (state.screenUnlockedBonusAdded) 1 else 0,
                delta = ScoreType.SCREEN_UNLOCKED.delta,
                extra = "累计 ${state.screenOnUnlockedMs / 1000}s / 1800s"
            )
            ScoreRow(
                label = stringResource(R.string.score_foreground),
                count = state.foregroundAppChanges,
                delta = ScoreType.FOREGROUND_APP.delta
            )
            ScoreRow(
                label = stringResource(R.string.score_power),
                count = state.powerEvents,
                delta = ScoreType.POWER.delta
            )
            ScoreRow(
                label = stringResource(R.string.score_mobile_data),
                count = state.mobileDataToggles,
                delta = ScoreType.MOBILE_DATA.delta
            )
            ScoreRow(
                label = stringResource(R.string.score_flip),
                count = state.flipCount,
                delta = ScoreType.FLIP.delta
            )
        }
    }
}

@Composable
private fun ScoreRow(
    label: String,
    count: Int,
    delta: Int,
    extra: String? = null
) {
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
                    if (count > 0) MaterialTheme.colorScheme.primary else Color.LightGray,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (count > 0) Icons.Filled.CheckCircle else Icons.Filled.Email,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (count > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (extra != null) {
                Text(
                    text = extra,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${count * delta} 分",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (count > 0) MaterialTheme.colorScheme.primary
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
        ),
        shape = RoundedCornerShape(12.dp)
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
                    cfg.to.isBlank() || cfg.host.isBlank() || cfg.pass.isBlank() -> "配置不完整"
                    state.emailSent20 || state.emailSent22 || state.emailSent -> "今日已发送 ✓"
                    else -> "监控中，未签到时 20:00 起发送"
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
            if (cfg.enabled && cfg.host.isNotBlank() && cfg.user.isNotBlank() && cfg.pass.isNotBlank()) {
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
