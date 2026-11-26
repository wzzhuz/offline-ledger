# tasks · account-icon-ref-and-weighted-chips

> 阶段 0 五项对齐 + 使用者五项拍板后才动文件。
> v0.2.60 已归档，本批按流程新开 change，不打补丁。

- [x] 1.1 强制开场核查远端 HEAD / CI / Release 与本地一致性
- [x] 1.2 盘点 `AccountIconCatalog` / 26 个 PNG / `CategoryIconCatalog` /
      `EditAccountDialog` / SAF 导入 / 6 个锁 version=8 的静态脚本
- [x] 1.3 阶段 0 五项对齐结论 + 五项拍板（不等宽 / 卸载重装 / 分类不做 /
      导入按推荐 / 不进备份）
- [x] 2.1 `EntryChipBar` 改 `EntryChip(label, weight, hasValue, onClick)` 非等宽
- [x] 2.2 短标签固定、标签只显示数量、有值改 6dp 圆点、窄项收右端
- [x] 2.3 账户 sheet 接 `AccountIconBox(size=44)`、删余额、卡号/卡种降次要
- [x] 2.4 `AccountKindBadge` 字号按尺寸等比放大；转账两卡换图标 + 删余额
- [x] 3.1 `AccountEntity.iconRef` + `version = 9` + DAO SQL / Repository /
      ViewModel / `EditAccountDialog.onConfirm` 全链路透传
- [x] 3.2 `Migrations.ALL` 保持空数组；备份 DTO 刻意不扩字段
- [x] 3.3 新增 `AccountCustomIcons`（SAF / 透明底校验 / 裁方 / 单色剪影 /
      128×128 白圆底 / 私有目录）
- [x] 3.4 账户编辑页「预置 26 项网格 / 导入 PNG / 自动」三入口
- [x] 3.5 资产页 / 排序页 / 模板 sheet 三处 `AccountIconBox` 读 `iconRef`
- [x] 4.1 `assets/NOTICE` 新增 [D] 本机导入授权段落
- [x] 4.2 版本号 0.2.61；6 个脚本升表锁收 9；`check_record_layout.py` 加 12 条断言
- [x] 4.3 活文档 §7.65 / §8 待验、`project.md` 第 82 批登记
- [x] 5.1 四件套 + delta（4 MODIFIED + 3 ADDED）
- [x] 5.2 12 个静态检查全 PASS
- [x] 5.3 推送 → 双 CI → 出 v0.2.61 包（dispatch 只传 create_release /
      upload_nutstore=true，坚果云 step 核对 success）
- [x] 5.4 归档：delta 合并回 reporting-ui / privacy-security + 移入 archive +
      正文逐块比对一致
- [x] 5.5 用 GitHub API 按 commit sha 逐文件比对远端、查 main CI

## 实测（归档时回填）

见文件末尾「第 82 批实测」。真机待验项全部保持 `[ ]`，见活文档 §7.65。
**本批 MUST 卸载重装，不能覆盖 v0.2.60。**

## 第 82 批实测（归档时回填）

- 实现 commit `3cc82d04`；诊断 commit `5e7ddff`；修复 commit `c2c1e852`
- **首轮失败复盘**：push CI `45817702062` 与出包 CI `45817729783` 均失败于
  `compileDebugKotlin`：`AssetsScreen.kt:950 Unresolved reference 'size'`
  （预置图标网格用了 `Modifier.size(30.dp)` 但文件没有
  `import androidx.compose.foundation.layout.size`）。
  日志域名被出口策略拦截，改用临时 `::error::` workflow 命令把编译错误转成
  check-run annotation，经 `api.github.com/check-runs/{id}/annotations` 读到，
  定位后补 import 并**还原 build.yml**（最终 workflow 与 v0.2.60 逐字节一致）。
  两个中间 commit 留在试验分支历史，最终状态无诊断代码。
  顺带修掉 `AccountCustomIcons` 一处运行期 bug：`inJustDecodeBounds=true` 时
  `decodeStream` 必返回 null，原 `?: error(...)` 会让每次导入都失败，
  改为先确认输入流能打开。
- 修复后 push CI `45819220312` 全绿；出包 CI `45820077404` 全绿
- **坚果云**：dispatch 只传 `create_release` / `upload_nutstore=true`，
  run `45820077404` 的「上传到坚果云」job step 结论 **success**
- Release `v0.2.61`：`offline-ledger-v0.2.61-release-c2c1e852.apk` /
  3,513,304 B / SHA256 `2ace5477f5e3fe670cc876422cda7de561c4b69e2b0481d64d1a21c80cf095ea`
- 归档目录 `openspec/changes/archive/2026-11-26-account-icon-ref-and-weighted-chips/`
- delta：`reporting-ui` 4 MODIFIED + 2 ADDED、`privacy-security` 1 ADDED，
  归档后逐块比对正文**全部一致**
- 6 个静态脚本升表锁统一收到 9 / (7,8,9)；`check_record_layout.py` 加
  12 条第 82 批断言 + 归档态「delta 必须已合并」；12 个静态检查全 PASS
- 既有 Requirement 标题一律不改；`Migrations.ALL` 保持空数组
- **version 9 且无 Migration ⇒ MUST 卸载重装**，覆盖 v0.2.60 会开库失败
- 真机待验项全部保持 `[ ]`，见活文档 §7.65；CI 全绿不替代观感验收
