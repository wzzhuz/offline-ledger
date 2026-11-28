# 第 93 批：虚拟台账页重做与明细可见性收口

## 真机反馈（v0.2.71）

1. 台账「记收礼 / 记还礼」新增的记录**在主账本明细列表看不到**；台账里能看到，
   但**没有任何编辑 / 删除入口**，记错了不能改、不能删。
2. 台账页与主账本差距很大：新增按钮和流水混在同一条列表里，也没有自己的统计页。
3. 最上面账本切换框太占位置。

## 要解决什么

| # | 问题 | 根因 | 处理 |
|---|---|---|---|
| 1 | 台账流水在主账本明细不可见 | 第 91 批在 `RecordDao.pagedFiltered` 追加了 `NOT EXISTS (... LIKE '台账:%')` 排除条件，属过度隔离：「不计入收支」本就保证报表零污染 | **撤回排除 SQL**，流水回明细，靠 `excludeFromStats` 标记区分 |
| 2 | 台账记录全局不能改 / 删 | `VirtualBookScreen` 流水行是纯 `Text`，无 `clickable`、无 `onEdit` / 删除回调；叠加明细排除 → 数据失联 | 页内补编辑（复用全屏 `RecordEditorDialog`）与删除（二次确认 + 既有 `deleteRecord`） |
| 3 | 页面只做到草稿级 | 按钮 + 说明 + 按人卡 + 流水全塞进一条 `LazyColumn` | 重做成「标题 / 总览卡 / 分段 chip / 内容 / 底部固定录入区」 |
| 4 | 顶栏太占位置 | `Surface + statusBarsPadding + FilterChip + 说明文字` 在 4 个主 Tab 常驻一整行 | 改成明细页月份卡标题行里一枚 36dp 图标按钮，其余 Tab 不常驻 |

## 用户拍板

1. **1A** 恢复主账本明细显示台账流水（撤排除 SQL）
2. **2A** 页内总览卡 + 往来汇总 / 流水两段，不做独立统计页
3. **3B** 只在明细页标题行放一枚图标按钮，其他 Tab 不常驻

编辑 / 删除、按日期分组、底部固定录入区按无争议项随 change 一起落地。

## 不改什么

- 表结构（Room version 仍 9）、备份 DTO、CSV 格式、12 张 Entity
- 4 Tab 骨架、统计模块（MUST NOT 新增台账 scope）、自动记账落库位置
- 虚拟账本定义存储口径（SharedPreferences）、标签前缀 `台账:`、改名不改标签
- 「切换只切浏览范围、不携带录入归属」「自动记账永远落主账本」两条核心口径

## 影响

- `reporting-ui`：4 条 MODIFIED + 1 条 ADDED
- `RecordDao` / `VirtualBookScreen` / `VirtualBookSwitcher` / `DetailScreen` / `LedgerApp`
- `check_virtual_books.py` 断言反转（排除 → MUST NOT 排除）
- `project.md`、`docs/页面优化经验与避坑指南.md`

零改表、零新增依赖 / 权限 / `INTERNET`，**可直接覆盖 v0.2.71**。
