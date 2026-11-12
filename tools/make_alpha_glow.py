"""
Copies of the storm's glow textures whose alpha follows their brightness: python tools/make_alpha_glow.py
For shader packs that blend glow by alpha instead of adding it (Photon), so black stays see-through. Color unchanged.
"""
import numpy as np
from PIL import Image

D = 'src/main/resources/assets/horizons_unbound/textures/storm/'
for name in ('storm_glow', 'storm_band', 'storm_fx'):
    a = np.array(Image.open(D + name + '.png').convert('RGBA'))
    a[..., 3] = a[..., :3].max(axis=2)
    Image.fromarray(a, 'RGBA').save(D + name + '_a.png')
    print(name, 'alpha range', a[..., 3].min(), a[..., 3].max())
