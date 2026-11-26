package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.AccountType
import com.zwz.offlineledger.data.model.displayName
import com.zwz.offlineledger.ui.assets.AccountIconBox
import com.zwz.offlineledger.ui.assets.AccountIconCatalog
import com.zwz.offlineledger.ui.assets.accountBaseLabel
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 83 批：账户选择的**唯一通用出口**，与 `CategorySelectDialog` 对称。
 *
 * 复用范围（使用者第 4 项拍板 B）：记一笔账户 chip / 转账两卡 / 模板与周期规则
 * 表单 / 明细筛选（统计账户排行下钻也走同一份 FilterSheet，故一并覆盖）。
 * 全仓 MUST NOT 再出现第二份账户下拉或第二套账户网格。
 *
 * 两种布局（使用者第 1、2 项）：
 * - `GLOBAL`：按 `quickOrder` 全局排序、不分类型、归档沉底 —— 与「全部账户排序」
 *   独立页和记一笔既有候选顺序完全一致，默认视图（1A，零回归）。
 * - `GROUPED`：按 `AccountType` 四组固定顺序分组，**组内按 `sortOrder`** 继承
 *   资产页分组排序；MUST NOT 把 `quickOrder` 混进来（活文档 §1753：两套排序
 *   混用会导致「排完记一笔看到的不是排在前面的」）。切换后同一张卡在两种
 *   视图里位置不同是**预期行为**，不是 bug。
 *
 * 布局选择跨启动持久化（使用者第 3 项 B）：存 `LedgerPrefs.accountPickerLayout`，
 * 不进 Room / 不进备份 DTO / 不触发 Migration，换设备回退默认全局视图。
 *
 * 版式沿用第 82 批：44dp `AccountIconBox` + 放大卡种角标 + 次要小字，
 * **MUST NOT 显示余额**。搜索走 `accountBaseLabel`（含卡号后四位）；
 * 搜索非空时跨组平铺（与分类选择器「搜索跨组平铺」同口径）。
 * MUST NOT 引入 `ModalBottomSheet`（T12）、长按拖拽（§3.1）或新依赖。
 */
enum class AccountPickerLayout(val key: String) {
    GLOBAL("global"),
    GROUPED("grouped");

    companion object {
        fun of(raw: String?): AccountPickerLayout =
            entries.firstOrNull { it.key == raw } ?: GLOBAL
    }
}

/** 跨启动记住布局；返回「当前值 + 写回函数」，调用方零样板。 */
@Composable
fun rememberAccountPickerLayout(): Pair<AccountPickerLayout, (AccountPickerLayout) -> Unit> {
    val context = LocalContext.current
    val prefs = remember { LedgerPrefs(context) }
    val state = remember { mutableStateOf(AccountPickerLayout.of(prefs.accountPickerLayout)) }
    return state.value to { next ->
        state.value = next
        prefs.accountPickerLayout = next.key
    }
}

/** 组间固定顺序：与资产页 4 组一致；不按 `quickOrder`、不按名字猜。 */
private val AccountTypeOrder = listOf(
    AccountType.ASSET,
    AccountType.LIABILITY,
    AccountType.PREPAID,
    AccountType.INVESTMENT
)

/**
 * 账户次要信息唯一组合点 —— 卡号尾号 / 卡种 / 币种。
 * 第 82 批定义在 `RecordEditorDialog` 内，本批上提为全站共用（转账两卡、
 * 选择器、模板表单都读它）。余额刻意不进这里。
 */
fun accountSecondaryLabel(a: AccountEntity): String = buildList {
    a.cardLast4?.takeIf { it.length == 4 }?.let { add("尾号 $it") }
    when (a.cardKind) {
        "DEBIT" -> add("储蓄卡")
        "CREDIT" -> add("信用卡")
    }
    add(a.currencyCode)
}.joinToString(" · ")

@Composable
fun AccountSelectDialog(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    allowNone: Boolean,
    title: String,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String = "不指定账户"
) {
    val (layout, onLayoutChange) = rememberAccountPickerLayout()
    var query by remember { mutableStateOf("") }

    val shown = accounts.filter {
        accountBaseLabel(it).contains(query, ignoreCase = true)
    }
    val currentLabel = accounts.firstOrNull { it.id == selectedId }
        ?.let { accountBaseLabel(it) } ?: noneLabel

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = LedgerColors.CardSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }
                    Text(
                        "当前：" + currentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = layout == AccountPickerLayout.GLOBAL,
                            onClick = { onLayoutChange(AccountPickerLayout.GLOBAL) },
                            label = { Text("全局排序") }
                        )
                        FilterChip(
                            selected = layout == AccountPickerLayout.GROUPED,
                            onClick = { onLayoutChange(AccountPickerLayout.GROUPED) },
                            label = { Text("按类型分组") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text("搜索账户名 / 卡号后四位", style = MaterialTheme.typography.bodyMedium)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清空账户搜索")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    if (allowNone) {
                        NoneTile(noneLabel, selectedId == null) {
                            onSelect(null)
                            onDismiss()
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // 搜索命中跨组平铺，与分类选择器同口径；
                    // 空搜索且选分组视图时才按 AccountType 分段。
                    if (query.isNotBlank() || layout == AccountPickerLayout.GLOBAL) {
                        val ordered = shown.sortedWith(
                            compareBy<AccountEntity> { it.isArchived }
                                .thenBy { it.quickOrder }
                                .thenBy { it.id }
                        )
                        AccountGrid(ordered, selectedId) { id -> onSelect(id); onDismiss() }
                    } else {
                        var any = false
                        AccountTypeOrder.forEach { type ->
                            val group = shown.filter { it.type == type }
                                .sortedWith(
                                    compareBy<AccountEntity> { it.isArchived }
                                        .thenBy { it.sortOrder }
                                        .thenBy { it.id }
                                )
                            if (group.isEmpty()) return@forEach
                            any = true
                            Text(
                                type.displayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = LedgerColors.Neutral,
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                            )
                            AccountGrid(group, selectedId) { id -> onSelect(id); onDismiss() }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!any) {
                            Text(
                                if (query.isBlank()) "暂无可显示的账户" else "没有匹配的账户",
                                style = MaterialTheme.typography.bodySmall,
                                color = LedgerColors.Neutral
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountGrid(
    items: List<AccountEntity>,
    selectedId: Long?,
    onPick: (Long) -> Unit
) {
    items.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { a -> AccountTile(a, a.id == selectedId, { onPick(a.id) }, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccountTile(
    a: AccountEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) LedgerColors.Primary.copy(alpha = 0.12f)
                else LedgerColors.SurfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountIconBox(
                name = a.name,
                cardKind = a.cardKind,
                iconKey = AccountIconCatalog.iconKeyFor(a.name),
                iconRef = a.iconRef,
                size = 44
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    a.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = LedgerColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    accountSecondaryLabel(a),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NoneTile(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(
                if (selected) LedgerColors.Primary.copy(alpha = 0.12f)
                else LedgerColors.SurfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) LedgerColors.Primary else LedgerColors.OnSurface
        )
    }
}
