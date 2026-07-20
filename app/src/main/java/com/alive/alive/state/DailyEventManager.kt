package com.alive.alive.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alive.alive.data.EventLog
import com.alive.alive.data.EventLogDao
import com.alive.alive.util.BeijingTime
import com.alive.alive.util.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dayStateDataStore by preferencesDataStore(name = "alive_day_state")

enum class AliveEvent(val logTag: String) {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F")
}

data class DayState(
    val dayKey: String,
    val eventA: Boolean = false,
    val eventB: Boolean = false,
    val eventC: Boolean = false,
    val eventD: Boolean = false,
    val eventE: Boolean = false,
    val eventF: Boolean = false,
    val passiveCheckIn: Boolean = false,
    val activeCheckIn: Boolean = false,
    val careNotificationShown: Boolean = false,
    val emailSent: Boolean = false
) {
    /** 是否当日已签到（被动或主动）。 */
    val checkedIn: Boolean get() = passiveCheckIn || activeCheckIn

    /** 已标记的事件数量。 */
    val markedCount: Int get() = listOf(eventA, eventB, eventC, eventD, eventE, eventF).count { it }
}

/**
 * 当日状态中枢：
 *  - 北京时间 0:00 由 [AlarmReceiver] 调用 [resetIfNewDay] 触发重置
 *  - 各事件 BroadcastReceiver 调用 [markEvent]
 *  - 任意 2/3 事件标记后自动激活被动签到，当日停止监测
 *  - 主动签到由通知按钮触发
 */
