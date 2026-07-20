package com.alive.alive.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.mail.SmtpMailer
import com.alive.alive.state.DailyEventManager
import com.alive.alive.util.NotificationHelper
import com.alive.alive.work.EmailRetryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 三种精准闹钟的统一接收器：
 *  - RESET      0:00 重置当日，取消邮件重发周期任务，重排下一日所有闹钟
 *  - CARE       12:00 仍未签到则推送常驻通知
 *  - EMAIL_DAILY 23:59 仍未签到则发首封邮件，并启动 N 小时周期重发
 *
 * 处理完成后立即把同一闹钟排到次日同一时刻，保证每日循环。
 */
class AlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val scheduler = AlarmScheduler(context)
        val mgr = DailyEventManager(
            context.applicationContext,
            AliveDatabase.getInstance(context).eventLogDao()
        )
        when (action) {
            AlarmScheduler.ACTION_RESET -> {
                scope.launch {
                    mgr.resetIfNewDay()
                    WorkManager.getInstance(context).cancelUniqueWork(EmailRetryWorker.WORK_NAME)
                    // 重排次日三个闹钟
                    scheduler.scheduleReset()
                    scheduler.scheduleCare()
                    scheduler.scheduleEmailDaily()
                }
            }
            AlarmScheduler.ACTION_CARE -> {
                scope.launch {
                    if (mgr.isCareEligible()) {
                        NotificationHelper.showCare(context)
                        mgr.markCareNotificationShown()
                    }
                    scheduler.scheduleCare()
                }
            }
            AlarmScheduler.ACTION_EMAIL_DAILY -> {
                scope.launch {
                    val s = mgr.current()
                    if (!s.checkedIn) {
                        sendEmailOnce(context, mgr, "23:59 首封提醒")
                        startEmailRetryPeriodic(context)
                    }
                    scheduler.scheduleEmailDaily()
                }
            }
        }
    }

    private suspend fun sendEmailOnce(
        context: Context,
        mgr: DailyEventManager,
        reason: String
    ) {
        val app = context.applicationContext as AliveApp
        val cfg = app.settingsRepo.current()
        if (!cfg.enabled) {
            mgr.markEmailSent(false, "邮件通知未启用，跳过发送 ($reason)")
            return
        }
        val result = SmtpMailer.send(cfg)
        result.onSuccess {
            mgr.markEmailSent(true, "邮件发送成功 ($reason) → ${cfg.to}")
        }.onFailure { e ->
            mgr.markEmailSent(false, "邮件发送失败 ($reason): ${e.message}")
        }
    }

    private fun startEmailRetryPeriodic(context: Context) {
        // 启动后立即用一次性任务做一次（首封），然后周期 N 小时
        // 此处仅启动周期任务，首封已由 EMAIL_DAILY 闹钟触发
        val app = context.applicationContext as AliveApp
        scope.launch {
            val cfg = app.settingsRepo.configFlow.first()
            val intervalHours = cfg.resendHours.coerceIn(1, 24).toLong()
            val req = PeriodicWorkRequestBuilder<EmailRetryWorker>(intervalHours, TimeUnit.HOURS)
                .addTag(EmailRetryWorker.WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                EmailRetryWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
            Log.i("Alive/Alarm", "EmailRetryWorker scheduled every $intervalHours h")
        }
    }
}
