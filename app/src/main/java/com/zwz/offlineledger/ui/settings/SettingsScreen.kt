package com.zwz.offlineledger.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.offlineledger.ui.intake.TextIntakeActivity
import com.zwz.offlineledger.ui.theme.LedgerColors

/**
 * 第 4 批：设置页重排为分组列表 → 二级页，4 Tab 结构不变。
 *
 * 第 95 批：原来 8 张 SettingGroupCard 无小节标题顺序平铺，
 * 「账本 / 标签 / 虚拟账本 / 数据与备份 / 规则 / 自动记账 / 偏好 / 安全」
 * 功能上交错，观感乱。按功能补 4 个分组小节标题并重排。
 * **只动 item 顺序与小节标题**：不改任何 route 字符串、不删条目、
 * 不新增二级页、不动 4 Tab；TextIntakeActivity 的内部入口行为保持原样。
 */
@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "设置", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "按功能分组进入各自页面；所有数据只存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral
        )

        // ---- 组 1：账本与分类（怎么组织账） ----
        SectionTitle("账本与分类")
        SettingGroupCard(
            title = "账本",
            summary = "分类 / 模板 / 预算 / 回收站 / 健康自检"
        ) { onOpen("ledger") }
        SettingGroupCard(
            title = "标签管理",
            summary = "多标签 / 改名 / 归档 · 分类一笔一个，标签一笔多个"
        ) { onOpen("tags") }
        SettingGroupCard(
            title = "虚拟账本",
            summary = "礼金 / 装修等专项台账 · 视图切换 / 不计入收支 / 按人看往来"
        ) { onOpen("virtual-book-settings") }
        SettingGroupCard(
            title = "规则列表",
            summary = "保存的规则 / 编辑 / 应用到历史 / 一键撤销"
        ) { onOpen("bulk") }

        // ---- 组 2：数据与自动记账（数据怎么进出） ----
        SectionTitle("数据与自动记账")
        SettingGroupCard(
            title = "数据与备份",
            summary = "加密备份 / CSV 明文导出 / 账单导入 / Firefly III 迁移"
        ) { onOpen("backup") }
        SettingGroupCard(
            title = "自动记账 / 文本诊断",
            summary = "规则增补 / 粘贴原文试解析 / 自动与仅草稿开关 / 结果摘要"
        ) {
            context.startActivity(
                Intent(context, TextIntakeActivity::class.java).apply {
                    action = TextIntakeActivity.ACTION_TEXT_INTAKE_DEBUG
                }
            )
        }

        // ---- 组 3：偏好与安全 ----
        SectionTitle("偏好与安全")
        SettingGroupCard(
            title = "偏好",
            summary = "对账阈值 / 配色 / 默认币种 / 默认账户"
        ) { onOpen("prefs") }
        SettingGroupCard(
            title = "安全",
            summary = "应用锁 / PIN / 最近任务防泄露"
        ) { onOpen("security") }

        // ---- 组 4：关于 ----
        SectionTitle("关于")
        Text(
            text = "隐私基线：不声明 INTERNET 权限 · 关闭系统自动备份并排除数据库 · " +
                "本地库为明文（第 50 批起，自用可信设备）· " +
                "导出备份为口令加密，口令不落盘，由使用者自行保管。",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.Neutral,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LedgerColors.Neutral,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}
