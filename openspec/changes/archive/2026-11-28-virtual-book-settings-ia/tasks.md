# 第 95 批 Tasks

## 实现

- [x] `VirtualBookStore.remove(id)`：只删视图定义，`filterNot` + `normalize`，MUST NOT 碰 records / DAO / 回收站；注释写明「流水不删、同名可重建」。
- [x] 新增 `VirtualBookCreateScreen`：名称 + 三个模板 chip + 模板说明 + 创建按钮，复用 `VirtualBookTemplate.hint`，创建成功回列表。
- [x] 重写 `VirtualBookSettingsScreen`：删掉内嵌新建表单；标题行右上角「+」接 `onCreate`；每张卡片七项操作收进右侧「⋮」。
- [x] 删除触发用 `pendingDelete + LaunchedEffect(menu)` 解耦（第 60 批 Q2A 坑），确认弹窗四要素含「不删流水 / 可同名重建」。
- [x] `LedgerApp` 注册 `composable("virtual-book-create")`，列表页 `onCreate` 接路由。
- [x] `SettingsScreen` 补 4 个 `SectionTitle` 小节标题并重排 8 张卡片；route 字符串与 TextIntakeActivity 入口逐字不动。
- [x] 修 `Box` 用 `foundation.layout` 而非 `material3`。

## 规格与脚本

- [x] `reporting-ui` 1 条 MODIFIED + 2 条 ADDED，逐块比对与主 spec 正文一致。
- [x] `VirtualBookCodecTest` 扩 2 个 JVM 单测（tagFor 同名重建可重新聚合 / 删定义后其他视图与排序稳定），共 12 个 @Test。
- [x] `check_virtual_books.py` 迁移第 94 批设置页断言到新建页，新增第 95 批断言（新建独立、⋮/删除/pendingDelete、remove 不碰流水、确认口径、4 分组、8 route 未丢、路由已注册）。
- [x] 版本号 0.2.73 → 0.2.74（`build.gradle.kts` / `check_record_layout.py` / `check_text_intake.py` / `check_virtual_books.py`）。
- [x] `project.md` 追加第 95 批记录；`docs/页面优化经验与避坑指南.md` 追加 §7.75。
- [x] 四件套归档到 `openspec/changes/archive/2026-11-28-virtual-book-settings-ia/`。

## 验证

- [x] 14 个静态检查全 PASS
- [x] `testDebugUnitTest` + `assembleDebug` + `assembleRelease`（lintVital）全绿
- [x] 出包 CI「上传到坚果云」step success
- [x] 按 commit sha 逐文件比对关键文件一致

## 真机待验（CI 全绿不替代观感验收）

- [ ] 设置 → 虚拟账本：页面只有总开关 + 列表，没有名称输入框；右上角「+」跳独立新建页
- [ ] 新建页三个模板 chip 说明正确；创建成功后回到列表且新视图出现
- [ ] 每张卡片右侧「⋮」七项都在，一行放下，不再占两行
- [ ] ⋮ → 删除：弹窗能弹出（不复现「点删除没弹窗」），写明不删流水 / 可同名重建
- [ ] 删除已记过 3 笔的视图后，主账本明细仍能看到那 3 笔、余额不变
- [ ] 再建同名视图，台账页重新聚合出那 3 笔
- [ ] 设置主菜单四组标题齐全，8 个入口逐一点开都能进（路由零回归）
- [ ] 「关于」组隐私基线文案仍在
- [ ] 从 v0.2.73 覆盖安装零回归；4 Tab / 明细 / 记一笔 / 自动记账零回归
- [ ] 切进任一虚拟台账后分享一笔支付宝原文，仍落主账本
