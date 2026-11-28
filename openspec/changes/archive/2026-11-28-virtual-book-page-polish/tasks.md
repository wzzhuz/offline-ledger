# 第 93 批 Tasks

## 实现

- [x] `RecordDao.pagedFiltered` 删除第 91 批 `台账:%` 排除段，改注释说明撤回原因。
- [x] `VirtualBookSwitcher` 重写为 36dp 图标按钮 + DropdownMenu，去掉 Surface / 状态栏避让 / 说明文字。
- [x] `DetailScreen` 加 `virtualBookEntry` slot，`MonthOverviewCard` 第一行末尾渲染。
- [x] `LedgerApp` topBar 改为状态栏等高薄层；切换器只挂明细页；台账页补 `onEditRecord`。
- [x] `VirtualBookScreen` 重做五块结构：标题行 / 总览卡 / 分段 chip / 内容区 / 底部固定录入区。
- [x] 流水段按 `localDate` 分组 + 当日收/还小计；行可点进编辑、行尾二次确认删除。
- [x] 删除事务走 `LocalLifecycleOwner.current.lifecycleScope`，删除后 `reload` 触发重载。
- [x] 台账页标题行补 `statusBarsPadding()`（二级页 topBar 不渲染）。

## 规格与脚本

- [x] `reporting-ui` 4 条 MODIFIED + 1 条 ADDED，逐块比对与主 spec 正文一致。
- [x] `check_virtual_books.py` 断言反转 + 第 93 批能力断言；版本号 0.2.71 → 0.2.72。
- [x] `check_record_layout.py` / `check_text_intake.py` 版本号同步。
- [x] `project.md` 追加第 93 批记录；`docs/页面优化经验与避坑指南.md` 追加 §7.73。
- [x] 四件套归档到 `openspec/changes/archive/2026-11-28-virtual-book-page-polish/`。

## 验证

- [x] 14 个静态检查全 PASS
- [x] `testDebugUnitTest` + `assembleDebug` + `assembleRelease`（lintVital）全绿
- [x] 出包 CI「上传到坚果云」step success
- [x] 按 commit sha 逐文件比对关键文件一致

## 真机待验（CI 全绿不替代观感验收）

- [ ] 台账新增一笔后，主账本明细（无筛选）能看到，且带不计入收支标记
- [ ] 主账本月度收入 / 支出 / 预算合计仍不含台账流水
- [ ] 台账页「流水」段点行能进全屏编辑态，改金额保存后总览与按人汇总刷新
- [ ] 行尾删除二次确认后进入回收站、余额复原、不出现 `The coroutine scope left the composition`
- [ ] 「往来汇总 / 流水」两段切换正常，按日期分组与当日小计正确
- [ ] 上下滚动流水时「记收礼 / 记还礼」固定在底部
- [ ] 明细页月份卡标题行末尾那枚图标按钮可弹出账本列表；资产 / 统计 / 设置三个 Tab 不显示
- [ ] 切换后内容区不再被常驻条挤压；标题行在 360dp 窄屏不挤坏月份文字
- [ ] 台账页顶部状态栏不遮挡标题
- [ ] 切进台账后分享一笔支付宝原文，仍落主账本（不带台账标签）
- [ ] 加密备份往返后 `excludeFromStats` 与标签仍在
- [ ] 从 v0.2.71 覆盖安装零回归；4 Tab / 记一笔 / 明细 / 统计 / 设置无异常
