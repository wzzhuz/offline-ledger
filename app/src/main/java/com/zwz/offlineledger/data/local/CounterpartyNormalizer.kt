package com.zwz.offlineledger.data.local

import com.zwz.offlineledger.data.local.entity.CounterpartyAliasEntity

/**
 * 第 30 批：支付宝/微信账单里的商户全称归一成手工记账的简称。
 * 纯包含匹配、大小写不敏感；无命中则原样返回，MUST NOT 改写原文语义。
 */
object CounterpartyNormalizer {
    fun normalize(raw: String, aliases: List<CounterpartyAliasEntity>): String {
        if (raw.isBlank()) return raw
        val hit = aliases.firstOrNull { raw.contains(it.pattern, ignoreCase = true) }
        return hit?.shortName ?: raw
    }
}
