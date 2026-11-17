# 任务清单 · 第 13 批（v0.2.0）

- [ ] 1. 归档 `polish-settings-pages`（1 ADDED + 2 MODIFIED 合并 + 移入 archive + 校验）
- [ ] 2. 写 change 四件套与 4 份 delta（含 MODIFIED 整块替换）
- [ ] 3. `RecordDao` 加 4 条只读聚合；`check_stat_rules.py` marker 扩项
- [ ] 4. 纯 JVM `InsightData` + 单测
- [ ] 5. `StatsViewModel` state 与 reload 填值
- [ ] 6. `StatsScreen` 重排：总结卡 / 分类洞察卡 / 趋势卡 / 年度资产卡 / 口径入口 / 空态 / 警告置顶
- [ ] 7. 隐私模式：prefs + `LocalAmountHidden` + `maskedCents` + 设置页开关 + 三页金额遮蔽
- [ ] 8. 分类管理：收支分段 / 笔数 / 分类详情下钻 / 编辑视图字段分组
- [ ] 9. 统计排行卡「分类 ›」短跳转（LedgerApp 传回调，不新增路由）
- [ ] 10. 版本号 0.2.0；活文档 §7.3 / §8 更新
- [ ] 11. 六个静态检查本地全 PASS
- [ ] 12. 单次 commit 推送 + 按 commit sha 远端逐文件校验
- [ ] 13. 查 push CI；若红按 `design.md` 三级回退顺序修复
- [ ] 14. 手动触发 `release.yml`（minify=on / upload_nutstore=true）并查结果
- [ ] 15. 回填实测到 `design.md` / `tasks.md`；真机未验清单继承

## 真机未验（全部继承，不许被本批改写成「已通过」）
BiometricPrompt 弹窗、SAF 文件选择器、Excel 打开 CSV 是否乱码 / 中文 BOM、
5 万条切月与年报聚合耗时、滚动掉帧、模板与快捷方式手感、报销 / 预算 / 净支出环形图、
币种切换与分组小计、CSV 导出导入完整流程、横屏全屏页与双指缩放；
本批新增：6.73 寸上四张卡是否一屏顺眼、Top5 占比条、隐私模式切换、分类分段与笔数、分类详情弹窗。
