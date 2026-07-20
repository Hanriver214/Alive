package com.alive.alive.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * 解锁事件的辅助查询：判断用户最近是否有真实使用手机（任一应用进入前台）。
 *
 * 因为没有 AccessibilityService，我们用 UsageStatsManager 推断前台切换，
 * 仅用于在解锁日志中附带"解锁后5分钟内应用前台"信息（不影响计分）。
 */
object UsageStatsHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 最近 windowMillis 内是否有 ACTIVITY_RESUMED 事件（即有应用进入前台）。 */
    fun hasForegroundActivityRecently(context: Context, windowMillis: Long): Boolean {
        if (!hasPermission(context)) return false
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - windowMillis, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) return true
        }
        return false
    }
}
