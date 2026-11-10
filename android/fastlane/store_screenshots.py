"""Картки для Google Play: градієнт «Дванадцятки», підпис і екран у рамці телефону (1080×1920)."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS = Path(sys.argv[1])
OUT = Path(sys.argv[2])
FONTS = Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)

W, H = 1080, 1920
TITLE = ImageFont.truetype(str(FONTS / "russo_one.ttf"), 84)
SUB = ImageFont.truetype(str(FONTS / "nunito_800.ttf"), 44)
INK = (26, 27, 54)

SLIDES = [
    ("1_today.png", "Скільки до дзвінка?", "Таймер уроку, перерви\nй завдання дня"),
    ("3_wallet.png", "Оцінки — це гроші", "Батьки підтверджують,\nа гаманець росте до мрії"),
    ("2_week.png", "На перерві не нудно", "Вікторина, цікаві факти\nй рюкзак на сьогодні"),
    ("4_homework.png", "Домашка не загубиться", "Прострочене зверху,\nнагадування ввечері"),
    ("5_parent.png", "Батькам — один дотик", "Підтвердити все,\nбонуси й челенджі"),
    ("6_widget_raw.png", "Розклад на екрані", "Віджет: що зараз\nі що далі — без відкриття"),
]


def gradient() -> Image.Image:
    # Діагональ фіолетовий → бузковий → рожевий, як шапки в застосунку.
    stops = [(0.0, (108, 92, 231)), (0.55, (165, 94, 234)), (1.0, (253, 121, 168))]
    g = Image.new("RGB", (W, H))
    px = g.load()
    for y in range(H):
        for x in range(0, W, 2):
            t = (x / W * 0.35 + y / H * 0.65)
            for (a, ca), (b, cb) in zip(stops, stops[1:]):
                if a <= t <= b:
                    k = (t - a) / (b - a)
                    c = tuple(int(ca[i] + (cb[i] - ca[i]) * k) for i in range(3))
                    break
            px[x, y] = c
            if x + 1 < W:
                px[x + 1, y] = c
    return g


def sparkles(img: Image.Image) -> None:
    d = ImageDraw.Draw(img, "RGBA")
    for x, y, r in [(90, 120, 10), (980, 210, 14), (150, 560, 7), (960, 640, 9), (60, 1500, 12), (1010, 1300, 8), (520, 60, 8)]:
        d.polygon([(x, y - r * 2), (x + r * 0.5, y - r * 0.5), (x + r * 2, y), (x + r * 0.5, y + r * 0.5),
                   (x, y + r * 2), (x - r * 0.5, y + r * 0.5), (x - r * 2, y), (x - r * 0.5, y - r * 0.5)], fill=(255, 255, 255, 150))


def stroke_text(d: ImageDraw.ImageDraw, xy, text, font, fill, stroke, width, anchor="ma") -> None:
    d.text(xy, text, font=font, fill=fill, stroke_width=width, stroke_fill=stroke, anchor=anchor, align="center")


def rounded(img: Image.Image, r: int) -> Image.Image:
    m = Image.new("L", img.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, *img.size), r, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), m)
    return out


def phone(shot: Image.Image, height: int) -> Image.Image:
    """Екран у «корпусі»: темна рамка, заокруглення, тінь як у ігрових карток (зсув донизу)."""
    s = shot.convert("RGB")
    scale = height / s.height
    s = s.resize((int(s.width * scale), height), Image.LANCZOS)
    bezel = 16
    body = Image.new("RGBA", (s.width + bezel * 2, s.height + bezel * 2), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, body.width - 1, body.height - 1), 64, fill=INK)
    body.alpha_composite(rounded(s, 50), (bezel, bezel))
    return body


def card(shadow_of: Image.Image, depth: int = 14) -> Image.Image:
    """Ігрова тінь: та сама фігура темним, зсунута вниз."""
    a = shadow_of.split()[-1]
    sh = Image.new("RGBA", shadow_of.size, INK + (255,))
    sh.putalpha(a)
    out = Image.new("RGBA", (shadow_of.width, shadow_of.height + depth))
    out.alpha_composite(sh, (0, depth))
    out.alpha_composite(shadow_of, (0, 0))
    return out


BG = gradient()

for i, (name, title, sub) in enumerate(SLIDES, 1):
    img = BG.copy().convert("RGBA")
    sparkles(img)
    d = ImageDraw.Draw(img)
    # Заголовок підганяємо під ширину (до 940 px), щоб не тиснув у краї.
    size = 84
    font = TITLE
    while d.textlength(title, font=font) > 940 and size > 56:
        size -= 2
        font = ImageFont.truetype(str(FONTS / "russo_one.ttf"), size)
    stroke_text(d, (W // 2, 70 + (84 - size) // 2), title, font, (255, 255, 255), INK, 7)
    d.multiline_text((W // 2, 190), sub, font=SUB, fill=(255, 255, 255), anchor="ma", align="center", spacing=6)
    shot = Image.open(SHOTS / name)
    if name.startswith("6_"):
        # Віджет крупно, без чужих іконок і англійського лаунчера.
        w = shot.crop((54, 456, 773, 985))
        w = w.resize((int(w.width * 1.3), int(w.height * 1.3)), Image.LANCZOS)
        w = card(rounded(w, 64), depth=16)
        glow = Image.new("RGBA", img.size)
        ImageDraw.Draw(glow).ellipse((90, 420, 990, 1400), fill=(255, 255, 255, 60))
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(80)))
        img.alpha_composite(w, ((W - w.width) // 2, 470))
        # Маленька підказка під віджетом.
        d = ImageDraw.Draw(img)
        d.text((W // 2, 470 + w.height + 80), "Оновлюється на кожному дзвінку", font=SUB, fill=(255, 255, 255), anchor="ma")
    else:
        p = card(phone(shot, 1500), depth=18)
        img.alpha_composite(p, ((W - p.width) // 2, 345))
    img.convert("RGB").save(OUT / f"{i}.png", optimize=True)
    print(OUT / f"{i}.png")
