"""Генерирует текстуры корней и лоз друида в атлас блоков мода.

    python generate.py

Картинки 16×16, пиксель в пиксель, как ванильные блоки: лежат в
textures/block/, и игра сама сшивает их в атлас блоков как
rpgcore:block/<имя>. Трубка (FxSolids.Tube) оборачивает ширину картинки на
6 граней, а высоту рисует туда-обратно по сегментам — поэтому волокна
вертикальные и по ширине идут с шагом 4: швов нет ни вокруг, ни вдоль.

Любую можно заменить нарисованной руками, положив PNG с тем же именем.
"""

import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'icons'))
from pixel import write_png  # noqa: E402

OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'rpgcore',
                   'textures', 'block')

PAL = {
    'D': (30, 19, 12), 'd': (52, 35, 21), 'b': (78, 53, 32), 'l': (104, 72, 43),
    'h': (138, 100, 62),
    'g': (88, 178, 70), 'G': (170, 240, 120),
    'n': (38, 70, 30), 'm': (62, 104, 40), 'M': (98, 146, 54), 'L': (140, 190, 80),
    'w': (70, 56, 36), 'W': (98, 80, 50),
}


def fibres(seed, base, light, dark, crack, hi):
    """Вертикальные волокна шириной 4: борозда, светлый край, тело, тень."""
    rnd = random.Random(seed)
    rows = [[None] * 16 for _ in range(16)]
    for x in range(16):
        col = x % 4
        for y in range(16):
            c = crack if col == 0 else light if col == 1 else base
            if col == 0 and rnd.random() < 0.35:
                c = dark
            if col == 1 and rnd.random() < 0.2:
                c = hi
            if col == 3 and rnd.random() < 0.3:
                c = dark
            rows[y][x] = c
    return rows


def root():
    """Корень: тёмная кора, сучки, живая зелёная жилка в борозде."""
    r = fibres(7, 'b', 'l', 'd', 'D', 'h')
    for x, y in ((6, 3), (6, 4), (7, 4), (13, 10), (13, 11), (12, 11)):
        r[y][x] = 'D'
    for y in range(4, 12):
        r[y][8] = 'G' if y in (7, 8) else 'g'
    return r


def vine():
    """Лоза плюща: зелёный стебель, тёмные борозды, светлые прожилки."""
    r = fibres(13, 'm', 'M', 'n', 'n', 'L')
    for x, y, c in ((2, 2, 'L'), (3, 2, 'M'), (10, 6, 'L'), (11, 7, 'L'), (5, 12, 'L'),
                    (14, 13, 'M')):
        r[y][x] = c
    return r


def liana():
    """Лиана коры: одревесневший стебель с пятнами мха и живой жилкой."""
    rnd = random.Random(21)
    r = fibres(21, 'w', 'W', 'd', 'd', 'h')
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        r[y][x] = 'mM'[rnd.random() < 0.4]
        if x + 1 < 16:
            r[y][x + 1] = 'm'
    for y in (3, 4, 11, 12):
        r[y][12] = 'g'
    r[7][12] = 'G'
    return r


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (('druid_root', root), ('druid_vine', vine), ('druid_liana', liana)):
        rows = [[PAL[c] + (255,) for c in row] for row in fn()]
        write_png(os.path.join(OUT, name + '.png'), rows)
        print('block/' + name + '.png', 16, 'x', 16)
