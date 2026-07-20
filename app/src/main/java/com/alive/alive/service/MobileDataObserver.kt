package com.alive.alive.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：数据流量功能开启/关闭 +1 分。仅检测开关动作，不检测流量使用变化。
 *
 * 实现：监听 `Settings.Global.mobile_data` 的 URI 变化。
 * 该设置在用户从通知栏/设置中切换「移动数据」开关时由系统更新，
 * 与信号强弱、Wifi 切换无关。
 *
 * 在 OEM 自定义 ROM 上该 URI 可能不更新，属于已知限制。
 */
class MobileDataObserver(
    private val context: Context,
    handler: Handler
) : ContentObserver(handler) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastEnabled: Boolean? = null

    private val uri: Uri = Settings.Global.getUriFor("mobile_data")

    fun start() {
        // 初始化基线值
        lastEnabled = readMobileDataEnabled()
        context.contentResolver.registerContentObserver(uri, false, this)
        Log.i(TAG, "MobileDataObserver started, initial enabled=$lastEnabled")
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(this)
        Log.i(TAG, "MobileDataObserver stopped")
    }

    override fun onChange(selfChange: Boolean, changedUri: Uri?) {
        if (changedUri != null && changedUri != uri) return
        val newEnabled = readMobileDataEnabled()
        if (newEnabled == null) return
        if (newEnabled != lastEnabled) {
            val direction = if (newEnabled) "开启" else "关闭"
            Log.i(TAG, "mobile_data toggled: $lastEnabled -> $newEnabled")
            lastEnabled = newEnabled
            scope.launch {
                val mgr = DailyEventManager(
                    context.applicationContext,
                    AliveDatabase.getInstance(context).eventLogDao()
                )
                mgr.addScore(ScoreType.MOBILE_DATA, "数据流量$direction")
            }
        }
    }

    private fun readMobileDataEnabled(): Boolean? = try {
        Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
    } catch (t: Throwable) {
        Log.w(TAG, "read mobile_data failed", t)
        null
    }

    companion object {
        private const val TAG = "Alive/MobileData"
    }
}
