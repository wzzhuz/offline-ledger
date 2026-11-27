# text-intake-diagnosis · Design

## 决策

| # | 决策 | 理由 |
|---|---|---|
| 1 | 走**零电量文本入口**，无障碍整套搁置但不删除结论 | 使用者对 2 年旧机电量敏感；HyperOS 全选已把整页文字交到系统层，绕过了节点树 L2 与 App 改版风险 |
| 2 | 同时注册 `ACTION_PROCESS_TEXT` 与 `ACTION_SEND`（text/plain） | 前者若对第三方开放可省 1 步；后者是使用者已亲眼看到的一级菜单，作为保底。一个 Manifest 两个 filter，成本近零 |
| 3 | 不注册剪贴板监听，不读 `ClipboardManager` 后台 | Android 10+ 只有默认输入法或前台持焦点应用能读；13+ 后台返回 null/空且不抛异常，是隐性失效 |
| 4 | 本批**只诊断、不落库** | 原文质量未验前写规则表与自动落库，等于把不确定性固化进代码。先看清是节点聚合还是 OCR |
| 5 | 预览页只读 + 内存态 + 一键清空，默认不导出文件 | 使用者已明确嫌「截图进相册要定期清理」；调试产物再变文件等于换个目录把麻烦带回来 |
| 6 | 诊断页 MUST 先过应用锁，`excludeFromRecents=true`，尊重 `FLAG_SECURE` 偏好 | 原文含商户全称、卡号尾号甚至余额，属 privacy-security「应用锁与界面防泄露」覆盖范围 |
| 7 | 入口 label 用「离线账本 · 文本诊断」，**不叫「记一笔」** | 避免误以为本包已能记账；本批没有任何写库路径 |
| 8 | change **保持活跃不归档** | 真机原文回来后要在同一 change 里补规则表 + 自动落库，最后一起归档；避免第 77 批那样跨批收口又新开 change |

## 考虑过的其他方案（被否决）

| 方案 | 否决理由 |
|---|---|
| 无障碍读节点树 + 摇一摇 / 长按音量键 | 一次系统级广泛授权；进程常驻内存、厂商可能为省电主动关服务、开关与绑定状态分离；对 2 年旧机不友好。搁置为「以后真嫌 3 步多」的备选 |
| 常驻自动识别（支付完成即扫） | 每来一次界面事件 dump 整棵树，耗电与隐私感都最差 |
| 系统截屏 → 分享 → OCR | 截图自动进相册、要定期清理；需 2MB 中文 OCR 依赖；OCR 金额有误差风险 |
| 支付宝 / 微信导出 CSV 或交易流水证明 | 不能及时记录、导出麻烦（申请 / 密码 / 邮箱 / 解压 / SAF 选文件） |
| 剪贴板一键填充 / 后台监听 | 后台读不到；步数 5~6，反而更多 |
| `NotificationListenerService` | 不少消费无通知或通知无金额；需 `POST_NOTIFICATIONS` 与保活，撞 project.md 永久不做条目 |
| `MediaProjection` / 无障碍 `takeScreenshot` | API 34+ 每会话弹窗；FLAG_SECURE 页面拿不到；不落盘但仍需无障碍授权 |
| 云端大模型（含菜单里的「问AI / 问问DeepSeek」） | 把整段账单文本发给第三方，直接撞第一条铁律「默认回传且用户不可控」 |
| 端侧 Gemini Nano / ML Kit GenAI | 设备门槛（AICore / 旗舰芯片 / 12GB RAM）、API Beta、每应用配额、仅前台可推理、短文本数秒延迟；最要命是**金额幻觉**与「自动落库」组合会静默记错账，撞铁律 2 |
| `SYSTEM_ALERT_WINDOW` 悬浮球 / 常驻通知 Action / 前台服务 | 高危权限或撞 project.md 永久不做条目；覆盖层还会污染节点树 |

## 架构

