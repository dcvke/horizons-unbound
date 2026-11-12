"""
Synthesizes the mod's sounds (no samples, no downloads): python tools/make_sounds.py <out_dir>
Writes 16-bit mono WAVs; convert them to Ogg Vorbis for Minecraft (see tools/make_sounds.ps1 or any encoder).
Everything is built from layers of shaped noise and a few tones, then put in a space with a reverb, the way a real
storm is heard: a deep roar, wind howling at a wandering pitch, thunder cracking close and rolling away.
Loops are crossfaded end-into-start so they repeat without a click.
"""
import sys
import wave
import numpy as np

SR = 32000
rng = np.random.default_rng(1234)


def t(seconds):
    return np.arange(int(seconds * SR)) / SR


def band(x, lo, hi):
    """Static band-pass by FFT mask with soft edges."""
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec = np.fft.rfft(x)
    edge = lambda v, c, w: 1 / (1 + np.exp(-np.clip((v - c) / w, -60, 60)))
    mask = edge(f, lo, max(lo * .15, 2)) * (1 - edge(f, hi, max(hi * .15, 2)))
    return np.fft.irfft(spec * mask, len(x))


def brown(n):
    w = rng.standard_normal(n)
    b = np.cumsum(w)
    b -= np.convolve(b, np.ones(4001) / 4001, mode='same')  # remove drift
    return b / (np.abs(b).max() + 1e-9)


def curve(n, rate):
    """A smooth random curve in 0..1 that wanders about `rate` times a second."""
    k = max(4, int(n / SR * rate) + 3)
    pts = rng.random(k)
    x = np.linspace(0, k - 1, n)
    i = np.minimum(x.astype(int), k - 2)
    u = x - i
    u = u * u * (3 - 2 * u)
    return pts[i] * (1 - u) + pts[i + 1] * u


def moving_band(n, fc, width=.6):
    """Noise through a band whose center follows fc (an array of n Hz values): wind at a wandering pitch."""
    out = np.zeros(n)
    block, hop = 2048, 512
    noise = rng.standard_normal(n + block)
    win = np.hanning(block)
    for start in range(0, n, hop):
        c = fc[min(start + block // 2, n - 1)]
        seg = band(noise[start:start + block] * win, c * (1 - width / 2), c * (1 + width / 2))
        end = min(n, start + block)
        out[start:end] += seg[:end - start]
    return out / (np.abs(out).max() + 1e-9)


def sweep_noise(seconds, f0, f1, width=.6):
    n = int(seconds * SR)
    return moving_band(n, f0 * (f1 / f0) ** np.linspace(0, 1, n), width)


def wind(seconds, lo, hi, rate=.35, width=.5):
    """Wind: its pitch wanders between lo and hi, its strength gusts."""
    n = int(seconds * SR)
    return moving_band(n, lo * (hi / lo) ** curve(n, rate), width) * (.35 + .65 * curve(n, rate * 1.7))


def reverb(x, seconds=2.5, bright=3000, mix=.35, tail=False):
    """Puts x in a big open space: convolution with a decaying burst of noise. With tail, the result rings on past x."""
    m = int(seconds * SR)
    ir = band(rng.standard_normal(m), 60, bright) * np.exp(-np.arange(m) / (m / 6.5))
    ir[:int(.012 * SR)] = 0  # a short gap before the first echoes
    size = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)
    wet *= np.sqrt(np.mean(x ** 2) + 1e-12) / (np.sqrt(np.mean(wet ** 2)) + 1e-12)
    dry = np.concatenate([x, np.zeros(m)])
    out = dry * (1 - mix) + wet * mix
    return out if tail else out[:len(x)]


def env(n, attack, release, shape=2.0):
    x = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a: x[:a] = np.linspace(0, 1, a) ** shape
    if r: x[-r:] *= np.linspace(1, 0, r) ** shape
    return x


def loop(x, fade=1.0):
    """Make x loop seamlessly: the extra tail is crossfaded into the start."""
    f = int(fade * SR)
    body, tail = x[:-f].copy(), x[-f:]
    ramp = np.linspace(0, 1, f)
    body[:f] = body[:f] * np.sqrt(ramp) + tail * np.sqrt(1 - ramp)
    return body


def norm(x, peak=.8):
    x = x - np.mean(x)
    return x / (np.abs(x).max() + 1e-9) * peak


def add(x, at, y, gain=1.0):
    """Mixes y into x starting at `at` seconds (cut off at x's end)."""
    i = int(at * SR)
    if i >= len(x): return
    y = y[:len(x) - i]
    x[i:i + len(y)] += y * gain


def drop(seconds, f0, f1, decay, fall=1.5):
    """A deep tone falling from f0 to f1: the body of a boom."""
    tt = t(seconds)
    f = f1 + (f0 - f1) * np.exp(-tt * fall)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * decay) * (1 - np.exp(-tt * 400)) * env(len(tt), 0, seconds * .25)  # no click where it ends


