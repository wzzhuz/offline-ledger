# account-icon-ref-and-weighted-chips

## 为什么

v0.2.60 真机三条反馈：

1. **chips 文字被截**：「备注与不计入共用一个按钮，文本太长只能看到『备注』；
   支持账户也只能看到前面部分」。核查：`EntryChipBar` 是 5 枚 `weight(1f)`
   等分，360dp 屏每枚约 64dp；而第 80 批为「编辑态旧值不丢」把
   `备注·前3字·不计入` 这类摘要塞进了 chip 的 label —— chip 是入口，不是展示位。
2. **账户选择只有纯文本**：「储蓄卡和信用卡图标很小，不能一眼区分，还得记卡号；
   选择里显示余额没必要，这一点不需要抄一羽」。核查后认账两点：
   `AccountIconBox` / `AccountIconCatalog`（26 个品牌 PNG）/ `AccountKindBadge`
   **项目里早就存在**、资产页一直在用，第 80 批写账户 sheet 时没接上
   （§7.51「借鉴竞品没清点我们已有什么」第二次）；余额则是照抄一羽加的。
3. **账户编辑想关联并导入图标**，且要求「和预制的是一个风格」。

## 改什么

- `EntryChipBar` 改**非等宽**：收支态 账户 2.2 / 备注 1.7 / 优惠 1.3 /
  标签 0.9 / 模板 0.9，顺序 **账户 → 备注 → 优惠 → 标签 → 模板**
  （左宽右窄、由主到次、两个最窄收右端）；转账 4 枚。
  模板固定「模板」（选中后 MUST NOT 跟着改名）、备注固定「备注」、
  优惠固定「优惠 / 手续费」、标签只显示「标签 / `#N`」；
  有值一律改右上角 6dp 小圆点，完整值进各自 sheet。
- 账户 sheet 与转账两卡接回既有 `AccountIconBox(size = 44)`，
  卡种角标按尺寸等比放大；**删掉余额行**；卡号后四位与卡种降为次要小字。
- `AccountEntity.iconRef` + `AppDatabase.version = 9`（`Migrations.ALL` 仍空，
  使用者明确「测试数据、直接卸载重装」）。`iconRef` 支持
  `null`/`auto` 按名推断、`preset:<key>` 显式关联、`custom:<uuid>` 本机导入。
  编辑账户时随「保存」一次写回；**备份 DTO 刻意不扩字段**（不进备份）。
- 新增 `AccountCustomIcons`：复用既有 SAF `OpenDocument()` +
  `contentResolver.openInputStream`，只收**透明底 PNG**；
  裁方 → 保留 alpha 染成中性深灰单色剪影 → 128×128 白圆底 +
  图形 56% 内缩 PNG，写进 `filesDir/account-icons/`，
  与第 64 批 26 个预置账户 PNG **同构**。
- 账户编辑页提供「预置（26 项网格）/ 导入 PNG / 自动」三入口；
  `assets/NOTICE` 新增 [D] 段区分「本机导入」与「随包分发」授权口径。

## 不改什么

- **分类自定义图标整块不做**（使用者第 3 项）：不开导入入口、
  不动 `CategoryIconCatalog`、90 个 Tabler 资源与 `default_categories.json` 零改动；
  后续需要按使用者口径「直接更新版本追加 Tabler 预置」，走编译期 `when` 常量表。
- 4 Tab、分类快捷栏、收款方联想条、描述首屏、聚合 SQL、CSV/Firefly 往返、
  统计口径、备份加密链路零改动。
- 零新增 Gradle 依赖（不引 Coil / Glide / SVG 解析库 / `material-icons-extended`）、
  零 `INTERNET`、零运行时权限、零自动抠图（位图零依赖做不到，不假装 1:1）。
- `Migrations.ALL` 保持空数组，`MigrationContractTest` 零改动。

## 对齐结论（阶段 0，五项）

| # | 项 | 结论 |
|---|---|---|
| 1 | 要解决什么 | ① chip 摘要截断（根因是摘要塞进 label + 等分宽度）；② 账户 sheet 接回既有品牌图标、放大卡种标识、删余额、卡号降次要；③ 账户可关联/导入本机图标，导入后与预制观感统一 |
| 2 | 改什么 / 不改什么 | 见上两节。只改 `ui/record/` chips 与账户 sheet、`ui/assets/` 图标与编辑页、Entity/DB/DAO/Repository/ViewModel、NOTICE、6 个静态脚本与文档；分类图标、4 Tab、聚合 SQL、备份 DTO 一律不动 |
| 3 | 关键约束 | 三条铁律；T14 编译期常量表；§7.41 借组织方式不借数据口径、等价适配要写明；§7.46 授权口径区分随包分发与本机导入；导入图不进备份；沙盒不编译 APK，走 Actions |
| 4 | 验收标准 | 12 个静态检查 + 单测 + Debug + Release lintVital 全绿；真机确认 chips 不截字、账户 sheet 一眼分储/信、导入 PNG 与预制并排协调、深色下圆底、4 Tab 与备份往返零回归；出包核对「上传到坚果云」job step success；**卸载重装**验收 |
| 5 | 被否决方案及原因 | 从 IconFont / 网络下载图标（无 INTERNET + §7.46 授权坑）；prefs 映射而非升表（使用者已放行升级，落库更贴合「账户关联」语义）；SVG / 普通照片自动抠图（需新增库，零依赖做不到）；把位图描摹成 Tabler 线性笔画（同上）；两行全平铺 chips（+40dp 首屏风险）；详见 `design.md` |

**使用者五项拍板**：① 不等宽、模板/标签窄、账户宽、模板文字不跟着变、
标签只显示数量、窄项调顺序；② 测试数据直接卸载重装（允许升表）；
③ 分类图标暂不考虑，后续追加 Tabler；④ 导入按推荐；⑤ 不进备份。

## 验收

见 `specs/reporting-ui/spec.md`（4 MODIFIED + 2 ADDED）与
`specs/privacy-security/spec.md`（1 ADDED）；真机清单见活文档 §7.65 的 §8。
**本批 version 9 且无 Migration，MUST 卸载重装，不能覆盖 v0.2.60。**
