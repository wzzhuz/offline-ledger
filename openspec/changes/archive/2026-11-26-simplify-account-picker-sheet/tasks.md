# tasks · simplify-account-picker-sheet

> 阶段 0 五项对齐 + 四项拍板后才动文件。
> v0.2.62 已归档，本批按流程新开 change，不打补丁。
> 本批零改表，可直接覆盖 v0.2.62。

- [x] 1.1 强制开场核查远端 HEAD / CI / Release 与本地一致性
- [x] 1.2 **清点更正**：`ModalBottomSheet` 自第 69 批 `StatsDrillSheet` 起已有先例，
      且写进 `category-stats` 主 spec 与 `check_stats_merge.py`；
      T12 只是记一笔低频项 sheet 的局部惯例，不是全仓禁令
- [x] 1.3 阶段 0 五项对齐 + 四项拍板（图标轮换 / 删叉号与搜索 / 1B / 保留当前值 + 不指定入网格）
- [x] 2.1 `AccountSelectSheet.kt` 换 `ModalBottomSheet` + `LazyColumn` 滚动口径
- [x] 2.2 顶部压到一行：删叉号 / 搜索框 / 两枚 chip，加单枚轮换图标
      （`Icons.Filled.List` ↔ `Canvas` 2×2 方块）
- [x] 2.3 `AccountSlot` 把「不指定」收进网格第一格，删独立横幅
- [x] 2.4 保留 `cardLast4` / `accountSecondaryLabel` / `accountBaseLabel`，
      确保删搜索后同名卡仍可区分
- [x] 3.1 delta：8 MODIFIED + 1 ADDED（4 条含「账户搜索 / 3344」的 Requirement 全部收口）
- [x] 3.2 版本号 0.2.63；`check_record_layout.py` 换第 84 批双向断言 +
      归档态「delta 必须已合并」；`check_stats_merge.py` / `check_firefly_mapping.py` 一字不动
- [x] 3.3 活文档 §7.67 / §8、`project.md` 第 84 批登记（含 T12 口径更正）
- [x] 4.1 12 个静态检查全 PASS
- [x] 4.2 推送 → 双 CI → 出 v0.2.63 包
      （dispatch 只传 create_release / upload_nutstore=true，核对坚果云 step success）
- [x] 4.3 归档：delta 合并回 `reporting-ui` + 移入 archive + 正文逐块比对一致
- [x] 4.4 用 GitHub API 按 commit sha 逐文件比对远端、查 main CI

## 实测（归档时回填）

见文件末尾「第 84 批实测」。真机待验项全部保持 `[ ]`，见活文档 §7.67。

## 第 84 批实测（归档时回填）

- 实现 + 四件套 commit `b487d596`；归档 commit 见下
- push CI `45842501760` **一次全绿**（12 静态检查 + 单测 + Debug + Release
  lintVital）。重点验证的两个版本风险均通过：
  `ModalBottomSheet(containerColor = …)` 与
  `LazyColumn + items(List<List<AccountSlot>>)` 在 BOM 2026.06.00 /
  Kotlin 2.3.21 下编译正常，无需回退或补 `sheetState`
- 出包 run `45843267868` **一次全绿**（未重演第 83 批的瞬时 `exit 128`）：
  创建 tag step success、坚果云 job step **success**。
  dispatch 前按第 83 批登记的检查确认
  `git/ref/tags/v0.2.63` 与 `releases/tags/v0.2.63` 均 404，无半成品残留
- Release `v0.2.63`：`offline-ledger-v0.2.63-release-b487d596.apk` /
  3,513,300 B / SHA256
  `29d330765605a6179dd5112d4f92748cf89925f7249794d98524ad960b1cc1c4`；
  tag `v0.2.63` → `b487d596`（实现 commit，出包早于归档，与前几批一致）
- delta：`reporting-ui` **8 MODIFIED + 1 ADDED**，归档后逐块比对 9 块正文
  **全部一致**，Requirement 总数 78 → 79；既有 Requirement 标题一律未改
- 主 spec 残留「账户搜索」字样 3 处，逐行核对均为第 84 批修订说明里的
  **历史引述**（「删除『账户搜索 SHALL…』」「账户搜索框已下线」「账户搜索框整体下线」），
  非仍在生效的要求
- 归档态 12 个静态检查全 PASS；`check_record_layout.py` 的
  「第 84 批 delta 必须已合并」归档态校验命中 archive 并通过
- `release.yml` / `build.yml` 逐字节未改；`CategorySelectDialog`、
  `BottomPickerSheet`、`TemplateQuickPick`、`FilterSheet`、`LedgerPrefs`、
  `AppDatabase`、`AndroidManifest.xml` 全部一字未动
- **零改表**：version 仍 9、`Migrations.ALL` 仍空、备份 DTO 零改动，
  **可直接覆盖 v0.2.62**
- 真机待验项全部保持 `[ ]`，见活文档 §7.67；CI 全绿不替代观感验收

## 第 85 批实测（修 bug，非新 change）

- 真机反馈两条：①「上滑展开之后再下滑收不起来，页面卡着一上一下」；
  ②「全屏展开下系统返回手势是回到半屏、不是关闭」。
  两条同一个根因：默认 `sheetState` 的 `PartiallyExpanded` 中间态。
- 按 `my-app-workflow` 步骤 1「修 bug（恢复原本行为）→ 不需要建 change，
  直接做」执行，**未新建 change 四件套**；
  按「改完代码不更新 spec」禁令同步了主 spec / 脚本 / 文档。
- 代码：`AccountSelectSheet.kt` 加
  `rememberModalBottomSheetState(skipPartiallyExpanded = true)` 并传给
  `ModalBottomSheet(sheetState = …)`；4 个调用点零改动。
- 版本号 0.2.63 → 0.2.64；`check_record_layout.py` 加 3 条必检串 +
  主 spec 第 85 批修订字样校验；12 个静态检查全 PASS。
- 上方 §7.67 的两条真机待验项（下滑手势与 `LazyColumn` 嵌套滚动、
  sheet 嵌 Dialog 的关闭路径）至此有结论，见活文档 §7.67 回填。
