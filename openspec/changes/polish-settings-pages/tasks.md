# polish-settings-pages · Tasks（第 12 批）

## 1. 前置（已完成）

- [x] 查远端 HEAD / 最近 CI / 进行中 change（v0.1.8 `105c415f1817`，CI 全绿）
- [x] 读 `docs/页面优化经验与避坑指南.md` §1 / §2 / §3 / §6 / §7 / §7.1
- [x] 归档 `compact-filter-sheet`：3 ADDED + 4 MODIFIED 合并 + move + 校验 PASS
      （基线 221 Req / 384 Scenario，进行中 change 为无）
- [x] 全局扫描 36 个 UI 文件，定位 9 处 `● ` / 28 处浮动 label / 5 处 DropdownMenu
- [x] 先把根因、范围决策与三级回退顺序写进 `design.md` 再动码

## 2. 规格 delta

- [x] `reporting-ui` MODIFIED「模板与规则管理收纳进设置页且不改导航」（Dialog → 独立页 + 固定入口）
- [x] `reporting-ui` MODIFIED「设置页收纳全部设置项且不改导航」（六组 → 4 组 + 关于）
- [x] `reporting-ui` ADDED「管理类页面入口固定、懒加载列表与统一写法」

## 3. 设置页框架

- [x] `SettingsPageTopBar` 扩展可选 `onAdd`，其余 6 个二级页零影响
- [x] 4 个管理页接 TopBar「+」并引入 `editingMode` 状态

## 4. 四个管理页

- [x] `CategoryManagerDialog.kt`：列表 `LazyColumn`、新增/编辑独立视图、父级选择不限高+搜索、
        行操作收「⋯」菜单、4 处 `label → placeholder`、`● ` 清零
- [x] `TemplateManagerDialog.kt`：同上；`QuickEntryForm` 6 处 label、`● ` 3 处、
        `DropdownSelector` 限高 + 搜索
- [x] `BudgetManagerDialog.kt`：同模式收口；2 处 label、下拉限高
- [x] `TrashScreen.kt`：右侧按钮横排紧凑、4 行信息合并主行+副行、padding 10→6dp

## 5. 全仓写法统一（顺带检查其他页面）

- [x] `AssetsScreen.kt`：`● ` 3 处清零；账户新增/编辑弹窗 6 处 label；账户类型下拉限高
- [x] `StatsScreen.kt`：趋势指标 `● ` 2 处 → `FilterChip` 底色
- [x] 操作型浮层 10 处浮动 label **保留**，理由写进指南 §7.1（避免下批误当漏项）
- [x] 全仓复扫 `● ` 与「管理类浮动 label」归零

## 6. 版本号

- [x] `defaultVersionName` 0.1.8 → 0.1.9

## 7. 活文档

- [x] 指南追加 §7.2「第二次外推：老页面写法同步与范围决策」
- [x] 指南 §8 更新版本 / 基线 / 未验清单继承

## 8. 交付

- [x] 六个静态检查本地全 PASS
- [x] Git Trees API 单次 commit 推送（不逐文件 PUT）
- [ ] 查 push CI 结果；红则按 `design.md` §3.3 回退顺序单处修复
- [ ] 重跑 `release.yml`（minify=on / upload_nutstore=true / create_release=false），
      产 `offline-ledger-release-v0.1.9` + mapping + metrics
- [ ] 按**最终 commit sha** 逐文件比对远端 blob sha；无 `diag/` 残留、无 token 明文
- [ ] 回报真实 commit sha + run id（可点开核），不编造

## 9. 真机复核（使用者，按决定继续推迟累积）

- [ ] 30 个分类 / 20 个模板时「+」是否固定可见、列表是否顺滑
- [ ] 编辑视图进出是否顺手、既有项回填是否完整
- [ ] 回收站一屏能看到几条、信息是否齐全
- [ ] `⋯` 菜单与 `FilterChip` 底色在 6.73 寸上是否清楚
- [ ] 累积未验清单（BiometricPrompt / SAF / Excel CSV / 5 万条切月耗时）由指南 §8 继承

## 10. 归档（待使用者明确发话）

- [ ] 1 份 ADDED 合并进 `specs/` + 2 份 MODIFIED 按标题整块替换
- [ ] change 移入 `archive/`
- [ ] 校验精确原文 / 重复标题 / Scenario / RFC2119；FAIL 走独立路径复核
