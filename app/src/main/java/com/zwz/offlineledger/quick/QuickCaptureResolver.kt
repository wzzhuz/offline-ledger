package com.zwz.offlineledger.quick

/**
 * 第 49 批：节点优先 / OCR 兜底 / 安全窗口明确降级的纯 JVM 决策器。
 * 不做静默猜测：任何一步失败都返回 NeedManual，由确认页给「手动记账」。
 */
object QuickCaptureResolver {

    const val NODE_TTL_MILLIS = 60_000L

    fun resolve(
        node: ScreenCaptureNodeSnapshot?,
        ocrLines: List<String>?,
        screenshotErrorCode: Int?,
        now: Long
    ): CaptureOutcome {
        if (node != null && now - node.atMillis <= NODE_TTL_MILLIS) {
            val bill = ScreenTextParser.parse(node.lines, now)
            if (bill != null) return CaptureOutcome.Success(bill, "节点文本")
        }
        if (ocrLines != null) {
            val bill = ScreenTextParser.parse(ocrLines, now)
            if (bill != null) return CaptureOutcome.Success(bill, "OCR")
        }
        if (screenshotErrorCode != null) {
            return CaptureOutcome.NeedManual(screenshotReason(screenshotErrorCode))
        }
        return CaptureOutcome.NeedManual("未识别到金额，请手动记账或换一屏再试")
    }

    /** 系统错误码只做可读映射；FLAG_SECURE 等失败一律不伪装成「解析成功」。 */
    fun screenshotReason(code: Int): String = when (code) {
        1 -> "截图间隔太短，稍等一秒再点磁贴"
        2 -> "无障碍服务未就绪，请到设置里重新开启"
        3 -> "该页面禁止截屏（安全窗口），请手动记账"
        4 -> "当前窗口不可截图，请手动记账"
        else -> "截图失败（错误码 $code），请手动记账"
    }
}
