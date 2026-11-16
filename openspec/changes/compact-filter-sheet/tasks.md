# compact-filter-sheet · 任务清单

## 1. 对齐与设计
- [x] 查远端 HEAD / 最近 3 条 commit / 上轮 run 结论（指南 §6.1）
- [x] 精读 `FilterSheet.kt` / `RecordFilter.kt` / `RecordDao.pagedFiltered` /
      `DetailViewModel` / `RecordPagingSource` / 4 份待改 spec
- [x] 复读 `docs/页面优化经验与避坑指南.md` §6 / §7
- [x] **先把根因与三级回退顺序写进 `design.md`，再动码**
- [x] 确认零表结构变更 → 覆盖安装，不用卸载

## 2. change 四件套
- [x] `proposal.md`（五项对齐结论、分级判定、影响分析、否决表）
- [x] `design.md`（根因、UTC 锚点、4 份 MODIFIED 决策、回退顺序）
- [x] `tasks.md`
- [x] `specs/reconciliation/spec.md`（3 份 MODIFIED + 1 条 ADDED）
- [x] `specs/ledger-transactions/spec.md`（1 份 MODIFIED）
- [x] `specs/reporting-ui/spec.md`（1 条 ADDED）

## 3. 数据层（零表结构变更）
- [x] `RecordFilter` 加 `counterpartyQuery`、`activeCount()` 同步
- [x] `FilterValues` 加 `counterpartyQuery`
- [x] `pagedFiltered` 加一条参数化 `LIKE` 条件，仍是单条 SQL
- [x] `RecordPagingSource` 传参
- [x] `RecordDao.allCounterparties()` 纯只读 DISTINCT，无 LIMIT
- [x] `DetailViewModel`：`counterparties` StateFlow（init + `bumpRevision` 刷新）、
      `applyFilter` / `clearFilter` 同步新字段

## 4. 筛选面板
- [x] `OptionRow(chunked(3))` 删除，改「52dp 标签 + 值 + ›」整行网格
- [x] 类型 / 币种改 `FilterChip` 单行底色选中；选项 > 4 时币种降级整行弹层
- [x] 账户 / 一级 / 二级分类整行可点 → 全量单选弹层（带搜索）
- [x] 删双层 16dp padding、删副标题、细分割线归整
- [x] 5 个输入框：`label → placeholder` + `bodyMedium` + `heightIn(min = 40.dp)`
- [x] 日期改 `DatePickerDialog`（UTC 锚点往返不串天）
- [x] 金额区间从 `initial` 回填（修静默清空 bug）
- [x] 交易对方输入框 + 有输入时才展开的联想（≤4 行、每行 ≥40dp）
- [x] `DetailScreen` 传 `counterparties`

## 5. 构建与交付
- [x] `defaultVersionName` 0.1.7 → 0.1.8
- [x] 活文档追加第 11 批记录、更新 §8 继承状态
- [x] 六个静态检查本地全 PASS
- [ ] Git Trees API 单次 commit 推送（不逐文件 PUT，避免触发 28 次 CI）
- [ ] 查 push CI 结果；红则按 `design.md` §6 回退顺序单处修复
- [ ] 重跑 `release.yml`（minify=on / upload_nutstore=true / create_release=false），
      查 24 步结果与制品
- [ ] 按**最终 commit sha** 逐文件比对远端 blob sha；无 `diag/` 残留、无 token 明文
- [ ] 回报真实 commit sha + run id（可点开核），不编造

## 6. 真机（使用者执行，沙盒验不了）
- [ ] 6.73 寸上一屏是否真的放得下 9 个维度
- [ ] 10+ 账户长名在弹层里是否完整、不被压窄
- [ ] placeholder 对比度、`FilterChip` 底色、DatePicker 弹窗观感
- [ ] 切 Tab 返回后筛选条件与滚动位置是否仍在
- [ ] 累积未验清单（BiometricPrompt / SAF / Excel CSV / 5 万条切月耗时）由指南 §8 继承，
      **不许被改写成「已通过」**

## 7. 归档（需使用者明确发话，本批不自动归档）
- [ ] 4 份 MODIFIED 按标题整块替换 + 2 条 ADDED 合并进 `specs/`
- [ ] change 移入 `archive/`
- [ ] 校验精确原文 / 重复标题 / Scenario / RFC2119；FAIL 走独立路径复核
