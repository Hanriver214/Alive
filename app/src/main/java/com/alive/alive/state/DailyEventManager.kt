package com.alive.alive.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alive.alive.data.EventLog
import com.alive.alive.data.EventLogDao
import com.alive.alive.util.NotificationHelper
import com.alive.alive.util.SystemTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dayStateDataStore by preferencesDataStore(name = "alive_day_state")

/**
 * 计分项类型。每种用户行为对应一个计分动作。
 *
 * - [UNLOCK]                解锁一次 +2
 * - [SCREEN_LOCKED]         未解锁亮屏累计 ≥2 分钟 +1（一次性）
 * - [SCREEN_UNLOCKED]       解锁后亮屏累计 ≥30 分钟 +1（一次性）
 * - [SCREEN_UNLOCKED_60]    解锁后亮屏累计 ≥60 分钟 +2（一次性，与 SCREEN_UNLOCKED 累计不独立）
 * - [FOREGROUND_APP]        前台应用变化/切换 +1
 * - [POWER]                 亮屏或锁屏充电/拔电 +2
 * - [MOBILE_DATA]           移动数据开关变化 +1
 * - [FLIP]                  手机翻转 +1
 */
enum class ScoreType(val logTag: String, val delta: Int) {
    UNLOCK("SCORE_UNLOCK", 2),
    SCREEN_LOCKED("SCORE_SCREEN_LOCKED", 1),
    SCREEN_UNLOCKED("SCORE_SCREEN_UNLOCKED", 1),
    SCREEN_UNLOCKED_60("SCORE_SCREEN_UNLOCKED_60", 2),
    FOREGROUND_APP("SCORE_FOREGROUND", 1),
    POWER("SCORE_POWER", 2),
    MOBILE_DATA("SCORE_MOBILE_DATA", 1),
    FLIP("SCORE_FLIP", 1)
}

data class DayState(
    val dayKey: String,
    /** 解锁次数。 */
    val unlockCount: Int = 0,
    /** 未解锁亮屏累计毫秒。 */
    val screenOnLockedMs: Long = 0L,
    /** 解锁后亮屏累计毫秒。 */
    val screenOnUnlockedMs: Long = 0L,
    /** 未解锁亮屏 ≥2 分钟的 +1 分是否已计入。 */
    val screenLockedBonusAdded: Boolean = false,
    /** 解锁后亮屏 ≥30 分钟的 +1 分是否已计入。 */
    val screenUnlockedBonusAdded: Boolean = false,
    /** 解锁后亮屏 ≥60 分钟的 +2 分是否已计入。 */
    val screenUnlocked60BonusAdded: Boolean = false,
    /** 前台应用变化次数。 */
    val foregroundAppChanges: Int = 0,
    /** 充电/拔电次数。 */
    val powerEvents: Int = 0,
    /** 移动数据开关变化次数。 */
    val mobileDataToggles: Int = 0,
    /** 手机翻转次数。 */
    val flipCount: Int = 0,
    val passiveCheckIn: Boolean = false,
    val activeCheckIn: Boolean = false,
    val careNotificationShown12: Boolean = false,
    val careNotificationShown18: Boolean = false,
    val emailSent20: Boolean = false,
    val emailSent22: Boolean = false,
    val emailSent: Boolean = false
) {
    /** 是否当日已签到（被动或主动）。 */
    val checkedIn: Boolean get() = passiveCheckIn || activeCheckIn

    /** 当前总分数（≥4 触发被动签到）。 */
    val score: Int
        get() = unlockCount * ScoreType.UNLOCK.delta +
            (if (screenLockedBonusAdded) ScoreType.SCREEN_LOCKED.delta else 0) +
            (if (screenUnlockedBonusAdded) ScoreType.SCREEN_UNLOCKED.delta else 0) +
            (if (screenUnlocked60BonusAdded) ScoreType.SCREEN_UNLOCKED_60.delta else 0) +
            foregroundAppChanges * ScoreType.FOREGROUND_APP.delta +
            powerEvents * ScoreType.POWER.delta +
            mobileDataToggles * ScoreType.MOBILE_DATA.delta +
            flipCount * ScoreType.FLIP.delta

    /** 触发被动签到的分数阈值。 */
    val passiveThreshold: Int = 4
}

