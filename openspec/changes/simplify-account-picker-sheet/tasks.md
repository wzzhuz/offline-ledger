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