```
系统文本选择菜单 / 分享选择器
        │  Intent(EXTRA_PROCESS_TEXT | EXTRA_TEXT)
        ▼
TextIntakeActivity (exported, excludeFromRecents)
        ├── LedgerPrefs → AppLockScreen（未解锁不渲染任何内容）
        ├── FLAG_SECURE / edge-to-edge（复用 MainActivity 口径）
        └── TextIntakePreviewScreen（只读）
                └── TextIntakeInspector.from(source, rawText)  ← 纯 Kotlin，可 JVM 单测
                        · 截断 20,000 字符并标记
                        · 疑似金额串全量列出（-?\d[\d,]*\.\d{2}），不去重成一个「答案」
                        · 方向 / 时间 / 商户 三项命中（标签 + 关键词）
                        · readyForAutoCommit 只读预演标签，不触发任何写
```

`TextIntakeInspector` 刻意不依赖 Android 类，便于纯 JVM 单测；Intent 抽取留在 Activity 侧。

## 置信度门槛（后续批的硬约束，本批只预演不执行）

写进 `reporting-ui` delta，后续「规则表 + 自动落库」必须遵守：

- 金额 MUST 精确匹配 `-?\d+\.\d{2}` 且**唯一候选**；多候选 MUST NOT 取第一个
- 方向 / 时间 / 商户四项齐全才允许自动落库；缺任一项降级草稿并提示手填
- 疑似 OCR 噪声（错字、小数点丢失、字段乱序）MUST NOT 由模型或启发式「猜一个值补上」
- 去重复用既有 `importSource` + `sourceId` / 「账户 + amountCents + localDate + note」复合键口径
- 自动落库仅适用于 `auto-text-intake` 入口；模板 / 手动 / 桌面快捷方式仍 MUST NOT 自动落库（保持现有 spec 条款原样）

理由：`project.md` 原始场景里弃用 Firefly 的原因正是「差 1 分钱、账单太多核算太费事，最后记一笔虚拟支出凑平」。0 次确认 + 不确定抽取，等于把这类事故自动化。

## 隐私护栏

- 零 `uses-permission`、零新增 Gradle 依赖、零 `INTERNET`、零改表
- 原文只在 `remember { mutableStateOf }` 内存态；**不用 rememberSaveable**（避免写进 savedInstanceState / 系统保存状态）
- 一键清空把字符串置空、GC 可回收；退出 Activity 即释放
- 默认不写任何文件；如后续需要留存，必须走 SAF 由使用者自选位置（本批不实现）
- 预览页顶部明示「本页含屏幕原文，请勿在公共场合展开」
- `excludeFromRecents=true` 防最近任务快照

## 静态脚本影响

- `check_record_layout.py` 第 173 行硬编码 `defaultVersionName = "0.2.64"` → 同步 0.2.65
- 新增 `check_text_intake.py` 并挂 `build.yml`：断言两个 intent-filter / 无新增 uses-permission / Activity 不引用 `LedgerRepository` 与 Room / 单测存在 / 版本号 / 文档登记 / 活跃 change 四件套齐全
- `check_manifest_privacy.py` 不改：它精确匹配 `<uses-permission>`，新 Activity 的 `exported` 不触发

## 真机待验（全部 [ ]）

1. 支付宝账单详情页「全选 → 竖向三点 → 更多」是否出现「离线账本 · 文本诊断」（判 PROCESS_TEXT 是否对第三方开放）
2. 「全选 → 分享」选择器里是否出现「离线账本」（判 ACTION_SEND 链路）
3. 两条任一成功后，把**原文预览贴回**：判节点聚合 vs 系统 OCR、噪声多少、四项字段是否齐全
4. 原文是否混入状态栏时间、贴纸文案、「更多 ⌄」等噪声
5. 应用锁开启时诊断页是否先要求解锁
6. 清空 / 退出后无残留文件
7. 深色模式、从 v0.2.64 覆盖安装零回归
