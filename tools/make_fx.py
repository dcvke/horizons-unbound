"""
Draws the storm's soft effects atlas (textures/storm/storm_fx.png): python tools/make_fx.py
Four 32x32 cells, white on black (the glow is drawn additively, so black is see-through):
  0 dot   a round glow fading to nothing      1 beam  a vertical glow, soft at its sides and both ends
  2 ring  a soft ring                          3 spark a streak, long and thin
Brightness comes in steps, so it keeps the pixel look without a hard edge anywhere.
"""
import numpy as np
from PIL import Image

N, LEVELS = 32, 12
y, x = (np.mgrid[0:N, 0:N] + .5) / N * 2 - 1  # -1..1 across a cell, y down


def smooth(v):
    v = np.clip(v, 0, 1)
    return v * v * (3 - 2 * v)


d = np.sqrt(x * x + y * y)
dot = smooth(1 - d) ** 2.2
v = (y + 1) / 2  # 0 at the top
beam = np.exp(-(x / .38) ** 2) * smooth(v / .3) * smooth((1 - v) / .12)
ring = np.exp(-((d - .72) / .13) ** 2) * smooth((1 - d) / .08)
spark = np.exp(-(x / .22) ** 2 - (y / .5) ** 2)


def steps(a):
    a = np.clip(a / a.max(), 0, 1)
    return np.round(a * LEVELS) / LEVELS


atlas = np.zeros((2 * N, 2 * N))
atlas[:N, :N], atlas[:N, N:], atlas[N:, :N], atlas[N:, N:] = steps(dot), steps(beam), steps(ring), steps(spark)
rgb = (atlas * 255).astype(np.uint8)
img = np.dstack([rgb, rgb, rgb, np.full_like(rgb, 255)])
assert img[0, 0, 0] == 0 and img[N - 1, N - 1, 0] == 0  # cell corners are black: no seams between cells
Image.fromarray(img, 'RGBA').save('src/main/resources/assets/horizons_unbound/textures/storm/storm_fx.png')
print('ok')
