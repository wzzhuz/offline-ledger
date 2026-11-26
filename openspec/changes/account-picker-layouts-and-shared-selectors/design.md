# design · account-picker-layouts-and-shared-selectors

## 决策 1：两种布局 = 项目已有的两套排序，不新造口径

活文档 §1753 记过一次很贵的坑：「排序之后记一笔看到的不是排在前面的」，
根因是把 `sortOrder`（资产页分组内）和 `quickOrder`（记一笔全局候选）混用。
第 60 批拆成两列后，两套口径一直各管各的：

| 视图 | 排序 | 来源 |
|---|---|---|
| `GLOBAL`（默认，1A） | `quickOrder`，不分 `AccountType`，归档沉底 | `RecordEditorViewModel` / `accountDao.allByQuickOrder()` |
| `GROUPED` | 组间 `ASSET → LIABILITY → PREPAID → INVESTMENT` 固定序；**组内 `sortOrder`** | `AssetsViewModel.moveAccount` / `commitAccountOrder` |

因此组件内显式 `sortedWith`，不依赖调用方传入顺序：
- 全局：`compareBy { isArchived }.thenBy { quickOrder }.thenBy { id }`
- 分组：同组内 `compareBy { isArchived }.thenBy { sortOrder }.thenBy { id }`
- 搜索非空：**统一按全局视图跨组平铺**（与 `CategorySelectDialog` 的
  「搜索跨组平铺」同口径），避免搜索时还套分组标题割裂结果。

使用者第 2 项确认「切换后同一张卡在两种视图里位置不同是预期」——
spec 里 MUST 写死，免得以后又被「顺手统一」回去。
组间顺序复用 `AccountType.displayName()`，MUST NOT 按 `AccountTemplates.group`
（「支付余额 / 银行储蓄卡 / …」）分组：那是模板清单的分组，没有可继承的排序。

按 §7.41 说清：本轮没有参考图，只借「一羽可以切换布局」这个**交互概念**；
一羽的分组很可能是「银行 / 支付 / 信用」自定义组，本 App 按既有 `AccountType`
四组，因为只有这套口径有「分组排序」可继承。

## 决策 2：账户选择器抽成唯一通用出口，与分类选择器对称

清点结果：`DropdownSelector` 全仓只在 `TemplateManagerDialog.kt`（1 定义 + 3 调用），
模板与周期规则**共用同一张 `QuickEntryForm`**；`CategorySelectDialog` 已是
记一笔 / 明细筛选 / Firefly 导入共用的通用组件；而账户选择器是
`RecordEditorDialog.kt` 的 private 函数 —— 这就是第 2 问的真缺口。

新组件 `ui/components/AccountSelectSheet.kt` 自带
`Dialog + 底部对齐 Surface(topStart/topEnd 20dp)` 壳，与 `BottomPickerSheet` 同构但
不跨包反向依赖 `ui.record`。MUST NOT 引 `ModalBottomSheet`（T12）、
MUST NOT 新做长按拖拽（§3.1，排序仍走 `account-quick-sort` 独立页与资产页）。
`accountSecondaryLabel` 上提为 public 唯一组合点，转账两卡继续读它，零重复。

每格沿用第 82 批：44dp `AccountIconBox` + 等比放大卡种角标 +
`尾号 xxxx · 储蓄卡/信用卡 · CNY` 次要行，**不显示余额**。
搜索按 `accountBaseLabel`（含卡号后四位），一键清空图标随组件一起下沉。
归档项由调用方决定是否过滤（记一笔传全部并沉底、筛选过滤 `!isArchived`），
组件不过滤，保持既有口径。

## 决策 3：薄封装只留「回填哪一侧」

`RecordEditorDialog` 的 `AccountPickerSheet` 签名保持不变
（`type / accounts / fromId / toId / initialTarget / lockTarget / onFrom / onTo`），
内部只算 `selectedId` 与标题，然后调 `AccountSelectDialog(allowNone=false, …)`。
转账态两卡点开 `lockTarget=true` 时标题仍是「选择转出账户 / 选择转入账户」。

原 `type == TRANSFER && !lockTarget` 的转出/转入分段自第 81 批起**不可达**
（两卡已锁定目标），第 81 批 spec 也写明 sheet 内 MUST NOT 再有分段 —— 本批移除
死代码，MUST NOT 为了「兼容」把它搬进通用组件。

## 决策 4：模板 / 周期复用通用选择器，并补「不指定」

老 `label = "转出账户（可留空）：…"` 的下拉**没有「未选」选项**，选中后无法清空，
但周期规则编辑时确实需要改回不指定。修法：

- 分类：`CategorySelectDialog(type = 当前收支类型, allowAll = true)`。
  `allowAll` 原本是筛选侧「全部分类」，这里**复用语义**为「不指定分类」
  （模板分类本就可留空）。入口写成「分类（可不指定）」、
  值是「不指定 / 具体分类名」。tile 文案沿用「全部分类」不改本体 ——
  如实登记：这是语义复用不是同款文案（§7.41 不假装 1:1）。
- 账户：`AccountSelectDialog(allowNone = true, noneLabel = "不指定账户")`，
  `onSelect` 直接把 `Long?` 写回 `fromAccountId` / `toAccountId`。
