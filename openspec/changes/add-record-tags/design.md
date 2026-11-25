# 设计 · 账单多标签（第 75 批 / v0.2.54）

## 1. 数据模型（Q1A）

```
tags(id PK autoinc, name TEXT NOT NULL UNIQUE, sortOrder INT, isArchived INT, createdAt INT)
record_tags(recordId INT, tagId INT, PRIMARY KEY(recordId, tagId),
            FK recordId -> records.id ON DELETE CASCADE,
            FK tagId    -> tags.id    ON DELETE CASCADE,
            INDEX(tagId))
trash.tagNames TEXT   -- 删除时的标签名快照，逗号分隔；恢复时按名重新绑定
```

- 关联表而非单列串：`TagDao.replaceForRecord(recordId, tagIds)` 整体替换，
  `TagDao.tagsForRecord` / `tagsForRecords(ids)` 批量取，避免 N+1。
- **标签名 MUST NOT 含逗号**（新建/改名/导入时统一校验并拒绝），
  这是 CSV / Firefly / trash 三处用逗号分隔的公共前提。
- 回收站不建第二张关联表：`deleteRecord` 前把标签名写成 `tagNames` 存进 `TrashEntryEntity`；
  `restoreRecord` 后按名 `TagDao.ensureByName` 重新建/复用标签再写关联。
  理由：trash 的 `originalId` 与 records 主键同值域，若也用外键 CASCADE，
  删流水时会连带把 trash 快照清空，恢复必丢。
- 标签是**全账本级**（与一羽「标签不区分账本」一致），不按收支类型隔离。

## 2. 预置与升表（Q3A / Q4A）

- `SeedCallback.onCreate` 追加 3 条 `INSERT INTO tags`（给老婆 / 家用 / 旅行）。
  onCreate 只对全新库生效，正好与 Q4A「卸载重装」一致；**MUST NOT** 写「覆盖安装也能看到」。
- `AppDatabase.version = 8`，`entities` 追加 `TagEntity` / `RecordTagCrossRef`，
  新增 `abstract fun tagDao(): TagDao`。`Migrations.ALL` 仍为 `emptyArray()`，
  `MigrationContractTest` 的 `ALL.isEmpty()` 断言一字不改。
- `SeedData.allInsertStatements()` 的 categories INSERT 列清单不变（预置分类零改动）；
  标签预置单独走 `SeedCallback`，避免 `SeedCatalogTest` 的逐行一致断言被破坏。

## 3. 四条往返（Q2A）

| 链路 | 表示 | 落库 |
|---|---|---|
| 加密备份 | `BackupPayload.tags: List<BackupTag>` + `BackupRecord.tagNames: List<String>` | 导入按名 `ensureByName` 后写关联；旧备份缺键 → 空列表 |
| CSV | `CsvRow.tags: String`（逗号分隔，最多 8） | `HEADER` 末尾追加 `tags`，列进 optional 集合；`duplicateKey()` 不含 tags |
| Firefly | `FireflyEntry.tags`（由 `FireflyRow.tags` 透传） | `importFirefly` 写流水后按逗号拆分落标签；`note` 仍只保留 `notes` 原文 |
| 回收站 | `TrashEntryEntity.tagNames` | 删除存快照、恢复重绑 |

- CSV 列顺序加在末尾，与第 43 / 63 / 67 批「导出必带、导入缺列按空串」同一套兼容做法。
- `CsvCodec.parseImport` / `parseRebuild` / `detectFormat` 三处 optional 集合同步加 `tags`，
  防「改了导出没改解析」。
- 加密备份 DTO 新增字段一律带默认值，`ignoreUnknownKeys = true` 保持，
  旧备份与旧快照零改动可解析。

## 4. UI 与统计（Q2A / Q3A）

- **记一笔**：`EditorState.tagNames: List<String>`；`load()` 编辑态回填；
  `save()` 在 `insertRecord/updateRecord` 之后按返回的 id 调 `TagDao.replaceForRecord`。
  新建时 `insertRecord` 已返回 `Long`，无需改签名。多选弹层复用
  「搜索 + 左分组导航 + 右网格」的心智，但**不复用** `CategorySelectDialog`
  （它是按收支类型过滤、按 id 映射分类的契约，标签是全账本级、按名匹配）。
- **筛选**：`FilterValues` / `RecordFilter` 加 `tagIds: List<Long>`，
  `RecordDao.pagedFiltered` 用 `EXISTS (SELECT 1 FROM record_tags rt WHERE rt.recordId = r.id AND rt.tagId IN (:tagIds))`。
  MUST NOT 用 `LIKE`、MUST NOT 改既有聚合 SQL。
- **显示**：`RecordRow` 加 `tagNames: String`（SQL 用 `GROUP_CONCAT` 子查询或应用层批量取），
  明细行与详情页渲染 chips；统计页下钻 sheet 同款。
- **统计标签维度**：`TagDao.tagStats(fromYm, toYm, currencyCode)` 按标签聚合
  命中金额与笔数，卡片给相对条形；**MUST NOT 算百分比**，并在卡片上写一行
  「一笔可含多个标签，合计可能大于总支出」。
- **标签管理页**：`composable("tags")` + `TagManagerScreen(onBack)`，
  `SettingsScreen` 的「账本管理」组加一行入口。改名、归档、排序走 `SettingsViewModel`，
  与 `CategoryManagerScreen` 同构但不共享状态。

## 5. 明确不动

`CategoryEntity` / `CategoryDisplay` / `default_categories.json` / `SeedData.categories` /
`AccountEntity` / `RecordEntity` 既有列 / `colorHex` / 统计 `SUM(amountCents)` 口径 /
`CsvRow.duplicateKey()` / `Records importSource+sourceId` 唯一索引 /
`LedgerTheme` / `build.yml` 结构 / `Migrations.ALL` / 零 `INTERNET` / 零新增依赖。

## 6. 与活跃 change 的交叉（登记协调，不碰它们的逻辑）

- `ff3-description-field`：正在扩筛选/规则字段目录 → 本批只加 `tagIds`，
  MUST NOT 改它的字段描述映射。
- `detail-filter-and-transfer-edit` / `detail-scope-and-record-page`：正在改明细筛选默认值，
  `applyFilter` 只追加 `tagIds` 一行 copy，保留它们的 `periodDisplayName` / `resolveAllMonths` 语义。
- `record-category-picker-and-root-select`：分类选择器仍在改；标签弹层独立实现，不复用不阻塞。
- `restructure-preset-categories`：预制分类清单由它负责；本批逐字段零改动（Q5A 利息不动）。

## 7. 风险与回退

- **最高风险是升表**：不注册迁移 → 覆盖安装开库失败。交付说明 MUST 写
  「先导出备份、再卸载重装」，CI 全绿不替代真机迁移验收。
- 关联表双向 CASCADE：删标签会清所有关联（归档而非物理删更安全，管理页默认归档）。
- 多标签统计不给百分比，避免「合计 130%」这类误导。
- 标签名禁用逗号；导入时含逗号的名字 MUST 拒绝并列入导入报告，不静默截断。
