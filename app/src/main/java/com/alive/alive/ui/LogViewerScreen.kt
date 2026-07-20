package com.alive.alive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alive.alive.data.EventLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogViewerScreen(
    viewModel: MainViewModel
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "日志 (${logs.size})",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.clearLogs() }) {
                Text("清空")
            }
        }

        if (logs.isEmpty()) {
            Text(
                text = "暂无日志",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: EventLog) {
    val time = Instant.ofEpochMilli(log.timestamp)
        .atZone(ZoneId.of("Asia/Shanghai"))
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.eventType,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = typeColor(log.eventType),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (log.detail.isNotBlank()) {
                Text(
                    text = log.detail,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun typeColor(type: String): androidx.compose.ui.graphics.Color = when (type) {
    "A", "B", "C" -> androidx.compose.ui.graphics.Color(0xFF1976D2)
    "PASSIVE_CHECKIN", "ACTIVE_CHECKIN" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    "CARE_NOTIF" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    "EMAIL_SENT" -> androidx.compose.ui.graphics.Color(0xFF8E24AA)
    "EMAIL_FAILED" -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
    "RESET" -> androidx.compose.ui.graphics.Color(0xFF607D8B)
    else -> androidx.compose.ui.graphics.Color(0xFF424242)
}