- 表单参数 `accountNames: List<Pair<Long,String>>` → `accounts: List<AccountEntity>`
  （VM 的 `state.accounts` 本来就是完整实体，第 82 批的图标/卡种信息直接可用）。
- 保存校验 `visibleCategories.none { it.id == categoryId }`、周期规则必填账户
  校验、TRANSFER 两账户不同校验**一字不动**。
- 入口用整行 `TextButton`（标签列 72dp + 值 + `›`），与 `FormRow` / `PickRow`
  同构，MUST NOT 退回 `DropdownMenu`。

`CategorySelectDialog` 本体零改动：`check_firefly_mapping.py` 锁着
`columns = 4` / `allowAll`，改版式会让 Firefly 导入侧的复用校验误报。

## 决策 5：4B 的「统计筛选」究竟在哪

全仓盘点：统计页（`ui/stats/`）**没有独立账户筛选面板**；账户排行下钻经
`StatsViewModel` → `DetailViewModel.filterByAccount` 落到**明细的 `FilterSheet`**。
因此把 `FilterSheet` 的 `"account"` picker 分支换成 `AccountSelectDialog`
（`allowNone=true`、`noneLabel="全部账户"`、`accounts.filter { !isArchived }`），
4B 的明细与统计链路就一起覆盖了。

`accountOptions` 保留用于 `PickRow` 的只读展示名（第 62 批卡号后四位口径），
只是不再传给 picker。`tag / root / child / currency` 四个 picker 分支零改动。
`CategorySelectDialog` 的分类分支也零改动。

若使用者真机指的是统计页另一个我们没找到的入口，按真机指认再开 change，
本批不猜、不扩面。

## 决策 6：活跃 change 冲突不碰，标题不改

活跃 change `record-category-picker-and-root-select` 的 delta 里躺着一条**同标题**
ADDED「分类全量选择器采用分组 chips 且不用下拉框」，正文是早期 WrapRow 版，
与主 spec 现在的「顶部搜索 + 左侧一级导航 + 右侧 4 列彩色图标网格」已不一致。
本批：

- **只 MODIFIED 主 spec 那条**（把覆盖面从记一笔/筛选扩到模板与周期规则），
  沿用既有标题（归档匹配基准），正文整块改写为最终行为。
- 活跃 change 一个字节不动，design 登记「归档该 change 时以本批 MODIFIED
  为最终语义收口」——与第 79 批处理「既有 Requirement 标题不改」同口径。
- 其余既有 Requirement 标题一律不改。

## 决策 7：持久化走 prefs，不升表

使用者第 3 项拍板 B（跨启动）。`LedgerPrefs.accountPickerLayout`
（`"global"` / `"grouped"`，默认 global）随 `rememberAccountPickerLayout()` 读写。
沿用项目既有偏好口径（§「偏好走 SharedPreferences」）：
不进 Room、不进备份 DTO、不触发 Migration，`AppDatabase.version` 仍 9、
`Migrations.ALL` 仍空。换设备回退默认全局视图，
`privacy-security` 加 1 条 ADDED 说明仅存本机、零网络零权限。
**本批可直接覆盖 v0.2.61**（与第 82 批不同，那批升表必须卸载重装）。

## 考虑过的其他方案

| 方案 | 为什么否决 |
|---|---|
| 第三种布局（按币种 / 按余额） | 使用者明确「只要两种」 |
| 分组视图也按 `quickOrder` | 等于把 §1753 的坑重挖一遍，且「排序继承分组排序」无从谈起 |
| 按 `AccountTemplates.group` 分组 | 模板清单分组，没有可继承的排序键 |
| 布局状态只活在本会话 / 弹层期间 | 使用者第 3 项选 B |
| 改 `CategorySelectDialog` 版式来统一两者观感 | 撞 `check_firefly_mapping.py` 静态锁，且扩面 |
| 把 `BottomPickerSheet` 上提到 components 供两边复用 | `RecordEditorDialog` 的 EXTRA/NOTE 与 `TemplateQuickPick` 仍在用，反向下沉会牵动第 80 批断言；新组件自带同构壳更稳 |
| 顺手归档活跃 change | 扩面，且有同标题冲突；本次只登记不碰 |
| 把活跃 change 的旧 ADDED 直接改掉 | 会改变进行中 change 的语义基线，按 §4.2 应留到它自己归档时收口 |

## 风险与真机待验

- 分组视图组内 `sortOrder` 与资产页是否**真的**一致（尤其 `LIABILITY` 组）。
- 搜索跨组平铺时，选中项在切换回分组视图后是否仍高亮可见。
- 模板复用 `allowAll` 的「全部分类」文案表示「不指定」，会不会让人误读。
- 周期规则把账户改成「不指定」后，保存校验 MUST 仍拦得住。
- 明细筛选「全部账户」与统计账户排行下钻是否确为同一份面板。
- 44dp 图标 + 分组标题在中小屏弹层里是否需要滚动两次。
- 全部列在活文档 §7.66 的 §8，保持 `[ ]`；CI 全绿不替代观感验收。
