package com.zwz.offlineledger.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * 第 50 批：QS 磁贴只做「同步起页」这一件事。
 * MUST NOT 等截图 / OCR 结果才起页——节流、安全窗口、服务被复位时
 * 也要先看到透明页与「正在识别」，再明确降级，绝不能静默没反应。
 * API 34+ 必须用 startActivityAndCollapse(PendingIntent)；
 * 若系统抛异常，退回 startActivity 保证页面至少能起来。
 */
class QuickEntryTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCaptureActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(
                    this, REQUEST_CODE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Throwable) {
            try {
                startActivity(intent)
            } catch (ignored: Throwable) {
                // 系统级拒绝时保持静默，但透明页本身不再依赖识屏结果才出现。
            }
        }
    }

    companion object { private const val REQUEST_CODE = 4901 }
}
