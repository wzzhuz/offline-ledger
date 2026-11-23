# category-stats Delta（第 51 批）

## MODIFIED Requirements

### Requirement: 预置两级中文分类

系统 SHALL 在全新库创建时预置至少 7 个一级支出分类、3 个一级收入分类及若干二级分类；
第 30 批起首启为零账户，账户由资产页快捷模板创建，MUST NOT 再预置资产账户或信用负债账户。
第 51 批 SHALL 预置 15 个一级（支出 10 / 收入 5）、91 个二级，其中扩展二级 `enabled=false`
（在分类管理按需启用），停用一级下二级 SHALL 同步停用。

#### Scenario: 首次启动

- **GIVEN** 全新安装后首次创建数据库
- **WHEN** `SeedCallback.onCreate` 执行
- **THEN** `categories` 表存在 106 行预置数据，`accounts` 表为空
- **AND** 所有二级分类的 `type` 与父级一致、`level ∈ {0,1}`、无三级

#### Scenario: 换清单只在全新库生效

- **GIVEN** 设备上有 v0.2.33 及更早版本留下的库
- **WHEN** 覆盖安装 v0.2.34
- **THEN** 分类表不变（`onCreate` 不重跑）
- **AND** MUST NOT 声称新清单生效，装包说明 SHALL 要求全新安装且不导入旧备份

### Requirement: 预置分类清单可替换且支持启用开关

（原条款不变；仅把停用 Scenario 的示例一级由「养车」改为「宠物」，
因第 51 批养车改为默认启用。）

#### Scenario: 停用后记一笔不可选但统计仍计入

- **WHEN** 使用者停用一级「宠物」
- **THEN** 记一笔、筛选面板、预算、模板的分类选项不再出现「宠物」及其二级
- **AND** 统计占比卡、排行、历史流水与该分类的既有金额保持不变

#### Scenario: 重新启用

- **WHEN** 使用分类管理再次启用「宠物」
- **THEN** 该一级与其二级重新出现在录入侧，历史流水归属不变

### Requirement: 按 A 方案区分租房与代缴父母水电

「居住」一级 SHALL 只包含租房 / 自住产生的成本。代父母缴纳的水电 SHALL 归到
「**人情社交** / 赡养·代缴水电」，MUST NOT 与租房费用混在同一一级下。

## ADDED Requirements

### Requirement: 预置分类 ID 分配规则且 ID 无语义

随包 JSON 的 `id` SHALL 按：支出一级 `1..10`、收入一级 `30..34`、一级预留 `11..29`/`35..99`；
二级 `id = parentId × 1000 + 序号(1..999)`。系统 MUST NOT 从 ID 数值解析父子或收支类型，
树关系 SHALL 唯一由 `parentId` 表达，统计 SHALL 走 `records.rootCategoryId`。
一级与二级 ID 段 MUST NOT 重叠、全部 id MUST 全局唯一。
手工新建 / 导入新建 SHALL 由 AUTOINCREMENT 从历史 max+1 分配、MUST NOT 显式指定 id。
三处清单 SHALL 逐行一致，`SeedData.isPresetCategoryId` SHALL 由清单 id 集合生成。

#### Scenario: 加一级与二级不撞号

- **GIVEN** 未来新增一级与某父下新增多个二级
- **WHEN** 按预留段与父段内空位分配
- **THEN** 不重复、父子段不重叠，无需改表 / Migration / 重刷历史 `rootCategoryId`

**理由**：旧隐式规则 `父×100` 每父最多 99 个二级，父 1 第 101 个会撞父 2 第 1 个。
ID 数值对查询效率零影响（主键 B-tree / HashMap / GROUP BY 均与数值无关）。

### Requirement: AUTOINCREMENT 空洞不回填且删除策略可恢复

系统 MUST NOT 回填物理删除留下的 rowid 空洞。分类删除 SHALL 由
`CategoryDeletionPolicy.shouldArchive(linkedRecordCount, isPreset)` 判定：有流水或属于预制清单的
分类 MUST NOT 物理删，SHALL 转 `isArchived=1`；仅手工新建且无流水才可物理删。
删除确认弹窗 SHALL 明确提示「预制分类删除后不会自动补回，恢复标准清单需全新安装且不导入旧备份」。
归档区 SHALL 经 `CategoryDisplay.resolve` 显示「一级 / 二级」全名。

#### Scenario: 误删预制分类

- **GIVEN** 使用者误点删除一个无流水的预制二级分类
- **WHEN** `deleteCategory` 执行
- **THEN** 该行转归档、id 与父子关系保留
- **AND** 「已归档」区显示全名，点「恢复」即回到录入侧

#### Scenario: 外部导入产生大量不合理分类

- **GIVEN** 从 CSV / Firefly III 导入的分类大多已挂流水
- **WHEN** 使用者批量删除整理
- **THEN** 按既有规则转归档而非物理删，几乎不产生 ID 空洞
- **AND** 归档列表累积为已知且可接受限制，MUST NOT 为「整理干净」改写历史归属

**理由**：`onCreate` 只种一次、`Migrations.ALL` 为空、无「恢复默认」入口、
`restoreCategory` 对已删行影响 0 行；AUTOINCREMENT 永不重用正是引用不破的保证。

### Requirement: 分类导入必须整表替换

任何分类导入路径（含 `importBackup`）SHALL 先清空 `categories` 再按父先子后整表插回，
MUST NOT 按 id 增量合并、MUST NOT 按名称重映射、MUST NOT 只恢复流水而丢弃分类表。

#### Scenario: 旧备份导入新版

- **GIVEN** 使用者持有第 50 批及更早的加密备份
- **WHEN** 在 v0.2.34 全新安装后导入
- **THEN** 关联不错乱（备份内 id 自洽）
- **AND** 旧分类表整体覆盖新清单，新版预制分类不生效
- **AND** 装包说明 MUST 写明「恢复标准清单 = 全新安装且不导入旧备份」
