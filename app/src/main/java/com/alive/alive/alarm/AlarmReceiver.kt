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
 * 五个精准闹钟的统一接收器：
 *
 *  - RESET      0:00  重置当日，取消邮件重发周期任务，重排下一日所有闹钟
 *  - CARE_12    12:00 仍未签到则推送常驻通知「你还好吗？」
 *  - CARE_18    18:00 仍未签到则再次提醒
 *  - EMAIL_20   20:00 仍未主动签到则发首封邮件，并启动 N 小时周期重发
 *  - EMAIL_22   22:00 仍未签到则发第二封邮件 / 更强提醒
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
                    scheduler.scheduleAll()
                }
            }
            AlarmScheduler.ACTION_CARE_12 -> {
                scope.launch {
                    if (mgr.isCareEligible(12)) {
                        NotificationHelper.showCare(context, slot = 12)
                        mgr.markCareNotificationShown(12)
                    }
                    scheduler.scheduleCare12()
                }
            }
            AlarmScheduler.ACTION_CARE_18 -> {
                scope.launch {
                    if (mgr.isCareEligible(18)) {
                        NotificationHelper.showCare(context, slot = 18)
                        mgr.markCareNotificationShown(18)
                    }
                    scheduler.scheduleCare18()
                }
            }
            AlarmScheduler.ACTION_EMAIL_20 -> {
                scope.launch {
                    if (mgr.isEmailEligible(20)) {
                        sendEmailOnce(context, mgr, 20, "20:00 首封提醒")
                        startEmailRetryPeriodic(context)
                    }
                    scheduler.scheduleEmail20()
                }
            }
            AlarmScheduler.ACTION_EMAIL_22 -> {
                scope.launch {
                    if (mgr.isEmailEligible(22)) {
                        sendEmailOnce(context, mgr, 22, "22:00 第二封提醒")
                    }
                    scheduler.scheduleEmail22()
                }
            }
        }
    }

    private suspend fun sendEmailOnce(
        context: Context,
        mgr: DailyEventManager,
        slot: Int,
        reason: String
    ) {
        val app = context.applicationContext as AliveApp
        val cfg = app.settingsRepo.current()
        if (!cfg.enabled) {
            mgr.markEmailSent(slot, false, "邮件通知未启用，跳过发送 ($reason)")
            return
        }
        if (cfg.pass.isBlank()) {
            mgr.markEmailSent(slot, false, "SMTP 应用密码未配置，无法发送 ($reason)")
            return
        }
        val result = SmtpMailer.send(cfg)
        result.onSuccess {
            mgr.markEmailSent(slot, true, "邮件发送成功 ($reason) → ${cfg.to}")
        }.onFailure { e ->
            mgr.markEmailSent(slot, false, "邮件发送失败 ($reason): ${e.message}")
        }
    }

    private fun startEmailRetryPeriodic(context: Context) {
        // 20:00 首封发出后启动周期任务，按用户设定的间隔继续重发
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
