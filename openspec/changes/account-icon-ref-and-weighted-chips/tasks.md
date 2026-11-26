# tasks · account-icon-ref-and-weighted-chips

> 阶段 0 五项对齐 + 使用者五项拍板后才动文件。
> v0.2.60 已归档，本批按流程新开 change，不打补丁。

- [x] 1.1 强制开场核查远端 HEAD / CI / Release 与本地一致性
- [x] 1.2 盘点 `AccountIconCatalog` / 26 个 PNG / `CategoryIconCatalog` /
      `EditAccountDialog` / SAF 导入 / 6 个锁 version=8 的静态脚本
- [x] 1.3 阶段 0 五项对齐结论 + 五项拍板（不等宽 / 卸载重装 / 分类不做 /
      导入按推荐 / 不进备份）
- [x] 2.1 `EntryChipBar` 改 `EntryChip(label, weight, hasValue, onClick)` 非等宽
- [x] 2.2 短标签固定、标签只显示数量、有值改 6dp 圆点、窄项收右端
- [x] 2.3 账户 sheet 接 `AccountIconBox(size=44)`、删余额、卡号/卡种降次要
- [x] 2.4 `AccountKindBadge` 字号按尺寸等比放大；转账两卡换图标 + 删余额
- [x] 3.1 `AccountEntity.iconRef` + `version = 9` + DAO SQL / Repository /
      ViewModel / `EditAccountDialog.onConfirm` 全链路透传
- [x] 3.2 `Migrations.ALL` 保持空数组；备份 DTO 刻意不扩字段
- [x] 3.3 新增 `AccountCustomIcons`（SAF / 透明底校验 / 裁方 / 单色剪影 /
      128×128 白圆底 / 私有目录）
- [x] 3.4 账户编辑页「预置 26 项网格 / 导入 PNG / 自动」三入口
- [x] 3.5 资产页 / 排序页 / 模板 sheet 三处 `AccountIconBox` 读 `iconRef`
- [x] 4.1 `assets/NOTICE` 新增 [D] 本机导入授权段落
- [x] 4.2 版本号 0.2.61；6 个脚本升表锁收 9；`check_record_layout.py` 加 12 条断言
- [x] 4.3 活文档 §7.65 / §8 待验、`project.md` 第 82 批登记
- [x] 5.1 四件套 + delta（4 MODIFIED + 3 ADDED）
- [x] 5.2 12 个静态检查全 PASS
- [x] 5.3 推送 → 双 CI → 出 v0.2.61 包（dispatch 只传 create_release /
      upload_nutstore=true，坚果云 step 核对 success）
- [x] 5.4 归档：delta 合并回 reporting-ui / privacy-security + 移入 archive +
      正文逐块比对一致
- [x] 5.5 用 GitHub API 按 commit sha 逐文件比对远端、查 main CI

## 实测（归档时回填）

见文件末尾「第 82 批实测」。真机待验项全部保持 `[ ]`，见活文档 §7.65。
**本批 MUST 卸载重装，不能覆盖 v0.2.60。**
