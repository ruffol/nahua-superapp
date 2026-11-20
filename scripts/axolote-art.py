# -*- coding: utf-8 -*-
"""Genera la ilustracion del axolote (PNG) a partir de la geometria SVG del bundle."""
import math
from PIL import Image, ImageDraw

F = 10                      # factor de escala sobre el viewBox 220x180
W, H = 220 * F, 180 * F
OUT = r".tmp\axolote-report\axolote.png"

TAIL = "FF9EB8"
BODY = "FFB3C7"
BELLY = "FFD3DF"
GILL = "FF8FAE"
HEAD = "FFC2D2"
EYE = "3A2430"
MOUTH = "A8435F"


def col(h, a=255):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def rot_ellipse(img, cx, cy, rx, ry, fill, svg_angle=0.0):
    """Elipse rotada: se dibuja en una capa aparte y se rota alrededor de su centro."""
    w, h = int(2 * rx * F) + 6, int(2 * ry * F) + 6
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([3, 3, w - 3, h - 3], fill=fill)
    if svg_angle:
        # PIL gira en sentido antihorario; SVG con y hacia abajo gira en sentido horario.
        layer = layer.rotate(-svg_angle, resample=Image.BICUBIC, expand=False)
    img.alpha_composite(layer, (int(cx * F - w / 2), int(cy * F - h / 2)))


def cubic(p0, p1, p2, p3, steps=48):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def quad(p0, p1, p2, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**2 * p0[0] + 2 * u * t * p1[0] + t**2 * p2[0]
        y = u**2 * p0[1] + 2 * u * t * p1[1] + t**2 * p2[1]
        pts.append((x, y))
    return pts


def poly(pts):
    return [(x * F, y * F) for x, y in pts]


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 1. sombra
rot_ellipse(img, 110, 150, 52, 9, col("10161C", 22))

# 2. cola (dos curvas cubicas cerradas)
tail = cubic((158, 112), (186, 104), (196, 86), (186, 70))
tail += cubic((186, 70), (178, 88), (168, 94), (152, 98))
d.polygon(poly(tail), fill=col(TAIL))

# 3. cuerpo y barriga
d.ellipse([(108 - 62) * F, (110 - 40) * F, (108 + 62) * F, (110 + 40) * F], fill=col(BODY))
d.ellipse([(108 - 40) * F, (122 - 22) * F, (108 + 40) * F, (122 + 22) * F], fill=col(BELLY))

# 4. branquias (tres por lado, rotadas)
for cx, cy, rx, ry, ang in [
    (52, 82, 9, 18, -28), (46, 100, 9, 17, -12), (52, 118, 8, 15, 8),
    (168, 82, 9, 18, 28), (174, 100, 9, 17, 12), (168, 118, 8, 15, -8),
]:
    rot_ellipse(img, cx, cy, rx, ry, col(GILL), ang)

# 5. cabeza
d.ellipse([(108 - 44) * F, (72 - 34) * F, (108 + 44) * F, (72 + 34) * F], fill=col(HEAD))

# 6. ojos
for cx in (92, 124):
    d.ellipse([(cx - 5) * F, (68 - 5) * F, (cx + 5) * F, (68 + 5) * F], fill=col(EYE))

# 7. mejillas
for cx in (85, 131):
    rot_ellipse(img, cx, 80, 7, 7, col(GILL, 115))

# 8. boca (cuadratica)
mouth = quad((100, 84), (108, 92), (116, 84))
d.line(poly(mouth), fill=col(MOUTH), width=int(3 * F), joint="curve")
for px, py in (poly(mouth)[0], poly(mouth)[-1]):
    r = 1.5 * F
    d.ellipse([px - r, py - r, px + r, py + r], fill=col(MOUTH))

img = img.resize((760, int(760 * H / W)), Image.LANCZOS)
img.save(OUT)
print("ok", OUT, img.size)
