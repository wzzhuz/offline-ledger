package com.zwz.offlineledger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.ui.components.AmountMask
import com.zwz.offlineledger.ui.LedgerApp
import com.zwz.offlineledger.ui.lock.AppLockScreen
import com.zwz.offlineledger.ui.theme.LedgerColors
import com.zwz.offlineledger.ui.theme.LedgerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 阶段 5：改继承 FragmentActivity 以支持 BiometricPrompt（它本身继承
 * ComponentActivity，setContent 与 @AndroidEntryPoint 均不受影响）。
 *
 * 启用应用锁时，冷启动与后台恢复（ON_PAUSE）都会重新上锁；
 * 解锁前只渲染 AppLockScreen，MUST NOT 渲染任何账目界面。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LedgerPrefs(this)
        // 桌面快捷方式：只在解锁后的分支传给 LedgerApp，
        // 因此启用应用锁时快捷方式仍必须先解锁，不绕过锁。
        val startWithRecordEntry =
            intent?.action == "com.zwz.offlineledger.ACTION_QUICK_RECORD"
        LedgerColors.applyReverseColors(prefs.reverseColors)
        // 第 13 批：隐藏金额偏好冷启动即生效，直到用户手动关闭。
        AmountMask.hidden = prefs.hideAmounts
        applyFlagSecure(prefs.flagSecureEnabled)
        // 第 77 批（Q4B）：背景铺到系统栏下，可点内容由下方 safeDrawingPadding 避开。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            LedgerTheme {
                var unlocked by rememberSaveable { mutableStateOf(!prefs.appLockEnabled) }
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE && prefs.appLockEnabled) {
                            unlocked = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    // 一处覆盖全 App：Scaffold 的 contentWindowInsets 已置 0，
                    // MUST NOT 再叠加一次（design 决策 5）。
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (unlocked) {
                            LedgerApp(startWithRecordEntry = startWithRecordEntry)
                        } else {
                            AppLockScreen(
                                prefs = prefs,
                                onUnlocked = { unlocked = true },
                                onPrefsChanged = { applyFlagSecure(prefs.flagSecureEnabled) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyFlagSecure(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
