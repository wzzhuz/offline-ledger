package com.zwz.offlineledger.core.intake

/**
 * 第 87 批：文本识别的默认规则集。
 *
 * 覆盖层只允许增补词表与分类映射，MUST NOT 修改金额正则、主金额锚点、
 * 算术校验与落库门槛。这几项是「永不误记」的护栏，不能由一段粘贴文本改写。
 */
data class TextIntakeRuleSet(
    val statusWords: Set<String>,
    val discountWords: Set<String>,
    val noiseLabels: Set<String>,
    val categoryMap: Map<String, String>,
)

data class TextIntakeRulePatchResult(
    val rules: TextIntakeRuleSet,
    val invalidLines: List<String>,
)

/** 只读预演 + 可手工保存的草稿。原文不进这里。 */
data class TextIntakeCommitDraft(
    val importSource: String,
    val sourceId: String,
    val amountCents: Long,
    val amountYuan: String,
    val discountCents: Long?,
    val discountYuan: String,
    val originalYuan: String,
    val localDate: Int,
    val timestamp: Long,
    val accountName: String,
    val cardLast4: String,
    val categoryRoot: String?,
    val category: String,
    val billCategory: String,
    val counterparty: String,
    val description: String,
    val note: String,
    val statusWord: String,
    val discountLabel: String,
    val pageMarkers: Int,
    val warnings: List<String>,
)

data class TextIntakePreview(
    val source: String,
    val rawText: String,
    val truncated: Boolean,
    val originalCharCount: Int,
    val amountCandidates: List<String>,
    val hasDirection: Boolean,
    val hasTime: Boolean,
    val hasCounterparty: Boolean,
    val readyForAutoCommit: Boolean,
    val missing: List<String>,
    val warnings: List<String>,
    val commitDraft: TextIntakeCommitDraft?,
)

sealed interface TextIntakeCommitOutcome {
    data class Inserted(val recordId: Long, val message: String) : TextIntakeCommitOutcome
    data class Duplicate(val message: String) : TextIntakeCommitOutcome
    data class Rejected(val reason: String) : TextIntakeCommitOutcome
}
