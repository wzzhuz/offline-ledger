package com.zwz.offlineledger.data.display

/**
 * 第 29 批：分类显示名的唯一出口。
 *
 * 二级一律写成「一级 / 二级」，不同父级下同名的「其他」才可分辨；一级保持裸名。
 * 占比图例与扇区下钻（StatsViewModel）、筛选面板（FilterSheet）、明细筛选
 * chip（DetailScreen）四个消费方 MUST 全部经此构造，MUST NOT 各自拼 " / "，
 * 否则会出现「统计跳转丢父级、手工选却带父级」的分叉。
 *
 * 纯 JVM、无 Android / Room 依赖，便于单测锁死三种入口输出一致。
 */
object CategoryDisplay {
    fun resolve(parentName: String?, leafName: String): String =
        if (parentName.isNullOrBlank()) leafName else parentName + " / " + leafName
}
