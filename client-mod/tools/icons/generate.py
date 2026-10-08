"""Генерирует PNG значков в ресурсы мода и лист предпросмотра.

    python generate.py [папка-для-предпросмотра]

Значки ложатся в src/main/resources/assets/rpgcore/textures/gui/{skills,statuses}.
Любой можно заменить картинкой, нарисованной руками: мод берёт файл по имени,
генератор нужен только тем, у кого художника нет.
"""

import base64
import os
import sys
import tempfile
import zlib

from icons import CLASS_PALETTE, SKILL, SKILLS, STATUS, STATUSES
from pixel import Canvas, write_png

HERE = os.path.dirname(os.path.abspath(__file__))
TEXTURES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'rpgcore',
                        'textures', 'gui')


# Сторона картинки в пикселях. Рисунки заданы в логической сетке (24 у навыков,
# 18 у статусов), поэтому размер картинки меняется здесь одной цифрой.
OUT = 64

# Насколько отодвинуть рисунок статуса от краёв, чтобы ромб его не срезал.
STATUS_ZOOM = 1.4


def seed(name):
    """Зерно по имени, а не hash(): тот меняется от запуска к запуску."""
    return zlib.crc32(name.encode()) & 0xFFFF


def class_of(skill_id):
    return skill_id.split('_', 1)[0]


def render_skill(skill_id):
    palette = CLASS_PALETTE[class_of(skill_id)]
    canvas = Canvas(SKILL, palette, seed=seed(skill_id), out=OUT)
    SKILLS[skill_id](canvas, palette)
    canvas.mask_diamond()
    return canvas.pixels()


def render_status(status_id):
    # Статусы — тоже ромбы. Рисунки задуманы квадратными, поэтому отодвигаем их
    # от краёв: ромб срезает углы, и без отступа срезал бы и сам рисунок.
    canvas = Canvas(STATUS, 'steel', seed=seed(status_id), out=OUT, zoom=STATUS_ZOOM)
    STATUSES[status_id](canvas)
    canvas.mask_diamond()
    return canvas.pixels()


def png_bytes(rows):
    path = os.path.join(tempfile.gettempdir(), 'rpgcore-icon.png')
    write_png(path, rows)
    with open(path, 'rb') as f:
        data = f.read()
    os.remove(path)
    return data


def main():
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(os.path.join(TEXTURES, 'skills'), exist_ok=True)
    os.makedirs(os.path.join(TEXTURES, 'statuses'), exist_ok=True)

    skills = {k: render_skill(k) for k in SKILLS}
    statuses = {k: render_status(k) for k in STATUSES}
    for k, rows in skills.items():
        write_png(os.path.join(TEXTURES, 'skills', k + '.png'), rows)
    for k, rows in statuses.items():
        write_png(os.path.join(TEXTURES, 'statuses', k + '.png'), rows)
    print('навыков:', len(skills), 'статусов:', len(statuses))

    if preview_dir:
        preview(skills, statuses, os.path.join(preview_dir, 'icons-preview.svg'))


def preview(skills, statuses, path):
    """Лист: картинки в натуральную величину ×2 и примерно как в игре (масштаб 3)."""
    def img(rows, x, y, size):
        data = base64.b64encode(png_bytes(rows)).decode()
        return (f'<image x="{x}" y="{y}" width="{size}" height="{size}" '
                f'style="image-rendering:pixelated" href="data:image/png;base64,{data}"/>')

    def diamond(x, y, size, colour, width):
        h = size / 2
        return (f'<polygon points="{x + h},{y} {x + size},{y + h} {x + h},{y + size} '
                f'{x},{y + h}" fill="none" stroke="{colour}" stroke-width="{width}"/>')

    def text(x, y, s, size=13, colour='#d8c68a'):
        return (f'<text x="{x}" y="{y}" fill="{colour}" font-family="Segoe UI, sans-serif" '
                f'font-size="{size}" text-anchor="middle">{s}</text>')

    names = {
        'mage_mana_bolt': 'Мановый разряд', 'mage_flow_loop': 'Петля потоков',
        'mage_void_step': 'Шаг в пустоту', 'mage_herd': 'Сгон', 'mage_scatter': 'Россыпь',
        'mage_collapse': 'Коллапс', 'stun': 'Оглушение', 'root': 'Корни', 'silence': 'Тишина',
        'shield': 'Щит', 'bleed': 'Кровотечение', 'venom': 'Яд', 'curse': 'Иссушение',
        'slowed': 'Замедление', 'rage': 'Ярость', 'overheat': 'Перегрев'}
    width, height = 1000, 900
    big = OUT * 2
    parts = [f'<rect width="{width}" height="{height}" fill="#0b0b0d"/>',
             text(width / 2, 30, f'НАВЫКИ МАГА — {OUT}×{OUT}, ×2', 16)]
    for i, (k, rows) in enumerate(skills.items()):
        x = 20 + i * 162
        parts.append(img(rows, x, 50, big))
        parts.append(diamond(x, 50, big, '#6a46d8', 2))
        parts.append(text(x + big / 2, 50 + big + 20, names.get(k, k)))
    y1 = 240
    parts.append(text(width / 2, y1, f'ЭФФЕКТЫ — {OUT}×{OUT}, ромб, ×2', 16))
    for i, (k, rows) in enumerate(statuses.items()):
        x = 20 + (i % 5) * 195
        y = y1 + 20 + (i // 5) * 175
        parts.append(img(rows, x, y, big))
        parts.append(diamond(x, y, big, '#6e5a36', 2))
        parts.append(text(x + big / 2, y + big + 20, names.get(k, k), 12))
    y2 = 640
    parts.append(text(width / 2, y2, 'КАК В ИГРЕ ПРИ МАСШТАБЕ ИНТЕРФЕЙСА 3: книга, бой, ряд эффектов', 16))
    for i, (k, rows) in enumerate(skills.items()):
        parts.append(img(rows, 20 + i * 110, y2 + 20, 102))
    for i, (k, rows) in enumerate(skills.items()):
        parts.append(img(rows, 700 + (i % 3) * 72, y2 + 20 + (i // 3) * 72, 66))
    for i, (k, rows) in enumerate(statuses.items()):
        parts.append(img(rows, 20 + i * 80, y2 + 150, 72))
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
           f'viewBox="0 0 {width} {height}">' + ''.join(parts) + '</svg>')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(svg)
    print('предпросмотр:', path)


if __name__ == '__main__':
    main()
