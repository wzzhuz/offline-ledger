# 任务清单（第 26 批 · v0.2.10）

## 归档与规格
- [x] 1. 归档 `import-firefly-iii-csv`（第 22–25 批 delta 合并、移入 archive、校验）
- [x] 2. 新建 `polish-stats-and-import-metadata` 四件套
- [x] 3. 4 份 MODIFIED delta 写入（按标题整块替换，不追加 ADDED 制造两份清单）

## 实现
- [x] 4. `DetailViewModel.filterByCategory` + `LedgerApp` 跨 Tab 回调
- [x] 5. `StatsScreen` / `DrillDownDialog` 分类点击改跳明细
- [x] 6. 明细页筛选 chip（可一键清除，无新增无障碍按钮）
- [x] 7. `LineChart` 加 `staggerLabels` / `guideIndex`（默认不破坏其它调用点）
- [x] 8. `StatsScreen` 年度简化值 + 交错 + 图下汇总 + 移除点按 AlertDialog
- [x] 9. `FireflyCsvCodec` 备注改 `r.notes` 原文、去掉 `ff3:` 前缀
- [x] 10. 流水详情显示来源 chip
- [x] 11. 同步改 `FireflyCsvCodecTest` 断言
- [x] 12. 版本 `0.2.9 → 0.2.10`
- [x] 13. 活文档 §7.9 / §8、`project.md` 阶段计划回填

## 交付
- [ ] 14. push CI 与 release 实测回填、坚果云上传
- [ ] 15. **真机复核（由使用者装机确认）**：点分类跳明细且条件正确、筛选 chip 可清、
      年度图关键点可辨 / 点按引导线与图下汇总、导入后备注与 FF3 原文一致、
      导出 CSV 再导入同一份 0 写入
