package com.zwz.offlineledger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zwz.offlineledger.core.prefs.LedgerPrefs
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
        LedgerColors.applyReverseColors(prefs.reverseColors)
        applyFlagSecure(prefs.flagSecureEnabled)

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
                    if (unlocked) {
                        LedgerApp()
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
