package com.alive.alive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：用户解锁手机 +2 分。
 *
 * - ACTION_USER_PRESENT  → 解锁成功 → +2
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val mgr = DailyEventManager(context, AliveDatabase.getInstance(context).eventLogDao())
            mgr.addScore(ScoreType.UNLOCK, "用户解锁手机")
        }
    }
}
