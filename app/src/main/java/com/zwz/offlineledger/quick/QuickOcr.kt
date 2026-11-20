package com.zwz.offlineledger.quick

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * 第 49 批：端侧中文 OCR。只把带坐标的文本块文本交给 ScreenTextParser，
 * 单帧 Bitmap 仅在内存、不落盘、不上传、不记日志；识别完成后由调用方 recycle。
 */
object QuickOcr {
    fun recognize(bitmap: Bitmap, onLines: (List<String>) -> Unit, onError: () -> Unit) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val client = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            client.process(image)
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                    client.close()
                    onLines(lines)
                }
                .addOnFailureListener {
                    client.close()
                    onError()
                }
        } catch (e: Throwable) {
            onError()
        }
    }
}
