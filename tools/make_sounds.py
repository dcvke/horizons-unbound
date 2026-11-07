"""
Synthesizes the mod's sounds (no samples, no downloads): python tools/make_sounds.py <out_dir>
Writes 16-bit mono WAVs; convert them to Ogg Vorbis for Minecraft (see tools/make_sounds.ps1 or any encoder).
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
    edge = lambda v, c, w: 1 / (1 + np.exp(-(v - c) / w))
    mask = edge(f, lo, max(lo * .15, 2)) * (1 - edge(f, hi, max(hi * .15, 2)))
    return np.fft.irfft(spec * mask, len(x))


def brown(n):
    w = rng.standard_normal(n)
    b = np.cumsum(w)
    b -= np.convolve(b, np.ones(4001) / 4001, mode='same')  # remove drift
    return b / (np.abs(b).max() + 1e-9)


def sweep_noise(seconds, f0, f1, width=.6):
    """Noise through a band that glides from f0 to f1 (done in short overlapping blocks)."""
    n = int(seconds * SR)
    out = np.zeros(n)
    block, hop = 2048, 512
    noise = rng.standard_normal(n + block)
    win = np.hanning(block)
    for start in range(0, n, hop):
        k = start / max(n - 1, 1)
        fc = f0 * (f1 / f0) ** k
        seg = band(noise[start:start + block] * win, fc * (1 - width / 2), fc * (1 + width / 2))
        end = min(n, start + block)
        out[start:end] += seg[:end - start]
    return out


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
    return x / (np.abs(x).max() + 1e-9) * peak


def write(path, x):
    data = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def rumble():  # a storm far off: deep rolling noise with a slow throb
    s = 9.0
    x = band(brown(int(s * SR)), 25, 140) * 1.0
    tt = t(s)
    x *= .75 + .25 * np.sin(2 * np.pi * tt / 3.0) * np.sin(2 * np.pi * tt / 4.5)
    x += .35 * np.sin(2 * np.pi * 38 * tt) * (.6 + .4 * np.sin(2 * np.pi * tt / 2.25))
    for c in (1.7, 5.1, 7.4):  # distant rolls
        n = int(1.6 * SR); i = int(c * SR)
        roll = band(rng.standard_normal(n), 40, 220) * env(n, .3, 1.1)
        x[i:i + n] += roll[:len(x) - i] * .5
    return norm(loop(x, 1.0), .85)


def whoosh(rise):  # crossing the wall: a breath of air sweeping up (in) or down (out), and a soft thump
    s = 1.5
    x = sweep_noise(s, 250 if rise else 2400, 2400 if rise else 250, .7)
    n = len(x)
    e = np.concatenate([np.linspace(0, 1, n // 3) ** 1.5, np.linspace(1, 0, n - n // 3) ** 2]) if rise else env(n, .05, 1.2)
    x *= e
    tt = t(s)
    thump = np.sin(2 * np.pi * 55 * tt) * np.exp(-tt * (7 if rise else 9))
    if rise: thump = np.roll(thump, int(.35 * SR)); thump[:int(.35 * SR)] = 0
    return norm(x * .8 + thump * .6, .8)


def omen_wind():  # the wind before a storm: gusting, a faint whistle
    s = 10.0
    tt = t(s)
    base = band(rng.standard_normal(len(tt)), 250, 1100)
    gust = .55 + .45 * np.sin(2 * np.pi * tt / 3.3 + 1) * np.sin(2 * np.pi * tt / 5.0)
    whistle = band(rng.standard_normal(len(tt)), 900, 1150) * (.3 + .3 * np.sin(2 * np.pi * tt / 2.5) ** 2)
    low = band(brown(len(tt)), 30, 120) * .6
    return norm(loop(base * gust + whistle + low, 1.5), .8)


def merge():  # two storms becoming one: a huge low boom and a long roll
    s = 5.0
    tt = t(s)
    f = 80 * np.exp(-tt * .8) + 28
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * .9)
    crack = band(rng.standard_normal(len(tt)), 60, 900) * np.exp(-tt * 3.5)
    roll = band(brown(len(tt)), 20, 160) * env(len(tt), .2, 3.5) * .8
    return norm(boom * 1.0 + crack * .6 + roll, .9)


def end_hum():  # the hum in the silence of an End storm: beating drones and a little air
    s = 10.0
    tt = t(s)
    d = (np.sin(2 * np.pi * 55 * tt) + np.sin(2 * np.pi * 55.4 * tt) + .5 * np.sin(2 * np.pi * 110.2 * tt)
         + .3 * np.sin(2 * np.pi * 164.8 * tt) * (.5 + .5 * np.sin(2 * np.pi * tt / 5)))
    air = band(rng.standard_normal(len(tt)), 2500, 6000) * .05 * (.5 + .5 * np.sin(2 * np.pi * tt / 3.3))
    return norm(loop(d * .5 + air, 2.0), .7)


def forge():  # inside a Nether storm: low furnace rumble, crackles, a breath now and then
    s = 10.0
    tt = t(s)
    x = band(brown(len(tt)), 30, 180) * .9
    n = len(tt)
    for _ in range(140):  # crackles
        i = rng.integers(0, n - 800)
        L = rng.integers(60, 600)
        x[i:i + L] += band(rng.standard_normal(L), 1500, 6000) * np.exp(-np.arange(L) / (L / 4)) * rng.uniform(.2, .9)
    for c in (2.2, 6.6):  # whoomph
        i = int(c * SR); L = int(1.2 * SR)
        x[i:i + L] += band(rng.standard_normal(L), 80, 400) * env(L, .3, .8) * .6
    x = np.tanh(x / (np.percentile(np.abs(x), 99) + 1e-9) * .8)  # soft limiter: crackles mustn't drown the rumble
    return norm(loop(x, 1.0), .75)


def eye_wind():  # the eye of an Overworld storm: soft, airy, high wind
    s = 10.0
    tt = t(s)
    x = band(rng.standard_normal(len(tt)), 900, 3200) * (.5 + .5 * np.sin(2 * np.pi * tt / 4.0) ** 2)
    x += band(rng.standard_normal(len(tt)), 300, 700) * .4
    return norm(loop(x, 2.0), .6)


def heartbeat():  # inside a Deep Dark storm: lub-dub, and faint clicks
    s = 1.8
    tt = t(s)
    x = np.zeros(len(tt))
    for start, amp in ((0.0, 1.0), (0.28, .7)):
        i = int(start * SR)
        b = tt[:len(tt) - i]
        x[i:] += amp * np.sin(2 * np.pi * 46 * b) * np.exp(-b * 14) * (1 - np.exp(-b * 300))
    clicks = np.zeros(len(tt))
    for _ in range(6):
        i = rng.integers(int(.7 * SR), len(tt) - 300)
        clicks[i:i + 200] += band(rng.standard_normal(200), 2500, 7000) * np.exp(-np.arange(200) / 40) * .25
    return norm(x + clicks, .85)


def sculk_pulse():  # a Deep Dark storm answering a noise: thump, a run of clicks, a shimmer
    s = 1.4
    tt = t(s)
    thump = np.sin(2 * np.pi * 52 * tt) * np.exp(-tt * 10)
    clicks = np.zeros(len(tt))
    for k in range(9):
        i = int((.08 + k * .045) * SR)
        L = 160
        clicks[i:i + L] += band(rng.standard_normal(L), 2000 + k * 300, 6000 + k * 400) * np.exp(-np.arange(L) / 35) * (.9 - k * .07)
    shimmer = band(rng.standard_normal(len(tt)), 3000, 8000) * np.exp(-((tt - .55) / .25) ** 2) * .15
    return norm(thump + clicks + shimmer, .85)


def birth():  # a storm being born: a rising in-breath, the crack and boom at ignition (2.3 s), a roaring swirl, the touchdown (11 s)
    s = 15.0
    tt = t(s)
    n = len(tt)
    ign = int(2.3 * SR)
    breath = sweep_noise(2.3, 150, 1800, .8)
    breath *= np.linspace(0, 1, len(breath)) ** 2.2
    hum = np.sin(2 * np.pi * np.cumsum(40 + 50 * (tt[:ign] / 2.3) ** 2) / SR) * (tt[:ign] / 2.3) ** 2 * .6
    x = np.zeros(n)
    x[:ign] += breath[:ign] * .7 + hum
    b = tt[:n - ign]
    f = 90 * np.exp(-b * 1.2) + 30
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-b * .8)
    crack = band(rng.standard_normal(n - ign), 300, 6000) * np.exp(-b * 9)
    swirl = sweep_noise((n - ign) / SR, 900, 200, .9) * (1 - np.exp(-b * 2)) * np.exp(-b * .25) * .7
    roll = band(brown(n - ign), 20, 140) * np.exp(-b * .3) * .8
    x[ign:] += boom * 1.1 + crack * .9 + swirl + roll
    td = int(11.0 * SR)  # the funnel touching down: a deep thud and a burst of grit
    d = tt[:n - td]
    thud = np.sin(2 * np.pi * np.cumsum(55 * np.exp(-d * 1.5) + 28) / SR) * np.exp(-d * 1.4)
    grit = band(rng.standard_normal(n - td), 80, 1500) * np.exp(-d * 4)
    x[td:] += thud * 1.2 + grit * .5
    x = np.tanh(x * 1.4)
    return norm(x * env(n, 0, 2.5), .92)


SOUNDS = {
    'rumble': rumble, 'enter': lambda: whoosh(True), 'exit': lambda: whoosh(False), 'omen_wind': omen_wind,
    'merge': merge, 'end_hum': end_hum, 'forge': forge, 'eye_wind': eye_wind, 'heartbeat': heartbeat,
    'sculk_pulse': sculk_pulse, 'birth': birth,
}

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for name, make in SOUNDS.items():
        write(f'{out}/{name}.wav', make())
        print('wrote', name)