class DailyEventManager(
    private val context: Context,
    private val dao: EventLogDao
) {
    private object Keys {
        val DAY = stringPreferencesKey("day")
        val A = booleanPreferencesKey("a")
        val B = booleanPreferencesKey("b")
        val C = booleanPreferencesKey("c")
        val D = booleanPreferencesKey("d")
        val E = booleanPreferencesKey("e")
        val F = booleanPreferencesKey("f")
        val PASSIVE = booleanPreferencesKey("passive")
        val ACTIVE = booleanPreferencesKey("active")
        val CARE_SHOWN = booleanPreferencesKey("care_shown")
        val EMAIL_SENT = booleanPreferencesKey("email_sent")
    }

    val stateFlow: Flow<DayState> = context.dayStateDataStore.data.map { p ->
        DayState(
            dayKey = p[Keys.DAY] ?: "",
            eventA = p[Keys.A] ?: false,
            eventB = p[Keys.B] ?: false,
            eventC = p[Keys.C] ?: false,
            eventD = p[Keys.D] ?: false,
            eventE = p[Keys.E] ?: false,
            eventF = p[Keys.F] ?: false,
            passiveCheckIn = p[Keys.PASSIVE] ?: false,
            activeCheckIn = p[Keys.ACTIVE] ?: false,
            careNotificationShown = p[Keys.CARE_SHOWN] ?: false,
            emailSent = p[Keys.EMAIL_SENT] ?: false
        )
    }

    suspend fun current(): DayState = stateFlow.first()

    /**
     * 若 DataStore 里的日期 != 今日（北京时间），重置全部状态。
     * 用于 0:00 闹钟、设备启动、应用启动。
     */
    suspend fun resetIfNewDay(): Boolean {
        val today = BeijingTime.today().toString()
        val cur = current()
        if (cur.dayKey != today) {
            context.dayStateDataStore.edit { p ->
                p[Keys.DAY] = today
                p[Keys.A] = false
                p[Keys.B] = false
                p[Keys.C] = false
                p[Keys.D] = false
                p[Keys.E] = false
                p[Keys.F] = false
                p[Keys.PASSIVE] = false
                p[Keys.ACTIVE] = false
                p[Keys.CARE_SHOWN] = false
                p[Keys.EMAIL_SENT] = false
            }
            log("RESET", "当日状态已重置，新一天=$today")
            NotificationHelper.cancelCare(context)
            return true
        }
        return false
    }

    /**
     * 标记一个事件。返回 true 表示标记成功且本事件首次出现。
     * 若今日已签到则忽略标记，但仍会写日志。
     */
    suspend fun markEvent(event: AliveEvent, detail: String = ""): Boolean {
        if (current().checkedIn) {
            // 已签到，仅记录日志，不再修改标记
            log(event.logTag, "已签到，忽略事件标记。$detail")
            return false
        }
        val firstTime = when (event) {
                AliveEvent.A -> !current().eventA
                AliveEvent.B -> !current().eventB
                AliveEvent.C -> !current().eventC
                AliveEvent.D -> !current().eventD
                AliveEvent.E -> !current().eventE
                AliveEvent.F -> !current().eventF
            }
            context.dayStateDataStore.edit { p ->
                if (p[Keys.DAY] != BeijingTime.today().toString()) {
                    p[Keys.DAY] = BeijingTime.today().toString()
                    p[Keys.A] = false
                    p[Keys.B] = false
                    p[Keys.C] = false
                    p[Keys.D] = false
                    p[Keys.E] = false
                    p[Keys.F] = false
                    p[Keys.PASSIVE] = false
                    p[Keys.ACTIVE] = false
                    p[Keys.CARE_SHOWN] = false
                    p[Keys.EMAIL_SENT] = false
                }
                when (event) {
                    AliveEvent.A -> p[Keys.A] = true
                    AliveEvent.B -> p[Keys.B] = true
                    AliveEvent.C -> p[Keys.C] = true
                    AliveEvent.D -> p[Keys.D] = true
                    AliveEvent.E -> p[Keys.E] = true
                    AliveEvent.F -> p[Keys.F] = true
                }
            }
        log(event.logTag, detail)
        // 检查是否达成 2/3
        val after = current()
        if (!after.checkedIn && after.markedCount >= 2) {
            triggerPassiveCheckIn("达成 2/3 事件标记：A=${after.eventA} B=${after.eventB} C=${after.eventC}")
        }
        return firstTime
    }

    suspend fun triggerPassiveCheckIn(detail: String = "") {
        context.dayStateDataStore.edit { p -> p[Keys.PASSIVE] = true }
        log("PASSIVE_CHECKIN", detail)
        NotificationHelper.cancelCare(context)
        NotificationHelper.showCheckInResult(
            context,
            context.getString(com.alive.alive.R.string.passive_checkin_title),
            context.getString(com.alive.alive.R.string.passive_checkin_text)
        )
    }

    suspend fun triggerActiveCheckIn(detail: String = "") {
        context.dayStateDataStore.edit { p -> p[Keys.ACTIVE] = true }
        log("ACTIVE_CHECKIN", detail)
        NotificationHelper.cancelCare(context)
        NotificationHelper.showCheckInResult(
            context,
            context.getString(com.alive.alive.R.string.active_checkin_title),
            context.getString(com.alive.alive.R.string.active_checkin_text)
        )
    }

    suspend fun markCareNotificationShown() {
        context.dayStateDataStore.edit { p -> p[Keys.CARE_SHOWN] = true }
        log("CARE_NOTIF", "12:00 关怀通知已推送")
    }

    suspend fun markEmailSent(success: Boolean, detail: String = "") {
        if (success) {
            context.dayStateDataStore.edit { p -> p[Keys.EMAIL_SENT] = true }
            log("EMAIL_SENT", detail)
        } else {
            log("EMAIL_FAILED", detail)
        }
    }

    suspend fun isCareEligible(): Boolean {
        val s = current()
        // 仅当 12:00 仍无签到且未推送过关怀通知
        return !s.checkedIn && !s.careNotificationShown
    }

    suspend fun isEmailEligible(): Boolean {
        val s = current()
        // 23:59 仍未签到且今日尚未发过邮件
        return !s.checkedIn && !s.emailSent
    }

    private suspend fun log(type: String, detail: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            EventLog(
                timestamp = now,
                dayKey = BeijingTime.today().toString(),
                eventType = type,
                detail = detail
            )
        )
    }
}
