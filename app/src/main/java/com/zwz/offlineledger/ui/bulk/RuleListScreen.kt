package com.zwz.offlineledger.ui.bulk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zwz.offlineledger.bulk.RuleDefinition
import com.zwz.offlineledger.ui.ledgerFactory

/**
 * 第 38 批：规则列表 / 编辑器 / 查看匹配记录是三个独立全屏页。
 * 靠设置内二级页状态栈切换，不新增 NavHost 路由、不动 4 Tab 骨架、不改表。
 * 「应用」复用既有 preview → 单事务 → 末尾重算 → changeset 撤销链路。
 */
@Composable
fun RuleListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: RuleViewModel = viewModel(
        factory = ledgerFactory(context) { ep ->
            RuleViewModel(ep.appDatabase(), ep.ledgerRepository(), ep.recordDao(), ep.categoryDao())
        }
    )
    var page by rememberSaveable { mutableStateOf("list") }
    when (page) {
        "editor" -> RuleEditorContent(vm = vm, onBack = { page = "list" })
        "match" -> RuleMatchContent(vm = vm, onBack = { page = "list" })
        else -> RuleListContent(
            vm = vm,
            onBack = onBack,
            onNew = { vm.newDraft(); page = "editor" },
            onEdit = { r -> vm.editRule(r); page = "editor" },
            onApply = { r -> vm.previewRule(r); page = "match" },
            onApplyGroup = { gid -> vm.previewGroup(gid); page = "match" }
        )
    }
}

@Composable
private fun RuleListContent(
    vm: RuleViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (RuleDefinition) -> Unit,
    onApply: (RuleDefinition) -> Unit,
    onApplyGroup: (Long) -> Unit
) {
    val state by vm.state.collectAsState()
    var groupName by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回", maxLines = 1) }
            Text(
                "规则列表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "保存的规则可随时对历史记录再次「应用」；先看命中记录再写入，可一键撤销。" +
                    "「编辑」才进规则详情。",
                style = MaterialTheme.typography.bodySmall
            )
            SectionTitle("规则组")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前组：", maxLines = 1)
                val gLabels = state.groups.map { it.name }
                val gSel = state.groups.indexOfFirst { it.id == state.selectedGroupId }
                EnumDropdown("选择组", gLabels, gSel) { idx ->
                    vm.selectGroup(state.groups[idx].id)
                }
            }
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("新规则组名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.addGroup(groupName); groupName = "" }, modifier = Modifier.fillMaxWidth()) {
                Text("新建规则组", maxLines = 1)
            }
            state.groups.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.name + if (g.enabled) "" else "（停用）",
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    TextButton(onClick = { vm.toggleGroup(g) }) {
                        Text(if (g.enabled) "停用" else "启用", maxLines = 1)
                    }
                    TextButton(onClick = { vm.deleteGroup(g) }) { Text("删除", maxLines = 1) }
                }
            }

            val gid = state.selectedGroupId
            val rules = state.rules.filter { it.groupId == gid }
            SectionTitle("已保存规则（" + rules.size + " 条）")
            if (rules.isEmpty()) {
                Text("还没有规则；点下方「新建规则」开始。", style = MaterialTheme.typography.bodySmall)
            }
            rules.forEach { r ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (r.enabled) "" else "（停用）") + r.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onApply(r) }) { Text("应用", maxLines = 1) }
                        TextButton(onClick = { onEdit(r) }) { Text("编辑", maxLines = 1) }
                        TextButton(onClick = { vm.duplicateRule(r) }) { Text("复制", maxLines = 1) }
                        TextButton(onClick = { vm.deleteRule(r) }) { Text("删除", maxLines = 1) }
                    }
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Text("+ 新建一条规则", maxLines = 1)
            }
            if (gid != null) {
                Button(onClick = { onApplyGroup(gid) }, modifier = Modifier.fillMaxWidth()) {
                    Text("应用整个规则组（按组内顺序）", maxLines = 1)
                }
            }
            if (state.lastApplied.isNotBlank()) {
                Text("上次应用：" + state.lastApplied, style = MaterialTheme.typography.labelSmall)
            }
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
            if (state.error.isNotBlank()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 「查看匹配记录」：写之前的命中预览与前后对比，不是规则详情。 */
@Composable
private fun RuleMatchContent(vm: RuleViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回列表", maxLines = 1) }
            Text(
                "查看匹配记录",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "这里列出满足条件的记录与前后对比，不是规则详情；改规则请回列表点「编辑」。",
                style = MaterialTheme.typography.bodySmall
            )
            if (state.message.isNotBlank()) {
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
            if (state.error.isNotBlank()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.preview.isNotEmpty()) {
                Text(
                    "前后对比（前 10 条，共 " + state.preview.size + " 条）",
                    style = MaterialTheme.typography.titleSmall
                )
                state.preview.take(10).forEach { c ->
                    Text(
                        "#" + c.before.id + " 分类 " + c.before.categoryId + " → " + c.after.categoryId +
                            " / 对方 " + c.before.counterparty + " → " + c.after.counterparty,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Button(onClick = { vm.commit() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("确认写入 " + state.preview.size + " 条", maxLines = 1)
                }
                TextButton(onClick = onBack) { Text("取消，不写入", maxLines = 1) }
            } else {
                Text("当前没有待确认的匹配记录。", style = MaterialTheme.typography.bodySmall)
            }
            if (state.applied.isNotEmpty()) {
                Button(onClick = { vm.revert() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("一键撤销 " + state.applied.size + " 条", maxLines = 1)
                }
            }
            if (state.lastApplied.isNotBlank()) {
                Text("上次应用：" + state.lastApplied, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
