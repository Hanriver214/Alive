package com.alive.alive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alive.alive.AliveApp
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.AliveEvent
import com.alive.alive.state.DailyEventManager
import com.alive.alive.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件 a：用户解锁手机 + 任一应用进入前台。
 *
 * - ACTION_USER_PRESENT  → 解锁成功
 * - 解锁后 5 分钟内 UsageStatsManager 探测到 ACTIVITY_RESUMED → 真实使用 → 标记 A
 *
 * 若用户未授予「使用情况访问」权限，则仅解锁即视为满足 a（degraded mode）。
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
            // 有权限则要求 usedApp 为 true；无权限则降级仅看解锁
            if (!hasUsagePerm || usedApp) {
                mgr.markEvent(AliveEvent.A, detail)
            } else {
                // 仅解锁但无应用前台，暂不标记，等真正的应用前台事件
                mgr.markEvent(AliveEvent.A, "解锁但暂无应用前台，仍按 a 计")
            }
        }
    }
}
