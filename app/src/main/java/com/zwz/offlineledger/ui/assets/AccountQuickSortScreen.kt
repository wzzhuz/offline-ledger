package com.zwz.offlineledger.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.core.prefs.LedgerPrefs
import com.zwz.offlineledger.data.local.entity.AccountEntity
import com.zwz.offlineledger.data.model.displayName
import com.zwz.offlineledger.ui.components.CategoryHeaderBlue
import com.zwz.offlineledger.ui.components.ListDivider
import com.zwz.offlineledger.ui.components.rememberKeyedReorder
import com.zwz.offlineledger.ui.ledgerFactory
import com.zwz.offlineledger.ui.theme.LedgerColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 第 60 批 Q4A：独立「全部账户排序」页。
 *
 * - 全部未归档账户**不分 `AccountType`** 一起排序，常用账户可置顶；
 *   这里排的是记一笔候选顺序，与资产页分组内 `sortOrder` 完全隔离。
 * - `draftOrder` 与已落库 `quickOrder` 彻底分离：`rememberKeyedReorder`
 *   的 onCommit 传空 lambda，松手不写库；点「保存」才一次性写回。
 *   「放弃」零写库。
 * - 复用 `sh.calvin.reorderable:reorderable:3.1.0` 与
 *   `rememberKeyedReorder`，MUST NOT 自写 `Modifier.pointerInput`。
 * - 上移 / 下移以 `semantics.customActions` 保留作 TalkBack 兜底。
 */
@Composable
fun AccountQuickSortScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: AssetsViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            AssetsViewModel(ep.accountDao(), ep.ledgerRepository(), LedgerPrefs(context))
        }
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.reload() }

    val activeIds = state.accounts
        .filter { !it.isArchived }
        .sortedBy { it.quickOrder }
        .map { it.id }

    // onCommit 为空：松手绝不落库，只有底部「保存」触发写回。
    val reorder = rememberKeyedReorder(
        ids = activeIds,
        prefix = "quickacc",
        onCommit = { }
    )
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        reorder.move(from.key, to.key)
    }

    val byId = remember(state.accounts) { state.accounts.associateBy { it.id } }

    fun moveBy(id: Long, delta: Int) {
        val idx = reorder.ids.indexOf(id)
        val target = idx + delta
        if (idx < 0 || target !in reorder.ids.indices) return
        reorder.move(reorder.keyOf(id), reorder.keyOf(reorder.ids[target]))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LedgerColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LedgerColors.CardSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = LedgerColors.OnSurface
                )
            }
            Text(
                text = "全部账户排序",
                style = MaterialTheme.typography.titleLarge,
                color = LedgerColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                vm.commitQuickOrderAll(reorder.ids)
                onBack()
            }) { Text("保存", color = LedgerColors.OnSurface) }
        }

        Text(
            text = "这里排的是记一笔里常用账户的先后顺序，所有类型账户一起排",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            items(reorder.ids, key = { reorder.keyOf(it) }) { id ->
                val account = byId[id] ?: return@items
                ReorderableItem(reorderState, key = reorder.keyOf(id)) { _ ->
                    // 沿用已验证的带参写法；onDragStopped 刻意留空——
                    // 本页只在点「保存」时写库，松手 MUST NOT commit。
                    val dragHandle = with(this) {
                        Modifier.longPressDraggableHandle(onDragStopped = { })
                    }
                    QuickSortRow(
                        account = account,
                        dragModifier = dragHandle,
                        onUp = { moveBy(id, -1) },
                        onDown = { moveBy(id, 1) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("放弃（不保存）")
            }
            Button(
                onClick = {
                    vm.commitQuickOrderAll(reorder.ids)
                    onBack()
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("保存顺序") }
        }
    }
}

@Composable
private fun QuickSortRow(
    account: AccountEntity,
    dragModifier: Modifier,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("上移") { onUp(); true },
                    CustomAccessibilityAction("下移") { onDown(); true }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(LedgerColors.CardSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountIconBox(
                name = account.name,
                cardKind = account.cardKind,
                iconKey = AccountIconCatalog.iconKeyFor(account.name),
                size = 36
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 第 61 批：统一走 accountBaseLabel，旧库后缀自动去重。
                    text = accountBaseLabel(account),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.type.displayName() + " · " + account.currencyCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerColors.Neutral
                )
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 20.dp)
                    .background(CategoryHeaderBlue.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
        ListDivider(Modifier.padding(horizontal = 12.dp))
    }
}
