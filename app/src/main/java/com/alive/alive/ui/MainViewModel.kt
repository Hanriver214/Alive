package com.alive.alive.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.data.EventLog
import com.alive.alive.data.SmtpConfig
import com.alive.alive.mail.SmtpMailer
import com.alive.alive.state.DayState
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AliveDatabase.getInstance(app).eventLogDao()
    private val dayMgr = DailyEventManager(app, dao)
    private val settingsRepo = (app as AliveApp).settingsRepo

    val dayState: StateFlow<DayState> = dayMgr.stateFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, DayState("")
    )

    val config: StateFlow<SmtpConfig> = settingsRepo.configFlow.stateIn(
        viewModelScope, SharingStarted.Eagerly, SmtpConfig()
    )

    private val _logs = MutableStateFlow<List<EventLog>>(emptyList())
    val logs: StateFlow<List<EventLog>> = _logs.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _testMailResult = MutableStateFlow<String?>(null)
    val testMailResult: StateFlow<String?> = _testMailResult.asStateFlow()

    init {
        observeLogs()
        viewModelScope.launch { dayMgr.resetIfNewDay() }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            dao.observePage(500, 0).collect { _logs.value = it }
        }
    }

    fun saveConfig(cfg: SmtpConfig) {
        viewModelScope.launch { settingsRepo.save(cfg) }
    }

    fun clearLogs() {
        viewModelScope.launch { dao.clearAll() }
    }

    fun triggerActiveCheckIn() {
        viewModelScope.launch {
            dayMgr.triggerActiveCheckIn("用户从应用内点击主动签到")
        }
    }

    fun refresh() {
        viewModelScope.launch { dayMgr.resetIfNewDay() }
    }

    fun exportLogs() {
        viewModelScope.launch {
            val logs = dao.listAll()
            if (logs.isEmpty()) {
                _exportResult.value = "暂无日志可导出"
                return@launch
            }
            val ctx = getApplication<Application>()
            val csv = buildString {
                appendLine("timestamp,dayKey,eventType,detail")
                logs.forEach {
                    appendLine("${it.timestamp},${escapeCsv(it.dayKey)},${escapeCsv(it.eventType)},${escapeCsv(it.detail)}")
                }
            }
            val time = Instant.now().atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val file = File(ctx.cacheDir, "alive_logs_$time.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Alive 日志导出 $time")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "分享日志")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
            _exportResult.value = "已导出 ${logs.size} 条日志"
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    fun sendTestMail(cfg: SmtpConfig) {
        viewModelScope.launch {
            if (!cfg.enabled) {
                _testMailResult.value = "请先启用邮件通知"
                return@launch
            }
            if (cfg.user.isBlank() || cfg.pass.isBlank() || cfg.to.isBlank()) {
                _testMailResult.value = "请填写完整的邮箱信息"
                return@launch
            }
            _testMailResult.value = "正在发送…"
            val testCfg = cfg.copy(
                subject = "[Alive 测试] ${cfg.subject}",
                body = "这是一封测试邮件。\n\n${cfg.body}"
            )
            val result = SmtpMailer.send(testCfg)
            result.onSuccess {
                _testMailResult.value = "测试邮件发送成功"
            }.onFailure { e ->
                val hint = when {
                    e.message?.contains("EOF", true) == true && cfg.port == 587 ->
                        "\n提示：587 端口被网络拦截，建议切换为 465 端口重试"
                    e.message?.contains("EOF", true) == true ->
                        "\n提示：连接被重置，请检查网络或更换端口"
                    e.message?.contains("auth", true) == true || e.message?.contains("535", true) == true ->
                        "\n提示：认证失败，请确认应用密码/授权码正确"
                    else -> ""
                }
                _testMailResult.value = "发送失败: ${e.message}$hint"
            }
        }
    }

    fun diagnoseSmtp(cfg: SmtpConfig) {
        viewModelScope.launch {
            _testMailResult.value = "正在诊断网络…"
            val result = SmtpMailer.diagnose(cfg)
            _testMailResult.value = result.getOrElse { "诊断异常: ${it.message}" }
        }
    }

    fun sendAlertMail() {
        viewModelScope.launch {
            val cfg = settingsRepo.current()
            if (!cfg.enabled) {
                _testMailResult.value = "邮件通知未启用"
                return@launch
            }
            if (cfg.user.isBlank() || cfg.pass.isBlank() || cfg.to.isBlank()) {
                _testMailResult.value = "邮箱配置不完整"
                return@launch
            }
            val alertCfg = cfg.copy(
                subject = "[Alive 手动提醒] ${cfg.subject}",
                body = "这是用户手动触发的提醒邮件。\n\n${cfg.body}"
            )
            val result = SmtpMailer.send(alertCfg)
            result.onSuccess {
                _testMailResult.value = "提醒邮件已发送"
            }.onFailure { e ->
                _testMailResult.value = "发送失败: ${e.message}"
            }
        }
    }

    fun clearTestMailResult() {
        _testMailResult.value = null
    }

    private fun escapeCsv(s: String): String {
        val trimmed = s.trim()
        return if (trimmed.contains(",") || trimmed.contains("\"") || trimmed.contains("\n")) {
            "\"${trimmed.replace("\"", "\"\"")}\""
        } else {
            trimmed
        }
    }
}
