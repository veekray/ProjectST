"""Маленький растеризатор пиксель-арта: фигуры, свечение, палитры, PNG.

Зачем свой, а не Pillow: на машине сборки Pillow нет, а ставить пакеты из сети
проект не разрешает. PNG — это zlib и пара заголовков, и стандартной библиотеки
для этого хватает.

Модель простая. Каждый пиксель — пара (палитра, яркость от 0 до 1). Фигуры
задаются расстоянием до края (SDF), поэтому одна и та же фигура даёт и заливку,
и ореол вокруг, и тёмный контур. В конце яркость переводится в цвет палитры с
упорядоченным сглаживанием — отсюда «зерно» на переходах, как у рисованных
иконок.
"""

import math
import struct
import zlib

# ------------------------------------------------------------------ палитры
# От самого тёмного к самому светлому. Последний цвет — почти белый блик.

PALETTES = {
    'arcane': ['#07051a', '#150c3a', '#28166e', '#4523ad', '#6d3fe0', '#a073ff', '#d6b7ff', '#fff2ff'],
    'frost': ['#04101f', '#0a2147', '#11407e', '#1d6cba', '#35a2e6', '#76d3f7', '#c4f1ff', '#f4feff'],
    'storm': ['#03141c', '#06303f', '#0b5a6e', '#1592a3', '#2ccbd4', '#79f0ee', '#c9fffb', '#ffffff'],
    'nature': ['#0d1405', '#1e2d08', '#36500c', '#5a7f12', '#86ad1c', '#b6d93a', '#e2f487', '#fbffe0'],
    'earth': ['#140b05', '#2a170a', '#452812', '#653d1c', '#8a5a2a', '#b07f42', '#d6ae72', '#f5e3bd'],
    'fire': ['#1a0602', '#3b0e03', '#6b1a04', '#a23006', '#d65a0c', '#f28f1c', '#ffc955', '#fff5c9'],
    'blood': ['#160203', '#330507', '#5a0b0e', '#8a1317', '#bb2228', '#e0474a', '#f48c86', '#ffe0da'],
    'venom': ['#061206', '#0d270c', '#164413', '#21681a', '#38922a', '#62bf3d', '#a7e86d', '#effcd4'],
    'shadow': ['#09050f', '#170c24', '#2a1640', '#43235f', '#653683', '#8c52aa', '#bb86d6', '#efdcfa'],
    'gold': ['#160f02', '#332305', '#5a3f0a', '#8a6210', '#bb8a17', '#e2b52a', '#f6dc6e', '#fff8d6'],
    'steel': ['#0b0d10', '#1a1f26', '#2d3540', '#46515f', '#66737f', '#8f9aa3', '#c3cbd0', '#f2f5f7'],
    'void': ['#020106', '#07040f', '#100a20', '#1c1238', '#2c1d55', '#47317e', '#7a63b8', '#d8ccff'],
    'white': ['#101010', '#2a2a2a', '#4a4a4a', '#707070', '#9a9a9a', '#c4c4c4', '#e6e6e6', '#ffffff'],
}


def rgb(hex_colour):
    h = hex_colour.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------ фигуры (SDF)
# Отрицательное расстояние — внутри фигуры.

def circle(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) - r


def ring(cx, cy, r, w):
    return lambda x, y: abs(math.hypot(x - cx, y - cy) - r) - w / 2


def ellipse(cx, cy, rx, ry):
    def f(x, y):
        k = math.hypot((x - cx) / rx, (y - cy) / ry)
        return (k - 1) * min(rx, ry)
    return f


def capsule(ax, ay, bx, by, r):
    def f(x, y):
        px, py = x - ax, y - ay
        dx, dy = bx - ax, by - ay
        h = max(0.0, min(1.0, (px * dx + py * dy) / (dx * dx + dy * dy or 1)))
        return math.hypot(px - dx * h, py - dy * h) - r
    return f


