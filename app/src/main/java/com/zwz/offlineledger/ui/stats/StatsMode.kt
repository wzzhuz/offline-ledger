package com.zwz.offlineledger.ui.stats

/**
 * 第 44 批：从 StatsViewModel.kt 抽出为纯 Kotlin 文件，
 * 让 StatsDrillRange 的周期换算可被纯 JVM 单测直接覆盖，不依赖 Android ViewModel。
 */
enum class StatsMode { MONTH, YEAR, MULTI_YEAR }
