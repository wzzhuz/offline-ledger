# Delta for privacy-security

## ADDED Requirements

### Requirement: 账户品牌图标资源逐项登记且缺项回退

账户图标 SHALL 由 `AccountIconCatalog` 以**编译期显式常量表**
（`when(iconKey)`）提供，MUST NOT 用运行时字符串反射 / 查表映射。
`AccountTemplate.iconKey` SHALL 为资源键位预留，
资源 SHALL 为**随包本地图形**（第 64 批为 `drawable-nodpi/` 下
128×128 白圆底 PNG），MUST NOT 引入在线下载、
MUST NOT 因此申请 `INTERNET` 权限、MUST NOT 新增 Gradle 依赖。
每一份引入的品牌图形 SHALL 在 `assets/NOTICE` 逐项登记
**作者 / 来源链接 / 授权类型 / 传递依赖**，
MUST NOT 用「来自某图标平台」代替逐项审计
（平台图标由不同作者上传、授权状态逐个不同）。
**第 64 批资源已补齐**：27 个 `iconKey` SHALL 全部映射到本地图形
（26 个 PNG，`alipay` 与 `huabei` 复用同一份）；
**未登记的 `iconKey` 仍 SHALL 回退** `CategoryIcon` 的中性圆 + 中文首字。
MUST NOT 凭空绘制或临摹银行行徽、MUST NOT 复制一羽记账的视觉资产。
Q7 的使用者判断（「指示账户归属、记账类、无授权风险」）
与公开权利声明的差异 SHALL 在 `assets/NOTICE` [C] 与 design.md
如实并列并标注**由使用者决策并承担**，
MUST NOT 写成「已解决」或「无风险」的事实结论。

#### Scenario: 未登记键位仍回退

- **GIVEN** 一个不在 27 个模板键位内的自定义账户（`iconKey = null`）
- **WHEN** 打开资产页 / 模板 sheet / 记一笔
- **THEN** 显示中性圆 + 中文首字，页面正常渲染、零网络请求

#### Scenario: 替换资源不动 UI

- **GIVEN** 后续拿到逐项授权登记后的图标文件
- **WHEN** 覆盖同名 PNG（或新增键位时补 `when` 分支与 NOTICE 条目）
- **THEN** 三页 UI 代码零改动即可显示对应图标
- **AND** 未登记的 `iconKey` 仍回退中性圆

---

### Requirement: 账户品牌图标来源与授权口径逐项登记

**第 64 批**：实际随包分发的每一份账户品牌图形 SHALL 有可追溯来源，
并在 `assets/NOTICE` 逐项登记 `iconKey / 品牌 / 来源文件 / 授权类型 / 商标归属`。
本批 SHALL 只采用带明确开源许可的源：
`icongo/bank-logos`（MIT，21 家中国银行 SVG）与
`Simple Icons`（CC0-1.0，微信 / 支付宝 / 美团 / 抖音月付）；
无 CC0 / 明确授权图形的京东白条 SHALL 用**本作品原创**占位图形，
MUST NOT 临摹京东商标。
**IconFont 未被采用**：UGC 图标授权逐个不同、部分图库需打赏才商用、
且需登录态导出；平台聚合页 MUST NOT 当作「可随包分发」的依据。
若后续改用 IconFont，SHALL 逐个打开详情页核验授权、留存凭证、
替换同名 PNG、并**逐行改写** NOTICE [B] 表，MUST NOT 只写平台名。
`assets/NOTICE` [C] SHALL 如实并列：使用者判断、
2026 年修订《商标法》第 73 条第 3 款的指示性使用、
MIT / CC0 **不覆盖商标权**、Simple Icons 官方商标免责、
以及未取得逐家书面授权这一事实；
MUST NOT 写成「已获授权 / 无风险 / 已解决」。
转换流程 SHALL 可由 `tools/build_account_icons.py` 复现，
该脚本为构建期工具，App 运行时 MUST NOT 执行、MUST NOT 联网。

#### Scenario: 27 个键位全部有本地图形

- **GIVEN** 模板清单的 27 个 `iconKey`
- **WHEN** 查询 `AccountIconCatalog.drawableFor()`
- **THEN** 全部返回非 null 的 `R.drawable.ic_acct_*`，共 26 个 PNG 文件
- **AND** `alipay` 与 `huabei` 指向同一份支付宝母品牌图形
- **AND** 打开资产页 / 模板 sheet / 记一笔三页均显示图形、零网络请求

#### Scenario: 换成 IconFont 必须逐项改写登记

- **GIVEN** 使用者从 IconFont 导出了某家银行图标
- **WHEN** 核验该图标详情页授权并覆盖同名 PNG
- **THEN** NOTICE [B] 表对应行 SHALL 改写为新来源与授权类型
- **AND** MUST NOT 保留「MIT / CC0」旧值、MUST NOT 只写「来自 IconFont」

---

### Requirement: 银行图标使用纯图形徽记且不带中文行名

**第 65 批**：随包分发的 21 家中国银行图标 SHALL 使用
`icongo/bank-logos` 的 `logos/<slug>-rect.svg` **方形纯徽记版**，
MUST NOT 使用横版 `logos/<slug>.svg`（其内容为「徽记 + 中文/英文行名」，
在账户图标位缩成细条导致文字不可辨）。
转换时 SHALL 删除 `-rect.svg` 自带的 1024 圆角纯白底 path，
使 `getbbox()` 只框住彩色徽记并放大撑满圆，
MUST NOT 保留白底（否则徽记无法放大）。
`cebbank-rect.svg` SHALL 剔除紫色 `#6B1785` 的 `ank` 字样 path，
只保留黄色徽记。
`pingan-rect.svg` 为橙色方块内「中国平安」文字标、京东白条无纯图形
官方徽记，两者 SHALL 用**本作品原创纯几何符**占位（零文字、
MUST NOT 临摹商标），并在 `assets/NOTICE` 登记为原创、非官方 logo。
4 个 Simple Icons 图形（wechat / alipay / meituan / douyin）SHALL 保持不变：
它们是满幅方块标而非横版行名细条；`alipay`「支」与 `meituan`「美团」
为官方方块文字标，本批范围按使用者原话限定为「银行 logo」，
MUST 在 NOTICE 如实说明该保留，MUST NOT 悄悄扩张范围。
`AccountIconCatalog.kt` 的 `when` 分支与 `R.drawable` 引用 SHALL 零改动，
27 个键位 / 26 个 PNG 数量 SHALL 不变，`huabei` SHALL 继续复用 `alipay`。
授权与商标口径 SHALL 与第 64 批 [C] 完全一致，
MUST NOT 因换文件而写成「已解决授权问题」。

#### Scenario: 21 家银行图标无行名文字

- **GIVEN** 资产页 / 模板 sheet / 记一笔显示 21 家银行账户图标
- **WHEN** 在 28/34dp 下查看
- **THEN** 每家只显示纯图形徽记，MUST NOT 出现「中国工商银行」等行名细条
- **AND** 徽记放大撑满白圆底，肉眼可辨

#### Scenario: 替换资源不动 Kotlin

- **GIVEN** 第 65 批只替换 `drawable-nodpi/ic_acct_*.png` 内容
- **WHEN** 编译并运行
- **THEN** `AccountIconCatalog.kt` 与三页 UI 代码零改动
- **AND** 27 个键位仍全部返回非 null，PNG 仍为 26 个

