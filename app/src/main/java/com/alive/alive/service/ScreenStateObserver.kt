package com.alive.alive.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项（时长型）：
 *  - 未解锁状态屏幕亮起总时长 ≥2 分钟  +1（一次性）
 *  - 解锁后亮屏总时长 ≥30 分钟        +1（一次性）
 *
 * 实现：
 *  - ACTION_SCREEN_ON  记录亮屏起点 + 当时是否处于锁屏状态
 *  - ACTION_SCREEN_OFF 计算本次亮屏时长，按"亮屏期间是否解锁过"分别累加
 *
 * 注意：Android 上 ACTION_USER_PRESENT 在 ACTION_SCREEN_ON 之后触发，
 * 因此一次亮屏周期可能横跨「未解锁」和「已解锁」两段。
 * 这里在 SCREEN_ON 时用 KeyguardManager 判断初始状态，并在 USER_PRESENT
 * 时切分时段：把已累加到 locked 的部分截断，剩余算到 unlocked。
 */
class ScreenStateObserver(
    private val context: Context,
    private val handler: Handler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** 当前亮屏周期起点（epoch ms），null 表示屏幕熄灭。 */
    private var screenOnSince: Long? = null
    /** 当前亮屏周期开始时是否处于锁屏状态。 */
    private var startedLocked: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "ScreenStateObserver started")
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        // 停止时若屏幕仍亮着，把已累计的时长落账一次，避免丢失
        screenOnSince?.let { since ->
            val now = System.currentTimeMillis()
            val delta = now - since
            if (delta > 0) {
                if (startedLocked) {
                    scope.launch { submitLocked(delta) }
                } else {
                    scope.launch { submitUnlocked(delta) }
                }
            }
        }
        screenOnSince = null
        Log.i(TAG, "ScreenStateObserver stopped")
    }

    private fun handleScreenOn() {
        screenOnSince = System.currentTimeMillis()
        startedLocked = keyguardManager.isKeyguardLocked
        Log.i(TAG, "SCREEN_ON, startedLocked=$startedLocked")
    }

    private fun handleUserPresent() {
        // 用户解锁，把"未解锁亮屏"段截断落账，剩余时间计入"解锁后亮屏"
        val since = screenOnSince ?: return
        val now = System.currentTimeMillis()
        val delta = now - since
        if (delta > 0 && startedLocked) {
            scope.launch { submitLocked(delta) }
        }
        // 重置起点为现在，标记为已解锁
        screenOnSince = now
        startedLocked = false
        Log.i(TAG, "USER_PRESENT, switched to unlocked segment, lockedDelta=$delta ms")
    }

    private fun handleScreenOff() {
        val since = screenOnSince ?: run {
            screenOnSince = null
            return
        }
        val now = System.currentTimeMillis()
        val delta = now - since
        screenOnSince = null
        if (delta <= 0) return
        if (startedLocked) {
            scope.launch { submitLocked(delta) }
        } else {
            scope.launch { submitUnlocked(delta) }
        }
        Log.i(TAG, "SCREEN_OFF, delta=$delta ms, locked=$startedLocked")
    }

    private suspend fun submitLocked(deltaMs: Long) {
        val mgr = DailyEventManager(
            context.applicationContext,
            AliveDatabase.getInstance(context).eventLogDao()
        )
        mgr.addScreenOnLockedMs(deltaMs)
    }

    private suspend fun submitUnlocked(deltaMs: Long) {
        val mgr = DailyEventManager(
            context.applicationContext,
            AliveDatabase.getInstance(context).eventLogDao()
        )
        mgr.addScreenOnUnlockedMs(deltaMs)
    }

    companion object {
        private const val TAG = "Alive/Screen"
    }
}
