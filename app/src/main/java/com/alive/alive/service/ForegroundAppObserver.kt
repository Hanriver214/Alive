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
 * 计分项：前台应用变化/切换 +1 分。打开任意应用或切换应用均算。
 *
 * 实现：周期性轮询 UsageStatsManager.queryEvents，检测 ACTIVITY_RESUMED 事件。
 * 每出现一次新的前台包名（与上次不同）即视为一次切换，+1。
 *
 * 需要「使用情况访问」权限。若用户未授权，本 Observer 不启动计分，仅打日志。
 */
class ForegroundAppObserver(
    private val context: Context,
    private val handler: Handler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** 上一次记录到的前台包名。 */
    private var lastPackage: String? = null
    /** 上一次轮询的时间戳，用于增量查询。 */
    private var lastPollTime: Long = 0L

    private val pollIntervalMs = 30_000L

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    fun start() {
        if (!UsageStatsHelper.hasPermission(context)) {
            Log.w(TAG, "UsageStats permission not granted, ForegroundAppObserver disabled")
            return
        }
        lastPollTime = System.currentTimeMillis()
        // 初始化基线：取最近 5 秒内的最后一个 ACTIVITY_RESUMED 包名
        lastPackage = queryLastForegroundPackage(System.currentTimeMillis() - 5_000L)
        handler.postDelayed(pollRunnable, pollIntervalMs)
        Log.i(TAG, "ForegroundAppObserver started, initial pkg=$lastPackage")
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "ForegroundAppObserver stopped")
    }

    private fun pollOnce() {
        if (!UsageStatsHelper.hasPermission(context)) return
        val now = System.currentTimeMillis()
        val from = lastPollTime
        lastPollTime = now
        if (now <= from) return

        val events = try {
            usm.queryEvents(from, now)
        } catch (t: Throwable) {
            Log.w(TAG, "queryEvents failed", t)
            return
        }
        val e = UsageEvents.Event()
        var newPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                newPkg = e.packageName
            }
        }
        if (newPkg != null && newPkg != lastPackage) {
            val prev = lastPackage
            lastPackage = newPkg
            Log.i(TAG, "Foreground changed: $prev -> $newPkg")
            scope.launch {
                val mgr = DailyEventManager(
                    context.applicationContext,
                    AliveDatabase.getInstance(context).eventLogDao()
                )
                mgr.addScore(ScoreType.FOREGROUND_APP, "前台切换: $prev → $newPkg")
            }
        }
    }

    private fun queryLastForegroundPackage(from: Long): String? {
        if (!UsageStatsHelper.hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = e.packageName
            }
        }
        return last
    }

    companion object {
        private const val TAG = "Alive/Foreground"
    }
}
