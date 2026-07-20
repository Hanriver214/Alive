package com.alive.alive.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.AliveEvent
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件 f：WiFi 纯连接状态监测。变化就算标记，不使用定位权限。
 *
 * 使用 ConnectivityManager.NetworkCallback 监听 WiFi 连接状态变化。
 * 当 WiFi 从断开变为连接或从连接变为断开时触发标记 F。
 *
 * 注意：不使用 WifiManager（需 ACCESS_FINE_LOCATION），仅使用 ConnectivityManager
 * 通过 NetworkCapabilities 判断是否为 WiFi 网络。
 */
class WifiObserver(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isWifiConnected = false
    private var lastTriggerTime = 0L
    private val TRIGGER_DEBOUNCE_MS = 5000L

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkWifiState()
        }

        override fun onLost(network: Network) {
            checkWifiState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            checkWifiState()
        }

        override fun onUnavailable() {
            checkWifiState()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        isWifiConnected = checkCurrentWifiState()
        Log.i(TAG, "WifiObserver started, initial WiFi=$isWifiConnected")
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        Log.i(TAG, "WifiObserver stopped")
    }

    private fun checkWifiState() {
        val nowConnected = checkCurrentWifiState()
        if (nowConnected != isWifiConnected) {
            isWifiConnected = nowConnected
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime > TRIGGER_DEBOUNCE_MS) {
                lastTriggerTime = now
                val status = if (nowConnected) "连接" else "断开"
                Log.i(TAG, "WiFi $status")
                scope.launch {
                    val mgr = DailyEventManager(
                        context.applicationContext,
                        AliveDatabase.getInstance(context).eventLogDao()
                    )
                    mgr.markEvent(AliveEvent.F, "WiFi$status")
                }
            }
        }
    }

    private fun checkCurrentWifiState(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        return activeNetwork != null &&
            connectivityManager.getNetworkCapabilities(activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    companion object {
        private const val TAG = "Alive/WiFi"
    }
}