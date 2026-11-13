# 任务清单

## 1. 归档与准备
- [x] 归档 6c-2（10 ADDED + 1 MODIFIED 按标题替换 + 移入 archive + 校验）
- [x] 写本 change 四件套
- [x] `project.md` 落档「真机体验口径」覆盖声明与默认本地化修正

## 2. 基础层：默认值与偏好
- [x] `LedgerCurrency.DEFAULT = CNY` + `SUPPORTED` 列表
- [x] `LedgerClock.zoneId = Asia/Shanghai`
- [x] `SeedData` 预置账户 SQL 改 CNY
- [x] `LedgerPrefs.defaultCurrency`
- [x] 设置页默认币种下拉 + 批量统一币种按钮
- [x] `SettingsViewModel` / `SettingsState` 同步

## 3. 数据结构：收款方与版本重置
- [x] `RecordEntity` / `TrashEntryEntity` / 模板 / 规则 Entity 加 `payee`
- [x] `RecordEntity` / `TrashEntryEntity` 的 `defaultValue` 改 CNY
- [x] `AppDatabase version = 1`；`Migrations.ALL = emptyArray()`
- [x] 备份 DTO（`BackupRecord` / `BackupTemplate` / `BackupRecurringRule`）加 `payee`
- [x] `LedgerMappers` 全量补 `payee` 映射；`exportSnapshot()` 显式传 CNY

## 4. 账户编辑与批量币种
- [x] `AccountDao.updateAccountMeta` 支持期初余额与币种
- [x] `RecordDao` / `TrashDao` 批量币种更新
- [x] `LedgerRepository.updateAccountMeta` 末尾重算；新增 `applyCurrencyToAll`
- [x] 资产页新增「编辑账户」弹窗（期初余额 / 币种 / 名称 / 额度 / 计入净资产）
- [x] `AssetsViewModel.addAccount` 取默认币种

## 5. 记一笔：紧凑 / 日历 / 收款方 / 键盘
- [x] `EditorState` 加 `payee`；`setPayee` / `setDate` / `payees` 常用收款方
- [x] `RecordDao.distinctPayees()`；`pagedFiltered` 与 `csvRows` 带 `payee`
- [x] 重写 `RecordEditorDialog`：横向分类、单行账户+日期、备注/收款方首屏可见、更多折叠
- [x] 纯数字键盘（13 键）
- [x] Material3 `DatePickerDialog` 日历选择
- [x] 明细行显示收款方

## 6. 底栏与模板
- [x] `LedgerApp` 移除悬浮 FAB，改为底栏内嵌 `+`
- [x] 模板下拉按收支类型过滤 + 一级分组 + 标签带归属
- [x] 模板列表行显示分类；类型切换清空分类；保存前校验类型一致

## 7. CSV 与备份
- [x] `CsvCodec.HEADER` 加可选 `payee` 列
- [x] `CsvRow` 加 `payee` 默认值；导出 / 解析 / 导入全链路带 `payee`
- [x] 去重键不含 `payee`（保持既有语义）

## 8. 测试与验证
- [x] 新增纯 JVM 单测：默认币种/时区、payee 备份与 CSV 往返、8 列 CSV 兼容
- [x] 六个静态检查本地预跑
- [ ] 单次 commit 推送后用远端 API 逐文件校验 sha
- [ ] CI 全绿（Actions 能查到运行，不把「文件已推送」当「构建通过」）
- [ ] release 手动触发并上传坚果云，产出使用者可装的包

## 9. 收尾
- [ ] `design.md` 回填实现期偏离与 CI 实测
- [ ] 使用者确认后归档（ADDED 合并 + 2 MODIFIED 按标题替换 + 移入 archive）

> 真机实测整体推迟到完成度更高后统一装机体验；但本 change 的包会被使用者
> 立即安装，因此第 8 节的 release 出包是必须的。卸载重装提醒见 `design.md` 第 3 节。
