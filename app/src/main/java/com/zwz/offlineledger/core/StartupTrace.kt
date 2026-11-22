package com.zwz.offlineledger.core

import android.util.Log

/**
 * 实验分支 `experiment/plaintext-db` 专用：冷启动五点分段打点。
 *
 * 与 main 上第 49 批的同名类语义一致，唯一差别是 ENABLED = true，
 * 便于和「v0.2.30 有加密 / 无缓存」做同口径对照。
 *
 * 索引：
 * 0 = Application.onCreate 入口
 * 1 = System.loadLibrary("sqlcipher") 返回（本分支已删除，紧跟 0，delta≈0）
 * 2 = getOrCreatePassphrase 返回（本分支已删除，紧跟 1，delta≈0）
 * 3 = Room 构建完成 / 首次 DAO 查询前
 * 4 = 首屏 Paging 返回
 *
 * 因此本分支的 0→1、1→2 恒为 0ms，2→3 只剩 Hilt 图 + 明文 Room open。
 * 查看：`adb logcat -s StartupTrace:I`，每次冷启动打印一行分段耗时。
 * 不写文件、不持久化、不进账本数据、不进任何导出。
 */
object StartupTrace {

    const val ENABLED = true

    private const val TAG = "StartupTrace"

    private val marks = LongArray(5) { 0L }
    private val labels = arrayOf(
        "Application.onCreate",
        "loadLibrary 返回(已移除)",
        "getOrCreatePassphrase 返回(已移除)",
        "Room 构建完成/首次 DAO 前",
        "首屏 Paging 返回"
    )

    fun mark(index: Int) {
        if (!ENABLED || index !in marks.indices) return
        marks[index] = System.currentTimeMillis()
        if (index == 4) logIfEnabled()
    }

    /** 各段耗时（ms）；未打到的点记为 -1。 */
    fun report(): String {
        if (!ENABLED) return "StartupTrace: disabled"
        return buildString {
            append("分段(ms): ")
            for (i in marks.indices) {
                val delta = if (i == 0 || marks[i] == 0L || marks[i - 1] == 0L) -1
                else marks[i] - marks[i - 1]
                append(labels[i]).append('=').append(delta)
                if (i != marks.lastIndex) append(" | ")
            }
            val total = if (marks[0] != 0L && marks[4] != 0L) marks[4] - marks[0] else -1
            append(" || 总计=").append(total)
        }
    }

    fun logIfEnabled() {
        if (ENABLED) Log.i(TAG, report())
    }
}
