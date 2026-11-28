# 第 94 批 Tasks

## 实现

- [x] 新增纯 JVM `VirtualBookTemplate`（人情往来 / 专项支出 / 自定义），定义按钮、汇总词、分段名、内置分类、默认分类过滤。
- [x] `VirtualBook` + `VirtualBookCodec` 扩 `template` / `defaultCategoryId`，编码 7 段，兼容旧 5 段、非法模板回退 GIFT。
- [x] `VirtualBookStore` 加 `setTemplate` / `setDefaultCategory`，`add` 带模板；修腐化注释。
- [x] `VirtualBookSettingsScreen` 新建选模板、已建视图「配置」改模板、非礼金模板设一次默认分类；修三处腐化文案。
- [x] `VirtualBookScreen` 全面按模板渲染：按钮数量 / 文案、总览措辞、分段名、按人卡词、当日小计词；`onRecord` 回传分类 id。
- [x] `LedgerApp` `VirtualEditorPreset` 加 `categoryId`，`onRecord` 两参版本接模板分类。
- [x] `RecordEditorDialog` 加 `presetCategoryId` 参数并纳入 `LaunchedEffect` key。
- [x] `RecordEditorViewModel.load` / `applyPresetTo` 加分类参数：按 id 精确命中（只校验存在 + type 一致，不要求 enabled），命中二级时 `expandedRootId` 指父级。
- [x] 修 VM 第 92 批注释里「主账本明细也不排除」的过期表述。

## 规格与脚本

- [x] `reporting-ui` 3 条 MODIFIED + 1 条 ADDED，逐块比对与主 spec 正文一致。
- [x] `VirtualBookCodecTest` 扩 6 个 JVM 单测（旧 5 段回退 / 7 段往返 / 非法回退 / 模板分类映射 / 专项只给支出）。
- [x] `check_virtual_books.py` 同步预填断言字面串 + 第 94 批能力断言 + 腐化文案 MUST NOT 残留；版本号 0.2.72 → 0.2.73。
- [x] `check_record_layout.py` / `check_text_intake.py` 版本号同步。
- [x] `project.md` 追加第 94 批记录；`docs/页面优化经验与避坑指南.md` 追加 §7.74。
- [x] 四件套归档到 `openspec/changes/archive/2026-11-28-virtual-book-scene-templates/`。

## 验证

- [x] 14 个静态检查全 PASS
- [x] `testDebugUnitTest` + `assembleDebug` + `assembleRelease`（lintVital）全绿
- [x] 出包 CI「上传到坚果云」step success
- [x] 按 commit sha 逐文件比对关键文件一致

## 真机待验（CI 全绿不替代观感验收）

- [ ] 新建「人情往来」视图 → 点「记收礼」：记一笔已带 INCOME +「其他收入 > 红包收入」，直接保存成功
- [ ] 点「记还礼」：已带 EXPENSE +「人情社交 > 礼金」，分类快捷栏展开到对应一级
- [ ] 新建「专项支出」视图 → 底部只有一枚「记支出」，总览显示支出 / 净支出，分段显示「汇总」
- [ ] 专项视图设一次默认分类后，每笔都自动带入，不用再选
- [ ] 切到「自定义」模板两个按钮文案正确；改回「人情往来」后自定义默认分类被清掉
- [ ] 从 v0.2.72 覆盖安装后老视图仍在、模板为「人情往来」、行为零回归
- [ ] 设置页不再出现「主账本明细默认不显示」「左上角切换器」
- [ ] 台账流水在主账本明细仍可见、可改可删；月度收入仍不虚高
- [ ] 切进任一虚拟台账后分享一笔支付宝原文，仍落主账本
- [ ] 4 Tab / 记一笔 / 明细 / 统计 / 设置零回归
