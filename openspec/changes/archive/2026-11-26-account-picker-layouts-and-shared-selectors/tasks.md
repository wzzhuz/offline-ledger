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

## 第 83 批实测（归档时回填）

- 实现 + 四件套 commit `3acc8ad8`；归档后见文件末尾 main commit
- push CI `45831228592` **全绿**（12 静态检查 + 单测 + Debug + Release lintVital），
  本批无编译报错（第 82 批漏 `size` import 的坑未重演）
- **首次出包 run `45832141371` 失败**：前 19 个 step 全 success
  （含 R8 编译、签名核对、Release APK 制品上传），仅「创建 tag 并发布 Release」
  step `exit 128`；「上传到坚果云」因此 skipped，v0.2.62 tag / Release 均未生成。
  check-run annotation 只有 `Process completed with exit code 128.`，
  Actions 日志域名仍被出口策略拦截（403 policy_default_denied）。
  核查 `release.yml` 第 340–348 行、workflow `permissions: contents/actions: write`、
  远端 tag/release 均 404 后判定为 **GitHub 侧瞬时 git push 故障**，
  非代码 / 配置问题，**未改任何 workflow 或业务代码**。
  原样重新 dispatch 一次（同样的 `create_release=true` / `upload_nutstore=true`），
  run `45833836043` **全绿**：创建 tag success、坚果云 success。
  登记此条是 §4.1「工具不通要换通道，别瞎猜」与「重跑前先确认没有半成品」的实例。
- 归档态 12 个静态检查全 PASS；`check_record_layout.py` 追加了「第 83 批 delta
  必须已合并」的归档态校验（活跃目录已移走，glob 命中 archive）
- Release `v0.2.62`：`offline-ledger-v0.2.62-release-3acc8ad8.apk` /
  3,513,300 B / SHA256 `300ab0f91e26e79e0aaba773cc8c55fd8a5c434a740c5d0b835a058e28e291b5`；
  tag `v0.2.62` → `3acc8ad8`（实现 commit，出包早于归档，与前几批一致）
- delta：`reporting-ui` 3 MODIFIED + 2 ADDED、`privacy-security` 1 ADDED，
  归档后逐块比对正文**全部一致**；既有 Requirement 标题一律未改
- `CategorySelectDialog` 本体零改动，`check_firefly_mapping.py` 锁一字未动；
  活跃 change `record-category-picker-and-root-select` 未碰，design 决策 6 已登记
- **零改表**：`AppDatabase.version` 仍 9、`Migrations.ALL` 仍空、备份 DTO 零改动，
  **可直接覆盖 v0.2.61**（与第 82 批必须卸载重装不同）
- 真机待验项全部保持 `[ ]`，见活文档 §7.66；CI 全绿不替代观感验收
