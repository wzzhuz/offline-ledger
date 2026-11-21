package com.zwz.offlineledger.quick

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * 第 50 批：端侧中文 OCR 返回带坐标的文本块，而不是只回拼好的行文本。
 * 单帧 Bitmap 仅在内存、不落盘、不上传、不记日志；识别完成后由调用方 recycle。
 * ML Kit v2 不公开逐行置信度，所以「识别详情」展示 块号 + 坐标 + 命中规则，
 * MUST NOT 编造置信度数值。
 */
object QuickOcr {

    fun recognizeBlocks(
        bitmap: Bitmap,
        onBlocks: (List<TextBlockCandidate>) -> Unit,
        onError: () -> Unit
    ) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            client.process(image)
                .addOnSuccessListener { result ->
                    val blocks = mutableListOf<TextBlockCandidate>()
                    var idx = 0
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val r = line.boundingBox
                            blocks.add(
                                TextBlockCandidate(
                                    idx++, line.text,
                                    r?.left ?: 0, r?.top ?: 0
                                )
                            )
                        }
                    }
                    client.close()
                    onBlocks(blocks)
                }
                .addOnFailureListener {
                    client.close()
                    onError()
                }
        } catch (e: Throwable) {
            onError()
        }
    }

    /** 兼容旧调用点；新链路一律走 recognizeBlocks 以保留坐标与改选能力。 */
    fun recognize(bitmap: Bitmap, onLines: (List<String>) -> Unit, onError: () -> Unit) {
        recognizeBlocks(
            bitmap,
            { blocks -> onLines(blocks.map { it.text }) },
            onError
        )
    }
}
