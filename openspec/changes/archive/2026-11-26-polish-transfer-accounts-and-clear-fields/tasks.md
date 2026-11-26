# tasks · polish-transfer-accounts-and-clear-fields

> 阶段 0 五项对齐 + 使用者三项确认完成后才动文件。
> 本批按流程新开 change（v0.2.59 已归档，不打补丁、不改已归档的四件套）。

- [x] 1.1 强制开场核查远端 HEAD / CI / Release 与本地一致性
- [x] 1.2 核查 `release.yml` 坚果云逻辑 + 最近 6 次 run 的 job steps（第 4 问）
- [x] 1.3 阶段 0 五项对齐结论 + 三项确认（只改转账态 / 不做互换 / 移除 chip）
- [x] 2.1 抽 `ClearFieldIcon`（36dp / 18dp / 有内容才显示）
- [x] 2.2 交易对方改调公共出口；描述、备注 sheet、账户搜索补清空
- [x] 2.3 新增 `TransferAccountCards` / `TransferAccountCard`（两卡常驻、零新依赖）
- [x] 2.4 `AccountPickerSheet` 加 `lockTarget` + 标题按目标区分 + 隐藏分段
- [x] 2.5 `EntryChipBar` 转账态 4 枚 / 收支态 5 枚，移除「转出/转入」
- [x] 3.1 版本号 0.2.60；`check_record_layout.py` 追加第 81 批断言
- [x] 3.2 `check_workflow_syntax.py` 加坚果云默认值 / 步骤绑定两条断言
- [x] 3.3 活文档 §7.64 / §8 待验、`project.md` 第 81 批登记
- [x] 4.1 四件套 + delta（5 MODIFIED + 1 ADDED）
- [x] 4.2 12 个静态检查全 PASS
- [x] 4.3 推送 → 双 CI → 出 v0.2.60 包（dispatch 带 `upload_nutstore=true`）
- [x] 4.4 核对「上传到坚果云」job step 结论为 success
- [x] 4.5 归档：delta 合并回 specs + 移入 archive + 校验正文一致
- [x] 4.6 用 GitHub API 按 commit sha 逐文件比对远端内容、查 main CI

## 实测（归档时回填）

见文件末尾「第 81 批实测」。真机待验项全部保持 `[ ]`，见活文档 §7.64。

## 第 81 批实测（归档时回填）

- 实现 commit `ac217e94`；push CI `45807437321` 全绿、出包 CI `45807462243` 全绿
- **坚果云恢复**：本次 `workflow_dispatch` 只传 `create_release` /
  `upload_nutstore=true`，其余走 workflow 默认；run `45807462243` 的
  「上传到坚果云」job step 结论 **success**（v0.2.57–59 连续三次为 skipped）
- Release `v0.2.60`：`offline-ledger-v0.2.60-release-ac217e94.apk` /
  3,512,600 B / SHA256 `2fbce018d913cf40166ec3784a0102a4eb6a4dc4404ddf090157afda99e2527a`
- 归档目录 `openspec/changes/archive/2026-11-26-polish-transfer-accounts-and-clear-fields/`
- delta：`reporting-ui` 5 MODIFIED + 1 ADDED，归档后逐块比对正文**全部一致**
- `check_record_layout.py` / `check_workflow_syntax.py` 归档态断言全 PASS
  （新增「第 81 批 delta 必须已合并」「坚果云默认值必须为 true」两条可执行检查）
- 既有 Requirement 标题一律不改；零改表 → v0.2.59 直接覆盖安装
- 真机待验项全部保持 `[ ]`，见活文档 §7.64；CI 全绿不替代观感验收
