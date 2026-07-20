package com.alive.alive.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.data.EventLog
import com.alive.alive.data.SmtpConfig
import com.alive.alive.state.DayState
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
}
