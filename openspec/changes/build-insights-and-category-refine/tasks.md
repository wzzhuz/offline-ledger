# 任务清单 · 第 13 批（v0.2.0）

- [x] 1. 归档 `polish-settings-pages`（1 ADDED + 2 MODIFIED 合并 + 移入 archive + 校验）
- [x] 2. 写 change 四件套与 4 份 delta（含 MODIFIED 整块替换）
- [x] 3. `RecordDao` 加 4 条只读聚合；`check_stat_rules.py` marker 扩项
- [x] 4. 纯 JVM `InsightData` + 单测
- [x] 5. `StatsViewModel` state 与 reload 填值
- [x] 6. `StatsScreen` 重排：总结卡 / 分类洞察卡 / 趋势卡 / 年度资产卡 / 口径入口 / 空态 / 警告置顶
- [x] 7. 隐私模式：prefs + `LocalAmountHidden` + `maskedCents` + 设置页开关 + 三页金额遮蔽
- [x] 8. 分类管理：收支分段 / 笔数 / 分类详情下钻 / 编辑视图字段分组
- [x] 9. 统计排行卡「分类 ›」短跳转（LedgerApp 传回调，不新增路由）
- [x] 10. 版本号 0.2.0；活文档 §7.3 / §8 更新
- [x] 11. 六个静态检查本地全 PASS
- [x] 12. 单次 commit 推送 + 按 commit sha 远端逐文件校验
- [x] 13. 查 push CI；若红按 `design.md` 三级回退顺序修复
- [x] 14. 手动触发 `release.yml`（minify=on / upload_nutstore=true）并查结果
- [x] 15. 回填实测到 `design.md` / `tasks.md`；真机未验清单继承

## 第 14 批（真机反馈修正，同一 change 内）

- [x] 1. 统计页删 `InsightMode`，`CategoryInsightCard` 拆成占比 / 排行两张独立卡
- [x] 2. 顶层顺序改为 总结 → 趋势 → 占比 → 排行 →（年度）资产 → 口径；「分类管理 ›」移挂占比卡
- [x] 3. 分类管理一级默认折叠 + `rememberSaveable(expandedRoots)`
- [x] 4. 展开态二级 `chunked(3)` chips + 补空位；点 chip 弹操作 Dialog（五项操作不丢）
- [x] 5. 4 份 delta 按标题整块替换矛盾 Requirement（不新增 MODIFIED）
- [x] 6. 版本号 0.2.1；活文档 §2.6 / §7.3 / §7.4 / §8 更新
- [x] 7. 六个静态检查本地全 PASS
- [x] 8. 单次 commit 推送 + 按 commit sha 远端逐文件校验
- [x] 9. 查 push CI；若红按 `design.md` 第 14 批回退顺序修复
- [x] 10. 手动触发 `release.yml`（minify=on / upload_nutstore=true）并查结果
- [x] 11. 回填实测到 `design.md` / `tasks.md`；真机未验清单继承

## 第 14 批实测（2026-11-17）

push CI `44011265168` success（17 步全绿、首轮零编译错误）；
release `44012015782` success（28 步、坚果云 completed success）；
制品 APK 12,218,823 B / mapping 3,484,719 B / metrics 271 B；
远端按最终 commit sha 比对 291↔291 PASS、无 `diag/` 残留、无 token 明文。
真机未验清单继续按使用者决定整体推迟，见下节。

## 真机未验（全部继承，不许被本批改写成「已通过」）
BiometricPrompt 弹窗、SAF 文件选择器、Excel 打开 CSV 是否乱码 / 中文 BOM、
5 万条切月与年报聚合耗时、滚动掉帧、模板与快捷方式手感、报销 / 预算 / 净支出环形图、
币种切换与分组小计、CSV 导出导入完整流程、横屏全屏页与双指缩放；
本批新增：6.73 寸上四张卡是否一屏顺眼、Top5 占比条、隐私模式切换、分类分段与笔数、分类详情弹窗。


