package com.zwz.offlineledger.quick

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zwz.offlineledger.MainActivity
import com.zwz.offlineledger.ui.theme.LedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 第 49 批：QS 磁贴拉起的透明页。底下那个第三方账单 Activity 是 PAUSED 而非
 * STOPPED，画面仍在合成帧里，所以点磁贴那一刻可以当场截屏。
 * 窗口透明由 Theme.OfflineLedger.Translucent 提供，不弹标准 Dialog。
 */
@AndroidEntryPoint
class QuickCaptureActivity : ComponentActivity() {

    @Inject lateinit var writer: QuickCaptureWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedgerTheme {
                QuickCaptureConfirmScreen(
                    writer = writer,
                    onClose = { finish() },
                    onManual = {
                        startActivity(
                            Intent(this@QuickCaptureActivity, MainActivity::class.java)
                                .setAction("com.zwz.offlineledger.ACTION_QUICK_RECORD")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        finish()
                    },
                    onSettings = {
                        startActivity(
                            Intent(this@QuickCaptureActivity, QuickCaptureSettingsActivity::class.java)
                        )
                    }
                )
            }
        }
    }
}
