# account-picker-layouts-and-shared-selectors

## 为什么

v0.2.61 真机两条反馈：

1. **账户选择想能切换布局**：「记一笔里面账户选择是不是参考一羽记账可以切换布局，
   当前我们只要两种就行了，全局排序，按账户类型分组的（排序继承分组排序）」。
2. **模板 / 周期记账还用老下拉**：「里面的分类选择和账户选择还是最老的下拉列表，
   和其他的不一样，也很难用，改成分类选择器；账户是否也做成一个通用的选择器，
   这样记一笔等地方都可以复用」。

清点后有两点值得先说清：

- **两套排序早就存在**：`quickOrder`（记一笔全局候选）与 `sortOrder`（资产页
  分组内）自第 60 批起严格分离（活文档 §1753）。本批**不引入新排序、不改落库
  口径**，只把同一份数据按两种口径渲染成一个可切换视图。
- **老下拉全仓只剩一处**：`DropdownSelector` 只在 `TemplateManagerDialog.kt`；
  `CategorySelectDialog` 早已是记一笔 / 明细筛选 / Firefly 导入共用的通用组件。
  真缺口是**账户选择器仍是 `RecordEditorDialog` 里的 private 函数**，别的页面复用不了。

顺带修一个真 bug：老下拉选中账户后无法清空，但 label 写着「（可留空）」。

## 改什么

- 新增 `ui/components/AccountSelectSheet.kt` 作为账户选择**唯一通用出口**：
  `AccountPickerLayout.GLOBAL`（`quickOrder`、不分类型、归档沉底，默认）与
  `GROUPED`（`AccountType` 四组固定序 + 组内 `sortOrder`）两种布局，顶部分段切换；
  布局存 `LedgerPrefs.accountPickerLayout`，**跨启动持久化**。
  每格 44dp `AccountIconBox` + 放大角标 + 次要小字，MUST NOT 显示余额；
  搜索按 `accountBaseLabel`，命中跨组平铺；`allowNone` 提供「不指定 / 全部账户」。
- `accountSecondaryLabel` 从 `RecordEditorDialog` 上提为 `ui.components` public
  唯一组合点，转账两卡继续读它。
- **记一笔**：`AccountPickerSheet` 改薄封装，只保留「回填哪一侧」与标题；
  移除第 81 批起不可达的 `!lockTarget` 转出/转入分段死代码。
- **模板 / 周期规则**：删掉全仓最后一份 `DropdownSelector`，分类接
  `CategorySelectDialog`（`allowAll` 复用为「不指定分类」），转出/转入接
  `AccountSelectDialog`（`allowNone` = 不指定账户）；表单参数从
  `List<Pair<Long,String>>` 改成 `List<AccountEntity>`。
- **明细筛选**：`FilterSheet` 的 `"account"` picker 分支换 `AccountSelectDialog`。
  统计页无独立账户筛选入口，账户排行下钻经 `DetailViewModel.filterByAccount`
  落到同一份 `FilterSheet`，故自动覆盖（使用者第 4 项 B）。
- 版本号 0.2.62；`check_record_layout.py` 同步断言目标并加 14 条第 83 批校验 +
  归档态「delta 必须已合并」；活文档 §7.66 / §8、`project.md` 登记。

## 不改什么

- **零改表**：`AppDatabase.version` 仍 9、`Migrations.ALL` 仍空、备份 DTO 零改动；
  本批可直接覆盖 v0.2.61。
- `CategorySelectDialog` 本体版式一字不动（`check_firefly_mapping.py` 锁着
  `columns = 4` / `allowAll`）；`quickOrder` / `sortOrder` 两套落库口径零改动。
- **活跃 change `record-category-picker-and-root-select` 不碰**：其 delta 里同标题
  ADDED 是早期 WrapRow 版，与主 spec 现状不一致；本批只 MODIFIED 主 spec，
  归档时以本批为最终语义收口（见 `design.md` 决策 6）。既有 Requirement 标题一律不改。
- 4 Tab、分类快捷栏、转账两卡、联想条、聚合 SQL、CSV/Firefly 往返、统计口径、
  零新增依赖 / 零 `INTERNET` / 零运行时权限 / 零新图标资源。

## 对齐结论（阶段 0，五项）

| # | 项 | 结论 |
|---|---|---|
| 1 | 要解决什么 | ① 账户 sheet 加两种可切换布局；② 撤最后一份老下拉，分类与账户都收口到通用选择器，供记一笔 / 模板 / 周期 / 明细 / 统计链路复用；顺带补「不指定」 |
| 2 | 改什么 / 不改什么 | 见上两节。改 `ui/components`（新增）、`ui/record`（薄封装）、`ui/templates`、`ui/detail`、Prefs、脚本与文档；不改选择器本体、两套排序口径、表结构、备份契约与活跃 change |
| 3 | 关键约束 | 三条铁律；§1753 两套排序 MUST NOT 混回去；§7.41 只借交互不借数据口径、等价适配写明；T12 不用实验 API；模板账户 MUST 可清空；沙盒不编译，走 Actions |
| 4 | 验收标准 | 12 静态检查 + 单测 + Debug + Release lintVital 全绿；真机确认两种顺序各自正确、跨启动记住、模板/周期回填与保存校验、「不指定」可用、明细与统计下钻同一面板、转账锁定目标零回归、坚果云 job step success |
| 5 | 被否决方案 | 第三种布局（使用者明确只要两种）；分组视图也按 `quickOrder`（重挖 §1753 的坑）；按 `AccountTemplates.group` 分组（无可继承排序）；改 `CategorySelectDialog` 版式（撞静态脚本）；本批顺手归档活跃 change（扩面）；详见 `design.md` |

**使用者四项拍板**：① 1A 默认全局排序；② 口径「对」（分组四组 + 组内 `sortOrder`、
全局 `quickOrder`、位置不同属预期）；③ 3B 跨启动持久化；④ 4B 连明细/统计一起换。

## 验收

`specs/reporting-ui/spec.md`（3 MODIFIED + 2 ADDED）、
`specs/privacy-security/spec.md`（1 ADDED）；真机清单见活文档 §7.66 的 §8。
**本批没有升表，可直接覆盖 v0.2.61。**