def thunder(seconds=5.0, close=1.0):
    """A thunderclap: a few sharp cracks, then the roll, uneven, dying away into the distance."""
    n = int(seconds * SR)
    tt = t(seconds)
    x = np.zeros(n)
    for k in range(int(2 + 3 * close)):
        at = k * rng.uniform(.03, .11)
        c = band(rng.standard_normal(int(.25 * SR)), 180, 3000 + 4000 * close) * np.exp(-t(.25) * rng.uniform(14, 30))
        add(x, at, c, close * rng.uniform(.5, 1))
    roll = band(brown(n), 35, 420) * (.25 + .75 * curve(n, 3.5)) * np.exp(-tt * (3.2 / seconds)) * (1 - np.exp(-tt * 12))
    x += roll * 1.3 + band(rng.standard_normal(n), 120, 900) * (.2 + .8 * curve(n, 5)) * np.exp(-tt * (4.5 / seconds)) * .12 + drop(seconds, 75, 30, 2.2) * .3
    return x * env(n, 0, seconds * .35)


def write(path, x):
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


# ---------------------------------------------------------------- the sounds

def rumble():  # a storm far off: a roar you feel more than hear, wind howling round it, thunder rolling inside
    s = 14.0
    n = int(s * SR)
    tt = t(s)
    x = band(brown(n), 30, 140) * (.7 + .3 * curve(n, .5)) * .5                 # the deep roar
    x += wind(s, 90, 320, .3, .7) * .9                                         # the body of the wind
    x += wind(s, 320, 820, .22, .22) * .3 + wind(s, 500, 1300, .3, .12) * .14  # howls, far and thin
    x += (np.sin(2 * np.pi * 31 * tt) + np.sin(2 * np.pi * 33.5 * tt)) * .05 * (.5 + .5 * curve(n, .4))  # a slow beat underfoot
    for at in (1.6, 6.3, 10.2):
        add(x, at, reverb(thunder(3.2, .25), 3, 900, .6, tail=True), .5)
    return norm(loop(reverb(x, 3, 2200, .3), 2.0), .85)


def whoosh(enter):  # crossing the wall: the air pulls past, pressure drops with a thump, and the world goes close (in) or wide (out)
    s = 2.0
    n = int(s * SR)
    tt = t(s)
    x = sweep_noise(s, 220 if enter else 2600, 2600 if enter else 220, .8)
    x *= np.exp(-((tt - (.55 if enter else .25)) / .3) ** 2)
    add(x, .55 if enter else .2, drop(1.2, 110, 45, 5), .45)
    air = band(rng.standard_normal(n), 300 if enter else 1200, 900 if enter else 5000) * np.exp(-((tt - .9) / .5) ** 2) * .25
    return norm(reverb(x * .8 + air, 1.4 if enter else 2.4, 1800 if enter else 5000, .4) * env(n, .01, .5), .8)


