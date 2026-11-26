# design · polish-transfer-accounts-and-clear-fields

## 决策 1：先分清参考图与现状（§7.41）

使用者给的 4 张截图 + 本轮 1 张全是**一羽记账**，不是本 App。
核查 `RecordEditorDialog.kt` 后，v0.2.59 转账态的实际首屏是：
类型 Tab / 金额 / 日期无标签副行 / 描述内联 / 5 枚 chips / 键盘 / 保存。
分类行、收款方行、联想条在转账态全部隐藏，两张账户卡**完全不存在**，
账户被折进「转出/转入」chip 的底部 sheet。

**判断**：支出/收入态折叠账户成立，因为 `LedgerPrefs.defaultAccountId` 预填、
账户是低频；转账态没有这个前提——转出与转入都是必填主字段，
一个都不能靠默认值猜。第 80 批把「账户低频」当成全类型结论，是本批要修的外推。

## 决策 2：借两张白卡的观感，不借卡片堆叠

一羽是两张大白卡 + 中间 ⇅。本批落地：

- 两张 `RoundedCornerShape(16.dp)` 卡片，`LedgerColors.CardSurface`
  （浅色纯白 / 深色 `#1E2025`），页面微灰底形成清晰边界；
  **MUST NOT 用阴影、描边或卡片堆叠**（§7.41「借卡片观感，不借卡片堆叠」）。
- 每张高 ≥64dp：左边 40dp 圆角矩形图标容器 + 主色 12% 底 + 「出 / 入」字，
  右边标签 + 账户名（含卡号后四位） + 余额小字 + `›`。
  **零新增图标依赖**——只引 `material-icons-core` 的 `Icons.Filled.Close`，
  MUST NOT 为对齐截图去引 `material-icons-extended`（数 MB，项目刻意不引）。
- 不做 ⇅ 互换（使用者确认 ②）：那是一个新增手势，收益低、要真机验。
- 点哪张卡就把 `AccountPickerSheet` 的 `initialTarget` 设为 0/1 并
  `lockTarget = true`，sheet 标题改为「选择转出账户 / 选择转入账户」，
  **sheet 内 MUST NOT 再显示分段**——卡片本身已经指明了目标，
  留分段就是双入口，等于把刚撤掉的「更多」层换个形式加回来。

## 决策 3：转账态 chips 减到 4 枚

使用者确认 ③ 移除「转出/转入」chip。转账态 chips =
模板 / 备注 / 标签 / 手续费；支出/收入态仍 = 账户 / 模板 / 备注 / 标签 / 优惠。
`EntryChipBar` 按 `isTransfer` 组装两条 list，`accountLabel` 只在收支态用。
MUST NOT 为了「统一 5 枚」留一枚点开也是账户的重复入口。

## 决策 4：清空图标是补齐既有 spec，不是新功能

`ClearFieldIcon` 抽成同包 private 组件（36dp 容器 + 18dp Icon + `clickable`），
四处调用方各自判空，空内容 MUST NOT 渲染空叉号：

| 位置 | v0.2.59 | 本批 |
|---|---|---|
| 交易对方（首屏内联） | 已有内联实现 | 改调公共出口 |
| 描述（首屏内联） | 无 | 新增 `vm.setDescription("")` |
| 备注（备注 sheet） | 无 | 新增 `onNote("")` |
| 账户搜索（账户 sheet） | 无 | 新增 `query = ""` |

`size(36.dp)` 而非 `IconButton`（默认 48dp），保住既有
`heightIn(min = 40.dp)` / 32–44dp 行高量级。

**顺带修一处规格腐烂**：`记一笔输入框紧凑且支持一键清空` 后半段仍描述
「『更多』展开区」「⌄/⌃ 展开指示器」「账户 › 整行点击」，全是第 77 批之前的
形态。第 80 批 MODIFIED 了 7 条却漏了这条。本批整块改写，去掉过期段落、
把描述纳入覆盖，MUST NOT 让主 spec 留下两条相反要求。

## 决策 5：坚果云上传回归排查（第 4 问）

核查远端 `release.yml` 与最近 6 次 run 的 job steps：

| 版本 | run | 上传到坚果云步骤 |
|---|---|---|
| v0.2.55 及更早 | `45646127262` / `45741996379` | success |
| v0.2.56 | `45747016651` | success |
| v0.2.57 | `45772816373` | **skipped** |
| v0.2.58 | `45777241409` | **skipped** |
| v0.2.59 | `45789650969` | **skipped** |

`release.yml` 的 `upload_nutstore` input **默认 `true`**，
`if: inputs.upload_nutstore` 控制「上传到坚果云」步骤。
本会话从 v0.2.57 起，每次 `workflow_dispatch` 都把完整 inputs 全量传了一遍，
其中显式带 `"upload_nutstore":"false"` → 步骤直接 skipped。
与 secrets 无关（v0.2.56 及之前 success 证明 `JGY_USER` /
`JGY_APP_PASSWORD` 配过且有效），不是 workflow 被改坏、不是服务异常。
**是本会话自己关的，如实认账。**

**防范（§4.1「口头提醒无效，必须落成可执行检查」）**：
`check_workflow_syntax.py` 新增两条断言——`upload_nutstore` 的 workflow 默认值
必须是 `true`、「上传到坚果云」步骤必须绑定 `inputs.upload_nutstore`。
以后手动 dispatch **只传要改的 inputs**，别把整份默认值全量覆盖；
漏传时 GitHub 会自动用默认 `true`，本来不会出这事。
本批出包显式带 `true`，并核对 job step 结论为 success 再交付。
免费版每月 1GB 上传流量，单包约 3.4MB，无流量顾虑。
若遇到 HTTP 000（跨境链路抖动），换个时间重跑即可，MUST NOT 判成配置坏。

## 考虑过的其他方案

| 方案 | 为什么否决 |
|---|---|
| 账户继续留 chip、只把 sheet 默认展开 | 还是看不到两张卡，没解决「主元素不该折叠」 |
| 两卡照搬一羽，连「转账到账金额 / 全部」一起搬 | 金额已在顶部大字号，重复；且不存在的业务字段不补（§7.41） |
| 做 ⇅ 互换 | 新增手势，使用者明确否决 |
| 三种类型都把账户放回首屏 | 使用者确认只改转账态；收支态默认账户本就低频 |
| 转账态保留第 5 枚 chip 作为「全部账户」入口 | 双入口，等于换形式把中间层加回来 |
| 用 `ModalBottomSheet` 或新图标库 | 实验 API / 新增依赖，项目 T12 与 §7.41 均不允许 |
| 不改 workflow、只口头提醒「下次记得传 true」 | §4.1 已证明口头提醒无效，必须落成断言 |

## 风险与真机待验

- 转账首屏多两张卡（约 140dp），中小屏是否挤压描述行 / 键盘，真机确认。
- 卡片与微灰底的层级、深色下对比、40dp 图标容器的「出 / 入」字是否清楚。
- 点卡进 sheet 是否正确锁定目标、sheet 关闭后两卡回填。
- 三处新增叉号是否出现、点一下清空、不撑高行；收支态 5 枚 chips 零回归。
- 全部列在活文档 §7.64 的 §8，保持 `[ ]`；CI 全绿不替代观感验收。
