package com.alive.alive.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import com.alive.alive.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：检测任意其他应用在当日被启动过 +1 分。
 *
 * 实现：通过 UsageStatsManager.queryEvents() 查询最近一段时间内的
 * MOVE_TO_FOREGROUND / ACTIVITY_RESUMED 事件，检测是否有非本应用进入前台。
 *
 * 需要「使用情况访问」权限（PACKAGE_USAGE_STATS），该权限为特殊权限，
 * 需用户在系统设置中手动授予。若未授予，本 Observer 不产生计分。
 */
class AppLaunchObserver(
    private val context: Context,
    private val handler: Handler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val myPackageName = context.packageName

    /** 今日是否已检测到其他应用启动。 */
    private var launchDetected = false

    /** 上一次查询的截止时间，用于下次只查增量。 */
    private var lastQueryTime: Long = 0L

    private val pollIntervalMs = 30_000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            if (!launchDetected) {
                handler.postDelayed(this, pollIntervalMs)
            }
        }
    }

    fun start() {
        launchDetected = false
        lastQueryTime = System.currentTimeMillis()
        if (!UsageStatsHelper.hasPermission(context)) {
            Log.w(TAG, "未授予使用情况访问权限，应用启动检测不会生效。请在系统设置中授予。")
        }
        handler.postDelayed(pollRunnable, pollIntervalMs)
        Log.i(TAG, "AppLaunchObserver started")
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "AppLaunchObserver stopped")
    }

    private fun pollOnce() {
        if (launchDetected) return

        if (!UsageStatsHelper.hasPermission(context)) {
            Log.d(TAG, "无使用情况权限，跳过轮询")
            return
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.w(TAG, "UsageStatsManager unavailable")
            return
        }

        val now = System.currentTimeMillis()
        val from = lastQueryTime
        lastQueryTime = now

        val events = try {
            usm.queryEvents(from, now)
        } catch (t: Throwable) {
            Log.w(TAG, "queryEvents failed", t)
            return
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            // MOVE_TO_FOREGROUND 和 ACTIVITY_RESUMED 都表示应用进入前台
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                type == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                val pkg = event.packageName
                if (pkg != null && pkg != myPackageName) {
                    Log.i(TAG, "Detected app launch: $pkg")
                    scope.launch {
                        val mgr = DailyEventManager(
                            context.applicationContext,
                            AliveDatabase.getInstance(context).eventLogDao()
                        )
                        if (!mgr.current().checkedIn) {
                            mgr.addScore(ScoreType.FOREGROUND_APP, "应用启动: $pkg")
                            launchDetected = true
                        }
                    }
                    return
                }
            }
        }
    }

    companion object {
        private const val TAG = "Alive/AppLaunch"
    }
}