def omen_wind():  # the wind before a storm: gusts, a thin whistle that comes and goes, a low unease, thunder very far away
    s = 14.0
    n = int(s * SR)
    tt = t(s)
    x = wind(s, 220, 900, .4, .8) + wind(s, 500, 1600, .55, .5) * .5
    x += wind(s, 900, 1700, .25, .06) * .22 * curve(n, .3) ** 2                 # the whistle
    x += band(brown(n), 28, 110) * .5 + np.sin(2 * np.pi * 55 * tt) * .05 * curve(n, .2)
    add(x, 8.5, reverb(band(thunder(3.5, .1), 20, 160), 3.5, 400, .7, tail=True), .5)
    return norm(loop(reverb(x, 2.5, 4000, .25), 2.0), .8)


def merge():  # two storms becoming one: the air drawn in, a double blow, and thunder rolling out for miles
    s = 7.0
    n = int(s * SR)
    x = np.zeros(n)
    suck = sweep_noise(.7, 150, 1500, .9) * np.linspace(0, 1, int(.7 * SR)) ** 3
    add(x, 0, suck, .5)
    add(x, .7, drop(4, 120, 40, 1.0), .3)
    add(x, .9, drop(3.5, 95, 36, 1.2), .2)
    add(x, .7, thunder(5.5, 1.0), 1.0)
    add(x, 1.6, thunder(4.5, .3), .5)
    return norm(np.tanh(reverb(x, 4, 2500, .4) * 1.6) * env(n, 0, 2.5), .9)


def end_hum():  # inside an End storm: silence that hums - slow beating drones, glass harmonics drifting in and out, far pings
    s = 14.0
    n = int(s * SR)
    tt = t(s)
    x = (np.sin(2 * np.pi * 55 * tt) + np.sin(2 * np.pi * 55.35 * tt) + .6 * np.sin(2 * np.pi * 82.6 * tt)
         + .4 * np.sin(2 * np.pi * 110.3 * tt) * (.5 + .5 * np.sin(2 * np.pi * tt / 7))
         + .5 * np.sin(2 * np.pi * 165.2 * tt) + .3 * np.sin(2 * np.pi * 220.9 * tt)) * .16
    for f in (659.3, 880, 1320.4, 1760.9, 2637):  # the glass
        vib = 1 + .0025 * np.sin(2 * np.pi * rng.uniform(.15, .4) * tt + rng.uniform(0, 6))
        x += np.sin(2 * np.pi * np.cumsum(f * vib) / SR) * curve(n, .25) ** 2 * .16 * (880 / f) ** .5
    pings = np.zeros(n)
    for at in (2.1, 5.8, 7.4, 11.2):
        f = rng.choice([1318.5, 1760, 2093, 2637])
        add(pings, at, np.sin(2 * np.pi * f * t(1.2)) * np.exp(-t(1.2) * 6), .12)
    x += reverb(pings, 5, 6000, .85) + band(rng.standard_normal(n), 2500, 6500) * .02 * curve(n, .3)
    return norm(loop(reverb(x, 4, 5000, .35), 2.5), .7)


def forge():  # inside a Nether storm: a furnace breathing - a fluttering roar, bellows, crackling, iron ringing somewhere far off
    s = 14.0
    n = int(s * SR)
    tt = t(s)
    flutter = .75 + .25 * np.sin(2 * np.pi * np.cumsum(8 + 5 * curve(n, .5)) / SR)
    x = band(brown(n), 28, 190) * flutter + wind(s, 120, 420, .4, .8) * .4
    breath = .5 + .5 * np.sin(2 * np.pi * tt / 3.5 - 1.2)                       # the bellows
    x *= .6 + .4 * breath
    x += band(rng.standard_normal(n), 700, 3500) * .05 * breath ** 3            # the hiss on each breath
    crack = np.zeros(n)
    for _ in range(170):
        i = rng.integers(0, n - 800)
        L = rng.integers(50, 500)
        crack[i:i + L] += band(rng.standard_normal(L), 1500, 6000) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(.15, .8)
    clang = np.zeros(n)
    for at in (3.4, 10.1):  # iron, far away
        c = sum(np.sin(2 * np.pi * f * t(2)) * np.exp(-t(2) * d) for f, d in ((523, 3), (1372, 5), (2213, 8), (3120, 12)))
        add(clang, at, c, .1)
    x = np.tanh((x + crack * .35) / (np.percentile(np.abs(x), 99) + 1e-9) * .8) + reverb(clang, 4, 4000, .9)
    return norm(loop(reverb(x, 2, 2500, .25), 2.0), .75)


