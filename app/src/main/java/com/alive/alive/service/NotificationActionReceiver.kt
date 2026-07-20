package com.alive.alive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知栏「我挺好」按钮 → 主动签到。
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_ACTIVE_CHECKIN) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val mgr = DailyEventManager(
                context.applicationContext,
                AliveDatabase.getInstance(context).eventLogDao()
            )
            mgr.triggerActiveCheckIn("用户点击通知「我挺好」按钮")
        }
    }
}
