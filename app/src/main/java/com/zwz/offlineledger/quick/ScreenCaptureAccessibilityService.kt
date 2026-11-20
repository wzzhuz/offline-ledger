package com.zwz.offlineledger.quick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

/**
 * 第 49 批：系统按需绑定的无障碍服务。无常驻前台服务、无常驻通知；
 * 白名单窗口事件里被动收集极轻节点文本（内存、TTL、不落盘、不记全屏日志）。
 * 点磁贴时由 QuickCaptureBridge 调用 captureFrame 截单帧，安全窗口直接回错误码，
 * 由确认页明确降级到手动记账，MUST NOT 静默猜测金额。
 */
class ScreenCaptureAccessibilityService : AccessibilityService() {

    private lateinit var prefs: QuickCapturePrefs

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = QuickCapturePrefs(this)
        QuickCaptureBridge.attach(this)
    }

    override fun onDestroy() {
        QuickCaptureBridge.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::prefs.isInitialized || !prefs.nodeCacheEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (!prefs.isAllowed(pkg)) return
        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        try {
            collect(root, lines, 0)
        } catch (e: Throwable) {
            return
        }
        if (lines.isNotEmpty()) {
            QuickCaptureBridge.publish(
                ScreenCaptureNodeSnapshot(pkg, lines.take(120), System.currentTimeMillis())
            )
        }
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 24 || out.size >= 120) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty() && t.length <= 120) out.add(t)
        val cd = node.contentDescription?.toString()?.trim()
        if (!cd.isNullOrEmpty() && cd != t && cd.length <= 120) out.add(cd)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    override fun onInterrupt() { QuickCaptureBridge.clearSnapshot() }

    @SuppressLint("NewApi")
    fun captureFrame(executor: Executor, onBitmap: (Bitmap) -> Unit, onFailure: (Int) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFailure(QuickCaptureBridge.ERROR_NO_ACCESS)
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer ?: run {
                    onFailure(QuickCaptureBridge.ERROR_INTERNAL)
                    return
                }
                try {
                    val hw = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        ?: run {
                            onFailure(QuickCaptureBridge.ERROR_INTERNAL)
                            return
                        }
                    val soft = Bitmap.createBitmap(hw.width, hw.height, Bitmap.Config.ARGB_8888)
                    Canvas(soft).drawBitmap(hw, 0f, 0f, null)
                    hw.recycle()
                    onBitmap(soft)
                } catch (e: Throwable) {
                    onFailure(QuickCaptureBridge.ERROR_INTERNAL)
                } finally {
                    buffer.close()
                }
            }

            override fun onFailure(errorCode: Int) { onFailure(errorCode) }
        })
    }
}
