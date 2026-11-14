"""Draws the mod's icon: a storm column with its four kinds' glowing rings under a ragged cloud cap, on a night sky.

python tools/make_icon.py OUT.png [SIZE]   (drawn on a 32 x 32 pixel grid, scaled up without smoothing)
"""
import random
import sys

from PIL import Image

GRID = 32
SKY_TOP, SKY_BOTTOM = (18, 16, 38), (52, 40, 86)
CLOUD, CLOUD_DARK, CLOUD_LIGHT = (86, 86, 110), (60, 60, 84), (120, 120, 146)
RINGS = [(0xB8, 0x4D, 0xFF), (0xFF, 0x5A, 0x0A), (0x2F, 0x9B, 0xFF), (0x12, 0xB5, 0xAE)]  # End, Nether, Overworld, Deep Dark glows
CORES = [(0xF2, 0xD9, 0xFF), (0xFF, 0xE2, 0x7A), (0xEA, 0xF8, 0xFF), (0xB4, 0xFF, 0xF6)]


def draw(size):
    rnd = random.Random(7)
    img = Image.new('RGBA', (GRID, GRID))
    px = img.load()
    for y in range(GRID):  # night sky
        t = y / (GRID - 1)
        for x in range(GRID):
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(SKY_TOP, SKY_BOTTOM)) + (255,)
    for _ in range(14):  # stars
        x, y = rnd.randrange(GRID), rnd.randrange(12)
        px[x, y] = (230, 225, 255, 255)
    cx = 16
    # the cap: a wide, stepped cloud with a ragged pixel rim
    for y, half in [(5, 7), (6, 11), (7, 13), (8, 14), (9, 15), (10, 12)]:
        for x in range(cx - half, cx + half):
            if 0 <= x < GRID and (abs(x - cx) < half - 2 or rnd.random() < .7):
                px[x, y] = (CLOUD_LIGHT if y < 7 else CLOUD if y < 9 else CLOUD_DARK) + (255,)
    # the column, a little wider at the top
    for y in range(10, 30):
        half = 5 if y > 12 else 6
        for x in range(cx - half, cx + half):
            shade = CLOUD_DARK if x < cx - 2 else CLOUD if x < cx + 2 else CLOUD_LIGHT
            px[x, y] = shade + (255,)
    # the rings on its seams, one per kind, glow then a bright core line
    for k, y in enumerate([13, 17, 21, 25]):
        for x in range(cx - 7, cx + 7):
            px[x, y] = RINGS[k] + (255,)
            px[x, y + 1] = RINGS[k] + (255,)
        for x in range(cx - 6, cx + 6, 2):
            px[x, y] = CORES[k] + (255,)
    # the pale ring at its foot and the ground
    for x in range(cx - 8, cx + 8):
        px[x, 29] = (200, 200, 220, 255)
    for x in range(GRID):
        px[x, 30] = (34, 46, 40, 255)
        px[x, 31] = (26, 34, 30, 255)
    return img.resize((size, size), Image.NEAREST)


if __name__ == '__main__':
    out = sys.argv[1]
    draw(int(sys.argv[2]) if len(sys.argv) > 2 else 128).save(out)
