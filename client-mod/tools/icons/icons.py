"""Рисунки значков навыков и статусов.

Каждый значок — функция, которая рисует на холсте. Навыки — 24×24 в ромбе,
цвет по классу; статусы — 18×18 квадратом, цвет по стихии эффекта.

Запуск: python generate.py — PNG ложатся в ресурсы мода.
"""

import math

from pixel import (Canvas, arc, capsule, circle, ellipse, polygon, ring, star, subtract,
                   union)

SKILL = 24
STATUS = 18

# Палитра класса: по ней класс узнаётся на значке раньше, чем рисунок.
CLASS_PALETTE = {
    'mage': 'arcane',
}

SKILLS = {}
STATUSES = {}


def skill(skill_id):
    def wrap(fn):
        SKILLS[skill_id] = fn
        return fn
    return wrap


def status(status_id):
    def wrap(fn):
        STATUSES[status_id] = fn
        return fn
    return wrap


# ================================================================== маг (аркана)

@skill('mage_mana_bolt')
def mana_bolt(c: Canvas, p):
    # Сгусток маны летит наискосок, за ним рваный хвост.
    c.background(p, base=0.12, glow=0.25, focus=(15, 9))
    c.shape(capsule(5, 18, 13, 10, 2.4), p, value=0.42, light=0.15, glow=2.5, glow_value=0.3)
    c.shape(capsule(7, 19, 12, 14, 1.2), p, value=0.5, light=0.1)
    c.shape(capsule(4, 15, 9, 11, 1.0), p, value=0.48, light=0.1)
    c.shape(circle(15, 9, 4.6), p, value=0.62, light=0.3, core=0.45, core_at=(14, 8),
            core_r=4, glow=4, glow_value=0.5, outline=True, centre=(15, 9))
    c.sparkle(14, 8, p, 1.05)
    c.dot(19, 5, p, 0.9)
    c.dot(6, 9, p, 0.7)


@skill('mage_flow_loop')
def flow_loop(c: Canvas, p):
    # Кольцо маны с разлетающимися наружу волнами и печатью в центре.
    c.background(p, base=0.10, glow=0.22)
    for a in range(0, 360, 90):
        x = 11.5 + 9 * math.cos(math.radians(a + 45))
        y = 11.5 + 9 * math.sin(math.radians(a + 45))
        c.shape(circle(x, y, 1.3), p, value=0.55, light=0.1, glow=1.5, glow_value=0.3)
    c.shape(ring(11.5, 11.5, 6.5, 2.4), p, value=0.62, light=0.3, glow=3, glow_value=0.42,
            outline=True)
    c.shape(star(11.5, 11.5, 3.2, 1.3, 4, turn=math.pi / 4), p, value=0.75, light=0.2,
            core=0.3, core_r=3)
    c.sparkle(9, 7, p, 1.05)


@skill('mage_void_step')
def void_step(c: Canvas, p):
    # Разлом в пустоту и след рывка к нему.
    c.background('void', base=0.12, glow=0.2, focus=(14, 12))
    c.shape(ellipse(14, 12, 5.2, 7.2), p, value=0.65, light=0.3, glow=3.5, glow_value=0.45,
            outline=True, centre=(14, 12))
    c.shape(ellipse(14.4, 12, 3.4, 5.4), 'void', value=0.08, light=-0.1, centre=(14, 12))
    c.shape(ellipse(15, 12, 1.4, 2.6), 'void', value=0.35, light=0.1, centre=(15, 12))
    for y, length in ((8, 6), (12, 8), (16, 6)):
        c.shape(capsule(9 - length, y, 9, y, 0.7), p, value=0.5, light=0.25,
                light_dir=(1, 0))
    c.sparkle(12, 7, p, 1.0)


@skill('mage_herd')
def herd(c: Canvas, p):
    # Шевроны сгоняют к светящейся точке впереди.
    c.background(p, base=0.10, glow=0.25, focus=(17, 12))
    for i, x in enumerate((4, 8, 12)):
        chevron = polygon([(x, 6), (x + 4, 11.5), (x, 17), (x + 2.2, 17), (x + 6.2, 11.5),
                           (x + 2.2, 6)])
        c.shape(chevron, p, value=0.35 + i * 0.13, light=0.2, outline=True)
    c.shape(circle(18, 11.5, 2.6), p, value=0.8, light=0.2, core=0.3, core_r=2.5,
            glow=3.5, glow_value=0.55, centre=(18, 11.5))
    c.sparkle(18, 11, p, 1.1)


