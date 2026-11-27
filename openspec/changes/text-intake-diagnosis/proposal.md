# text-intake-diagnosis

## 对齐结论（阶段 0，含第 87 批修正）

| # | 项 | 结论 |
|---|---|---|
| 1 | 要解决什么 | 用 HyperOS「系统全选文本」入口做零电量自动记账；第 86 批验入口与原文质量，第 87 批用 6 笔实测原文写规则表、严格充分条件自动落库，并提供规则覆盖层与试解析页，使开放集格式不会造成错账 |
| 2 | 改什么 / 不改什么 | 改：`TextIntakeActivity` / `TextIntakeInspector` / `TextIntakeRules` / `TextIntakeModels`、JVM 单测、`LedgerRepository.commitTextIntake`、`LedgerPrefs`、设置入口、备份排除规则、版本号 0.2.66、静态脚本、`project.md`、活文档、delta spec。**不改**：表结构（version 9）、CSV/Firefly 写路径、`MainActivity` 主导航、4 Tab、备份 DTO、无障碍搁置结论 |
| 3 | 关键约束 | 零 `uses-permission`、零新增 Gradle 依赖、零 `INTERNET`、零后台组件；严格充分条件才自动落库，缺项一律降级草稿、MUST NOT 猜值；优惠过 `ExtraAmountRules`；分类只预置 `餐饮美食→餐饮`；账户匹配不到降级；复用 Repository 事务与既有去重，MUST NOT 新开写余额分支；原文只在内存；微信页未验不外推 |
| 4 | 验收标准 | 13 静态检查 + 单测（6 笔原文 + 未知格式 + 规则覆盖层）+ Debug + Release lintVital 全绿；真机「同一笔分享两次只记一笔」「优惠显示原价/优惠/实付」「2203 与 7375 归对账户」「仅草稿开关生效」「撤销后余额复原」「未知格式不误记」；坚果云 job step success |
| 5 | 被否决方案 | 完整表见 `design.md`「考虑过的其他方案」 |

分级判定：**需要建 change**（新增系统入口、自动落库行为），不是 bug 修复。
本 change **保持活跃、不归档**：微信支付成功页后面几轮再验，同一 change 收口。

## 为什么改（使用者原始诉求）

> 「提高自动记账能力，目标是可以识屏自动填充内容，减少手工输入」「最核心诉求是减少手输，减少人工操作」。

排查过程中逐条被否掉的旧路：

- 下拉磁贴：平台层不能主动弹出，Android 12+ 要手动拖进快捷设置，手感不尽人意
- 系统截屏 → 分享：截图自动进相册，**要定期清理，使用不方便**
- 支付宝定期导出 CSV：**不能及时记录，导出也麻烦**
- 无障碍读节点树 + 摇一摇：一次系统级广泛授权、进程常驻内存、厂商可能为省电主动关服务；手机用了 2 年，**对电量敏感**

转折来自真机反馈（小米 HyperOS 3.0）：

> 「系统全选可以整个页面内容选中。选中之后我看到有『复制』『分享』『网页搜索』『朗读』，竖向 3 个点的更多选项，里面有『百度地图』『百度搜索』『百度翻译』『问AI』『问问DeepSeek』『浏览器搜索』等选项」。

系统层做了整屏文字聚合，绕过 App 自身是否 `textIsSelectable`、绕过支付宝/微信改版与 WebView / 自绘控件 L2 风险；「分享」是一级菜单项。于是零电量文本入口成立：没有图片、没有文件、没有后台、没有权限。此前按 AOSP 口径说「Android 没有全选整页文字」属 §7.51「凭印象外推」，在此更正。

## 使用者拍板

