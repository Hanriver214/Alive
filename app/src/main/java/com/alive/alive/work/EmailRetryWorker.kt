package com.alive.alive.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.mail.SmtpMailer
import com.alive.alive.state.DailyEventManager

/**
 * 周期重发邮件的 Worker。
 *
 * - 若今日已签到 → 直接 return，不再发邮件（但周期任务仍存在，下次 0:00 RESET 时由 AlarmReceiver 取消）
 * - 若邮件未启用 → return
 * - 若 SMTP 应用密码未配置 → return 并写日志
 * - 否则发一封，写日志
 */
class EmailRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AliveApp
        val mgr = DailyEventManager(app, AliveDatabase.getInstance(app).eventLogDao())
        val s = mgr.current()
        if (s.checkedIn) {
            // 已签到，无需再发
            return Result.success()
        }
        val cfg = app.settingsRepo.current()
        if (!cfg.enabled) {
            return Result.success()
        }
        if (cfg.pass.isBlank()) {
            mgr.markEmailSent(0, false, "周期重发跳过：SMTP 应用密码未配置")
            return Result.success()
        }
        val result = SmtpMailer.send(cfg)
        result.onSuccess {
            mgr.markEmailSent(0, true, "周期重发邮件成功 → ${cfg.to}")
        }.onFailure { e ->
            mgr.markEmailSent(0, false, "周期重发邮件失败: ${e.message}")
        }
        // 不论成功失败都 return success，让周期任务继续；失败时下次还会再试
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "alive_email_retry"
    }
}
