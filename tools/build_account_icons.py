#!/usr/bin/env python3
"""第 64 批：账户品牌图标构建期转换脚本（App 运行时不执行）。

依赖：cairosvg、Pillow、WenQuanYi Micro Hei（画原创占位字）。
用途：从两个带明确开源许可的源重新生成
      app/src/main/res/drawable-nodpi/ic_acct_*.png。
授权与逐项登记见 app/src/main/assets/NOTICE 第 64 批段落。

源 1：icongo/bank-logos（MIT）—— https://github.com/icongo/bank-logos
源 2：Simple Icons（CC0-1.0）—— https://cdn.simpleicons.org/<slug>
源 3：京东白条无 CC0 / 明确授权图形，本作品原创红色「白」字占位。

注意：MIT / CC0 只覆盖图标 SVG 代码；各品牌商标权不在此范围，
      MUST NOT 写成「已获逐家书面授权」。
"""
import io
import pathlib
import urllib.request

import cairosvg
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable-nodpi"
RAW_GH = "https://raw.githubusercontent.com/icongo/bank-logos/main/logos/"
SI_CDN = "https://cdn.simpleicons.org/"

# iconKey -> 来源 slug
BANKS = {
    "icbc": "icbc", "ccb": "ccb", "abc": "abchina", "boc": "boc",
    "bocom": "bankcomm", "psbc": "psbc", "cmb": "cmbchina", "spdb": "spdb",
    "citic": "citicbank", "ceb": "cebbank", "hxb": "hxb", "cmbc": "cmbc",
    "cgb": "cgbchina", "cib": "cib", "pab": "pingan", "czb": "czbank",
    "njcb": "njcb", "bob": "bankofbeijing", "bosc": "bosc",
    "jsb": "jsbchina", "nbcb": "nbcb",
}
SIMPLE = {
    "wechat": "wechat", "alipay": "alipay",
    "meituan": "meituan", "douyin": "tiktok",
}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "offline-ledger-build"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


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


def build_original_jdbaitiao() -> None:
    """原创占位：不临摹任何商标，红色圆角块 + 白色「白」字。"""
    canvas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse([3, 3, 125, 125], fill=(255, 255, 255, 255),
                 outline=(226, 229, 235, 255), width=3)
    draw.rounded_rectangle([22, 22, 106, 106], radius=18, fill=(225, 37, 27, 255))
    try:
        font = ImageFont.truetype(
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", 52)
    except OSError:
        font = ImageFont.load_default()
    draw.text((64, 64), "白", fill="white", font=font, anchor="mm")
    canvas.save(OUT / "ic_acct_jdbaitiao.png")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for key, slug in BANKS.items():
        to_round_icon(fetch(RAW_GH + slug + ".svg"), key)
        print("bank", key, "<-", slug)
    for key, slug in SIMPLE.items():
        to_round_icon(fetch(SI_CDN + slug), key)
        print("simple", key, "<-", slug)
    # 花呗在 AccountIconCatalog 的 when 里复用 ic_acct_alipay，
    # MUST NOT 生成独立 PNG（否则 26 个文件变成 27 个，与 NOTICE 不符）。
    build_original_jdbaitiao()
    print("done", len(list(OUT.glob("ic_acct_*.png"))), "files")


if __name__ == "__main__":
    main()
