"""Генерирует текстуры эффектов навыков в ресурсы мода.

    python generate.py

Текстуры белые с прозрачностью: цвет даёт вершина, поэтому одна картинка
служит всем классам. Любую можно заменить нарисованной руками, положив PNG с
тем же именем в src/main/resources/assets/rpgcore/textures/fx/.

Без Pillow, как и генератор значков: PNG пишет стандартная библиотека.
"""

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'icons'))
from pixel import write_png  # noqa: E402

OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'rpgcore',
                   'textures', 'fx')


def clamp(v):
    return max(0.0, min(1.0, v))


def image(w, h, alpha_at):
    """Белая картинка, прозрачность — функция от (u, v) в [0, 1]."""
    rows = []
    for y in range(h):
        row = []
        for x in range(w):
            a = clamp(alpha_at((x + 0.5) / w, (y + 0.5) / h))
            row.append((255, 255, 255, int(round(a * 255))))
        rows.append(row)
    return rows


def glow(u, v):
    # Мягкое пятно с ярким центром: ядро снаряда, вспышка.
    d = math.hypot(u - 0.5, v - 0.5) * 2
    if d >= 1:
        return 0
    return 0.85 * (1 - d) ** 2.4 + 0.6 * math.exp(-(d / 0.14) ** 2)


def ring(u, v):
    # Поперёк полосы границы: тонкая яркая черта посередине — это и есть радиус,
    # — и мягкий ореол по бокам.
    t = v - 0.5
    return math.exp(-(t / 0.05) ** 2) + 0.45 * math.exp(-(t / 0.22) ** 2)


def beam(u, v):
    # Лента следа: мягче границы, без резкой черты.
    t = v - 0.5
    return math.exp(-(t / 0.13) ** 2) + 0.35 * math.exp(-(t / 0.3) ** 2)


def spark(u, v):
    # Четырёхлучевая звёздочка с ядром.
    x, y = (u - 0.5) * 2, (v - 0.5) * 2
    d = math.hypot(x, y)
    rays = (math.exp(-(x / 0.07) ** 2) * math.exp(-(y / 0.75) ** 2)
            + math.exp(-(y / 0.07) ** 2) * math.exp(-(x / 0.75) ** 2))
    diag = 0.35 * (math.exp(-((x - y) / 0.09) ** 2) + math.exp(-((x + y) / 0.09) ** 2)) \
        * math.exp(-(d / 0.45) ** 2)
    core = math.exp(-(d / 0.18) ** 2)
    return rays + diag + core


def fill(u, v):
    # Заливка круга: почти прозрачная середина, светлее к краю, мягкий обрез.
    d = math.hypot(u - 0.5, v - 0.5) * 2
    if d >= 1:
        return 0
    edge = clamp((1 - d) / 0.04)
    return (0.25 + 0.75 * d ** 3) * edge


# ------------------------------------------------------------------ руны

def seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy or 1))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def arc(px, py, cx, cy, r, a0, a1):
    a = math.atan2(py - cy, px - cx)
    if a0 <= a <= a1:
        return abs(math.hypot(px - cx, py - cy) - r)
    e0 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
    e1 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
    return min(math.hypot(px - e0[0], py - e0[1]), math.hypot(px - e1[0], py - e1[1]))


def dot(px, py, cx, cy, r):
    return max(0.0, math.hypot(px - cx, py - cy) - r)


P = math.pi
# Восемь знаков в клетке 0..1: отрезки, дуги и точки. Не алфавит, а узор,
# который читается как письмо.
GLYPHS = [
    [('s', .5, .2, .5, .8), ('s', .3, .35, .7, .35), ('d', .5, .88, .04)],
    [('a', .5, .5, .28, -P, P * .5), ('s', .5, .2, .5, .5)],
    [('s', .3, .2, .7, .8), ('s', .7, .2, .3, .8), ('a', .5, .5, .12, -P, P)],
    [('s', .35, .2, .35, .8), ('a', .35, .5, .3, -P * .5, P * .5), ('d', .7, .2, .04)],
    [('s', .5, .15, .5, .85), ('a', .5, .35, .2, 0, P), ('a', .5, .65, .2, -P, 0)],
    [('s', .25, .8, .5, .2), ('s', .5, .2, .75, .8), ('d', .5, .62, .05)],
    [('a', .5, .5, .3, -P * .9, P * .1), ('s', .5, .5, .76, .5), ('d', .5, .5, .05)],
    [('s', .3, .25, .7, .25), ('s', .5, .25, .5, .8), ('a', .5, .8, .16, -P, 0)],
]


def rune_alpha(u, v):
    cell = min(int(u * len(GLYPHS)), len(GLYPHS) - 1)
    gx = u * len(GLYPHS) - cell
    gy = v
    # Поля по краям клетки: знаки не слипаются в сплошную полосу.
    gx = (gx - 0.5) * 1.25 + 0.5
    d = 9.0
    for part in GLYPHS[cell]:
        if part[0] == 's':
            d = min(d, seg(gx, gy, *part[1:]))
        elif part[0] == 'a':
            d = min(d, arc(gx, gy, *part[1:]))
        else:
            d = min(d, dot(gx, gy, *part[1:]))
    stroke = clamp((0.045 - d) / 0.02 + 0.5)
    halo = 0.35 * math.exp(-(d / 0.09) ** 2)
    # Тонкие черты сверху и снизу держат пояс как кольцо.
    rails = 0.5 * (math.exp(-((v - 0.06) / 0.018) ** 2) + math.exp(-((v - 0.94) / 0.018) ** 2))
    return stroke + halo + rails


def main():
    os.makedirs(OUT, exist_ok=True)
    textures = {
        'glow': (64, 64, glow),
        'ring': (8, 64, ring),
        'beam': (8, 64, beam),
        'spark': (64, 64, spark),
        'fill': (64, 64, fill),
        'runes': (512, 64, rune_alpha),
    }
    for name, (w, h, fn) in textures.items():
        write_png(os.path.join(OUT, name + '.png'), image(w, h, fn))
        print('fx/' + name + '.png', w, 'x', h)


if __name__ == '__main__':
    main()
