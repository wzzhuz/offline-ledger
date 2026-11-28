# 重构预制分类清单（v2）：宽一级、细二级、ID 规则显式化

## Why

使用者核查预制分类后认为一级不合理：「三餐」更接近二级；一级过细且维度混杂
（三餐按消费内容、养车按对象、人情按用途），记一笔要在三种判断里切，增加摩擦。
主流做法是「一级看结构，少而稳（6~9 个）；二级看原因，每类 3~5 个、只拆想看清的」；
钱迹官方统计里「三餐」恰是一级，本轮取其二级思路 + 主流宽一级原则，不照抄。

追加确认后锁定的三处事实：

1. 旧 ID 规则是隐式约定、无文档，且 `父×100+序号` 每父最多 99 个二级，父 1 第 101 个会撞父 2 第 1 个；
2. ID 数值对查询效率零影响，且备份自带完整分类表、`importBackup` 整表替换，ID 重编不用改备份代码；
3. AUTOINCREMENT 空洞不该回填；误删预制分类当前不可逆，需要防。

## What Changes

- 重写 `assets/default_categories.json`（v4，106 行）与其两处同构副本 / fallback：
  一级改名并按生活领域收敛，养车保留独立一级且默认启用，「三餐」降为 `餐饮 / 正餐`，
  扩展二级默认停用。
- ID 全表重编并写死规则：一级 `1..10` 支出 / `30..34` 收入 / `11..99` 预留；
  二级 `parentId × 1000 + 序号`。
- 新增纯 JVM `CategoryDeletionPolicy` 与 `SeedData.isPresetCategoryId`：预制分类一律转归档，
  删除弹窗补警告，归档区显示带父级前缀。
- 规格修订：`category-stats` 新增 ID 规则 / 空洞不回填 / 删除策略 / 导入整表替换四条 Requirement，
  顺手修「预置账户」「停用示例养车」「人情」三处文档债；`ledger-transactions` version 3 → 5；
  `backup-restore` 明确 MUST NOT 按 ID 增量合并。
- 版本号 `0.2.34`（已核实未被 tag / release / artifact 占用）。

## Capabilities

- `category-stats`：预置两级中文分类、清单可替换、分类管理与归档、ID 规则与删除策略（新增）
- `ledger-transactions`：预置数据来源与数据库版本
- `backup-restore`：覆盖导入的全量替换语义

## Impact

- **破坏性**：`SeedCallback.onCreate` 只对全新库生效 → 全新安装、测试数据重造，
  **不导 v0.2.33 及更早备份**。导入旧备份会把旧分类表整表覆盖回来，新清单不生效。
- **不改**：表结构、`AppDatabase.version`（仍 5）、`Migrations.ALL`（仍空）、统计口径、
  余额重算、导出加密、自动备份排除、应用锁、三条铁律、三个挂账 change。
- **文件**：清单三处 + `SeedData.kt` + `LedgerRepository.kt` + `CategoryManagerDialog.kt`
  + 新增策略类 + 4 个测试文件 + 3 份 spec + project.md / 活文档。
- **风险**：CSV / Firefly 按名称路径匹配，旧 CSV 会缺项；批量规则按名称存分类，改名后旧规则对不上。
  两者都因「不导旧数据」而不触发，写进装包说明。


---

## 第 90 批追加：v2 → v3（同一 change 收口，不另开分类 change）

**Why（追加）**：v2 的「居住」边界与主 spec 自相矛盾（话费 / 家具家电 / 保洁搬家
挂在只该含租房成本的「居住」下）；日常生成场景缺理发、快递、家政等最易漏记项；
家具家电归购物更合理；宠物应默认启用。

**What Changes（追加）**：新增支出一级「日常」(11)，按「房子 / 人 / 实物」三段收口；
话费 / 保洁搬家 / 家具家电三条换新 id 迁入；5004 改名「家居家纺」并与 5009 并存；
宠物与 7 个二级改默认启用；补齐 7 条日常/医疗二级；新增 `scissors` / `flower`
两个 Tabler 图标。清单 version 4 → 5、106 → 114 行、一级 15 → 16。

**Impact（追加）**：
- 清单三处 + `CategoryIconKeys` + 2 个 drawable + Catalog / Tints / NOTICE +
  `SeedCatalogTest` / `CategoryIconKeysTest` + 主 spec `category-stats` / `reporting-ui` +
  `project.md` / 提示词文档 / `RULE_PROMPT` / 活文档 §7.72 / 静态断言。
- **不改**：表结构（version 仍 9）、备份 DTO、CSV / Firefly 写路径、自动记账解析器、
  4 Tab、三条铁律、零新增 Gradle 依赖 / 权限 / `INTERNET`。
- **破坏性**：必须卸载重装且不导 v0.2.68 及更早备份；旧备份会整表覆盖回 v2 分类。

**分级**：改预制清单 + MODIFIED 主 spec 多条 Requirement，属需求变更；
但主题与本 change 完全同源，为避免两层 delta 打架，**并入本 change 一起收口**，
不新建第二个分类 change。本 change 仍保持活跃、不提前归档（真机待验）。
