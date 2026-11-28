# 第 95 批 Design：虚拟账本设置页 IA + 设置菜单分组

## 决策 1：新建拆成独立页，列表页只留一个「+」

`VirtualBookCreateScreen` 是独立 composable 路由 `virtual-book-create`：
名称输入框 + 三个模板 chip + 模板说明 + 创建按钮。列表页标题行右侧放
`IconButton(Icons.Filled.Add)`，`onCreate` 走 `navController.navigate`。

新建成功 `onCreated()` 直接 `goBack()`，列表页靠 `tick` 重读 prefs。
模板说明复用 `VirtualBookTemplate.hint`，与「配置」弹窗同源，不复制文案。

**否决**：列表页内折叠成一块「展开才显示」（还是混在一屏）；顶部弹底部 sheet
（项目里 `BottomPickerSheet` 是记账/模板用的，塞新建表单语义不对）。

## 决策 2：七项操作收进「⋮」，照抄账户列表的解耦写法

`VirtualBookCard` 用 `IconButton(Icons.Filled.MoreVert) + DropdownMenu`，
菜单项：查看 / 配置 / 重命名 / 归档(恢复) / 上移 / 下移 / 删除。
两行文本按钮压成一行，与 `AssetsScreen.AccountCard`（第 60 批）同款。

**删除触发 MUST 用 `pendingDelete + LaunchedEffect(menu)` 解耦**。
第 60 批 Q2A 真机反馈「点删除没弹窗」：DropdownMenu 的 Popup dismiss 与
AlertDialog 新窗口同帧竞争。直接在 `onClick` 里 `menu=false; onDelete()`
会复现这个坑，所以延迟到 menu 完全关闭之后触发。

## 决策 3：`remove()` 只删定义，流水与标签都不动

```kotlin
fun remove(id: String) {
    save(all().filterNot { it.id == id })
}
```

`VirtualBookStore` 只持 `SharedPreferences`，MUST NOT import 任何 DAO /
`LedgerRepository`、MUST NOT 写 `DELETE FROM`、MUST NOT 碰回收站。

理论依据：虚拟账本从设计之初就是「一条带前缀的标签 + 一个独立视图」，
流水本身一直躺在 `records` 表。`VirtualBookCodec.tagFor(name)` 由名字稳定派生，
且 `normalize()` 对已有 `台账:` 前缀强制保留，所以**删定义再建同名 = 原地找回**，
不需要迁移、不需要改标签、不需要备份恢复。

确认弹窗四要素（沿用第 62 批「删除确认要能看出删的是哪张卡」）：
视图名 / 标签名 / 红字口径说明（不删流水 + 可同名重建）/「确认删除」。

## 决策 4：设置主菜单只加小节标题 + 重排，路由零改动

清点发现 `SettingsScreen` 本来就是 8 张 `SettingGroupCard`（第 4/15 批做过分组
重排），不是上一轮我说的「大 LazyColumn 平铺」。真正缺的是**小节标题**。

| 组 | 条目 |
|---|---|
| 账本与分类 | 账本 / 标签管理 / 虚拟账本 / 规则列表 |
| 数据与自动记账 | 数据与备份 / 自动记账·文本诊断 |
| 偏好与安全 | 偏好 / 安全 |
| 关于 | 隐私基线文字（原有 `SectionTitle("关于")` 保留） |

`SectionTitle` 已存在，直接复用。`onOpen(...)` 的 7 个 route 字符串与
`TextIntakeActivity.ACTION_TEXT_INTAKE_DEBUG` 内部入口**逐字不动**。
静态脚本逐条断言 8 个入口仍在，防分组重排丢项。

上一轮对齐里的「账户与资产」组没有落地：仓库里账户管理在资产 Tab 内，
设置页没有该入口，硬凑会是空组。已在 proposal 登记微调。

## 考虑过的其他方案（否决）

| 方案 | 否决理由 |
|---|---|
| 列表页内折叠新建表单 | 用户明确要「拆开，避免混杂」 |
| 删除时把带该标签的流水移入回收站 | 等于把视图当真账本，违背「标签聚合视图」定位，且会改余额 |
| 删除后在 prefs 留墓碑 / 软删字段 | 定义本来就不进备份，软删反而增加状态；同名重建是更简单的恢复路径 |
| 设置菜单改 NavHost 路由 / 合并二级页 | 扩面太大，本批只解决「观感乱」 |
| 给设置菜单换 LazyColumn + stickyHeader | 8 条固定项，`Column + verticalScroll` 足够，换容器无收益 |
| 新建页做成底部 sheet | 与 `BottomPickerSheet` 的记账语义不符 |

## 影响面核查

- `check_virtual_books.py`：第 94 批「设置页必须有 newTemplate / store.add」断言
  迁移到 `VirtualBookCreateScreen`；新增「列表页 MUST NOT 内嵌新建表单」、
  `remove()` 只删定义、删除确认四要素、4 个分组标题、8 个 route 未丢、
  新建页路由已注册等断言。
- 主 spec 1 条 MODIFIED + 2 条 ADDED，标题一律沿用，逐块比对一致。
- 零改表（version 仍 9）、零改备份 DTO / CSV / 预置分类清单、零新增依赖 /
  权限 / `INTERNET`、可直接覆盖 v0.2.73。
