# design · account-icon-ref-and-weighted-chips

## 决策 1：chip 是入口，不是展示位

第 80 批决策 11 为「编辑态旧值不丢」把摘要拼进 chip label：
`备注·前3字·不计入`、`优惠·5.00`、完整账户名。
而 `weight(1f)` 等分在 360dp 屏每枚约 64dp，扣内边距只剩约 56dp 文字区，
`labelMedium` 单行 ellipsis 必然截成「备注」「工商…」。

使用者给的第四方案比上轮 A/B/C 都对：**按信息量分配宽度**。

| chip | 权重 | 文案 | 有值提示 |
|---|---|---|---|
| 账户 | 2.2 | 完整账户名 | — |
| 备注 | 1.7 | 固定「备注」 | 右上角圆点（含「不计入」） |
| 优惠 / 手续费 | 1.3 | 固定「优惠」/「手续费」 | 右上角圆点 |
| 标签 | 0.9 | 「标签」或 `#N` | 右上角圆点 |
| 模板 | 0.9 | 固定「模板」 | — |

**顺序**：账户 → 备注 → 优惠 → 标签 → 模板。理由：左宽右窄、由主到次、
两个最窄的收在右端，避免窄项夹在中间把一排切碎；账户作为当前上下文放最左。
转账态 4 枚：备注 2.0 / 手续费 1.6 / 标签 0.9 / 模板 0.9
（账户已常驻首屏两卡，MUST NOT 再进 chips）。

「模板选中后文字不跟着变」按使用者要求固定为「模板」；
模板是否已应用由 sheet 内容体现，MUST NOT 把模板名塞回 chip。
完整旧值仍在各自 sheet 可见可改，主 spec 原「chips 在 label 上显示可读摘要」
整块改写为「角标提示 + sheet 完整值」，MUST NOT 让旧值不可修改。

## 决策 2：又是没清点「我们已有什么」（§7.51 第二次）

核查 `AccountIconCatalog.kt`：`AccountIconBox`（40dp 品牌图标 + 卡种角标）、
26 个 PNG（21 家银行 `icongo/bank-logos` MIT + Simple Icons CC0）、
`AccountKindBadge` 全部已存在，`AssetsScreen.kt:483` 一直在用。
第 80 批写 `AccountPickerSheet` 时只用了 12dp「储/信」小角标 + 纯文本 +
余额，既漏接图标、又照抄了一羽的余额。**两处都认账，不写成「引入新图标」。**

落地：
- 账户 sheet 每格 `AccountIconBox(size = 44)`；`AccountKindBadge` 尺寸改为
  `size * 0.4` 等比放大（44 → 17dp，字号 `size * 0.5` → 8.5sp），
  MUST NOT 再用 12dp / 7sp 小角标。
- **余额整行删除**，包括转账两卡里的余额（同属抄一羽的部分）。
- 卡号后四位与卡种降为次要小字 `尾号 1122 · 储蓄卡 · CNY`
  （`accountSecondaryLabel` 单一组合点）——「不用记，是能看见」，
  且用文字而非小角标保证储/信一眼可分。
- 搜索仍走 `accountBaseLabel`（含卡号后缀），spec 要求不变。
- 转账两卡改用 `AccountIconBox` 而非「出 / 入」占位字，全站图标语言统一。

零新增资源、零新增依赖。

## 决策 3：升表落库，但 `Migrations.ALL` 仍为空

上轮把「零改表 prefs 映射」列为推荐，唯一顾虑是升表会打断覆盖安装。
使用者第 2 项明确「当前是测试数据，不需要考虑升级，直接卸载重装」
⇒ 顾虑消除，按**升表**执行：

`AccountEntity.iconRef: String? = null` + `AppDatabase.version = 9`
+ `AccountDao.updateAccountMeta` SQL 补 `iconRef = :iconRef`
+ Repository / ViewModel / `EditAccountDialog.onConfirm` 一并透传，
随「保存」一次事务落库。`Migrations.ALL` 保持 `emptyArray()`，
`MigrationContractTest.ALL.isEmpty()` 零改动。

`iconRef` 三种取值：

| 值 | 含义 |
|---|---|
| `null` / `auto` | 按账户名反查 `AccountTemplates.ALL`（旧数据、改名前的自动推断零回归） |
| `preset:<iconKey>` | 显式关联某个预制品牌图标，**改名也 MUST NOT 丢** |
| `custom:<uuid>` | 使用者本机导入的 PNG |

**备份 DTO 刻意不扩字段**（`BackupAccount` / `LedgerMappers` 零改动）：
导出不带图标引用、导入后回退自动图标 —— 与第 5 项「不进备份」一致。
CSV / Firefly 导入新建账户同样不写该列（默认值 null）。
图标文件存 `filesDir/account-icons/`，卸载即随应用数据清除，不产生跨设备孤儿。

**代价显式登记**：version 9 + 无 Migration ⇒ 覆盖安装 v0.2.60 会开库失败，
**MUST 卸载重装**。全仓 6 个静态脚本原锁 `version = 8`（或 7/8），
本批统一收到 9 / (7,8,9)，并在 `check_record_layout.py` 加 12 条第 82 批断言。

