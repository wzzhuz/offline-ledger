package com.zwz.offlineledger.quick

import android.graphics.Bitmap
import java.util.concurrent.Executor

/**
 * 第 49 批：进程内桥。磁贴拉起的透明 Activity 与系统绑定的 AccessibilityService
 * 处在同一进程，用这里交换「最近一份结构化文本快照」与「单帧截图请求」。
 * MUST 只存内存、带 TTL、不落盘、不上传、不记全屏日志。
 */
object QuickCaptureBridge {

    private const val TTL_MILLIS = 60_000L

    @Volatile
    private var service: ScreenCaptureAccessibilityService? = null

    @Volatile
    private var snapshot: ScreenCaptureNodeSnapshot? = null

    fun attach(s: ScreenCaptureAccessibilityService) { service = s }

    fun detach(s: ScreenCaptureAccessibilityService) {
        if (service === s) {
            service = null
            snapshot = null
        }
    }

    fun isAttached(): Boolean = service != null

    fun publish(s: ScreenCaptureNodeSnapshot) { snapshot = s }

    /** 过期或非白名单的快照一律不返回。 */
    fun freshSnapshot(now: Long): ScreenCaptureNodeSnapshot? {
        val s = snapshot ?: return null
        if (now - s.atMillis > TTL_MILLIS) return null
        return s
    }

    fun clearSnapshot() { snapshot = null }

    fun capture(
        executor: Executor,
        onBitmap: (Bitmap) -> Unit,
        onFailure: (Int) -> Unit
    ) {
        val s = service
        if (s == null) {
            onFailure(ERROR_NO_SERVICE)
            return
        }
        s.captureFrame(executor, onBitmap, onFailure)
    }

    const val ERROR_NO_SERVICE = -1
    const val ERROR_INTERNAL = 1
    const val ERROR_NO_ACCESS = 2
    const val ERROR_INTERVAL_SHORT = 3
    const val ERROR_INVALID_DISPLAY = 4
    const val ERROR_INVALID_WINDOW = 5
    const val ERROR_SECURE_WINDOW = 6
}
