package com.alive.alive.service

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.alive.alive.data.AliveDatabase
import com.alive.alive.state.AliveEvent
import com.alive.alive.state.DailyEventManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 事件 e：输入法键盘唤起。使用输入法就算标记。
 *
 * 通过在窗口添加一个隐藏的 View，监听其全局布局变化来检测键盘状态。
 * 当键盘弹出时，可用高度会明显减少，以此判断键盘是否唤起。
 */
class KeyboardObserver(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var hiddenView: View? = null
    private var isKeyboardVisible = false
    private var lastTriggerTime = 0L
    private val TRIGGER_DEBOUNCE_MS = 60000L

    fun start() {
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        hiddenView = View(context).apply {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    checkKeyboardVisibility()
                }
            })
        }
        try {
            windowManager.addView(hiddenView, params)
            Log.i(TAG, "KeyboardObserver started")
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission, cannot detect keyboard")
        }
    }

    fun stop() {
        hiddenView?.let {
            runCatching { windowManager.removeView(it) }
        }
        hiddenView = null
        Log.i(TAG, "KeyboardObserver stopped")
    }

    private fun checkKeyboardVisibility() {
        val view = hiddenView ?: return
        val rect = android.graphics.Rect()
        view.getWindowVisibleDisplayFrame(rect)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val visibleHeight = rect.bottom - rect.top
        val keyboardHeight = screenHeight - visibleHeight
        val threshold = screenHeight * 0.15
        val keyboardNowVisible = keyboardHeight > threshold

        if (keyboardNowVisible != isKeyboardVisible) {
            isKeyboardVisible = keyboardNowVisible
            if (keyboardNowVisible) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > TRIGGER_DEBOUNCE_MS) {
                    lastTriggerTime = now
                    Log.i(TAG, "Keyboard shown, height=$keyboardHeight")
                    scope.launch {
                        val mgr = DailyEventManager(
                            context.applicationContext,
                            AliveDatabase.getInstance(context).eventLogDao()
                        )
                        mgr.markEvent(AliveEvent.E, "键盘唤起 (高度=$keyboardHeight)")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "Alive/Keyboard"
    }
}