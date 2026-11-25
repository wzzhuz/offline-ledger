# ledger-transactions · Delta Specification

> 第 75 批：账单多标签。2 条 ADDED + 2 条 MODIFIED（整块替换基线过时条文）。

## ADDED Requirements

### Requirement: 账单标签采用关联表多对多模型

系统 SHALL 建立 `tags` 定义表与 `record_tags` 关联表，`record_tags` 以
`(recordId, tagId)` 为复合主键，两个外键 SHALL 指向 `records.id` 与 `tags.id`
且均为 `ON DELETE CASCADE`，并 SHALL 建 `tagId` 索引。
一笔流水 SHALL 可挂多个标签，一个标签 SHALL 可挂多笔流水；标签为全账本级，
MUST NOT 按收支类型隔离，MUST NOT 影响分类的两级互斥模型与 `rootCategoryId`。
标签名 SHALL 唯一，MUST NOT 含逗号（CSV / Firefly / 回收站快照均以逗号分隔），
新建、改名与导入时 SHALL 统一校验并拒绝含逗号的名字，MUST NOT 静默截断。
删除流水 SHALL 由级联清掉关联；标签管理页默认 SHALL 用归档而非物理删除。

#### Scenario: 一笔流水挂多个标签

- **GIVEN** 一笔「人情社交」支出
- **WHEN** 记一笔时选了「给老婆」与「家用」
- **THEN** `record_tags` 有两条关联，分类仍只有一个
- **AND** 两个标签都能在各自的标签筛选里查到这笔

#### Scenario: 转账也能挂标签

- **GIVEN** 一笔转账无分类
- **WHEN** 打上「给老婆」标签
- **THEN** 关联正常写入，转账仍不进分类统计

#### Scenario: 标签名含逗号被拒

- **WHEN** 新建或导入名为「家用,旅行」的标签
- **THEN** 报错并列入导入报告，MUST NOT 被拆成两个、MUST NOT 静默截断

#### Scenario: 改名不产生重复

- **GIVEN** 标签「家用」已挂在 12 笔流水上
- **WHEN** 在管理页改名为「家庭」
- **THEN** 12 笔流水全部显示「家庭」，`tags` 表仍只有一行

### Requirement: 记一笔支持多选标签并回填编辑态

记一笔 SHALL 提供多选标签入口，一次可选多个标签并允许在弹层内新建标签；
编辑已有流水时 SHALL 回填该流水的标签，保存后 SHALL 整体替换关联
（`TagDao.replaceForRecord`），MUST NOT 追加导致重复。
新建时 SHALL 复用 `insertRecord` 已返回的 `Long` 作为 recordId，
MUST NOT 为此改动 `insertRecord` 签名或 `RecordEntity` 既有列。
标签弹层 MUST NOT 复用 `CategorySelectDialog`（该组件按收支类型过滤、
按分类 id 映射，与标签的全账本级 / 按名匹配契约不同）。

#### Scenario: 新建多标签

- **WHEN** 新建支出并选 3 个标签后保存
- **THEN** `record_tags` 恰有 3 行，重试保存不会变成 6 行

#### Scenario: 编辑回填

- **GIVEN** 一笔已有 2 个标签的流水
- **WHEN** 打开编辑
- **THEN** 两个标签处于选中态，取消一个再保存后只剩 1 行关联

### Requirement: 组合筛选支持按标签且不改既有聚合 SQL

`FilterValues` / `RecordFilter` SHALL 增加标签条件，
`RecordDao.pagedFiltered` SHALL 用 `EXISTS` 子查询按 `record_tags` 过滤，
MUST NOT 用 `LIKE` 模糊匹配标签，MUST NOT 修改既有 `SUM(amountCents)` 聚合 SQL
与统计口径唯一出口。无标签条件时 SHALL 与现在行为完全一致。
明细行、详情页与统计下钻 sheet SHALL 显示该笔的标签 chips，
MUST NOT 挤压金额与账户显示名。

#### Scenario: 按标签筛选

- **GIVEN** 「家用」标签下 8 笔流水分散在餐饮 / 居住 / 人情社交
- **WHEN** 筛选选「家用」
- **THEN** 列表恰为这 8 笔，跨分类都出现

#### Scenario: 名字相近不误命中

- **GIVEN** 存在标签「家用」与「家用支出」
- **WHEN** 只筛「家用」
- **THEN** MUST NOT 出现只挂「家用支出」的流水

## MODIFIED Requirements

### Requirement: 稳定版前重置 Room 版本且不注册迁移

稳定版前 `AppDatabase version` SHALL 为 `8`（第 75 批：新增 `tags` / `record_tags`
两张表并由 `trash` 增加 `tagNames` 列），`Migrations.ALL` SHALL 为空数组，
系统 MUST NOT 注册任何 `Migration`。迁移 SQL 常量 MAY 保留供测试与历史追溯，
但 MUST NOT 被 Room 在运行时执行。
`MigrationContractTest` 的 `ALL.isEmpty()` 断言 SHALL 保持不变。
因未注册迁移，覆盖安装 SHALL 明确要求先导出备份、卸载、再全新安装，
文档 MUST NOT 声称可直接覆盖升级；CI 全绿 MUST NOT 替代真机迁移验收。

#### Scenario: 全新安装含标签表

- **GIVEN** 设备未装过本应用
- **WHEN** 首次打开
- **THEN** `onCreate` 建出 `tags` / `record_tags` 且种入 3 个预置标签
- **AND** 不执行任何迁移 SQL

#### Scenario: 覆盖安装需卸载

- **GIVEN** 设备已装 version 7 的包
- **WHEN** 直接覆盖安装本版本
- **THEN** 明确要求先用 CSV / 加密备份带走数据再卸载重装
- **AND** 文档不得声称可直接升级

### Requirement: 预置数据来源与数据库版本

预置分类 SHALL 来自随包 JSON 而非 Kotlin 常量；`AppDatabase.version` SHALL 为 `8`
（第 75 批订正：旧文写 5 为文档债）。`SeedCallback.onCreate` 只对**全新库**生效，
更换预制清单或新增预置标签 MUST 全新安装，MUST NOT 声称覆盖安装或导入旧备份后能生效。
本批 `SeedData.categories` 与 `default_categories.json` SHALL 逐字段零改动
（「收入 → 理财收益 → 利息」保持现状，不提为一级）。

#### Scenario: 利息保持二级

- **WHEN** 检查预制分类清单
- **THEN** `id=33001 利息` 仍是「理财收益」下的已启用二级分类
- **AND** 清单与 v0.2.53 逐字段一致

#### Scenario: 预置标签只在全新库

- **GIVEN** 全新安装的 v0.2.54
- **WHEN** 首次进入标签管理页
- **THEN** 可见「给老婆 / 家用 / 旅行」三个预置标签
- **AND** 使用者归档过的标签 MUST NOT 在下次启动被复活
