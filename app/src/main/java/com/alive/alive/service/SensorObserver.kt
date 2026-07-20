package com.alive.alive.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.AliveEvent
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件 d：手机姿态变化。只有翻转（屏幕朝下→朝上或反之）才算事件触发标记。
 *
 * 使用重力传感器检测手机翻转：
 * - 检测 z 轴加速度绝对值从 < 5 变为 > 9（或反之），表示手机从平放变为直立或翻转
 * - 翻转角度阈值：当 z 轴加速度符号改变时视为翻转
 */
class SensorObserver(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastZSign: Int = 0
    private var lastFlipTime: Long = 0
    private val FLIP_DEBOUNCE_MS = 5000L
    private val GRAVITY_THRESHOLD = 5.0f

    fun start() {
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "SensorObserver started")
        } else {
            Log.w(TAG, "Gravity sensor not available")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "SensorObserver stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        val z = event.values[2]
        val currentSign = if (z > GRAVITY_THRESHOLD) 1 else if (z < -GRAVITY_THRESHOLD) -1 else 0
        if (currentSign == 0) return
        if (lastZSign != 0 && lastZSign != currentSign) {
            val now = System.currentTimeMillis()
            if (now - lastFlipTime > FLIP_DEBOUNCE_MS) {
                lastFlipTime = now
                Log.i(TAG, "Phone flipped: z=$z, sign changed from $lastZSign to $currentSign")
                scope.launch {
                    val mgr = DailyEventManager(
                        context.applicationContext,
                        AliveDatabase.getInstance(context).eventLogDao()
                    )
                    mgr.markEvent(AliveEvent.D, "手机翻转 (z=${z.format(1)})")
                }
            }
        }
        lastZSign = currentSign
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)

    companion object {
        private const val TAG = "Alive/Sensor"
    }
}