package com.alive.alive.service

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：检测任意其他应用在当日被启动过 +1 分。
 *
 * 实现：周期性轮询 ActivityManager.getRunningAppProcesses()，检测前台应用的包名变化。
 * 记录当日首次检测到的非本应用包名作为"应用启动"计分，之后不再重复计分。
 *
 * 无需额外权限（GET_USAGE_STATS），兼容性更好。
 *
 * 注意：API getRunningAppProcesses() 在 API 28 已废弃，但在大多数设备上仍可用。
 * 某些定制 ROM 可能返回空列表，此时本 Observer 不产生任何计分（无报错）。
 */
class AppLaunchObserver(
    private val context: Context,
    private val handler: Handler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val myPackageName = context.packageName

    /** 今日是否已检测到其他应用启动。 */
    private var launchDetected = false

    /** 上一次记录的前台包名，用于去重。 */
    private var lastForegroundPkg: String? = null

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
        lastForegroundPkg = null
        handler.postDelayed(pollRunnable, pollIntervalMs)
        Log.i(TAG, "AppLaunchObserver started")
    }

    fun stop() {
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "AppLaunchObserver stopped")
    }

    private fun pollOnce() {
        if (launchDetected) return

        val foregroundPkg = getForegroundPackage()
        if (foregroundPkg == null) {
            Log.d(TAG, "Cannot get foreground package (possibly restricted by ROM)")
            return
        }
        if (foregroundPkg == myPackageName) {
            Log.d(TAG, "Foreground is self, ignoring")
            return
        }
        if (foregroundPkg == lastForegroundPkg) {
            return
        }

        lastForegroundPkg = foregroundPkg
        Log.i(TAG, "Detected app launch: $foregroundPkg")
        scope.launch {
            val mgr = DailyEventManager(
                context.applicationContext,
                AliveDatabase.getInstance(context).eventLogDao()
            )
            if (!mgr.current().checkedIn) {
                mgr.addScore(ScoreType.FOREGROUND_APP, "应用启动: $foregroundPkg")
                launchDetected = true
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getForegroundPackage(): String? {
        return try {
            am.runningAppProcesses?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
            }?.processName
        } catch (t: Throwable) {
            Log.w(TAG, "getRunningAppProcesses failed", t)
            null
        }
    }

    companion object {
        private const val TAG = "Alive/AppLaunch"
    }
}
