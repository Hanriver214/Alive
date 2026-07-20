package com.alive.alive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.AliveEvent
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件 b：充电行为和断电行为。亮屏或锁屏充电、拔充电线动作均算。
 * ACTION_POWER_CONNECTED（充电）和 ACTION_POWER_DISCONNECTED（断电）都触发标记 B。
 */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED) return
        val isConnected = intent.action == Intent.ACTION_POWER_CONNECTED
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val mgr = DailyEventManager(
                context.applicationContext,
                AliveDatabase.getInstance(context).eventLogDao()
            )
            mgr.markEvent(AliveEvent.B, if (isConnected) "充电连接（亮屏/锁屏均计）" else "断电拔出（亮屏/锁屏均计）")
        }
    }
}