@skill('mage_scatter')
def scatter(c: Canvas, p):
    # Веер из печатей-ромбиков, разлетающихся из угла.
    c.background(p, base=0.10, glow=0.22, focus=(6, 18))
    for i, a in enumerate((-80, -60, -40, -20, 0)):
        r = 10.5
        x = 6 + r * math.cos(math.radians(a))
        y = 18 + r * math.sin(math.radians(a))
        c.shape(capsule(6, 18, x, y, 0.5), p, value=0.32, light=0.1)
        c.shape(star(x, y, 2.6, 1.2, 4), p, value=0.62 + 0.04 * i, light=0.3,
                glow=2, glow_value=0.4, outline=True, centre=(x, y))
    c.shape(circle(6, 18, 2.2), p, value=0.7, light=0.2, glow=2, glow_value=0.4)
    c.sparkle(6, 17, p, 1.0)


@skill('mage_collapse')
def collapse(c: Canvas, p):
    # Всё стягивается в точку: лучи внутрь, кольцо вокруг, яркое ядро.
    c.background('void', base=0.10, glow=0.15)
    c.shape(ring(11.5, 11.5, 8, 1.2), p, value=0.4, light=0.2)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        x0, y0 = 11.5 + 9 * math.cos(a), 11.5 + 9 * math.sin(a)
        x1, y1 = 11.5 + 4 * math.cos(a), 11.5 + 4 * math.sin(a)
        c.shape(capsule(x0, y0, x1, y1, 0.8), p, value=0.55, light=0.2)
    c.shape(circle(11.5, 11.5, 3.4), p, value=0.75, light=0.25, core=0.45, core_r=3,
            glow=4, glow_value=0.6, outline=True)
    c.sparkle(11, 11, p, 1.15, arms=2)


# ================================================================== статусы

@status('stun')
def stun(c: Canvas, p='gold'):
    # Золотые звёзды кружат по светящемуся кольцу.
    c.background('storm', base=0.06, glow=0.16, focus=(8.5, 8))
    c.shape(ring(8.5, 9, 6.2, 1.6), p, value=0.5, light=0.2, glow=2, glow_value=0.35)
    for x, y, r in ((3.2, 10.5, 2.9), (13.8, 10.5, 2.9), (8.5, 3.6, 3.4)):
        c.shape(star(x, y, r, r * 0.45, 5), p, value=0.78, light=0.35, glow=2.2,
                glow_value=0.5, outline=True, centre=(x, y))
    c.shape(star(8.5, 13.6, 2.2, 1.0, 5), p, value=0.6, light=0.3, outline=True,
            centre=(8.5, 13.6))
    c.dot(8, 3, p, 1.15)
    c.dot(3, 10, p, 1.0)
    c.dot(13, 10, p, 1.0)


@status('root')
def root(c: Canvas, p='nature'):
    # Корни оплетают щиколотку.
    c.background('earth', base=0.10, glow=0.15)
    c.shape(capsule(8.5, 2, 8.5, 11, 2.2), 'steel', value=0.4, light=0.25)
    for y, d in ((9, 1), (12, -1), (15, 1)):
        c.shape(arc(8.5, y, 5, 1.6, 200 if d > 0 else 160, 340 if d > 0 else 20), p,
                value=0.6, light=0.3, outline=True)
    c.shape(capsule(1, 16.5, 16, 16.5, 1.2), 'earth', value=0.45)
    c.shape(capsule(3, 16, 6, 12, 0.8), p, value=0.55)
    c.shape(capsule(14, 16, 11, 12, 0.8), p, value=0.55)
    c.dot(5, 8, p, 1.0)


@status('silence')
def silence(c: Canvas, p='shadow'):
    # Звуковые волны перечёркнуты.
    c.background(p, base=0.10, glow=0.15)
    for r in (3, 5.5, 8):
        c.shape(arc(4, 8.5, r, 1.3, 300, 60), p, value=0.55 - r * 0.02, light=0.2)
    c.shape(capsule(2, 15, 15, 2, 1.3), 'blood', value=0.72, light=0.2, outline=True)
    c.dot(4, 8, p, 1.0)


@status('shield')
def shield(c: Canvas, p='frost'):
    # Сфера-щит с бликом.
    c.background(p, base=0.08, glow=0.2)
    c.shape(circle(8.5, 8.5, 7), p, value=0.45, light=0.35, glow=1.5, glow_value=0.4,
            outline=True)
    c.shape(ring(8.5, 8.5, 6, 1.2), p, value=0.75, light=0.3)
    c.shape(ellipse(6, 5.5, 2.2, 1.4), p, value=0.95, light=0.1, centre=(6, 5.5))
    c.dot(11, 11, p, 0.85)


