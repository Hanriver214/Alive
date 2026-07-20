package com.alive.alive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alive.alive.service.AliveForegroundService
import com.alive.alive.ui.DashboardScreen
import com.alive.alive.ui.LogViewerScreen
import com.alive.alive.ui.MainViewModel
import com.alive.alive.ui.SettingsScreen
import com.alive.alive.ui.theme.AliveTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户允许/拒绝都进入主界面 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestEssentialPermissions()
        startGuard()

        setContent {
            AliveTheme {
                AliveRoot()
            }
        }
    }

    private fun requestEssentialPermissions() {
        // 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 电池优化白名单
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    private fun startGuard() {
        AliveForegroundService.start(this)
    }
}

@Composable
private fun AliveRoot() {
    val viewModel: MainViewModel = viewModel()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = { AliveBottomBar(tab) { tab = it } }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> DashboardScreen(viewModel)
                1 -> LogViewerScreen(viewModel)
                2 -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun AliveBottomBar(selected: Int, onChange: (Int) -> Unit) {
    val items: List<Triple<String, ImageVector, String>> = listOf(
        Triple("今日", Icons.Filled.Assessment, "dashboard"),
        Triple("日志", Icons.Filled.List, "logs"),
        Triple("设置", Icons.Filled.Settings, "settings")
    )
    NavigationBar {
        items.forEachIndexed { index, (label, icon, _) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onChange(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
