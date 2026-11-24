package com.zwz.offlineledger.core

/**
 * 第 63 批：账户**显示名**与**卡号后缀剥离**的唯一实现，纯 JVM、零依赖。
 *
 * 第 61 批把它放在 `ui.assets`，第 62 批接上了全站 UI；但 CSV 导出 / 导入
 * 属于 `backup.csv` 纯 JVM 层，不能依赖 `ui`（ui 依赖 Room Entity）。
 * 因此下沉到 `core`：UI 层、CSV 层 MUST 共用这一份，
 * MUST NOT 在 SQL / Codec / Repository 里再拼一遍字符串。
 *
 * 旧库兼容：v0.2.40 及更早把 ` (1122)` 拼进了 `name` 本身，
 * 若 `name` 已以该后缀结尾则不再追加，MUST NOT 出现 `(1122)(1122)`。
 */
fun accountLabel(name: String?, last4: String?): String? {
    if (name == null) return null
    if (last4.isNullOrBlank()) return name
    val suffix = " ($last4)"
    return if (name.endsWith(suffix)) name else name + suffix
}

/** 与 [accountLabel] 严格互逆：把尾部的 ` (xxxx)` 后缀剥掉，得到纯名。 */
fun stripAccountCardSuffix(name: String, last4: String?): String {
    if (last4.isNullOrBlank()) return name
    val suffix = " ($last4)"
    return if (name.endsWith(suffix)) name.removeSuffix(suffix) else name
}
