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
 * 事件 b：充电行为。亮屏或锁屏充电均算。
 * 仅 ACTION_POWER_CONNECTED 触发标记 B；断电仅写日志（通过断电后再连也会再标，但 DailyEventManager
 * 的 markEvent 在已签到后会自动忽略，且重复标记 B 在 DayState 中天然幂等）。
 */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val mgr = DailyEventManager(
                context.applicationContext,
                AliveDatabase.getInstance(context).eventLogDao()
            )
            mgr.markEvent(AliveEvent.B, "电源已连接（亮屏/锁屏均计）")
        }
    }
}