| # | 问题 | 拍板 |
|---|---|---|
| 1 | 无障碍整套是否搁置、改走零电量文本入口 | **同意** |
| 2 | 是否先出诊断小包 | **先出诊断小包**（v0.2.65 已完成） |
| 3 | 置信度门槛 | **可以**；后续修正为「严格充分条件 + 算术自洽」 |
| 4 | 自动落库 | **维持** |
| 5 | 分类 / 备注 / 账户降级 | **接受推荐**：只预置 `餐饮美食→餐饮`，备注不填，账户匹配不到降级草稿 |
| 6 | 规则外置形态 | **B**：AI 生成 1~3 行人读规则，用户粘贴到规则增补框即时生效，不用自己写、不用等出包 |
| 7 | 微信支付成功页 | **后面几轮再看**，本批不外推 |

## 第 87 批实测原文

6 笔全部走 App 入口，覆盖：

- 状态词：`交易成功` / `自动扣款成功`
- 优惠：`实体店通用红包 -0.30` / `中国银行立减金 -0.29`，并有 `订单金额 15.90 / 40.00`
- 无优惠但 `商品说明` 混入 `消费6.00元`（推翻「金额候选唯一」）
- 商户字段：`收款方全称` / `交易详情` 区块 / 仅首行代付人
- 账户：`交通银行信用卡(2203)` / `中国银行储蓄卡(7375)`
- 分类：`餐饮美食` / `亲友代付` / `文化休闲` / `公共服务`
- 图标占位符：`logo` / `arrow` / `extend-icon`
- `商家订单号` 16 / 22 / 32 位，掩码前缀 7895 / 2026 / 0920

## 改什么

- `core/intake/`：规则集、覆盖层解析、只读状态机、字段抽取、金额/时间/优惠/账户/分类/订单号模型
- `ui/intake/TextIntakeActivity`：应用锁、规则增补框、粘贴试解析、自动/仅草稿总开关、只读预演、预填草稿、手工保存、撤销、结果摘要与一键清空
- `data/repository/LedgerRepository.commitTextIntake`：复用事务 / `withResolvedRoot` / 余额重算 / `countByImportSource` / `ExtraAmountRules`
- `core/prefs/LedgerPrefs`：规则覆盖层、自动开关、结果摘要；备份规则排除 SharedPreferences
- `ui/settings/SettingsScreen`：内部入口「自动记账 / 文本诊断」
- 静态脚本、版本号、文档与 delta

## 不改什么

无障碍服务、摇一摇 / 音量键触发、传感器、`takeScreenshot`、节点树 dump、OCR 依赖、云端 / 端侧大模型、通知与前台服务、悬浮窗、任何新权限、任何新 Gradle 依赖、任何 Room 表结构或备份 DTO 字段。

## 影响

- Manifest 仍只多一个 exported Activity；`check_manifest_privacy.py` 精确匹配 `<uses-permission>`，不误报
- `MainActivity` 一字不动，桌面快捷方式与应用锁零回归
- 零改表，可直接覆盖 v0.2.65
- 隐私面扩大可控：原文只在内存、先过应用锁、`excludeFromRecents=true`；规则/开关/摘要不含原文且排除系统备份

## 验收

13 个静态检查 + 单测 + Debug + Release lintVital 全绿；真机重点从「入口能不能打开」转为「格式命中、误判降级、去重、撤销、规则覆盖层即时生效」。


---

## 第 88 批修正：真机反馈「分享进来只有文本诊断，没有自动记录也不是预填」

**使用者反馈**：

> 「分享里面只有『离线账本 文本诊断』调到的是文本诊断入口，没有自动记录，也不是预填」
> 「后续如果离开这个会话，我要用什么提示词来生成规则，把这个写到文档里面」

**根因**：

1. `TextIntakeActivity.extract()` 只读 `EXTRA_PROCESS_TEXT` / `EXTRA_TEXT`，
   没有 `Intent.clipData` 兜底。HyperOS 等 ROM 的「全选 → 分享」可能只把文本放进 `clipData`，
   `EXTRA_TEXT` 为空 → source 判成 `empty` → `LaunchedEffect` 直接 return，
   于是既不自动落库也不形成预填草稿，页面只剩一个空诊断页。
2. 入口标签沿用第 86 批的「离线账本 · 文本诊断」，而第 87 批已经会记账，
   标签与能力不符，属文档腐化（Manifest 注释里甚至还写着「本批还不能记账」）。

