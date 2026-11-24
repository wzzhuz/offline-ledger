#!/usr/bin/env python3
"""第 64 批建立、第 65 批修订：账户品牌图标构建期转换脚本。

依赖：cairosvg、Pillow（App 运行时不执行、不联网）。
用途：从两个带明确开源许可的源重新生成
      app/src/main/res/drawable-nodpi/ic_acct_*.png。
授权与逐项登记见 app/src/main/assets/NOTICE 第 64/65 批段落。

源 1：icongo/bank-logos（MIT）—— https://github.com/icongo/bank-logos
源 2：Simple Icons（CC0-1.0）—— https://cdn.simpleicons.org/<slug>
源 3：pab / jdbaitiao 无纯图形官方徽记，用本作品原创几何符占位。

**第 65 批修订**：银行改用 `-rect.svg` **纯徽记版**（不带中文行名），
不再用横版 `xxx.svg`。理由：横版是「徽记 + 中文/英文行名」，
在 34dp 账户图标位缩成细条看不清；-rect 是方形纯徽记，放大撑满圆后清晰。
另：`-rect.svg` 自带一个 1024 圆角纯白底 path，会让 getbbox() 框住整张
画布、徽记放大不了 —— MUST 先删掉该 path 再裁切。

注意：MIT / CC0 只覆盖图标 SVG 代码；各品牌商标权不在此范围，
      MUST NOT 写成「已获逐家书面授权」。
"""
import io
import pathlib
import re
import urllib.request

import cairosvg
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable-nodpi"
RAW_GH = "https://raw.githubusercontent.com/icongo/bank-logos/main/logos/"
SI_CDN = "https://cdn.simpleicons.org/"

# iconKey -> 来源 slug（第 65 批：一律取 <slug>-rect.svg 纯徽记版）
BANKS = {
    "icbc": "icbc", "ccb": "ccb", "abc": "abchina", "boc": "boc",
    "bocom": "bankcomm", "psbc": "psbc", "cmb": "cmbchina", "spdb": "spdb",
    "citic": "citicbank", "ceb": "cebbank", "hxb": "hxb", "cmbc": "cmbc",
    "cgb": "cgbchina", "cib": "cib", "czb": "czbank",
    "njcb": "njcb", "bob": "bankofbeijing", "bosc": "bosc",
    "jsb": "jsbchina", "nbcb": "nbcb",
    # pab 无纯图形徽记，见 build_originals()
}
SIMPLE = {
    "wechat": "wechat", "alipay": "alipay",
    "meituan": "meituan", "douyin": "tiktok",
}

# icongo/bank-logos 的 -rect 版第一个 path 是 1024 圆角纯白底。
WHITE_BG = re.compile(
    r'<path[^>]*?\bd="M0 0m224 0l576[^>]*?fill="#FFFFFF"[^>]*?>\s*</path>|'
    r'<path[^>]*?\bd="M0 0m224 0l576[^>]*?fill="#FFFFFF"[^>]*?/>'
)


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "offline-ledger-build"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def strip_white_bg(svg_text: str) -> str:
    """删掉自带的圆角纯白底，让 getbbox() 只框住彩色徽记。"""
    return WHITE_BG.sub("", svg_text)


def to_round_icon(svg_bytes: bytes, key: str) -> None:
    """SVG → 512px → 裁透明边距 → 等比 104px → 128px 白色圆底 PNG。"""
    raw = cairosvg.svg2png(bytestring=svg_bytes, output_width=512, output_height=512)
    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    im.thumbnail((104, 104), Image.LANCZOS)
    canvas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse([3, 3, 125, 125], fill=(255, 255, 255, 255),
                 outline=(226, 229, 235, 255), width=3)
    canvas.alpha_composite(im, ((128 - im.width) // 2, (128 - im.height) // 2))
    canvas.save(OUT / f"ic_acct_{key}.png")


def build_ceb_and_originals() -> None:
    """ceb 去掉紫色 'ank' 文字 path；pab / jdbaitiao 用本作品原创几何符。

    两家都没有「不带文字的纯图形官方徽记」：
      - cebbank-rect.svg 是 黄色 E 徽记 + 紫色 "ank" 字样 → 只留黄色 path
      - pingan-rect.svg 是橙色方块内「中国平安」文字标 → 无法裁出纯图形
    原创符均为纯几何、零文字、不临摹任何商标，拿到授权可直接替换同名 PNG。
    """
    # --- ceb：从 -rect SVG 里剔除紫色 #6B1785 的 "ank" path ---
    ceb = strip_white_bg(fetch(RAW_GH + "cebbank-rect.svg").decode("utf-8"))
    kept = [p for p in re.findall(r"<path[^>]*?>", ceb) if "#6B1785" not in p]
    head = ceb[: ceb.index("<path")]
    to_round_icon((head + "".join(kept) + "</svg>").encode("utf-8"), "ceb")

    pab = """<svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
<rect x="96" y="96" width="832" height="832" rx="180" fill="#EC681B"/>
<path d="M300 600 C360 470 430 410 512 410 C594 410 664 470 724 600"
 fill="none" stroke="#FFFFFF" stroke-width="76" stroke-linecap="round"/>
<path d="M512 410 L512 660" fill="none" stroke="#FFFFFF"
 stroke-width="76" stroke-linecap="round"/>
</svg>"""
    jdb = """<svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
<rect x="96" y="96" width="832" height="832" rx="180" fill="#E11B1B"/>
<rect x="290" y="360" width="444" height="76" rx="38" fill="#FFFFFF"/>
<rect x="290" y="474" width="444" height="76" rx="38" fill="#FFFFFF"/>
<rect x="290" y="588" width="300" height="76" rx="38" fill="#FFFFFF"/>
</svg>"""
    to_round_icon(pab.encode("utf-8"), "pab")
    to_round_icon(jdb.encode("utf-8"), "jdbaitiao")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for key, slug in BANKS.items():
        svg = fetch(RAW_GH + slug + "-rect.svg").decode("utf-8")
        to_round_icon(strip_white_bg(svg).encode("utf-8"), key)
        print("bank-rect", key, "<-", slug)
    for key, slug in SIMPLE.items():
        to_round_icon(fetch(SI_CDN + slug), key)
        print("simple", key, "<-", slug)
    build_ceb_and_originals()
    # 花呗在 AccountIconCatalog 的 when 里复用 ic_acct_alipay，
    # MUST NOT 生成独立 PNG（27 个键位 / 26 个文件）。
    print("done", len(list(OUT.glob("ic_acct_*.png"))), "files")


if __name__ == "__main__":
    main()