def eye_wind():  # the eye of an Overworld storm: calm - high soft air, the wind singing far above, the storm a murmur beyond the wall
    s = 14.0
    n = int(s * SR)
    x = wind(s, 1100, 3000, .3, .7) * .7 + wind(s, 300, 700, .25, .6) * .35
    x += wind(s, 560, 840, .18, .05) * .18 * curve(n, .25) ** 2 + wind(s, 840, 1260, .2, .04) * .1 * curve(n, .25) ** 2  # singing
    x += band(brown(n), 24, 75) * .35                                           # the storm outside
    return norm(loop(reverb(x, 3.5, 5000, .4), 2.5), .6)


def heartbeat():  # inside a Deep Dark storm: lub-dub, close and huge, and things clicking in the dark
    s = 1.8
    n = int(s * SR)
    x = np.zeros(n)
    for at, amp, f in ((0.0, 1.0, 74), (0.28, .7, 62)):
        add(x, at, drop(.6, f * 1.5, f, 11, 9), amp)
        add(x, at, band(rng.standard_normal(int(.12 * SR)), 90, 420) * np.exp(-t(.12) * 40), amp * 1.1)
    clicks = np.zeros(n)
    for _ in range(6):
        add(clicks, rng.uniform(.75, 1.6), band(rng.standard_normal(200), 2500, 7000) * np.exp(-np.arange(200) / 40), .22)
    return norm(reverb(x, 1.2, 500, .3) + reverb(clicks, 1.5, 6000, .5), .85)


def sculk_pulse():  # a Deep Dark storm answering a noise: a thump, a run of clicks climbing, a shiver through the air
    s = 1.8
    n = int(s * SR)
    tt = t(s)
    x = np.zeros(n)
    add(x, 0, drop(1, 140, 70, 8, 8), .4)
    for k in range(10):
        add(x, .08 + k * .042, band(rng.standard_normal(160), 2000 + k * 320, 6000 + k * 400) * np.exp(-np.arange(160) / 35), .85 - k * .06)
    shiver = band(rng.standard_normal(n), 3000, 8000) * np.exp(-((tt - .6) / .25) ** 2) * (.5 + .5 * np.sin(2 * np.pi * 23 * tt)) * .16
    return norm(reverb(x + shiver, 1.6, 6000, .4) * env(n, 0, .5), .85)


