package com.zwz.offlineledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * 第 84 批（v0.2.63）：按使用者真机反馈把顶部从 4 行控件压到 1 行——
 * 两枚 `FilterChip` → 标题栏右侧**一枚轮换图标**；删叉号、删搜索框；
 * 壳改用 `ModalBottomSheet`（自带点遮罩 / 下滑 / 系统返回关闭）。
 *
 * 复用范围（第 83 批 4B）：记一笔账户 chip / 转账两卡 / 模板与周期规则
 * 表单 / 明细筛选（统计账户排行下钻也走同一份 FilterSheet，故一并覆盖）。
 * 全仓 MUST NOT 再出现第二份账户下拉或第二套账户网格。
 *
 * 两种布局（第 83 批 1A / 2）：
 * - `GLOBAL`：按 `quickOrder` 全局排序、不分类型、归档沉底 —— 与「全部账户排序」
 *   独立页和记一笔既有候选顺序完全一致，默认视图。
 * - `GROUPED`：按 `AccountType` 四组固定顺序分组，**组内按 `sortOrder`** 继承
 *   资产页分组排序；MUST NOT 把 `quickOrder` 混进来（活文档 §1753）。
 *   切换后同一张卡在两种视图里位置不同是**预期行为**。
 *
 * 布局选择跨启动持久化于 `LedgerPrefs.accountPickerLayout`，
 * 不进 Room / 不进备份 DTO / 不触发 Migration。
 *
 * 版式沿用第 82 批：44dp `AccountIconBox` + 放大卡种角标 + 次要小字，
 * **MUST NOT 显示余额**。同名卡靠次要行「尾号 xxxx」区分（第 84 批删搜索后
 * 这是唯一区分手段，MUST NOT 连带删掉 `cardLast4`）。
 * 图标全部来自既有 `material-icons-core` + Compose 自绘，MUST NOT 新增图标资源。
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
 * 余额刻意不进这里。第 84 批删掉账户搜索后，尾号是同名卡唯一区分手段。
 */
fun accountSecondaryLabel(a: AccountEntity): String = buildList {
    a.cardLast4?.takeIf { it.length == 4 }?.let { add("尾号 $it") }
    when (a.cardKind) {
        "DEBIT" -> add("储蓄卡")
        "CREDIT" -> add("信用卡")
    }
    add(a.currencyCode)
}.joinToString(" · ")

/** 网格里的两类格子：真实账户 / 「不指定」。第 84 批把后者收进网格第一格。 */
private sealed class AccountSlot {
    data class Item(val account: AccountEntity) : AccountSlot()
    data class None(val label: String) : AccountSlot()
}

private val GlobalComparator = compareBy<AccountEntity> { it.isArchived }
    .thenBy { it.quickOrder }
    .thenBy { it.id }

