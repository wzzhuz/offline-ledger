package com.zwz.offlineledger.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * 第 49 批：QS 磁贴只做扳机。直接拉起透明确认页，
 * MUST NOT 绕广播或后台服务再起 Activity（Android 10+ 后台启动限制）。
 * API 34+ 必须用 startActivityAndCollapse(PendingIntent)。
 */
class QuickEntryTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
    }

    companion object { private const val REQUEST_CODE = 4901 }
}
