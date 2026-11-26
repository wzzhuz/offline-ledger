# tasks · account-picker-layouts-and-shared-selectors

> 阶段 0 五项对齐 + 使用者四项拍板后才动文件。
> v0.2.61 已归档，本批按流程新开 change，不打补丁。
> 本批零改表，可直接覆盖 v0.2.61。

- [x] 1.1 强制开场核查远端 HEAD / CI / Release 与本地一致性
- [x] 1.2 盘点两套排序口径、`DropdownSelector` 唯一残留、`CategorySelectDialog`
      三处复用、统计页无独立账户筛选、活跃 change 同标题冲突
- [x] 1.3 阶段 0 五项对齐 + 四项拍板（1A / 口径对 / 3B / 4B）
- [x] 2.1 新增 `ui/components/AccountSelectSheet.kt`：双布局分段、搜索跨组平铺、
      44dp 图标、无余额、allowNone、壳
- [x] 2.2 `accountSecondaryLabel` 上提为 public 唯一组合点
- [x] 2.3 `LedgerPrefs.accountPickerLayout` + `rememberAccountPickerLayout()` 跨启动
- [x] 3.1 记一笔 `AccountPickerSheet` 改薄封装，移除不可达的 target 分段
- [x] 3.2 模板/周期：`DropdownSelector` 整块删除，分类接 `CategorySelectDialog`、
      转出/转入接 `AccountSelectDialog`、`SelectorRow` 整行 › 入口、补「不指定」
- [x] 3.3 明细筛选 `"account"` 分支换通用选择器（统计下钻链路自动覆盖）
- [x] 4.1 版本号 0.2.62；`check_record_layout.py` 断言目标同步 + 14 条第 83 批断言
      + 归档态「delta 必须已合并」；`check_firefly_mapping.py` 一字不动
- [x] 4.2 活文档 §7.66 / §8、`project.md` 第 83 批登记
- [x] 5.1 四件套 + delta（3 MODIFIED + 3 ADDED）
- [x] 5.2 12 个静态检查全 PASS
- [x] 5.3 推送 → 双 CI → 出 v0.2.62 包（dispatch 只传 create_release /
      upload_nutstore=true，坚果云 step 核对 success）
- [x] 5.4 归档：delta 合并回 reporting-ui / privacy-security + 移入 archive +
      正文逐块比对一致
- [x] 5.5 用 GitHub API 按 commit sha 逐文件比对远端、查 main CI

## 实测（归档时回填）

见文件末尾「第 83 批实测」。真机待验项全部保持 `[ ]`，见活文档 §7.66。
