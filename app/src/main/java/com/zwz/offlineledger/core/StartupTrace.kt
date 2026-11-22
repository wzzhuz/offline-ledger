package com.zwz.offlineledger.core

/**
 * 冷启动五点分段打点，用于定位 1~2s 落在哪一段。
 *
 * 索引：
 * 0 = Application.onCreate 入口
 * 1 = System.loadLibrary("sqlcipher") 返回
 * 2 = DatabaseKeyManager.getOrCreatePassphrase 返回
 * 3 = 首次 DAO 查询前（Hilt 图已建、Room 即将 open）
 * 4 = 首屏 Paging 返回
 *
 * **release 默认关闭**：`ENABLED` 是编译期常量，false 时所有 mark 都是空操作，
 * 诊断时临时改 true 即可。不写文件、不持久化、不进账本数据、不进导出。
 * 全用 JVM API，单元测试类路径下加载也安全。
 */
object StartupTrace {

    const val ENABLED = false

    private val marks = LongArray(5) { 0L }
    private val labels = arrayOf(
        "Application.onCreate",
        "loadLibrary 返回",
        "getOrCreatePassphrase 返回",
        "首次 DAO 查询前",
        "首屏 Paging 返回"
    )

    fun mark(index: Int) {
        if (!ENABLED || index !in marks.indices) return
        marks[index] = System.currentTimeMillis()
    }

    /** 各段耗时（ms）；未打到的点记为 -1。 */
    fun report(): String {
        if (!ENABLED) return "StartupTrace: disabled"
        return buildString {
            append("StartupTrace 分段(ms): ")
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

    /** 诊断时用；release 关闭状态下返回空串，不会污染日志。 */
    fun logIfEnabled() {
        if (ENABLED) println(report())
    }
}
