# 账户图标资源来源（第 64 批）

> 逐项授权登记与全文见 `app/src/main/assets/NOTICE`；转换脚本见
> `tools/build_account_icons.py`（构建期工具，App 不执行、无网络权限）。

## 第 65 批修订（当前口径）：银行改用纯徽记

使用者反馈「带文字的反而看不清楚」，因此 21 家银行**全部**从横版
`logos/<slug>.svg` 换成方形纯徽记 `logos/<slug>-rect.svg`（不带中文行名）。

关键三步（已固化进 `tools/build_account_icons.py`）：
1. 取 `-rect.svg`；
2. **删掉自带的 1024 圆角纯白底 path**（`d="M0 0m224 0l576..."` +
   `fill="#FFFFFF"`），否则 `getbbox()` 框住整张画布、徽记放大不了；
3. `cebbank-rect.svg` 剔除紫色 `#6B1785` 的 `ank` 字样，只留黄色 E 徽记。

`pingan-rect.svg`（橙色方块内「中国平安」）与京东白条无纯图形官方徽记，
改用本作品原创纯几何符（零文字、不临摹商标）：
`pab` 橙色圆角块 + 白色双弧/竖线，`jdbaitiao` 红色圆角块 + 三条白横杠。
拿到官方纯图形授权后直接覆盖同名 PNG 即可。

4 个 Simple Icons（wechat / alipay / meituan / douyin）不变：它们是满幅
方块标而非横版行名细条，34dp 下清晰。`alipay`「支」与 `meituan`「美团」
是官方方块文字标，本次范围按使用者原话限定为「银行 logo」，故保留。

**第 64 批以下表格记录的是历史口径（横版），仅作来源与许可说明保留。**

---

## 本批实际采用（第 64 批历史）

| 源 | 覆盖 | 许可 | 地址 |
|---|---|---|---|
| icongo/bank-logos | 21 家中国银行 | MIT License | https://github.com/icongo/bank-logos/tree/main/logos/ |
| Simple Icons | 微信 / 支付宝 / 美团 / 抖音月付 | CC0-1.0 | https://simpleicons.org/ , https://cdn.simpleicons.org/&lt;slug&gt; |
| 本作品原创 | 京东白条占位 | 本项目 MIT | `tools/build_account_icons.py` |

### 键位 → slug 映射

`icbc→icbc-rect, ccb→ccb-rect, abc→abchina, boc→boc, bocom→bankcomm,
psbc→psbc, cmb→cmbchina, spdb→spdb, citic→citicbank, ceb→cebbank,
hxb→hxb, cmbc→cmbc, cgb→cgbchina, cib→cib, pab→pingan, czb→czbank,
njcb→njcb, bob→bankofbeijing, bosc→bosc, jsb→jsbchina, nbcb→nbcb`

`wechat→wechat, alipay→alipay, huabei→alipay(母品牌), meituan→meituan,
douyin→tiktok, jdbaitiao→原创`

## 为什么没用 IconFont（使用者最初指定）

IconFont 是 UGC 聚合平台：图标由不同作者上传，授权逐个不同；部分图库标注
「非商业性免费，商用需按图标打赏（最低 3 元）」，官方库与 UGC 混排；下载需
登录态导出。平台聚合页不等于「可随包分发」，也无法逐项核验单图标授权。

若仍要用 IconFont，操作路径：

1. 登录 iconfont.cn，按银行名搜索，逐个打开**详情页**核验授权标签；
2. 明确标注「可商用 / CC0 / MIT」的才加入项目；需打赏的留存凭证与截图；
3. 下载 SVG（或 PNG），按 `tools/build_account_icons.py` 的 `to_round_icon()`
   统一转成 128×128 白圆底 PNG；
4. 覆盖 `app/src/main/res/drawable-nodpi/ic_acct_<key>.png`；
5. **逐行改写** `assets/NOTICE` 第 64 批 [B] 表的「来源 / 授权」两列，
   MUST NOT 用「来自 IconFont」代替逐项登记。

## 其他可选源（本批未采用）

- **Wikimedia Commons**：部分银行 logo 标 `PD-textlogo`（仅简单几何/文字，
  未达著作权独创性门槛，进入公有领域），但描述里仍带 `Trademarked` 提示，
  商标权另算。适合纯文字/简单几何徽记。
- **各银行官网品牌资源页 / 官方 App 素材**：需逐家取得书面许可，
  本批未取得，不随包分发。
- **Bootstrap Icons / Tabler / Lucide**（MIT / CC0）：只有微信、支付宝等通用
  品牌，没有中国商业银行徽记，不能替代。

## 授权与商标口径

MIT / CC0 只覆盖图标 SVG 代码与本项目原创绘制部分，**不覆盖各品牌商标权**。
使用者判断（2026-11-24）是「用 Logo 仅指示账户归属、记账类场景」；
2026 年修订《商标法》第 73 条第 3 款规定仅为指示用途、适用对象、应用场景
或表明真实来源的正当使用，注册商标专用权人无权禁止，但容易导致混淆的除外。
Simple Icons 官方免责同时写明 CC0 不意味着项目内所有 logo 也是 CC0、每个
logo 都是所属公司的商标。上述差异在 NOTICE [C] 如实并列，**不写成「无风险」**。
