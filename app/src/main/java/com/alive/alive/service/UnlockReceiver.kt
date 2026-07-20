package com.alive.alive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import com.alive.alive.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：用户解锁手机 +2 分。
 *
 * - ACTION_USER_PRESENT  → 解锁成功 → +2
 *
 * 若用户授予「使用情况访问」权限，会在日志中附带"解锁后5分钟内应用前台"信息（仅展示，不影响计分）。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val app = context.applicationContext as AliveApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val mgr = DailyEventManager(context, AliveDatabase.getInstance(context).eventLogDao())
            val usedApp = try {
                UsageStatsHelper.hasForegroundActivityRecently(context, 5 * 60_000L)
            } catch (t: Throwable) {
                Log.w("Alive", "usage stats query failed", t)
                false
            }
            val hasUsagePerm = UsageStatsHelper.hasPermission(context)
            val detail = if (hasUsagePerm) {
                "解锁后5分钟内应用前台=$usedApp"
            } else {
                "未授予使用情况权限，仅按解锁计"
            }
            mgr.addScore(ScoreType.UNLOCK, detail)
        }
    }
}