def polygon(points):
    pts = list(points)

    def f(x, y):
        d = (x - pts[0][0]) ** 2 + (y - pts[0][1]) ** 2
        s = 1.0
        j = len(pts) - 1
        for i in range(len(pts)):
            vi, vj = pts[i], pts[j]
            ex, ey = vj[0] - vi[0], vj[1] - vi[1]
            wx, wy = x - vi[0], y - vi[1]
            t = max(0.0, min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey or 1)))
            bx, by = wx - ex * t, wy - ey * t
            d = min(d, bx * bx + by * by)
            c1 = y >= vi[1]
            c2 = y < vj[1]
            c3 = ex * wy > ey * wx
            if (c1 and c2 and c3) or (not c1 and not c2 and not c3):
                s = -s
            j = i
        return s * math.sqrt(d)
    return f


def star(cx, cy, r_out, r_in, points, turn=0.0):
    pts = []
    for i in range(points * 2):
        a = turn + math.pi * i / points - math.pi / 2
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return polygon(pts)


def union(*shapes):
    return lambda x, y: min(s(x, y) for s in shapes)


def subtract(a, b):
    return lambda x, y: max(a(x, y), -b(x, y))


def arc(cx, cy, r, w, start, end):
    """Дуга кольца от угла start до end (градусы, 0 — вправо, по часовой)."""
    a0, a1 = math.radians(start), math.radians(end)

    def f(x, y):
        a = math.atan2(y - cy, x - cx) % (2 * math.pi)
        lo, hi = a0 % (2 * math.pi), a1 % (2 * math.pi)
        inside = lo <= a <= hi if lo <= hi else (a >= lo or a <= hi)
        if inside:
            return abs(math.hypot(x - cx, y - cy) - r) - w / 2
        e1 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
        e2 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
        return min(math.hypot(x - e1[0], y - e1[1]), math.hypot(x - e2[0], y - e2[1])) - w / 2
    return f


# ------------------------------------------------------------------ холст

BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def noise(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 0xFFFF


class Canvas:
    """
    Холст: на пиксель — палитра и яркость.

    Рисунок задаётся в логических координатах (сетка 24 у навыков, 18 у
    статусов), а растеризуется в out пикселей. Поэтому один и тот же рисунок
    можно отдать в 24×24 или в 64×64: фигуры заданы расстояниями, а не
    пикселями, и в крупном размере просто получают больше деталей.

    zoom > 1 отодвигает рисунок от краёв: так квадратный рисунок помещается в
    ромб, который срезает углы.
    """

    def __init__(self, size, palette, seed=1, out=None, zoom=1.0):
        self.n = size
        self.out = out if out else size
        self.k = size * zoom / self.out
        self.seed = seed
        self.pal = [[palette, 0.0] for _ in range(self.out * self.out)]
        self.alpha = [255] * (self.out * self.out)
        self.c = (size - 1) / 2

    def _u(self, px):
        """Логическая координата центра пикселя."""
        return self.c + (px + 0.5 - self.out / 2) * self.k

    def _cells(self):
        for py in range(self.out):
            v = self._u(py)
            for px in range(self.out):
                yield py * self.out + px, px, py, self._u(px), v

    # --- фон

    def background(self, palette=None, base=0.10, glow=0.30, focus=None, radius=None, grain=0.03):
        """
        Тёмный фон: пятно света за рисунком и виньетка к краям.

        Самое светлое — за главной фигурой, края уходят почти в чёрное: так
        рисунок горит, а не лежит на ровной подложке.
        """
        fx, fy = focus if focus else (self.c, self.c)
        rad = radius if radius else self.n * 0.7
        base = base * 0.55
        glow = glow * 1.25
        for i, px, py, x, y in self._cells():
            d = math.hypot(x - fx, y - fy) / rad
            v = base + glow * math.exp(-3.2 * d * d)
            edge = math.hypot(x - self.c, y - self.c) / (self.n * 0.62)
            v -= 0.06 * max(0.0, edge - 0.6)
            v += (noise(px, py, self.seed) - 0.5) * grain
            cell = self.pal[i]
            if palette:
                cell[0] = palette
            cell[1] = v

    # --- фигуры

    def _coverage(self, sdf, x, y):
        hit = 0
        for sy in (-0.375, -0.125, 0.125, 0.375):
            for sx in (-0.375, -0.125, 0.125, 0.375):
                if sdf(x + sx * self.k, y + sy * self.k) < 0:
                    hit += 1
        return hit / 16

    def shape(self, sdf, palette, value=0.6, light=0.35, light_dir=(-0.7, -0.7), core=0.0,
              core_at=None, core_r=None, glow=0.0, glow_value=0.45, outline=False,
              outline_value=0.02, cover=0.45, centre=None, rim=0.32, edge_shade=0.18):
        """
        Залить фигуру.

        value     — средняя яркость
        light     — сколько добавляет свет слева сверху и забирает тень справа снизу
        core      — дополнительная яркость к сердцевине (core_at, core_r)
        glow      — радиус ореола вокруг, в логических клетках
        outline   — тёмный контур снаружи толщиной около клетки
        """
        cx, cy = centre if centre else (self.c, self.c)
        lx, ly = light_dir
        n = self.n
        rim_depth = 1.1
        outline_width = 0.75
        inside = [False] * len(self.pal)
        for i, px, py, x, y in self._cells():
            if self._coverage(sdf, x, y) < cover:
                continue
            inside[i] = True
            v = value + light * ((x - cx) * lx + (y - cy) * ly) / (n * 0.5)
            if core:
                kx, ky = core_at if core_at else (cx, cy)
                kr = core_r if core_r else n * 0.3
                v += core * max(0.0, 1 - math.hypot(x - kx, y - ky) / kr)
            # Свет на краю, обращённом к свету, и тень на противоположном:
            # так фигура объёмная, а не вырезанная из бумаги.
            depth = -sdf(x, y)
            if depth < rim_depth and (rim or edge_shade):
                gx = sdf(x + 0.25, y) - sdf(x - 0.25, y)
                gy = sdf(x, y + 0.25) - sdf(x, y - 0.25)
                norm = math.hypot(gx, gy) or 1
                facing = (gx * lx + gy * ly) / norm
                weight = 1 - max(0.0, depth) / rim_depth
                v += rim * max(0.0, facing) * weight
                v -= edge_shade * max(0.0, -facing) * weight
            v += (noise(px, py, self.seed + 7) - 0.5) * 0.03
            self.pal[i] = [palette, v]
        if glow or outline:
            for i, px, py, x, y in self._cells():
                if inside[i]:
                    continue
                d = sdf(x, y)
                if outline and 0 < d < outline_width:
                    self.pal[i] = [palette, outline_value]
                    continue
                if glow and 0 < d < glow:
                    g = glow_value * math.exp(-2.4 * d / glow)
                    cell = self.pal[i]
                    if g > cell[1] or cell[0] != palette and g > cell[1] * 0.8:
                        self.pal[i] = [palette, max(cell[1], g)]
        return inside

    def sparkle(self, x, y, palette, value=1.0, arms=1):
        """Блик-крестик: яркая точка и короткие лучи."""
        self.dot(x, y, palette, value, r=0.5)
        for k in range(1, arms + 1):
            for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                self.dot(x + dx * 0.9, y + dy * 0.9, palette, value - 0.25 * k, r=0.35)

    def dot(self, x, y, palette, value, r=0.45):
        """Точка-блик радиусом r клеток."""
        for i, px, py, u, v in self._cells():
            if math.hypot(u - x, v - y) <= r:
                self.pal[i] = [palette, value]

    def mask_diamond(self):
        """Прозрачно всё вне ромба: значки — ромбы."""
        half = self.out / 2
        for py in range(self.out):
            for px in range(self.out):
                if abs(px + 0.5 - half) + abs(py + 0.5 - half) > half:
                    self.alpha[py * self.out + px] = 0

    # --- вывод

    def pixels(self):
        out = []
        for py in range(self.out):
            row = []
            for px in range(self.out):
                name, v = self.pal[py * self.out + px]
                ramp = PALETTES[name]
                k = len(ramp) - 1
                t = max(0.0, min(1.0, v)) * k
                # Упорядоченное сглаживание: зерно на переходах между тонами.
                t += (BAYER4[py % 4][px % 4] / 16 - 0.5) * 0.42
                idx = max(0, min(k, int(round(t))))
                r, g, b = rgb(ramp[idx])
                row.append((r, g, b, self.alpha[py * self.out + px]))
            out.append(row)
        return out

    def save(self, path):
        write_png(path, self.pixels())


def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in rows)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(png)
