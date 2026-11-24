package com.zwz.offlineledger.backup.csv

import com.zwz.offlineledger.core.accountLabel
import com.zwz.offlineledger.core.stripAccountCardSuffix

/**
 * 第 63 批：CSV / Firefly 导入时的账户身份解析，纯 JVM、零 Room 依赖。
 *
 * 背景：账户表**允许同名**（同一家银行的储蓄卡 / 信用卡都叫「工商银行」），
 * 唯一键是自增 `id`。旧实现 `firstOrNull { it.name == row.accountName }`
 * 会静默命中第一张卡，导致同名两卡的流水归错账户、卡号信息丢失。
 *
 * 新口径：导出带 `cardLast4`，导入按 `(name, cardLast4)` 复合匹配；
 * 老 CSV / 第三方账单没有这列时回退纯名匹配，若同名多条 MUST 报歧义，
 * MUST NOT 静默取第一条。
 */

/** 纯 JVM 账户引用，由调用方从 `AccountEntity` 映射，不依赖 Room。 */
data class AccountRef(
    val id: Long,
    val name: String,
    val cardLast4: String?
)

/** 从 CSV 单元格解析出的待匹配身份。 */
data class ParsedAccount(val name: String, val last4: String?)

sealed interface AccountMatch {
    data class Unique(val ref: AccountRef) : AccountMatch
    data class Missing(val reason: String) : AccountMatch
    data class Ambiguous(val candidates: List<AccountRef>, val reason: String) : AccountMatch
}

/** 尾部 ` (4位数字)` 后缀，与 [accountLabel] 的组合格式严格对应。 */
private val CARD_SUFFIX = Regex("""^(.*) \((\d{4})\)$""")

/**
 * 解析 `account` 单元格 + 可选 `cardLast4` 列，得到 `(纯名, 后四位?)`。
 *
 * - 自有导出：`account="工商银行 (1122)"` + `cardLast4="1122"` → 同名解析
 * - 手工/第三方写成展示名但没带列：`account="工商银行 (1122)"` → 从单元格拆出
 * - 老 CSV / 第三方账单：`account="工商银行"` 且无列 → `last4 = null`
 * - 显式列优先于单元格内联后缀（列是机器键，单元格是给人看的）
 */
fun parseAccountCell(cell: String, explicitLast4: String?): ParsedAccount {
    val trimmed = cell.trim()
    val inline = CARD_SUFFIX.matchEntire(trimmed)?.let { m ->
        m.groupValues[1] to m.groupValues[2]
    }
    val fromCell = inline?.first ?: trimmed
    val last4 = explicitLast4?.trim()?.takeIf {
        it.length == 4 && it.all { ch -> ch.isDigit() }
    } ?: inline?.second
    return ParsedAccount(fromCell, last4)
}

/**
 * 在候选账户中按 `(name, cardLast4)` 复合匹配。
 *
 * `last4 == null` 时不限定卡号（老 CSV 兼容），但同名多条仍 MUST 报歧义。
 * 旧库 `name` 本身已含 ` (1122)` 的账户，先用
 * [stripAccountCardSuffix] 还原纯名再比，MUST NOT 被判成缺失。
 */
fun resolveAccount(refs: List<AccountRef>, parsed: ParsedAccount): AccountMatch {
    val hits = refs.filter { ref ->
        val base = stripAccountCardSuffix(ref.name, ref.cardLast4)
        val nameOk = base == parsed.name || ref.name == parsed.name
        val last4Ok = parsed.last4 == null || ref.cardLast4 == parsed.last4
        nameOk && last4Ok
    }
    return when {
        hits.size == 1 -> AccountMatch.Unique(hits.single())
        hits.isEmpty() -> AccountMatch.Missing(
            "账户不存在或已归档：${accountLabel(parsed.name, parsed.last4)}"
        )
        else -> AccountMatch.Ambiguous(
            hits,
            "账户不唯一，请在 CSV 的 cardLast4 列指定卡号后四位：候选 " +
                hits.joinToString("、") { accountLabel(it.name, it.cardLast4).orEmpty() }
        )
    }
}
