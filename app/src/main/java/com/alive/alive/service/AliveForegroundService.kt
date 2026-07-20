package com.alive.alive.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.alive.alive.alarm.AlarmScheduler
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 守护进程常驻前台服务：
 *  - 启动后立即成为 Foreground Service
 *  - 动态注册 UnlockReceiver / PowerReceiver（系统广播只能动态接收）
 *  - 启动 MobileDataObserver / SensorObserver / ScreenStateObserver / ForegroundAppObserver
 *  - 重排 0:00 / 12:00 / 18:00 / 20:00 / 22:00 闹钟
 *  - 跨天保护：若启动时发现日期已变，先 reset
 */
class AliveForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var unlockReceiver: UnlockReceiver? = null
    private var powerReceiver: PowerReceiver? = null
    private var mobileDataObserver: MobileDataObserver? = null
    private var sensorObserver: SensorObserver? = null
    private var screenStateObserver: ScreenStateObserver? = null
    private var foregroundAppObserver: ForegroundAppObserver? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        registerListeners()
        mobileDataObserver = MobileDataObserver(this, handler).also {
            runCatching { it.start() }.onFailure { e ->
                Log.e("Alive/Service", "MobileDataObserver start failed", e)
            }
        }
        sensorObserver = SensorObserver(this).also {
            runCatching { it.start() }.onFailure { e ->
                Log.e("Alive/Service", "SensorObserver start failed", e)
            }
        }
        screenStateObserver = ScreenStateObserver(this, handler).also {
            runCatching { it.start() }.onFailure { e ->
                Log.e("Alive/Service", "ScreenStateObserver start failed", e)
            }
        }
        foregroundAppObserver = ForegroundAppObserver(this, handler).also {
            runCatching { it.start() }.onFailure { e ->
                Log.e("Alive/Service", "ForegroundAppObserver start failed", e)
            }
        }
        scope.launch {
            DailyEventManager(this@AliveForegroundService, AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao())
                .resetIfNewDay()
            AlarmScheduler(this@AliveForegroundService).scheduleAll()
            // 写一条 SERVICE_START 日志
            AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao()
                .insert(com.alive.alive.data.EventLog(
                    timestamp = System.currentTimeMillis(),
                    dayKey = com.alive.alive.util.SystemTime.today().toString(),
                    eventType = "SERVICE_START",
                    detail = "守护服务启动"
                ))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIF_FOREGROUND_ID, NotificationHelper.buildForeground(this))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterListeners()
        mobileDataObserver?.runCatching { stop() }
        mobileDataObserver = null
        sensorObserver?.runCatching { stop() }
        sensorObserver = null
        screenStateObserver?.runCatching { stop() }
        screenStateObserver = null
        foregroundAppObserver?.runCatching { stop() }
        foregroundAppObserver = null
        scope.launch {
            AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao()
                .insert(com.alive.alive.data.EventLog(
                    timestamp = System.currentTimeMillis(),
                    dayKey = com.alive.alive.util.SystemTime.today().toString(),
                    eventType = "SERVICE_STOP",
                    detail = "守护服务停止"
                ))
        }
        super.onDestroy()
    }

    private fun registerListeners() {
        unlockReceiver = UnlockReceiver().also {
            ContextCompat.registerReceiver(
                this, it,
                IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        powerReceiver = PowerReceiver().also {
            ContextCompat.registerReceiver(
                this, it,
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun unregisterListeners() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        powerReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        powerReceiver = null
    }

    companion object {
        private const val TAG = "Alive/Service"

        fun start(context: Context) {
            val intent = Intent(context, AliveForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AliveForegroundService::class.java).apply {
                action = "ACTION_STOP"
            })
            runCatching { context.stopService(Intent(context, AliveForegroundService::class.java)) }
        }
    }
}
