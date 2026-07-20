package com.alive.alive.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 *  - 启动 MobileDataObserver
 *  - 重排 0:00 / 12:00 / 23:59 闹钟
 *  - 跨天保护：若启动时发现日期已变，先 reset
 */
class AliveForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var unlockReceiver: UnlockReceiver? = null
    private var powerReceiver: PowerReceiver? = null
    private var mobileDataObserver: MobileDataObserver? = null
    private var sensorObserver: SensorObserver? = null
    private var keyboardObserver: KeyboardObserver? = null
    private var wifiObserver: WifiObserver? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        registerListeners()
        mobileDataObserver = MobileDataObserver(this, handler).also { it.start() }
        sensorObserver = SensorObserver(this).also { it.start() }
        keyboardObserver = KeyboardObserver(this).also { it.start() }
        wifiObserver = WifiObserver(this).also { it.start() }
        scope.launch {
            DailyEventManager(this@AliveForegroundService, AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao())
                .resetIfNewDay()
            AlarmScheduler(this@AliveForegroundService).scheduleAll()
            // 写一条 SERVICE_START 日志
            AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao()
                .insert(com.alive.alive.data.EventLog(
                    timestamp = System.currentTimeMillis(),
                    dayKey = com.alive.alive.util.BeijingTime.today().toString(),
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
        mobileDataObserver?.stop()
        mobileDataObserver = null
        sensorObserver?.stop()
        sensorObserver = null
        keyboardObserver?.stop()
        keyboardObserver = null
        wifiObserver?.stop()
        wifiObserver = null
        scope.launch {
            AliveDatabase.getInstance(this@AliveForegroundService).eventLogDao()
                .insert(com.alive.alive.data.EventLog(
                    timestamp = System.currentTimeMillis(),
                    dayKey = com.alive.alive.util.BeijingTime.today().toString(),
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
                    addAction(Intent.ACTION_SCREEN_ON)
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
