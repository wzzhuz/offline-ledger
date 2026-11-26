# 实施清单

> **流程合规声明**：本 change 四件套在代码实施完成、出包、真机验证通过之后才补写。
> 第 78 / 79 批当时按「直接改，别走流程」执行，属
> `my-app-workflow/踩坑清单.md` §4.1「跳流程：没写提案就动手改代码」。
> 归档时按 `openspec/AGENTS.md` §5 补齐 delta 并合并回 specs，
> 不把先斩后奏粉饰成按部就班。以下 checkbox 为事后据实回填。

## 阶段 0 / change 四件套（事后补写）

- [x] 补写 `proposal.md`（含五项对齐结论、验收标准、被否决方案指向 design）
- [x] 补写 `design.md`（含「考虑过的其他方案」表与两条真机反馈根因）
- [x] 补写 `tasks.md`（本文件，含流程合规声明）
- [x] 补写 `specs/reporting-ui/spec.md`（ADDED 3 / MODIFIED 5）
- [x] 补写 `specs/category-stats/spec.md`（MODIFIED 2）

## 第 78 批：v0.2.57（`07e4c1c4`）

- [x] `LedgerTheme` 加 `darkTheme`（默认 `isSystemInDarkTheme()`）+ 静态深色板
- [x] `LedgerColors` 升为可观察调色板，`SideEffect` 同步 `ColorScheme`；反转开关在深浅切换后保留
- [x] 新增 `ui/theme/LedgerDesignTokens.kt`（`LedgerShapes` / `LedgerSpacing` / `LedgerType`，金额样式统一 `tnum`）
- [x] 统计页总支出 / 收入 / 结余、明细行、交易详情主金额接 `LedgerType`
- [x] `CategoryHeaderBlue` 改读 `LedgerColors.Primary`，收敛两套蓝
- [x] 统计卡与 9 处硬编码浅底 / 白卡改读表面色；统计卡间距收 12dp（后被第 79 批回退）
- [x] 版本号 `0.2.57`；静态脚本断言同步
- [x] 活文档追加 §7.60；`project.md` 追加第 78 批记录
- [x] push CI `45772062777` 全绿；出包 CI `45772816373` 全绿；Release `v0.2.57`

## 第 79 批：v0.2.58（`864cc24e`，真机反馈修复）

- [x] `LedgerColors` 拆出 `Background` / `CardSurface` / `OnSurface` 三语义
- [x] 新增 `dynamicWithNeutralSurface()`：动态浅色只借强调色，中性槽位套回静态板
- [x] 统计卡容器固定纯白、间距回 16dp（`LedgerSpacing.CardGap` 同步）
- [x] 6 处整块蓝底顶栏与资产页身份区改中性卡片 + `OnSurface` 文字
- [x] 负债浅红字改语义 `ExpenseRed`；统计选中气泡 / 分类排序选中态改主色 12% 透明底；FAB 图标改 `OnPrimary`
- [x] 版本号 `0.2.58`；静态脚本断言同步
- [x] 活文档追加 §7.61；`project.md` 追加第 79 批记录
- [x] push CI `45777239229` 全绿；出包 CI `45777241409` 全绿；Release `v0.2.58`
- [x] 产物 `offline-ledger-v0.2.58-release-864cc24e.apk` / 3,512,600 B / SHA256 `5d0b8cb5…`

## 归档（本次）

- [x] 把 delta 合并回 `openspec/specs/reporting-ui/spec.md`（ADDED 3 / MODIFIED 5）
- [x] 把 delta 合并回 `openspec/specs/category-stats/spec.md`（MODIFIED 2）
- [x] `openspec/changes/<change-id>/` 移入 `openspec/changes/archive/2026-11-26-<change-id>/`
- [x] 修正 `project.md` / 活文档里「不 MODIFIED 主 spec」的过期表述
- [x] 校验 specs 已反映新行为：12 个静态检查全 PASS
- [x] 合并 `feature/visual-tokens-and-dark-theme` 到 `main`（fast-forward）
- [x] 用 GitHub API 按 commit sha 逐文件校验远端内容
- [x] 校验 `main` 的 push CI 结论

## 仍为真机待验（`[ ]`，不因归档而关闭）

- [ ] `tnum` 在本机字体是否生效（含 `+` / `-` / 小数点 / 千分位）
- [ ] 深色模式逐页观感、深浅壁纸对比度、动态色下两套蓝是否彻底消失
- [ ] 16dp 卡片间距、顶栏中性卡片与页面底的层级、72% 次级文字是否偏淡
- [ ] 配色反转开关在深浅切换后仍生效
- [ ] 从 v0.2.56 / v0.2.57 覆盖安装、4 Tab 与记一笔首屏零回归
