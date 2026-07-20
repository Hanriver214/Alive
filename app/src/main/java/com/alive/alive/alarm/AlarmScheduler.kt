package com.alive.alive.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alive.alive.util.BeijingTime

/**
 * 北京时间三个关键时间点的精准闹钟：
 *  - 0:00 重置当日状态 + 取消邮件重发任务
 *  - 12:00 推送「你还好吗？」关怀通知（若仍未签到）
 *  - 23:59 发送首封 SMTP 邮件 + 启动 N 小时周期重发
 *
 * 用 setExactAndAllowWhileIdle 让闹钟在 Doze 下也能触发。
 * 闹钟一次性触发后由 AlarmReceiver 在内部重排次日同一时刻。
 */
class AlarmScheduler(private val context: Context) {

    private val am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll() {
        scheduleReset()
        scheduleCare()
        scheduleEmailDaily()
    }

    fun scheduleReset() {
        val triggerAt = BeijingTime.startOfTomorrow() // 明日 0:00
        setExact(triggerAt, ACTION_RESET, REQUEST_RESET)
    }

    fun scheduleCare() {
        val today = BeijingTime.today()
        val now = BeijingTime.now().toInstant().toEpochMilli()
        var triggerAt = BeijingTime.epochSecondOfDay(today, 12, 0)
        if (triggerAt <= now) {
            // 今天 12:00 已过，排到明天
            triggerAt = BeijingTime.epochSecondOfDay(today.plusDays(1), 12, 0)
        }
        setExact(triggerAt, ACTION_CARE, REQUEST_CARE)
    }

    fun scheduleEmailDaily() {
        val today = BeijingTime.today()
        val now = BeijingTime.now().toInstant().toEpochMilli()
        var triggerAt = BeijingTime.epochSecondOfDay(today, 23, 59)
        if (triggerAt <= now) {
            triggerAt = BeijingTime.epochSecondOfDay(today.plusDays(1), 23, 59)
        }
        setExact(triggerAt, ACTION_EMAIL_DAILY, REQUEST_EMAIL_DAILY)
    }

    fun cancelAll() {
        cancel(ACTION_RESET, REQUEST_RESET)
        cancel(ACTION_CARE, REQUEST_CARE)
        cancel(ACTION_EMAIL_DAILY, REQUEST_EMAIL_DAILY)
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
        const val ACTION_CARE = "com.alive.alive.alarm.CARE"
        const val ACTION_EMAIL_DAILY = "com.alive.alive.alarm.EMAIL_DAILY"
        private const val REQUEST_RESET = 10
        private const val REQUEST_CARE = 11
        private const val REQUEST_EMAIL_DAILY = 12
    }
}