private val GroupComparator = compareBy<AccountEntity> { it.isArchived }
    .thenBy { it.sortOrder }
    .thenBy { it.id }

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentLabel = accounts.firstOrNull { it.id == selectedId }
        ?.let { accountBaseLabel(it) } ?: noneLabel

    /**
     * 第 84 批：使用者明确选 B（`ModalBottomSheet`）。
     * 清点更正：`ModalBottomSheet` **不是全仓禁令**——统计页
     * `DrillDownDialog.kt` 自第 69 批起就在用，且已写进 `category-stats` 主 spec、
     * `check_stats_merge.py` 还断言了它。第 77/80/83 批的「不用实验 API」
     * 只是**记一笔低频项 sheet**（BottomPickerSheet / TemplateQuickPick）的局部惯例，
     * 这两个文件本批一字不动，其禁令继续有效。
     * sheet 自带 scrim 点击 / 下滑 / 系统返回三种关闭路径，故第 84 批删叉号后
     * MUST NOT 再自己补遮罩 clickable（会与 sheet 的 scrim 抢事件）。
     */
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LedgerColors.CardSurface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // 第 84 批 1：点一下在两种布局间轮换，图标显示**当前模式**。
                        // 图标随模式变是使用者标注的「可选」项，本批采纳：
                        // 全局 = 既有 Icons.Filled.List（三横线）；分组 = 自绘 2×2 圆角方块。
                        IconButton(
                            onClick = {
                                onLayoutChange(
                                    if (layout == AccountPickerLayout.GLOBAL)
                                        AccountPickerLayout.GROUPED
                                    else AccountPickerLayout.GLOBAL
                                )
                            }
                        ) {
                            if (layout == AccountPickerLayout.GLOBAL) {
                                Icon(
                                    Icons.Filled.List,
                                    contentDescription = "当前全局排序，点击切换到按类型分组",
                                    tint = LedgerColors.OnSurface
                                )
                            } else {
                                LayoutGridGlyph()
                            }
                        }
                    }
                    // 第 84 批 2：保留「当前：xxx」，编辑态一眼看得到当前账户。
                    Text(
                        "当前：" + currentLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerColors.Neutral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            /** 点选即写回并关闭；sheet 的 onDismissRequest 不重复调 onDismiss。 */
            fun pick(id: Long?) {
                onSelect(id)
                onDismiss()
            }

            if (layout == AccountPickerLayout.GLOBAL) {
                val slots = buildList {
                    if (allowNone) add(AccountSlot.None(noneLabel))
                    accounts.sortedWith(GlobalComparator).forEach { add(AccountSlot.Item(it)) }
                }
                items(slots.chunked(2)) { row -> SlotRow(row, selectedId) { pick(it) } }
            } else {
                // 第 84 批 3：「不指定」收进网格第一格，不再单独占一条横幅。
                // 分组视图里它排在所有组标题之前（不属于任何 AccountType 组），
                // 第一格之后留一格空位；全局视图则与第一个账户同行。
                if (allowNone) {
                    items(listOf(listOf(AccountSlot.None(noneLabel)))) { row ->
                        SlotRow(row, selectedId) { pick(it) }
                    }
                }
                AccountTypeOrder.forEach { type ->
                    val group = accounts.filter { it.type == type }.sortedWith(GroupComparator)
                    if (group.isEmpty()) return@forEach
                    item {
                        Text(
                            type.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerColors.Neutral,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                    items(group.map { AccountSlot.Item(it) }.chunked(2)) { row ->
                        SlotRow(row, selectedId) { pick(it) }
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        "暂无可显示的账户",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerColors.Neutral,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** 自绘 2×2 圆角方块：分组视图图标。零新增 drawable 资源、零新增依赖。 */
@Composable
private fun LayoutGridGlyph() {
    val color = LedgerColors.OnSurface
    Canvas(modifier = Modifier.size(24.dp)) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap) / 2f
        val radius = CornerRadius(3.dp.toPx())
        for (r in 0..1) for (c in 0..1) {
            drawRoundRect(
                color = color,
                topLeft = Offset(c * (cell + gap), r * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun SlotRow(
    slots: List<AccountSlot>,
    selectedId: Long?,
    onPick: (Long?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { slot ->
            when (slot) {
                is AccountSlot.Item -> AccountTile(
                    a = slot.account,
                    selected = slot.account.id == selectedId,
                    onClick = { onPick(slot.account.id) },
                    modifier = Modifier.weight(1f)
                )
                is AccountSlot.None -> NoneTile(
                    label = slot.label,
                    selected = selectedId == null,
                    onClick = { onPick(null) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (slots.size == 1) Spacer(Modifier.weight(1f))
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

/**
 * 第 84 批：「不指定 / 全部账户」与真实账户同款 tile，贴一羽「无账户」灰圆。
 * MUST NOT 再做成独立整行横幅（那样又多占一行）。
 */
@Composable
private fun NoneTile(
    label: String,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LedgerColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = LedgerColors.Neutral
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) LedgerColors.Primary else LedgerColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
