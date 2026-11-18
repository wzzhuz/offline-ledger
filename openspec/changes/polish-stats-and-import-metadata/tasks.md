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
- [x] 14. push CI 与 release 实测回填、坚果云上传（实测 `d9e9dc28d903` /
      CI `44253403111` / release `44254405887`；制品 12,319,308 B / 3,663,922 B /
      271 B；首轮三次红由诊断日志定位 `RecordRow` 缺来源列与 `@Composable` 错位）
- [x] 15. **真机复核（由使用者装机确认）**：v0.2.10 已装机验证，年度图关键点、
      点按引导线、备注原样、CSV 再导入 0 写入均无新问题；下钻四处缺口转第 27 批

---

# 第 27 批任务（v0.2.11 · 下钻契约四层）

## 实现
- [x] 1. `RecordFilter` / `FilterValues` 加只读 `filterDisplayName`
- [x] 2. `filterByCategory` 带父级 id 与展示名；新增 `filterByCounterparty` / `filterByAccount`
- [x] 3. `FilterSheet` 合并为一行「分类」+ 带搜索全量弹层（`r:` / `c:` 前缀）
- [x] 4. 明细页 chip 三类条件显示具体名（收款方标「包含」）
- [x] 5. `LedgerApp` 三回调 + `StatsScreen` 签名扩展，共用跳明细
- [x] 6. `DonutChart` 图例整行可点（40dp 触摸区 / 12dp 色块）
- [x] 7. `CHART_PALETTE` 换色相分明的低饱和 10 色、按 id 稳定
- [x] 8. `StatsUiState.rankChildren` 复用既有聚合，排行一级内联展开二级
- [x] 9. 排行三维（分类 / 收款方 / 账户）全部跳明细
- [x] 10. 主 spec `ledger-transactions` 条件清单 MODIFIED 整块替换 +
      `category-stats` delta MODIFIED + `reporting-ui` delta ADDED
- [x] 11. 活文档 §7.12 / §1 / §8、`project.md` 阶段计划
- [x] 12. 版本 `0.2.10 → 0.2.11`
- [x] 13. 清理上批遗留 `.github/workflows/diag.yml` / `diag/unit.log`

## 交付
- [x] 14. push CI 与 release 实测回填、坚果云上传（代码 `73e8a19818d0` / CI `44260916347` **success**
      17 步全绿、首轮零编译错误；release `44261811015` **success** 23 步绿 / 5 步按 input
      跳过，上传到坚果云 completed success；制品 `release-v0.2.11` 12,320,054 B、
      `mapping-v0.2.11` 3,672,588 B、`metrics-v0.2.11-p-m-nsfalse` 272 B）
- [ ] 15. **真机复核（由使用者装机确认）**：chip 显示具体分类名、面板可见可改可清；
      扇区与图例都能跳、相邻色块可辨；排行一级展开二级、点二级跳明细；
      收款方 / 账户排行跳明细且条件正确