## 决策 4：导入怎么导、怎么和预制一个风格

**导入方式**：复用项目既有 SAF `ActivityResultContracts.OpenDocument()` +
`contentResolver.openInputStream`（备份 / CSV / Firefly 导入同款）。
零新增权限、零联网；Manifest 至今不声明 `INTERNET`，
§7.41 明确否决过一羽「从 IconFont 下载 PNG」的联网方案，
§7.46 记过 UGC 图库逐个授权审计的坑。

**格式**：只收**透明底 PNG**。检查是否存在 alpha < 250 的像素，
无透明区域的普通照片直接拒绝并提示「App 不做自动抠图」——
零依赖做不到自动去底，MUST NOT 假装能（§7.41「不假装 1:1」）。

**生成管线**（`AccountCustomIcons.import`，全平台 API）：
降采样防 OOM → 透明底校验 → 按非透明 bbox 裁正方形 →
保留 alpha、RGB 统一染成中性深灰 `#4A505C` 单色剪影 →
`Canvas` 画 **128×128 白圆底 + 图形 56% 内缩** → 写私有目录 PNG。

**为什么同构就是「一个风格」**：第 64 批 26 个预置账户 PNG 本来就是
「统一裁切 + 白圆底 → 128×128」。导入生成物同尺寸、同圆底、同内缩，
放进同一个 `AccountIconBox` 时尺寸 / 圆底 / 角标完全一致，
MUST NOT 出现「一张彩色 logo、一张方图」的拼盘感。
如实登记：单色剪影 ≠ Tabler 线性笔画，位图在零依赖下无法描摹，
这是**容器语言统一**而非同款图标。上轮推荐 a 里写的「12% 色底」在本批
改成「白圆底」，理由正是预制资源本身是白圆底 —— 同构优先于抽象对齐。

**授权口径**（NOTICE 新增 [D] 段）：本机文件由使用者自己选、仅写私有目录，
不随 APK 分发 / 不上传 / 不进备份，§7.46 针对**随包分发**的逐项许可审计不适用；
UI 提示「仅本机使用、不上传、不进备份；许可由使用者自行确认」。
预置资源仍按 [A]/[B]/[C] 逐项登记，MUST NOT 因 [D] 改写成「已解决授权」。

## 决策 5：分类整块不做（使用者第 3 项）

使用者原话按以下口径理解并登记：「当前分类应该比较全了，后续如果需要，
直接更新版本，阈值 Tabler」= 预置 Tabler 覆盖已够，本批不开分类自定义图标入口；
将来需要时发新版、按 `CategoryIconKeys` / `CategoryIconCatalog` 的编译期
`when(iconKey)` 常量表追加资源（T14，MUST NOT 运行时字符串反射）。

因此 `CategoryIconCatalog`、90 个 drawable、`default_categories.json`、
`CategoryEntity.iconName`、分类管理三页全部零改动。
NOTICE [D] 段措辞同步限定为「账户」，MUST NOT 写成「账户或分类」。

## 考虑过的其他方案

| 方案 | 为什么否决 |
|---|---|
| 两行 3+2 chips | 净增约 40dp，第 80 批已因首屏风险否决；使用者本轮也没选 |
| 短标签 + 有值角标但保持等宽 | 账户名仍被截，没解决「账户只能看到前面部分」 |
| 自适应宽度换行 | chips 条高度随内容跳，键盘上方区域忽高忽低 |
| prefs 映射（上轮推荐） | 使用者已放行升表；落库更贴合「账户-图标」数据关系，账户删除不留孤儿 |
| 备份 DTO 扩 iconRef | 使用者第 5 项「不进备份」；且扩字段要动导入/导出契约 |
| 从 IconFont / 网络下载图标 | 无 INTERNET + §7.46 UGC 授权坑 |
| SVG / 普通照片自动抠图 | 需新增解析库或 CV，项目连图片加载库都不引 |
| 把位图描摹成 Tabler 线性笔画 | 零依赖做不到，§7.41 不假装 1:1 |
| 引入 `material-icons-extended` | 数 MB，项目刻意只用 core set |
| 用 `ModalBottomSheet` 做预置网格 | ExperimentalMaterial3Api，T12 惯例不用 |

## 风险与真机待验

- version 9 无 Migration ⇒ **必须卸载重装**，覆盖 v0.2.60 会开库失败。
- 窄项约 41dp 是否点得中（点击区仍 ≥40dp，但视觉上偏窄）。
- 右上角 6dp 圆点是否会被误读成通知红点。
- 白圆底在深色模式下是否刺眼（预置 logo 本来也是白圆底，属既有观感）。
- 26 项预置网格在 `AlertDialog` 内 180dp 滚动区是否够用。
- 导入非透明图能否得到明确提示；导入后与预制并排是否协调。
- 全部列在活文档 §7.65 的 §8，保持 `[ ]`；CI 全绿不替代观感验收。
