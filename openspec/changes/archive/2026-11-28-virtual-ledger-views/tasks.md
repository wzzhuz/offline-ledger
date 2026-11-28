# 任务清单（第 91 批）

## 实现

- [x] `VirtualBookCodec` / `VirtualBookStore`：纯 JVM 编解码、SharedPreferences 存储、
      统一标签前缀、改名不改标签、id 去重与排序规范化。
- [x] `VirtualBookDao`：按人分方向聚合 + 台账流水列表，两条 SQL 均不过滤不计入。
- [x] `AppDatabase` / `LedgerDi` 注册 DAO，version 仍 9、零新增 Entity。
- [x] `VirtualBookSwitcher`：4 Tab 顶部切换器，只切浏览范围、默认回主账本。
- [x] `VirtualBookScreen`：按人收 / 还 / 待还、流水列表、记收礼 / 记还礼入口。
- [x] `VirtualBookSettingsScreen`：新建 / 重命名 / 归档 / 排序 / 不计入开关。
- [x] `RecordEditorDialog` + `RecordEditorViewModel.applyPreset`：仅台账录入预填。
- [x] `LedgerApp` 新增两条路由与预填状态；`SettingsScreen` 新增入口。
- [x] `RecordDao.pagedFiltered` 排除台账标签，显式按标签筛选可查回。
- [x] `VirtualBookCodecTest` 4 个 JVM 单测；`check_virtual_books.py` 并注册 build.yml。
- [x] 版本号 0.2.69 → 0.2.70；两处硬编码版本断言同步。
- [x] `project.md` 补「虚拟台账视图不属永久不做」界定句与第 91 批决策记录。
- [x] delta 合并进 `reporting-ui` / `ledger-transactions` 主 spec，逐块比对正文一致。

## 真机待验（CI 全绿不替代观感验收，仍全部 `[ ]`）

- [ ] 顶部切换器在 4 个 Tab 是否都显示、是否挤压明细页首屏
- [ ] 新建「结婚礼金」→ 记收礼填对方与金额 → 主账本月度收入是否零污染
- [ ] 台账页按人显示 收 / 还 / 待还，收礼与还礼是否归到同一个人
- [ ] `CounterpartyNormalizer` 别名归一后「张三 / 张三哥」是否并成一行
- [ ] 切换器停在台账视图时，分享一笔支付宝原文是否仍落主账本（最关键回归项）
- [ ] 主账本明细默认看不到台账记录；按标签筛选后是否能查回
- [ ] 加密备份导出 → 恢复后端到端：不计入标志与标签是否仍在，重建同名视图能否恢复查看
- [ ] 设置页关掉「不计入」后，收礼是否如预期让月度收入虚高
- [ ] 改名后历史流水是否仍归入该视图
- [ ] 深色下切换器与台账卡片观感；v0.2.69 覆盖安装零回归

## 第 92 批修复（修 bug，不新建 change）

- [x] 台账预填并入 `load()` 协程内一次性合并，删除外部同步 `applyPreset`。
- [x] 预填 INCOME 时按该 type 取启用一级分类，避免保存被类型校验拦截。
- [x] `addPickedTag` / `visibleTagNames` 纯函数出口 + 标签选择器新建项即时可见可反选。
- [x] `TagNamesTest` 5 个 JVM 单测；`check_virtual_books.py` 加防回退断言。
- [x] 版本号 0.2.70 → 0.2.71；主 spec 补实现约束、归档 design 追加修订注记。
