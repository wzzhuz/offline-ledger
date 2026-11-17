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