/**
 * 当日状态中枢（计分制）：
 *  - 系统时区 0:00 由 [com.alive.alive.alarm.AlarmReceiver] 调用 [resetIfNewDay] 触发重置
 *  - 各行为 Observer / Receiver 调用 [addScore] 累加分数
 *  - 分数 ≥ 4 自动激活被动签到
 *  - 主动签到由通知按钮或应用内按钮触发
 */
class DailyEventManager(
    private val context: Context,
    private val dao: EventLogDao
) {
    private object Keys {
        val DAY = stringPreferencesKey("day")
        val UNLOCK_COUNT = intPreferencesKey("unlock_count")
        val SCREEN_LOCKED_MS = longPreferencesKey("screen_on_locked_ms")
        val SCREEN_UNLOCKED_MS = longPreferencesKey("screen_on_unlocked_ms")
        val SCREEN_LOCKED_BONUS = booleanPreferencesKey("screen_locked_bonus")
        val SCREEN_UNLOCKED_BONUS = booleanPreferencesKey("screen_unlocked_bonus")
        val SCREEN_UNLOCKED_60_BONUS = booleanPreferencesKey("screen_unlocked_60_bonus")
        val FOREGROUND_CHANGES = intPreferencesKey("foreground_changes")
        val POWER_EVENTS = intPreferencesKey("power_events")
        val MOBILE_DATA_TOGGLES = intPreferencesKey("mobile_data_toggles")
        val FLIP_COUNT = intPreferencesKey("flip_count")
        val PASSIVE = booleanPreferencesKey("passive")
        val ACTIVE = booleanPreferencesKey("active")
        val CARE_SHOWN_12 = booleanPreferencesKey("care_shown_12")
        val CARE_SHOWN_18 = booleanPreferencesKey("care_shown_18")
        val EMAIL_SENT_20 = booleanPreferencesKey("email_sent_20")
        val EMAIL_SENT_22 = booleanPreferencesKey("email_sent_22")
        val EMAIL_SENT = booleanPreferencesKey("email_sent")
    }

    val stateFlow: Flow<DayState> = context.dayStateDataStore.data.map { p ->
        DayState(
            dayKey = p[Keys.DAY] ?: "",
            unlockCount = p[Keys.UNLOCK_COUNT] ?: 0,
            screenOnLockedMs = p[Keys.SCREEN_LOCKED_MS] ?: 0L,
            screenOnUnlockedMs = p[Keys.SCREEN_UNLOCKED_MS] ?: 0L,
            screenLockedBonusAdded = p[Keys.SCREEN_LOCKED_BONUS] ?: false,
            screenUnlockedBonusAdded = p[Keys.SCREEN_UNLOCKED_BONUS] ?: false,
            screenUnlocked60BonusAdded = p[Keys.SCREEN_UNLOCKED_60_BONUS] ?: false,
            foregroundAppChanges = p[Keys.FOREGROUND_CHANGES] ?: 0,
            powerEvents = p[Keys.POWER_EVENTS] ?: 0,
            mobileDataToggles = p[Keys.MOBILE_DATA_TOGGLES] ?: 0,
            flipCount = p[Keys.FLIP_COUNT] ?: 0,
            passiveCheckIn = p[Keys.PASSIVE] ?: false,
            activeCheckIn = p[Keys.ACTIVE] ?: false,
            careNotificationShown12 = p[Keys.CARE_SHOWN_12] ?: false,
            careNotificationShown18 = p[Keys.CARE_SHOWN_18] ?: false,
            emailSent20 = p[Keys.EMAIL_SENT_20] ?: false,
            emailSent22 = p[Keys.EMAIL_SENT_22] ?: false,
            emailSent = p[Keys.EMAIL_SENT] ?: false
        )
    }

    suspend fun current(): DayState = stateFlow.first()

    /**
     * 若 DataStore 里的日期 != 今日（系统时区），重置全部状态。
     * 用于 0:00 闹钟、设备启动、应用启动。
     */
    suspend fun resetIfNewDay(): Boolean {
        val today = SystemTime.today().toString()
        val cur = current()
        if (cur.dayKey != today) {
            context.dayStateDataStore.edit { p ->
                p[Keys.DAY] = today
                p[Keys.UNLOCK_COUNT] = 0
                p[Keys.SCREEN_LOCKED_MS] = 0L
                p[Keys.SCREEN_UNLOCKED_MS] = 0L
                p[Keys.SCREEN_LOCKED_BONUS] = false
                p[Keys.SCREEN_UNLOCKED_BONUS] = false
                p[Keys.SCREEN_UNLOCKED_60_BONUS] = false
                p[Keys.FOREGROUND_CHANGES] = 0
                p[Keys.POWER_EVENTS] = 0
                p[Keys.MOBILE_DATA_TOGGLES] = 0
                p[Keys.FLIP_COUNT] = 0
                p[Keys.PASSIVE] = false
                p[Keys.ACTIVE] = false
                p[Keys.CARE_SHOWN_12] = false
                p[Keys.CARE_SHOWN_18] = false
                p[Keys.EMAIL_SENT_20] = false
                p[Keys.EMAIL_SENT_22] = false
                p[Keys.EMAIL_SENT] = false
            }
            log("RESET", "当日状态已重置，新一天=$today")
            NotificationHelper.cancelCare(context)
            return true
        }
        return false
    }

    /**
     * 为 [type] 加一次分。返回 true 表示本次加分成功（未签到且未触发被动签到前）。
     * 若今日已签到则忽略加分，但仍会写日志。
     */
    suspend fun addScore(type: ScoreType, detail: String = ""): Boolean {
        if (current().checkedIn) {
            log(type.logTag, "已签到，忽略计分。$detail")
            return false
        }
        ensureSameDay()
        context.dayStateDataStore.edit { p ->
            when (type) {
                ScoreType.UNLOCK -> p[Keys.UNLOCK_COUNT] = (p[Keys.UNLOCK_COUNT] ?: 0) + 1
                ScoreType.SCREEN_LOCKED -> p[Keys.SCREEN_LOCKED_BONUS] = true
                ScoreType.SCREEN_UNLOCKED -> p[Keys.SCREEN_UNLOCKED_BONUS] = true
                ScoreType.SCREEN_UNLOCKED_60 -> p[Keys.SCREEN_UNLOCKED_60_BONUS] = true
                ScoreType.FOREGROUND_APP -> p[Keys.FOREGROUND_CHANGES] = (p[Keys.FOREGROUND_CHANGES] ?: 0) + 1
                ScoreType.POWER -> p[Keys.POWER_EVENTS] = (p[Keys.POWER_EVENTS] ?: 0) + 1
                ScoreType.MOBILE_DATA -> p[Keys.MOBILE_DATA_TOGGLES] = (p[Keys.MOBILE_DATA_TOGGLES] ?: 0) + 1
                ScoreType.FLIP -> p[Keys.FLIP_COUNT] = (p[Keys.FLIP_COUNT] ?: 0) + 1
            }
        }
        log(type.logTag, "$detail (+${type.delta})")
        val after = current()
        if (!after.checkedIn && after.score >= after.passiveThreshold) {
            triggerPassiveCheckIn("分数达 ${after.score} ≥ ${after.passiveThreshold}，被动签到")
        }
        return true
    }

    /**
     * 累加未解锁亮屏时长，并在达到阈值（2 分钟）时一次性 +1。
     */
    suspend fun addScreenOnLockedMs(deltaMs: Long) {
        if (current().checkedIn) return
        if (deltaMs <= 0) return
        ensureSameDay()
        val bonusAlready = current().screenLockedBonusAdded
        context.dayStateDataStore.edit { p ->
            val newTotal = (p[Keys.SCREEN_LOCKED_MS] ?: 0L) + deltaMs
            p[Keys.SCREEN_LOCKED_MS] = newTotal
            if (!bonusAlready && newTotal >= SCREEN_LOCKED_THRESHOLD_MS) {
                p[Keys.SCREEN_LOCKED_BONUS] = true
            }
        }
        val after = current()
        if (after.screenLockedBonusAdded && !bonusAlready) {
            log(ScoreType.SCREEN_LOCKED.logTag, "未解锁亮屏累计 ${after.screenOnLockedMs} ms ≥ 2分钟 (+1)")
            if (!after.checkedIn && after.score >= after.passiveThreshold) {
                triggerPassiveCheckIn("分数达 ${after.score} ≥ ${after.passiveThreshold}，被动签到")
            }
        }
    }

    /**
     * 累加解锁后亮屏时长，并在达到阈值（30 分钟 / 60 分钟）时一次性 +1 / +2。
     * 两个阈值共享同一个累计时长，不是独立计算。
     */
    suspend fun addScreenOnUnlockedMs(deltaMs: Long) {
        if (current().checkedIn) return
        if (deltaMs <= 0) return
        ensureSameDay()
        val bonus30Already = current().screenUnlockedBonusAdded
        val bonus60Already = current().screenUnlocked60BonusAdded
        context.dayStateDataStore.edit { p ->
            val newTotal = (p[Keys.SCREEN_UNLOCKED_MS] ?: 0L) + deltaMs
            p[Keys.SCREEN_UNLOCKED_MS] = newTotal
            if (!bonus30Already && newTotal >= SCREEN_UNLOCKED_THRESHOLD_MS) {
                p[Keys.SCREEN_UNLOCKED_BONUS] = true
            }
            if (!bonus60Already && newTotal >= SCREEN_UNLOCKED_60_THRESHOLD_MS) {
                p[Keys.SCREEN_UNLOCKED_60_BONUS] = true
            }
        }
        val after = current()
        if (after.screenUnlockedBonusAdded && !bonus30Already) {
            log(ScoreType.SCREEN_UNLOCKED.logTag, "解锁后亮屏累计 ${after.screenOnUnlockedMs} ms ≥ 30分钟 (+1)")
            if (!after.checkedIn && after.score >= after.passiveThreshold) {
                triggerPassiveCheckIn("分数达 ${after.score} ≥ ${after.passiveThreshold}，被动签到")
            }
        }
        if (after.screenUnlocked60BonusAdded && !bonus60Already) {
            log(ScoreType.SCREEN_UNLOCKED_60.logTag, "解锁后亮屏累计 ${after.screenOnUnlockedMs} ms ≥ 60分钟 (+2)")
            if (!after.checkedIn && after.score >= after.passiveThreshold) {
                triggerPassiveCheckIn("分数达 ${after.score} ≥ ${after.passiveThreshold}，被动签到")
            }
        }
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

    suspend fun markCareNotificationShown(slot: Int) {
        when (slot) {
            12 -> {
                context.dayStateDataStore.edit { p -> p[Keys.CARE_SHOWN_12] = true }
                log("CARE_NOTIF_12", "12:00 关怀通知已推送")
            }
            18 -> {
                context.dayStateDataStore.edit { p -> p[Keys.CARE_SHOWN_18] = true }
                log("CARE_NOTIF_18", "18:00 二次提醒已推送")
            }
        }
    }

    suspend fun markEmailSent(slot: Int, success: Boolean, detail: String = "") {
        if (success) {
            context.dayStateDataStore.edit { p ->
                when (slot) {
                    20 -> p[Keys.EMAIL_SENT_20] = true
                    22 -> p[Keys.EMAIL_SENT_22] = true
                    else -> p[Keys.EMAIL_SENT] = true
                }
            }
            log("EMAIL_SENT" + if (slot != 0) "_$slot" else "", detail)
        } else {
            log("EMAIL_FAILED" + if (slot != 0) "_$slot" else "", detail)
        }
    }

    suspend fun isCareEligible(slot: Int): Boolean {
        val s = current()
        return when (slot) {
            12 -> !s.checkedIn && !s.careNotificationShown12
            18 -> !s.checkedIn && !s.careNotificationShown18
            else -> false
        }
    }

    suspend fun isEmailEligible(slot: Int): Boolean {
        val s = current()
        return when (slot) {
            20 -> !s.checkedIn && !s.emailSent20
            22 -> !s.checkedIn && !s.emailSent22
            else -> !s.checkedIn && !s.emailSent
        }
    }

    /**
     * 确保 DataStore 里的 dayKey 仍是今天；若跨天则先重置。
     * 在任意加分前调用，避免在新一天却写入昨天的计数。
     */
    private suspend fun ensureSameDay() {
        val today = SystemTime.today().toString()
        if (current().dayKey != today) {
            resetIfNewDay()
        }
    }

    private suspend fun log(type: String, detail: String) {
        val now = System.currentTimeMillis()
        dao.insert(
            EventLog(
                timestamp = now,
                dayKey = SystemTime.today().toString(),
                eventType = type,
                detail = detail
            )
        )
    }

    companion object {
        /** 未解锁亮屏 +1 的阈值：2 分钟。 */
        const val SCREEN_LOCKED_THRESHOLD_MS = 2L * 60_000L

        /** 解锁后亮屏 +1 的阈值：30 分钟。 */
        const val SCREEN_UNLOCKED_THRESHOLD_MS = 30L * 60_000L

        /** 解锁后亮屏 +2 的阈值：60 分钟。 */
        const val SCREEN_UNLOCKED_60_THRESHOLD_MS = 60L * 60_000L
    }
}
