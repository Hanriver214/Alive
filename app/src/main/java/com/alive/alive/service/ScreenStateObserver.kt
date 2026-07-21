package com.alive.alive.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
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
 *  - ACTION_SCREEN_ON  记录亮屏起点 + 当时是否处于锁屏状态，启动定时快照
 *  - ACTION_SCREEN_OFF 计算本次亮屏时长，按"亮屏期间是否解锁过"分别累加，停止定时快照
 *  - ACTION_USER_PRESENT 切换时段：把 locked 段落账，剩余算 unlocked
 *
 * 定时快照机制（每 30 秒）：
 *  - 亮屏期间定时把已累积时长写入 DataStore，让界面能实时刷新
 *  - 同时防止进程被杀时丢失过多数据（最多丢失最后 30 秒）
 *
 * 注意：Android 上 ACTION_USER_PRESENT 在 ACTION_SCREEN_ON 之后触发，
 * 因此一次亮屏周期可能横跨「未解锁」和「已解锁」两段。
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
    /** 上次快照时已累加的时长起点（用于计算增量）。 */
    private var lastSnapshotSince: Long? = null

    /** 快照间隔（毫秒）。 */
    private val snapshotIntervalMs = 30_000L

    private val snapshotRunnable = object : Runnable {
        override fun run() {
            doSnapshot()
            handler.postDelayed(this, snapshotIntervalMs)
        }
    }

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
        // ACTION_SCREEN_ON/OFF/USER_PRESENT 是 protected broadcast，只有系统能发送，
        // 使用 RECEIVER_EXPORTED 确保能可靠接收（部分 ROM 上 NOT_EXPORTED 会丢弃这类广播）
        ContextCompat.registerReceiver(
            context, receiver, filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        // 若服务启动时屏幕已经亮着，立即初始化并启动定时快照
        //（否则必须等下一次 SCREEN_ON 才能开始统计）
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            val now = System.currentTimeMillis()
            screenOnSince = now
            lastSnapshotSince = now
            startedLocked = keyguardManager.isKeyguardLocked
            handler.removeCallbacks(snapshotRunnable)
            handler.postDelayed(snapshotRunnable, snapshotIntervalMs)
            Log.i(TAG, "Screen already on at start, startedLocked=$startedLocked")
        }
        Log.i(TAG, "ScreenStateObserver started")
    }

    fun stop() {
        handler.removeCallbacks(snapshotRunnable)
        runCatching { context.unregisterReceiver(receiver) }
        // 停止时若屏幕仍亮着，把已累计的时长落账一次，避免丢失
        flushCurrentSegment()
        screenOnSince = null
        Log.i(TAG, "ScreenStateObserver stopped")
    }

    private fun handleScreenOn() {
        screenOnSince = System.currentTimeMillis()
        lastSnapshotSince = screenOnSince
        startedLocked = keyguardManager.isKeyguardLocked
        Log.i(TAG, "SCREEN_ON, startedLocked=$startedLocked")
        // 先移除再启动，防止重复调度
        handler.removeCallbacks(snapshotRunnable)
        handler.postDelayed(snapshotRunnable, snapshotIntervalMs)
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
        lastSnapshotSince = now
        startedLocked = false
        Log.i(TAG, "USER_PRESENT, switched to unlocked segment, lockedDelta=$delta ms")
    }

    private fun handleScreenOff() {
        // 停止定时快照
        handler.removeCallbacks(snapshotRunnable)
        flushCurrentSegment()
        screenOnSince = null
        lastSnapshotSince = null
        Log.i(TAG, "SCREEN_OFF")
    }

    /** 把当前亮屏段的时长落账，并清空起点。 */
    private fun flushCurrentSegment() {
        val since = screenOnSince ?: return
        val now = System.currentTimeMillis()
        val delta = now - since
        if (delta <= 0) return
        if (startedLocked) {
            scope.launch { submitLocked(delta) }
        } else {
            scope.launch { submitUnlocked(delta) }
        }
        Log.i(TAG, "flushCurrentSegment: delta=$delta ms, locked=$startedLocked")
    }

    /** 定时快照：把从上次快照到现在的增量写入。 */
    private fun doSnapshot() {
        val since = screenOnSince ?: return
        val lastSnapshot = lastSnapshotSince ?: since
        val now = System.currentTimeMillis()

        // 只写入增量部分
        val delta = now - lastSnapshot
        if (delta <= 0) return

        lastSnapshotSince = now
        // 关键：把 screenOnSince 也推进到 now，避免 flushCurrentSegment / handleUserPresent
        // 把已经快照过的时长再次计入，导致重复统计。
        screenOnSince = now

        if (startedLocked) {
            scope.launch { submitLocked(delta) }
        } else {
            scope.launch { submitUnlocked(delta) }
        }
        Log.d(TAG, "doSnapshot: delta=$delta ms, locked=$startedLocked")
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