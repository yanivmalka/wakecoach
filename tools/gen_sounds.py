"""Generates WakeCoach's built-in alarm sounds (16-bit mono WAV, 22.05 kHz).

Synthesized here instead of downloaded so there is nothing to license.
Run: python3 tools/gen_sounds.py  (writes into app/src/main/res/raw/)
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def write(name, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    with wave.open(os.path.join(OUT, name + ".wav"), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / peak * 0.85 * 32767)) for s in samples))


def silence(sec):
    return [0.0] * int(sec * RATE)


def bell(freq, sec, decay=3.0):
    """Struck-bell tone: fundamental plus inharmonic partials with exponential decay."""
    out = []
    for i in range(int(sec * RATE)):
        t = i / RATE
        env = math.exp(-decay * t) * min(1.0, t * 200)
        out.append(env * (math.sin(2 * math.pi * freq * t)
                          + 0.5 * math.sin(2 * math.pi * freq * 2.76 * t)
                          + 0.25 * math.sin(2 * math.pi * freq * 5.4 * t)))
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def chime_soft():
    # Gentle pentatonic chimes, one long phrase that loops calmly.
    seq = []
    for f in (523.25, 659.25, 783.99, 659.25, 880.0, 783.99):
        seq += bell(f, 0.9, decay=2.2)[: int(0.55 * RATE)]
    return seq + silence(1.2)


def birds_morning():
    # Short chirps: fast upward frequency sweeps with random spacing.
    rnd = random.Random(7)
    out = []
    for _ in range(14):
        f0, f1, d = rnd.uniform(2600, 3400), rnd.uniform(3800, 5200), rnd.uniform(0.06, 0.12)
        n = int(d * RATE)
        phase = 0.0
        for i in range(n):
            f = f0 + (f1 - f0) * (i / n)
            phase += 2 * math.pi * f / RATE
            out.append(math.sin(phase) * math.sin(math.pi * i / n))
        out += silence(rnd.uniform(0.05, 0.35))
    return out + silence(0.8)


def rise_bright():
    # Major arpeggio climbing an octave, brighter each step.
    out = []
    for k, f in enumerate((392.0, 493.88, 587.33, 783.99, 987.77, 1174.66)):
        out += [s * (0.6 + 0.08 * k) for s in bell(f, 0.5, decay=4.0)]
    return out + silence(0.4)


def urgent_beep():
    # Classic alarm-clock pattern: four fast beeps, short pause.
    out = []
    for _ in range(4):
        n = int(0.09 * RATE)
        out += [math.sin(2 * math.pi * 2000 * i / RATE) * (1 if i < n - 40 else 0) for i in range(n)]
        out += silence(0.06)
    return out + silence(0.35)


def ringback():
    # Phone ringback heard by the caller: 425 Hz, 1 s on, 3 s off (two rings).
    out = []
    for _ in range(2):
        n = int(1.0 * RATE)
        out += [math.sin(2 * math.pi * 425 * i / RATE) * min(1.0, i / 200, (n - i) / 200) for i in range(n)]
        out += silence(3.0)
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for fn in (chime_soft, birds_morning, rise_bright, urgent_beep, ringback):
        write(fn.__name__, fn())
        print("wrote", fn.__name__)
