# 任务 · add-record-tags（v0.2.54）

## 阶段 0：对齐（本批已完成）

- [x] 0.1 核查标签在代码 / 文档 / 数据往返中的现状
- [x] 0.2 联网核查一羽 / Firefly III / 随手记 / 鲨鱼记账的标签与多标签支持
- [x] 0.3 五项对齐结论经使用者「按你拟的」放行（Q1A / Q2A / Q3A / Q4A / Q5A）
- [x] 0.4 四件套 + 第 10 个静态脚本落本地、自查全绿
- [ ] 0.5 **推送门禁**：目标仓库与文件清单回显，等使用者点头

## 阶段 1：数据层（门禁后）

- [ ] 1.1 新增 `TagEntity` / `RecordTagCrossRef` / `TagDao`（含批量取、整体替换、统计聚合）
- [ ] 1.2 `AppDatabase version = 8`、entities 与 `tagDao()`；`Migrations.ALL` 保持空
- [ ] 1.3 `SeedCallback.onCreate` 追加 3 条预置标签；`SeedData.categories` 零改动
- [ ] 1.4 `TrashEntryEntity` 加 `tagNames`；`toTrash` / `toRecord` 同步
- [ ] 1.5 `deleteRecord` 存快照、`restoreRecord` 重绑

## 阶段 2：四条往返（门禁后）

- [ ] 2.1 备份 DTO 加 `BackupTag` / `BackupRecord.tagNames`；Exporter / Snapshot / importBackup 同步
- [ ] 2.2 `CsvRow.tags` + `CsvCodec` HEADER / export / parseImport / parseRebuild / detectFormat
- [ ] 2.3 `exportCsvRows` / `importCsvRows` / CSV 重建落标签
- [ ] 2.4 `FireflyEntry.tags` 透传 + `importFirefly` 落标签；`note` 不再含标签串
- [ ] 2.5 更新 `docs/FireflyIII导入说明.md` 与 Firefly 预览页「不会导入标签系统」文案

## 阶段 3：UI 与统计（门禁后）

- [ ] 3.1 `EditorState.tagNames` / `load` 回填 / `save` 写关联 + 多选弹层
- [ ] 3.2 `FilterValues` / `RecordFilter` / `pagedFiltered` 的 EXISTS 子查询 + 筛选面板
- [ ] 3.3 `RecordRow.tagNames` + 明细行 / 详情页 / 下钻 sheet 的 chips
- [ ] 3.4 `TagDao.tagStats` + 统计页标签卡（不给百分比 + 明示口径）
- [ ] 3.5 `TagManagerScreen` + `composable("tags")` + 设置页入口 + ViewModel CRUD

## 阶段 4：验证与交付（门禁后）

- [ ] 4.1 10 个静态检查全 PASS、双 CI 全绿
- [ ] 4.2 版本号 → `0.2.54`，出包，tag / Release / digest 核对
- [ ] 4.3 实测回填（活文档 §7.57 / project.md），归档校验保持 `[ ]`

## 真机待验（§8，全部 `[ ]`）

- [ ] 5.1 记一笔多选、编辑回填、改名后历史同步、不产生重复标签
- [ ] 5.2 标签管理页增删改排归档、预置 3 个标签只在全新安装出现
- [ ] 5.3 明细按标签筛选、行内 / 详情 / 下钻 chips 不挤压金额
- [ ] 5.4 统计标签卡条形与金额笔数一致、无百分比、口径说明可见
- [ ] 5.5 备份往返 / CSV 往返 / Firefly 导入 / 回收站恢复四条链路标签不丢
- [ ] 5.6 CSV `tags` 列与逗号禁用校验、旧文件缺列兼容
- [ ] 5.7 **`version 8` 覆盖安装确实开库失败**；卸载重装 + 导入备份后数据完整
- [ ] 5.8 分类仍互斥、转账仍无分类、预制分类逐字段零回归
- [ ] 5.9 §8 第 45–74 批既有真机待验项整体继承
