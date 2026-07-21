package com.alive.alive.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.DailyEventManager
import com.alive.alive.state.ScoreType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 计分项：数据流量功能开启/关闭 +1 分。仅检测开关动作，不检测流量使用变化。
 *
 * 实现：通过 ConnectivityManager.NetworkCallback 监听蜂窝网络（cellular）的
 * 可用性变化。当用户从通知栏/设置中切换「移动数据」开关时：
 * - 开启移动数据 → 系统建立蜂窝网络连接 → onCapabilitiesChanged 检测到 NET_CAPABILITY_INTERNET
 * - 关闭移动数据 → 系统断开蜂窝网络连接 → onLost
 *
 * 相比监听 Settings.Global.mobile_data URI（第三方应用收不到回调），
 * NetworkCallback 是官方推荐的网络状态监听方式，在主流 ROM 上均可可靠工作。
 *
 * 去抖策略：只在蜂窝网络「真正建立/断开」时计分，短时间内重复回调不重复计分。
 */
class MobileDataObserver(
    private val context: Context,
    @Suppress("unused") handler: android.os.Handler
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastConnected: Boolean = false
    private var hasBaseline: Boolean = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            Log.w(TAG, "ConnectivityManager unavailable, observer disabled")
            return
        }

        // 初始化基线值：检查当前蜂窝网络是否可用
        lastConnected = isCellularCurrentlyConnected()
        hasBaseline = true
        Log.i(TAG, "MobileDataObserver started, initial cellular connected=$lastConnected")

        // 只监听蜂窝网络（cellular）的变化
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "cellular onAvailable")
                handleChange(connected = true)
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "cellular onLost")
                handleChange(connected = false)
            }

            // 部分机型 onAvailable 之后会持续回调 onCapabilitiesChanged，
            // 这里只在状态从未连接变为连接时计分，防止重复计分
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (!lastConnected && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    Log.i(TAG, "cellular onCapabilitiesChanged -> connected")
                    handleChange(connected = true)
                }
            }
        }
        networkCallback = callback
        connectivityManager?.registerNetworkCallback(request, callback)
    }

    fun stop() {
        networkCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        Log.i(TAG, "MobileDataObserver stopped")
    }

    private fun handleChange(connected: Boolean) {
        if (!hasBaseline) {
            lastConnected = connected
            hasBaseline = true
            return
        }
        if (connected == lastConnected) return  // 状态未变化，忽略
        lastConnected = connected

        val direction = if (connected) "开启" else "关闭"
        Log.i(TAG, "mobile_data toggled: -> $connected ($direction)")
        scope.launch {
            val mgr = DailyEventManager(
                context.applicationContext,
                AliveDatabase.getInstance(context).eventLogDao()
            )
            mgr.addScore(ScoreType.MOBILE_DATA, "数据流量$direction")
        }
    }

    private fun isCellularCurrentlyConnected(): Boolean {
        val cm = connectivityManager ?: return false
        return runCatching {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "Alive/MobileData"
    }
}