## 实测回填（第 13 批，2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `3c9e003b45374a710f2ead33739245fc5a31fb0e`（v0.2.0） |
| push CI | `44002929060` **success**，17 步全绿，**首轮 Kotlin 编译零错误**，无诊断残留 |
| release | `44004138708` **success**，23 步绿 / 5 步按 input 跳过；坚果云上传步骤 `completed success` |
| 制品 | `offline-ledger-release-v0.2.0` 12,217,237 B；`mapping-v0.2.0` 3,476,907 B；`metrics-v0.2.0-p-m-nsfalse` 270 B |
| 远端校验 | 按 commit sha 独立比对 **291↔291 blob、sha 全一致、PASS**；无 `diag/` 残留、无 token 明文 |
| 归档 | `polish-settings-pages` 已移入 `archive/`，归档 change 累计 15 个；本 change **仍进行中**，待使用者发话归档 |
| 推送插曲 | 首次增量脚本跳过点开头路径，误删 `.github/` 与 `.gitignore`；force-push 回原 HEAD 并用「不跳过点路径 + 误删保护断言」重建，未触发 CI、未影响最终产物 |

`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，**不当作已验证 HEAD 200**。

## 第 15 批（v0.2.2，同一 change 内）

- [x] 1. `LedgerUiParts.kt` 新增 `ListDivider()` 单一来源
- [x] 2. 明细页内联分割 Box 换成 `ListDivider()`，视觉基准保持 16dp
- [x] 3. 分类 / 模板 / 预算 / 回收站四页补 `ListDivider`，风格对齐明细
- [x] 4. 资产页账户卡 1dp 贴片 → 4dp 组距 + 8dp 内边距 + 分割线
- [x] 5. 分类一级 `titleSmall + SemiBold`，二级 chip 按文本自适应 + `widthIn(max)`
- [x] 6. `SettingsViewModel.moveCategory` 改 `(type, level, parentId)` 分组作用域
- [x] 7. `SettingsViewModel` 新增 `commitRootOrder()` 供拖拽落库
- [x] 8. `AssetsViewModel.moveAccount` 改按 `AccountType` 分组（同类 bug）
- [x] 9. 分类一级行加零依赖长按拖手柄（稳定 key + 松手一次性落库）
- [x] 10. 设置页分组顺序改 账本 → 数据与备份 → 偏好 → 安全 → 关于
- [x] 11. 3 份 MODIFIED 按标题整块替换（2× reporting-ui + 1× category-stats）
- [x] 12. 活文档 §7.5 / §8 更新；版本号 0.2.2
- [x] 13. 六个静态检查本地全 PASS + 大括号 / import 粗检
- [x] 14. 单次 commit 推送 + 按 commit sha 远端逐文件校验
- [x] 15. 查 push CI；若红按 `design.md` 三级回退顺序修复
- [x] 16. 手动触发 `release.yml`（minify=on / upload_nutstore=true）并查结果
- [x] 17. 回填实测；真机未验清单继承

## 第 15 批实测（2026-11-17）

首轮 push CI `44033035742` failure（`Icons.Filled.Menu` 不存在）；临时诊断 workflow
回传日志定位到唯一 1 条编译错误，改为文本 `≡` 并清理诊断通道。
修复 commit `143327b85b33` → push CI `44034000868` **success**（17 步全绿）；
release `44034725835` **success**（23 步绿 / 5 步跳过、坚果云 success）。
制品：`release-v0.2.2` 12,222,648 B / `mapping-v0.2.2` 3,492,783 B / `metrics` 272 B。
远端按最终 commit sha 比对 291↔291 PASS、无 `diag/` 残留、无 token 明文。
真机未验清单继续按使用者决定整体推迟，见活文档 §8。


## 第 16 批（v0.2.3，同一 change 内）

- [x] 1. 新增 `ui/components/ReorderSupport.kt`（`WrapRow` / `ReorderDragState` / `rememberReorderDrag` / `ReorderHandle`）
- [x] 2. 分类一级改共享拖拽 + `animateItem()` 实时让位
- [x] 3. 分类二级改 `WrapRow` 自适应换行 + 长按拖拽；新增 `commitChildOrder`
- [x] 4. 资产页去 `Card` 纯行；`MiniAction` 升 `bodyMedium` + 40dp 触摸区
- [x] 5. 资产页按 `AccountType` 分组接入拖拽；新增 `commitAccountOrder`；↑↓ 收进 ⋯ 兜底
- [x] 6. 模板页接入共享拖拽；新增 `commitTemplateOrder`；↑↓ 保留
- [x] 7. 合并 `category-stats` 两个同标题 MODIFIED 为一份新文本
- [x] 8. 新增 `ledger-transactions` 2 份 MODIFIED；修订 `reporting-ui` 资产页表述
- [x] 9. 活文档 §7.6 / §8 更新；版本号 0.2.3
- [x] 10. 六个静态检查本地全 PASS + 括号 / import 粗检
- [x] 11. 单次 commit 推送（差异集合断言 + 误删保护）
- [x] 12. 按 commit sha 独立比对远端 blob
- [x] 13. 查 push CI；若红按 `design.md` 三级回退顺序修复
- [x] 14. 手动触发 `release.yml`（minify=on / upload_nutstore=true）并查结果
- [x] 15. 回填实测与真机未验清单继承

## 第 16 批实测（2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `49516fd15550594154b2a36a8109302cc53b2243`（14 文件，v0.2.3） |
| 首轮 push CI | `44045003985` **failure**，挂在「运行单元测试」（Kotlin 编译） |
| 诊断通道 | 临时 dispatch-only `diag.yml`（`7a94c11c740c…`）→ run `44045388978` success → 日志回传 `diag/unit.log`（`de1d21ac3ea1`）；job logs 仍 403 |
| 三条编译错误 | ① `CategoryManagerDialog.kt:48 Unresolved reference 'animateItem'`；② `TemplateManagerDialog.kt:5` 同；③ `TemplateManagerDialog.kt:183 @Composable invocations can only happen from the context of a @Composable function`——`rememberReorderDrag` 被放进非 composable 的 `LazyListScope` |
| 修复 commit | `3e367748af2f061b0589d2e32eab17c8b68f8f85`（去掉 `animateItem` import 与调用、把 `tplDrag`/`orderedTemplates` 提升到 `@Composable` 函数体、删除 `diag.yml` 与 `diag/unit.log`） |
| push CI | `44045792325` **success**（17 步全绿） |
| release | `44046503643` **success**，23 步绿 / 5 步按 input 跳过；**上传到坚果云 completed success**；重复排队 `44046920100` 已 cancelled |
| 制品 | `offline-ledger-release-v0.2.3` 12,231,819 B；`offline-ledger-mapping-v0.2.3` 3,502,962 B；`offline-ledger-metrics-v0.2.3-p-m-nsfalse` 271 B |
| 远端校验 | 按最终 commit sha 比对 **293↔293 blob、sha 全一致、PASS**；`diag/` 与 `diag.yml` 零残留、workflow 仅 build/release 两份；无 token 明文；六个静态检查全 PASS |

**回退顺序实际用上第 1 条**：`androidx.compose.foundation.lazy.animateItem` 在 Compose BOM
2026.06 不存在，去掉后**实时重排仍然生效**（`orderedIds` 状态驱动列表重组），
只是没有补间动画。第 3 条是独立发现的作用域错误，按「每次只改一处」在同一修复
commit 里一起处理。**如实限制**：让位是即时的、不是连续像素级补间，
顺滑度仍不如 Flutter `ReorderableListView`；不把「实时重排」包装成「动画已对齐」。
`nsfalse` 表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验只写日志不中断，
**不当作已验证 HEAD 200**。真机观感与累积未验清单继续按使用者决定整体推迟。


---

## 第 17 批（v0.2.4，同一 change 内）

- [x] 1. `ReorderSupport.kt`：`WrapRow` 加 `horizontalAlignment`，`layout()` 占满父宽、行尾留白在右
- [x] 2. chip label 显式 `TextAlign.Start`，去掉 M3 默认居中观感
- [x] 3. 新增 `ReorderBounds`（容器可见边界，root px）与 `onGloballyPositioned` 写入
- [x] 4. `ReorderDragState` 暴露 `edgeDirection`、`onDraggingChange` 回调与 `rowMeasureModifier()`
- [x] 5. `rememberReorderDrag` 加 `bounds` / `onDraggingChange` / `onEdgeScroll` 参数，帧级边缘自动滚动
- [x] 6. 资产页 `verticalScroll(enabled = !anyDragging)` + 共享 bounds + 边缘滚动
- [x] 7. 分类页 `LazyColumn(userScrollEnabled = !anyDragging)` + 共享 bounds + 边缘滚动
- [x] 8. 模板页 `LazyColumn(userScrollEnabled = !anyDragging)` + 共享 bounds + 边缘滚动
- [x] 9. 四处 `rowMeasureModifier()` 实测行高，替掉 56/40/44 估算
- [x] 10. 3 份 spec delta 按标题整块修订（WrapRow 左对齐 / 禁用父滚动 / 实测行高）
- [x] 11. 活文档 §7.7 追加「父子手势竞争」、§8 版本与真机清单更新
- [x] 12. 版本号 0.2.3 → 0.2.4；单次 commit 推送
- [x] 13. push CI + release（minify=on、上传坚果云）全绿、制品带短 sha
- [x] 14. design / tasks / 活文档实测回填，远端按最终 sha 独立校验
- [ ] 15. 真机复核：四处拖拽不误触滚动、边缘滚动、二级 chips 左对齐（**由使用者装机确认**）

## 第 17 批实测（2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `8f547c789686f25805c8e7ba9f86cc2b64131f90`（11 文件，v0.2.4） |
| push CI | `44057590347` **success**，17 步全绿，**首轮 Kotlin 编译零错误**（未走诊断通道） |
| release | `44058813183` **success**，23 步绿 / 5 步按 input 跳过；**上传到坚果云 completed success**；重复排队 `44059276870` 已 cancelled |
| 制品 | `offline-ledger-release-v0.2.4` 12,235,191 B；`offline-ledger-mapping-v0.2.4` 3,505,636 B；`offline-ledger-metrics-v0.2.4-p-m-nsfalse` 271 B |
| 远端校验 | 按最终 commit sha 比对 **293↔293 blob、sha 全一致、PASS**；`diag/` 零残留、workflow 仅 build/release、无 token 明文；六个静态检查本地与 CI 全 PASS |
| 文档回填 | 本 commit（仅文档，代码零变化） |

**首轮无失败**：动手前按 `design.md` §3 写定三级回退顺序，并把
`onEdgeScroll` 从 `suspend` lambda 收敛为普通 lambda（实际调用
`dispatchRawDelta` 是同步的），避免函数类型歧义；四个
`rememberReorderDrag` 调用点、`WrapRow` 唯一调用点、
`CategoryRootRow` 的 `measureModifier` 参数全部同步核对，无「改一处漏一处」。

**如实限制**：`nsfalse` 只表示认证 / 建目录 / PUT 均未失败；HEAD 落盘校验
只写日志不中断，**不当作已验证 HEAD 200**。边缘自动滚动用 root 坐标近似
+ 48dp 粗判定、手指 root y 由 `onGloballyPositioned` 记录手柄位置换算，
**沙盒无模拟器，手势竞争与边缘滚动完全验不了**；CI 全绿只证明
`verticalScroll / LazyColumn / pointerInput / onSizeChanged / dispatchRawDelta`
编译通过，不证明「手指拖到屏幕底部时列表按预期滚、父滚动不抢事件」。
这轮 MUST 由使用者装机拖一次才算数。若真机仍被抢，按 §3 回退顺序
退到 `NestedScrollConnection` 拦截；若边缘滚动抖动，退到固定步进 + 阈值死区。

## 第 19 批（引入 reorderable 与隐私口径改写，v0.2.4 基础上，暂不升版本号）

- [x] 1. 核实 `reorderable:3.1.0` 坐标 / 许可证 / 传递依赖 / API（上游 demo 一手源码）
- [x] 2. 订正「BOM 2026.06 无 `animateItem`」错误结论（design / 活文档）
- [x] 3. `app/build.gradle.kts` 引入纯 UI 拖拽库坐标并记录审计
- [x] 4. `project.md` 按使用者 2026-11-17 声明改写铁律 1 / 技术栈 / 永久不做 / 坚果云段
- [x] 5. `check_manifest_privacy.py` 硬禁令 → 显式授权闸门（始终禁止回传 SDK）
- [x] 6. `privacy-security` 3 份 MODIFIED 按标题整块替换，不留矛盾文本
- [x] 7. Manifest 注释、活文档 §2.7 / §7.7 同步
- [x] 8. 本地六个静态检查全 PASS
- [x] 9. **CI 真实验证**：只加依赖不动 Kotlin，`44074939870` success，库 3.1.0 与 BOM 2026.06.00 **兼容**
- [ ] 10. 资产页改 `LazyColumn` 并接入库 state（下一批）
- [ ] 11. 分类一级 / 模板逐页接入；二级 chips 保留自研 `WrapRow`（下一批）
- [ ] 12. 代码接入完成后统一升版本 `0.2.5`、出包上传坚果云、带短 sha
- [ ] 13. 真机复核：资产页 10+ 账户拖动不误触滚动、边缘自动滚动、让位动画顺滑（**由使用者装机确认**）

## 第 19 批 A 第一步实测（2026-11-17）

| 项 | 结果 |
|---|---|
| 代码 commit | `aa26aa87f293`（8 文件；仅依赖坐标 + 口径/规格/文档，**未动任何 Kotlin 拖拽代码**） |
| push CI | `44074939870` **success**（静态检查 6 项 / 单测 / Debug APK / release lintVital 全绿） |
| 并发取消 | `44074938760` cancelled（同 commit 的 dispatch 与 push 撞 concurrency，正常） |
| 制品 | `offline-ledger-debug-apk` 24,179,773 B |
| **库兼容性判定** | **PASS** —— Gradle 成功解析 `sh.calvin.reorderable:reorderable:3.1.0`、Kotlin 编译与 lintVital 全过，证明与 Compose BOM `2026.06.00` / Kotlin `2.3.21` 兼容；无版本冲突、无传递网络客户端 |
| 本地静态检查 | 六个全 PASS（含改写后的 `check_manifest_privacy.py` 显式授权闸门） |
| 回填 | 仅文档 commit，代码零变化 |

**本批刻意不升版本号**：v0.2.4 仍是当前可装包；库已入依赖树但拖拽实现仍为自研，
升 `0.2.5` SHALL 留到代码接入完成、真机可验之后，避免出现「版本号升了但行为没变」的错位。
**如实限制**：CI 全绿只证明**库能编译进项目**，不证明拖拽手感变好——
手势替换尚未写一行 Kotlin。真机项继续由活文档 §8 继承。


---

## 第 20 批：接入 reorderable 3.1.0（v0.2.5）

- [x] 核对远端 HEAD `3d2f45175fbb98e8b25eb9bbd59a25d50f36c3d5`（v0.2.4 回填），
      复用第 19 批已由 CI 验证的依赖坐标
- [x] 查证库一手源码 / README：`rememberReorderableLazyListState`、
      `LazyItemScope.ReorderableItem`、`longPressDraggableHandle`、`from.key` / `to.key`
- [x] 新增 `ReorderListSupport.kt`（`KeyedReorderState` + `rememberKeyedReorder`）
- [x] `ReorderSupport.kt` 精简为只保留 `WrapRow`
- [x] 资产页改 `LazyColumn` 并接入库；`AccountCard` 手柄改为可空
- [x] `AssetsViewModel.commitAccountOrderAll` 全量落库
- [x] 分类一级接入库；二级 chips 保留 `WrapRow` + 操作弹层上移下移
- [x] 模板列表接入库
- [x] 版本号 `0.2.5`
- [x] 3 份 delta 按标题整块修订，去掉「零新增依赖 / 自研手势层」矛盾文本
- [x] design / 活文档 / project 回填
- [x] 本地静态自检：6 个 Kotlin 文件括号平衡、无已删类残留 import
- [x] 单次 commit 推送 + 远端 sha 校验
- [x] push CI 与 release 实测回填、坚果云上传
- [ ] **真机复核（由使用者装机确认）**：资产页 10+ 账户滚动后长按拖动不误触滚屏、
      拖到屏幕边缘能连续滚到屏外账户、松手顺序持久；分类一级 / 模板让位补间顺滑；
      二级 chips 左对齐与点击编辑仍正常；⋮ 上移下移兜底可用
