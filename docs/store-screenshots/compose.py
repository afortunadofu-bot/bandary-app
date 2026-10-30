"""把實機截圖合成 Google Play 商店截圖（1080×1920）。

用法：把截圖放進 raw/（檔名見 README.md），執行
    python docs/store-screenshots/compose.py
輸出到 out/。

版面：上方鼠尾草綠漸層帶放一句說明文字，下方放真實截圖（裁掉狀態列、圓角、底部出血）。
只做排版，不修改截圖內容——商店截圖必須是 App 的真實畫面。
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
OUT = os.path.join(HERE, "out")

# 說明文字：鍵是檔名開頭的編號。文字必須跟該張截圖實際呈現的功能一致。
CAPTIONS = {
    "01": ("每一場演出，", "都用海報記下來"),
    # 詳情頁若拍到的是卡司＋照片＋節目表就用這句；若改拍歌單，換成（"歌單、照片、心得，", "一場一頁完整收好"）。
    "02": ("卡司、照片、節目表，", "一場一頁完整收好"),
    # 截圖不一定是未來月份（例如拍的是 4 月），說明文字不要寫「接下來」，改成不綁時間的描述。
    # 第一行最多約 9 個全形字，再長會撞到右邊界（「演出、開賣、有興趣的活動，」就超出了）。
    "03": ("演出和開賣日，", "都標在同一本行事曆"),
    "04": ("看見自己這幾年的", "聽團足跡"),
    "05": ("免費與售票活動，", "一次掌握"),
    "06": ("和好友互看", "接下來要去哪幾場"),
}

W, H = 1080, 1920
BAND_H = 400                 # 上方文字帶高度
SIDE = 84                    # 截圖左右留白
RADIUS = 44                  # 截圖圓角
STATUS_BAR_RATIO = 0.038     # 裁掉截圖頂端狀態列的比例（1080×2340 約 89px）
TOP_COLOR = (116, 156, 141)  # 與主打圖、開發人員頁首同一組鼠尾草綠
BOTTOM_COLOR = (98, 136, 121)

FONT_VF = r"C:\Windows\Fonts\NotoSansTC-VF.ttf"
FONT_FALLBACK = r"C:\Windows\Fonts\msjhbd.ttc"


def font(size, weight):
    try:
        f = ImageFont.truetype(FONT_VF, size)
        vals = []
        for ax in f.get_variation_axes():
            name = ax.get("name", b"")
            name = name.decode() if isinstance(name, bytes) else str(name)
            vals.append(weight if "eight" in name or name.lower().startswith("w") else ax.get("default", 0))
        f.set_variation_by_axes(vals)
        return f
    except Exception:
        return ImageFont.truetype(FONT_FALLBACK, size)


def vertical_gradient(w, h, top, bottom):
    mask = Image.linear_gradient("L").resize((w, h))
    return Image.composite(Image.new("RGB", (w, h), bottom), Image.new("RGB", (w, h), top), mask)


def rounded_top_mask(w, h, r):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w, h + r], radius=r, fill=255)
    return m


def compose(path, caption):
    shot = Image.open(path).convert("RGB")
    # 裁掉狀態列（時間、電量、通知圖示），畫面內容不動。
    shot = shot.crop((0, round(shot.height * STATUS_BAR_RATIO), shot.width, shot.height))
    target_w = W - SIDE * 2
    scale = target_w / shot.width
    shot = shot.resize((target_w, round(shot.height * scale)), Image.LANCZOS)
    visible_h = H - BAND_H
    shot = shot.crop((0, 0, target_w, min(shot.height, visible_h)))

    canvas = vertical_gradient(W, H, TOP_COLOR, BOTTOM_COLOR).convert("RGBA")

    # 截圖後面一層柔和陰影，讓白底畫面從綠底浮出來。
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [SIDE, BAND_H + 10, SIDE + target_w, H + RADIUS], radius=RADIUS, fill=(0, 0, 0, 70)
    )
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(28)))

    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    layer.paste(shot, (SIDE, BAND_H))
    mask = Image.new("L", (W, H), 0)
    mask.paste(rounded_top_mask(target_w, shot.height, RADIUS), (SIDE, BAND_H))
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
    canvas.alpha_composite(layer)

    d = ImageDraw.Draw(canvas)
    f_eyebrow = font(34, 700)
    f_caption = font(76, 900)
    x, y = SIDE, 92
    for ch in "BANDARY 聽團曆":
        d.text((x, y), ch, font=f_eyebrow, fill=(255, 255, 255, 215))
        x += d.textlength(ch, font=f_eyebrow) + (10 if ch.isascii() else 4)
    y += 70
    for line in caption:
        d.text((SIDE, y), line, font=f_caption, fill=(255, 255, 255, 255))
        asc, desc = f_caption.getmetrics()
        y += asc + desc + 6

    return canvas.convert("RGB")


def main():
    if not os.path.isdir(RAW):
        sys.exit(f"找不到 {RAW}")
    os.makedirs(OUT, exist_ok=True)
    files = sorted(f for f in os.listdir(RAW) if f.lower().endswith((".png", ".jpg", ".jpeg")))
    if not files:
        sys.exit("raw/ 裡還沒有截圖，檔名規則見 README.md")
    for name in files:
        key = name[:2]
        if key not in CAPTIONS:
            print(f"略過 {name}：檔名開頭 {key} 沒有對應的說明文字（見 CAPTIONS）")
            continue
        out_path = os.path.join(OUT, os.path.splitext(name)[0] + ".png")
        compose(os.path.join(RAW, name), CAPTIONS[key]).save(out_path, "PNG", optimize=True)
        print(f"{name} → out/{os.path.basename(out_path)}")


if __name__ == "__main__":
    main()
