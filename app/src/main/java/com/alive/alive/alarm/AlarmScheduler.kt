package com.alive.alive.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alive.alive.util.SystemTime

/**
 * 系统时区下五个关键时间点的精准闹钟：
 *
 *  - 0:00 RESET           重置当日状态 + 取消邮件重发任务
 *  - 12:00 CARE_12        推送「你还好吗？」关怀通知（若仍未签到）
 *  - 18:00 CARE_18        若仍未签到，再次提醒
 *  - 20:00 EMAIL_20       若仍未主动签到，发送首封邮件
 *  - 22:00 EMAIL_22       仍未签到，发送第二封邮件 / 更强提醒
 *
 * 次日继续按邮件重发间隔（[com.alive.alive.data.SmtpConfig.resendHours]）提醒，
 * 由 [com.alive.alive.work.EmailRetryWorker] 周期任务承担。
 *
 * 用 setExactAndAllowWhileIdle 让闹钟在 Doze 下也能触发。
 * 闹钟一次性触发后由 AlarmReceiver 在内部重排次日同一时刻。
 */
class AlarmScheduler(private val context: Context) {

    private val am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll() {
        scheduleReset()
        scheduleCare12()
        scheduleCare18()
        scheduleEmail20()
        scheduleEmail22()
    }

    fun scheduleReset() {
        val triggerAt = SystemTime.startOfTomorrow() // 明日 0:00
        setExact(triggerAt, ACTION_RESET, REQUEST_RESET)
    }

    fun scheduleCare12() {
        setNextOccurrence(12, 0, ACTION_CARE_12, REQUEST_CARE_12)
    }

    fun scheduleCare18() {
        setNextOccurrence(18, 0, ACTION_CARE_18, REQUEST_CARE_18)
    }

    fun scheduleEmail20() {
        setNextOccurrence(20, 0, ACTION_EMAIL_20, REQUEST_EMAIL_20)
    }

    fun scheduleEmail22() {
        setNextOccurrence(22, 0, ACTION_EMAIL_22, REQUEST_EMAIL_22)
    }

    fun cancelAll() {
        cancel(ACTION_RESET, REQUEST_RESET)
        cancel(ACTION_CARE_12, REQUEST_CARE_12)
        cancel(ACTION_CARE_18, REQUEST_CARE_18)
        cancel(ACTION_EMAIL_20, REQUEST_EMAIL_20)
        cancel(ACTION_EMAIL_22, REQUEST_EMAIL_22)
    }

    /** 排定今天 [hour]:[minute]，若已过则排到明天同一时刻。 */
    private fun setNextOccurrence(hour: Int, minute: Int, action: String, requestCode: Int) {
        val today = SystemTime.today()
        val now = SystemTime.now().toInstant().toEpochMilli()
        var triggerAt = SystemTime.epochSecondOfDay(today, hour, minute)
        if (triggerAt <= now) {
            triggerAt = SystemTime.epochSecondOfDay(today.plusDays(1), hour, minute)
        }
        setExact(triggerAt, action, requestCode)
    }

    private fun setExact(triggerAt: Long, action: String, requestCode: Int) {
        val pi = buildPendingIntent(action, requestCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // 没有精准闹钟权限时退化为非精准，仍可触发，只是可能延迟几分钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancel(action: String, requestCode: Int) {
        am.cancel(buildPendingIntent(action, requestCode))
    }

    private fun buildPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val ACTION_RESET = "com.alive.alive.alarm.RESET"
        const val ACTION_CARE_12 = "com.alive.alive.alarm.CARE_12"
        const val ACTION_CARE_18 = "com.alive.alive.alarm.CARE_18"
        const val ACTION_EMAIL_20 = "com.alive.alive.alarm.EMAIL_20"
        const val ACTION_EMAIL_22 = "com.alive.alive.alarm.EMAIL_22"
        private const val REQUEST_RESET = 10
        private const val REQUEST_CARE_12 = 11
        private const val REQUEST_CARE_18 = 12
        private const val REQUEST_EMAIL_20 = 13
        private const val REQUEST_EMAIL_22 = 14
    }
}
