package com.zwz.offlineledger.quick

/**
 * 第 49 批：识屏记账的纯 JVM 数据模型。
 * 刻意不引用任何 Android 类，让 testDebugUnitTest 能直接锁死解析与降级契约。
 */
data class ScreenTextBlock(val text: String, val left: Int = 0, val top: Int = 0)

/** 解析候选；MUST 经确认页用户核对后才允许落库。 */
data class ParsedBill(
    val amountCents: Long,
    val isIncome: Boolean,
    val counterparty: String,
    val description: String,
    val note: String,
    val timestamp: Long,
    val fingerprint: String,
    val timeText: String = ""
)

/** 白名单窗口内被动收集的结构化文本快照：只内存、带 TTL、不落盘、不记全屏日志。 */
data class ScreenCaptureNodeSnapshot(
    val packageName: String,
    val lines: List<String>,
    val atMillis: Long
)

sealed class CaptureOutcome {
    data class Success(val bill: ParsedBill, val source: String) : CaptureOutcome()
    data class NeedManual(val reason: String) : CaptureOutcome()
}
