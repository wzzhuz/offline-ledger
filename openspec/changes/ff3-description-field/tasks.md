# 任务：第 43 批（v0.2.25）

- [x] 归档两个已验收 change（delta 合并进 specs + 移入 archive）
- [x] `records` / `trash` 加 `description`，`version = 5`
- [x] FF3 `description` 独立带出并落库，`notes` / 对方占位名各在各的
- [x] 自有 CSV 12 列，导出/导入/重建全链路带上，旧文件缺列兼容
- [x] 备份 DTO 与快照带上 description，旧备份缺键兼容，恢复零 1811
- [x] 记一笔加描述输入，明细行主标题动态回退（2-A'）
- [x] 筛选「描述包含」与规则「描述」条件 / 联想 / 「描述改为」动作
- [x] 纯 JVM 单测 6 条；六个静态检查全 PASS
- [ ] 真机复核：FF3 重导后三者分离、记一笔描述、列表主标题、CSV 导出重建、加密备份/快照恢复、规则按描述圈选与撤销（刻意留给使用者）

## 第 43 批实测回填（v0.2.25）

- 代码 commit：`0d9b4ab3b638c208ba141a138997473c42716c48`（20 新增 / 32 修改 / 12 删除，删除全为两个 change 归档移走）
- push CI：`44549506587` **success**，17 步全 success / 0 跳过，**首轮 Kotlin 编译与 6 条纯 JVM 单测零错误**
- release：`44550898979` **success**，28 步 = 23 success / 5 skipped，**「上传到坚果云」completed success**，head_sha 为代码 commit
- 制品：`offline-ledger-release-v0.2.25` 12403427 B（Actions 制品页 zip 压缩口径）、
  `offline-ledger-mapping-v0.2.25` 3814849 B、
  `offline-ledger-metrics-v0.2.25-p-m-nsfalse` 270 B
- `nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
  坚果云目录以使用者实际看到的短 sha 为准
- **改表（`version = 5`、无迁移层）→ 必须卸载重装，装之前先导出**
- 重复 release run `44551456398` 已 cancelled，保留并完成的是 `44550898979`
- 真机复核仍留 `[ ]`：FF3 重导后 description / notes / 对方三值分离、记一笔描述、
  列表主标题动态回退、CSV 12 列导出→重建、加密备份/快照恢复零 1811、
  规则按「描述」圈选与「描述改为」撤销
- 取舍（见 `design.md`）：Top5 对方排行「虚拟-不记录」仍聚一坨，是数据源占位习惯，
  不动统计口径；FF3 CSV 导出本身不是备份，完整重建仍以加密备份/快照为准