@status('bleed')
def bleed(c: Canvas, p='blood'):
    # Капля крови с бликом.
    c.background(p, base=0.08, glow=0.12)
    drop = union(circle(8.5, 11, 4.6), polygon([(8.5, 1.5), (4.3, 9.5), (12.7, 9.5)]))
    c.shape(drop, p, value=0.55, light=0.4, outline=True, centre=(8.5, 10))
    c.shape(capsule(6.6, 9, 6.2, 12, 0.8), p, value=0.95)
    c.dot(13, 15, p, 0.6)
    c.dot(14, 16, p, 0.5)


@status('venom')
def venom(c: Canvas, p='venom'):
    # Ядовитая капля и пузыри.
    c.background(p, base=0.08, glow=0.15)
    drop = union(circle(8, 11, 4.6), polygon([(8, 1.5), (3.8, 9.5), (12.2, 9.5)]))
    c.shape(drop, p, value=0.55, light=0.4, glow=1.5, glow_value=0.35, outline=True,
            centre=(8, 10))
    c.shape(capsule(6.1, 9, 5.7, 12, 0.8), p, value=0.95)
    for x, y, r in ((14, 5, 1.6), (15, 10, 1.1), (13, 14, 1.3)):
        c.shape(ring(x, y, r, 0.9), p, value=0.65)


@status('curse')
def curse(c: Canvas, p='shadow'):
    # Череп в лиловой дымке.
    c.background(p, base=0.10, glow=0.2)
    skull = union(circle(8.5, 7.5, 5.6), polygon([(4.5, 10), (12.5, 10), (11.5, 15), (5.5, 15)]))
    c.shape(skull, 'white', value=0.7, light=0.35, glow=2, glow_value=0.0, outline=True,
            outline_value=0.02, centre=(8.5, 9))
    c.shape(circle(6.3, 8.5, 1.5), p, value=0.1)
    c.shape(circle(10.7, 8.5, 1.5), p, value=0.1)
    c.shape(polygon([(8.5, 10.4), (7.6, 12), (9.4, 12)]), p, value=0.12)
    for x in (6.5, 8.5, 10.5):
        c.shape(capsule(x, 13.2, x, 14.8, 0.4), p, value=0.12)
    c.dot(6, 8, p, 0.9)
    c.dot(11, 8, p, 0.9)


@status('slowed')
def slowed(c: Canvas, p='frost'):
    # Песочные часы во льду.
    c.background(p, base=0.08, glow=0.18)
    glass = union(polygon([(3, 2), (14, 2), (8.5, 8.5)]), polygon([(3, 15), (14, 15), (8.5, 8.5)]))
    c.shape(glass, p, value=0.42, light=0.35, outline=True)
    c.shape(polygon([(6.2, 13.6), (10.8, 13.6), (8.5, 10.5)]), 'gold', value=0.7)
    c.shape(polygon([(6.5, 3.4), (10.5, 3.4), (8.5, 6)]), 'gold', value=0.6)
    c.shape(capsule(2, 1.5, 15, 1.5, 0.9), 'steel', value=0.6)
    c.shape(capsule(2, 15.5, 15, 15.5, 0.9), 'steel', value=0.6)
    c.sparkle(13, 9, p, 1.0)


@status('rage')
def rage(c: Canvas, p='blood'):
    # Три следа когтей.
    c.background('fire', base=0.10, glow=0.2)
    for x in (2.5, 7, 11.5):
        c.shape(capsule(x + 1, 2.5, x + 4, 15, 1.0), p, value=0.62, light=0.3, glow=1.5,
                glow_value=0.4, outline=True)
    c.dot(4, 3, 'fire', 1.0)


@status('overheat')
def overheat(c: Canvas, p='fire'):
    # Языки пламени.
    c.background(p, base=0.08, glow=0.2, focus=(8.5, 13))
    flame = union(
        polygon([(8.5, 0.5), (3, 9), (3.5, 14), (8.5, 17), (13.5, 14), (14, 9), (11, 6),
                 (10.5, 9)]),
        polygon([(4, 4), (2.5, 10), (5, 9)]))
    c.shape(flame, p, value=0.55, light=0.25, glow=1.5, glow_value=0.4, outline=True,
            centre=(8.5, 11))
    inner = polygon([(8.5, 6), (5.5, 12), (8.5, 16), (11.5, 12)])
    c.shape(inner, p, value=0.85, light=0.1, core=0.25, core_at=(8.5, 13), core_r=3)