def birth():
    """
    A storm being born, in step with client/StormBirth. Warm and deep: wind, weight and distance, nothing shrill.
     0 - 2.3   the air is drawn in: a soft low breath swelling, a hum underneath
     2.3       ignition: a deep, rounded boom and thunder rolling away; the beam a low warm drone
     4.5 - 11  the vortex closes in: the wind swelling and slowly rising, turning in ever quicker waves
    11         it shuts: the wind holds its breath, then a great soft blow, air rushing out, thunder close by
    11 - 20    the roar settles into the storm's own rumble; a last low pulse at 18
    """
    s = 20.0
    n = int(s * SR)
    tt = t(s)
    x = np.zeros(n)
    # the gathering
    g = int(2.3 * SR)
    k = tt[:g] / 2.3
    x[:g] += sweep_noise(2.3, 90, 420, 1.0) * k ** 2 * .7 + np.sin(2 * np.pi * np.cumsum(42 + 30 * k ** 2) / SR) * k ** 2 * .35
    # ignition and the beam
    add(x, 2.3, band(rng.standard_normal(int(.5 * SR)), 120, 1400) * np.exp(-t(.5) * 9), .7)
    add(x, 2.3, drop(4, 115, 38, .9), .6)
    add(x, 2.32, band(thunder(5, .3), 30, 2200) * env(5 * SR, .005, 1.5), .8)  # (the filter rings at the ends: faded)
    b = tt[:int(7.4 * SR)]
    beam = np.sin(2 * np.pi * 110 * b) + .6 * np.sin(2 * np.pi * 165.3 * b) + .35 * np.sin(2 * np.pi * 220.4 * b)
    add(x, 2.3, beam * (1 - np.exp(-b * 3)) * (1 - np.clip((b - 2.7) / 4.5, 0, 1)) ** 1.5 * (.85 + .15 * np.sin(2 * np.pi * .8 * b)), .09)
    # the vortex closing in
    c0, c1 = int(4.5 * SR), int(10.85 * SR)
    m = c1 - c0
    u = np.linspace(0, 1, m)
    wave_ = .65 + .35 * np.sin(2 * np.pi * np.cumsum(.8 + 3.6 * u ** 2.5) / SR)   # it turns in quicker and quicker waves
    x[c0:c1] += moving_band(m, 100 * 3.6 ** (u ** 1.5), 1.0) * u ** 1.5 * wave_ * 1.1
    x[c0:c1] += moving_band(m, 220 * 2.6 ** (u ** 1.8), .6) * u ** 2.2 * wave_ * .35
    x[c0:c1] += band(brown(m), 30, 130) * u ** 1.2 * .5
    hush = np.ones(n)                                                             # the breath before the blow
    hush[c1 - int(.12 * SR):c1] = np.linspace(1, .2, int(.12 * SR))
    hush[c1:int(11 * SR)] = .2
    x *= hush
    # the blow
    add(x, 11, band(rng.standard_normal(int(.6 * SR)), 100, 1300) * np.exp(-t(.6) * 7), .9)
    add(x, 11, drop(5, 105, 32, .8), .8)
    add(x, 11.02, band(thunder(6, .5), 30, 2400) * env(6 * SR, .005, 1.5), 1.1)
    add(x, 11, sweep_noise(2.2, 900, 160, 1.0) * np.exp(-t(2.2) * 1.8), .5)        # the air rushing out
    chord = (np.sin(2 * np.pi * 110 * t(5)) + .6 * np.sin(2 * np.pi * 164.8 * t(5)) + .4 * np.sin(2 * np.pi * 220.7 * t(5))) * np.exp(-t(5) * .8) * (1 - np.exp(-t(5) * 6))
    add(x, 11.05, chord, .07)                                                     # the rings, as a low warm swell
    # settling into the storm
    a = tt[int(11 * SR):] - 11
    x[int(11 * SR):] += (band(brown(len(a)), 30, 140) * .5 + wind(len(a) / SR, 90, 320, .3, .7) * .9 + wind(len(a) / SR, 300, 700, .22, .3) * .2) * (1 - np.exp(-a * 1.5)) * np.exp(-a * .12) * .6
    add(x, 18, drop(2.5, 70, 40, 2) * (1 - np.exp(-t(2.5) * 6)), .45)
    x = np.tanh(band(reverb(x, 4, 2200, .35), 25, 3200) * 1.15)                   # dark and roomy; no hiss, no bite
    return norm(x * env(n, .05, 3.5), .9)


SOUNDS = {
    'rumble': rumble, 'enter': lambda: whoosh(True), 'exit': lambda: whoosh(False), 'omen_wind': omen_wind,
    'merge': merge, 'end_hum': end_hum, 'forge': forge, 'eye_wind': eye_wind, 'heartbeat': heartbeat,
    'sculk_pulse': sculk_pulse, 'birth': birth,
}

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for name, make in SOUNDS.items():
        x = make()
        assert np.isfinite(x).all() and np.abs(x).max() <= 1, name
        write(f'{out}/{name}.wav', x)
        print(f'wrote {name}: {len(x) / SR:.1f} s, peak {np.abs(x).max():.2f}, rms {np.sqrt(np.mean(x ** 2)):.3f}')
