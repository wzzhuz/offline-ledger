package com.zwz.offlineledger.core

import android.util.Log

/**
 * 冷启动五点分段打点，用于定位 1~2s 落在哪一段（第 49 批引入，第 50 批适配明文库）。
 *
 * 索引：
 * 0 = Application.onCreate 入口
 * 1 = Application 初始化完成（Hilt 图构建前）
 * 2 = DatabaseModule 开始构建 Room
 * 3 = Room 构建完成 / 首次 DAO 查询前
 * 4 = 首屏 Paging 返回
 *
 * 第 50 批去加密后 1→2、2→3 之间不再有 so 加载 / Keystore / 密钥派生，
 * 与原加密版读数对照即可看出省掉的固定成本。
 *
 * **release 默认关闭**：`ENABLED` 是编译期常量，false 时所有 mark 都是空操作，
 * 诊断时临时改 true 重编一次，用 `adb logcat -s StartupTrace:I` 读一行分段耗时。
 * 不写文件、不持久化、不进账本数据、不进任何导出。
 */
object StartupTrace {

    const val ENABLED = false

    private const val TAG = "StartupTrace"

    private val marks = LongArray(5) { 0L }
    private val labels = arrayOf(
        "Application.onCreate",
        "Application 初始化完成",
        "DatabaseModule 开始",
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