**本批改动**：

- `extract()` 增加 `clipData` 第一项兜底；`external` 改为「收到非空文本且不是内部试解析」，不再枚举 action 字符串
- 入口标签改为「离线账本 · 自动记账」，`PROCESS_TEXT` 菜单项改为「自动识别并记账」；Manifest 过期注释同步
- 页面新增「本次入口状态」醒目卡片：字符数 / 来源 / 是否会自动写库 / 未收到文本时的操作引导
- 新增 `docs/自动记账规则生成提示词.md`：完整可复制 Prompt、四种指令、使用步骤、结构变化判定、隐私提醒
- App 内规则增补卡片新增「复制规则生成提示词」按钮，手机上不用翻文档
- 版本号 0.2.66 → 0.2.67；`check_text_intake.py` 增加 clipData / 标签 / 提示词文档 / 常量一致性断言

**流程判定**：修 bug（恢复第 87 批已承诺的自动落库行为）+ 文档补充，**不新建 change**，
在同一活跃 `text-intake-diagnosis` 内补 tasks/design/delta，仍不归档。


---

## 第 89 批修正：真机反馈「没有入库 + 页面太乱」

**使用者反馈**：

> 「没有入库，提示处理结果： REJECTED 写入失败 The coroutine scope left the composition」
> 「当前自动记账/文本诊断页面太混乱了。包含了新增规则，试解析，错误提示，手工保存，
> 最近结果摘要等等，自动记账失败都要找半天错误提示在哪，页面是不是拆一拆或者重新规划一下」

**根因（第 1 条，认账）**：

第 88 批把自动落库放在 `LaunchedEffect(unlocked, external, manualMode, autoEnabled,
autoAttempted, rulePatch)` 里，却在 effect 内部第一行写 `autoAttempted = true`——
它是 effect 的 key，赋值即触发重组 → effect 自我取消 → 正在跑的
`db.withTransaction` 被 cancel。应用锁 ON_PAUSE / 解锁 / 改规则也会让 key 变化。
而 `doCommit` 用 `catch (e: Exception)` 把 `CancellationException` 一并吞掉，
包装成 `Rejected("写入失败：The coroutine scope left the composition")`，
看起来像数据库失败，其实是**协程被取消**；事务可能根本没提交。

**根因（第 2 条）**：第 87/88 批把所有能力堆进一条垂直滚动列，
结果条、原文、规则、试解析、摘要、表单互相挤；
失败时 `REJECTED` 卡片混在中段，第一屏看不到。

**本批改动**：

- 写事务整体移到 Activity 级 `lifecycleScope`；结果用 Activity 级 `mutableStateOf` 持有，Compose 只读订阅
- 自动落库触发改为普通 `var autoCommitStarted` 一次性标记，不再作 effect key；
  未开应用锁在 `onCreate` 触发，开锁在 `onUnlocked` 触发，重复解锁不重跑
- `commitSafely` / `undo` MUST 先 `catch (e: CancellationException) { throw e }` 再 catch 业务异常
- 页面拆三页签：**本次结果 / 规则与试解析 / 记录**；外部入口默认「本次结果」，
  设置页内部入口默认「规则与试解析」
- 「本次结果」置顶常驻结果条（类型 + 原因 + 字符数 + 来源），紧随识别摘要与预填草稿表单；
  失败原因第一屏可见
- 规则、试解析、自动开关收进第二页；原文全文与结果留痕收进第三页
- 版本号 0.2.67 → 0.2.68；静态脚本新增 lifecycleScope / CancellationException /
  TabRow / 「LaunchedEffect 块内不得写库」断言

**流程判定**：修 bug（恢复自动落库）+ 同一 change 内的信息架构重排，**不新建 change**；
`text-intake-diagnosis` 仍活跃不归档（微信页留后续轮）。
零改表、零新增依赖 / 权限 / `INTERNET`、零后台组件，可直接覆盖 v0.2.67。
