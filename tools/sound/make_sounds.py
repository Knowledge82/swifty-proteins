"""Synthesises the cogitator sounds of Swifty Proteins from nothing but maths (document 14), version 2: "grimdark".

No samples, no recordings: oscillators, noise, filters, envelopes, plus three techniques that give WEIGHT:
  * modal synthesis  - a struck metal object is a sum of damped sine waves at its own (inharmonic) mode ratios;
  * formant filters  - a buzzing source through the resonances of a vowel: a low machine "chant";
  * drive + reverb   - tanh saturation (harmonics that a phone speaker CAN play) and a synthetic stone hall.
The random noise uses a FIXED seed and the encoder runs bit-exact: the same files every run (same ffmpeg version).

    python3 tools/sound/make_sounds.py [output_dir] [--wav-dir DIR] [--plots FILE]
"""
import argparse
import os
import subprocess
import sys
import wave
import zlib

import numpy as np
from scipy import signal

SR = 44_100
RNG = np.random.default_rng(42)  # replaced before EACH sound, see seed_for


def seed_for(name):
    """Each sound gets its OWN noise generator, seeded from its name. With one shared generator, changing how much
    noise one recipe uses shifts the noise of every recipe after it: editing the boot sound would change the bell."""
    return zlib.crc32(name.encode())


# Sounds APPROVED on the phone (version 2.0) keep their noise exactly: they were made with ONE generator, seed 42,
# shared in order, and this is how many numbers had been drawn from it when each one started. Rebuilding that point
# gives the same bytes as before (the noise of the strike, the tail of the hall: in the bell, up to 3.8 dB per third
# of an octave would change otherwise). Document 14, 6.
FROZEN_NOISE = {"snd_boot_line": 44_276, "snd_boot_done": 44_408, "snd_key": 88_773, "snd_select": 234_567}


def generator_for(name):
    if name in FROZEN_NOISE:
        rng = np.random.default_rng(42)
        rng.uniform(-1, 1, FROZEN_NOISE[name])   # draw (and drop) what was drawn before this sound in version 2.0
        return rng
    return np.random.default_rng(seed_for(name))


# ---------------------------------------------------------------------------------------------------------- basics

def n_of(seconds):
    return int(round(seconds * SR))


def t_of(seconds):
    return np.arange(n_of(seconds)) / SR


def phase_of(freq):
    """Accumulated phase: correct for a frequency that changes over time (no clicks in glides)."""
    return 2 * np.pi * np.cumsum(freq) / SR


def const(f, seconds):
    return np.full(n_of(seconds), float(f))


def sine(freq):
    return np.sin(phase_of(freq))


def saw(freq):
    p = phase_of(freq) / (2 * np.pi)
    return 2 * (p - np.floor(p + 0.5))


def noise(n):
    return RNG.uniform(-1, 1, n)


def lowpass(x, cutoff, order=4):
    b, a = signal.butter(order, cutoff / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def highpass(x, cutoff, order=4):
    b, a = signal.butter(order, cutoff / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bandpass(x, low, high, order=2):
    b, a = signal.butter(order, [low / (SR / 2), high / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def adsr(n, attack, decay, sustain, release):
    a, d, r = (int(v * SR) for v in (attack, decay, release))
    s = max(n - a - d - r, 0)
    env = np.concatenate([
        np.linspace(0, 1, a, endpoint=False),
        np.linspace(1, sustain, d, endpoint=False),
        np.full(s, sustain),
        np.linspace(sustain, 0, r),
    ])
    return np.pad(env, (0, max(n - len(env), 0)))[:n]


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def fade_out(x, seconds=0.012):
    n = min(n_of(seconds), len(x))
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    return x


def drive(x, amount):
    """tanh saturation. It adds HARMONICS (2f, 3f, ...) to a low tone: a phone speaker cannot play 60 Hz, but it plays
    120, 180, 240 Hz, and the ear rebuilds the missing low note from them ("missing fundamental")."""
    return np.tanh(amount * x) / np.tanh(amount)


def place(total_seconds, *parts):
    out = np.zeros(n_of(total_seconds))
    for start, x in parts:
        i = n_of(start)
        j = min(i + len(x), len(out))
        out[i:j] += x[: j - i]
    return out


# How loud each sound is, as SPEAKER LOUDNESS (see speaker_loudness) in dB. Four tiers: what sounds often is the
# quietest. Sounds must accompany, never distract. (Version 1 normalised the PEAK instead, and the loudness came out
# all over the place: document 14, section 3.5.)
QUIET = -36      # every tap: key, atom, boot line
BACKGROUND = -33 # while waiting: the scan
EVENT = -29      # something happened: signed in, denied, loaded, shutter
CEREMONY = -27   # once per start: the boot
PEAK_CEILING_DB = -3


def speaker_loudness(x, window=0.1):
    """A rough measure of how loud x SOUNDS from a phone: high-pass at 300 Hz (what the small speaker plays), then the
    loudest 100 ms RMS, in dBFS. Not a standard (LUFS would add a hearing curve and gating), but enough to compare
    our own sounds with each other."""
    y = highpass(x, 300, order=2)
    w = min(n_of(window), len(y))
    rms = np.sqrt(np.convolve(y ** 2, np.ones(w) / w, 'valid'))
    return 20 * np.log10(rms.max() + 1e-12)


def finish(x, loudness_db, floor_hz=140):
    """High-pass at floor_hz FIRST: a phone speaker plays nothing below ~300 Hz, yet a 50 Hz rumble would take most of
    the peak budget. Then no DC, 4 ms fades, and the gain that brings speaker_loudness to loudness_db; if that would
    push the peak above PEAK_CEILING_DB, the peak wins (no clipping) and the sound stays a little quieter."""
    x = highpass(x, floor_hz, order=2)
    x = x - np.mean(x)
    f = n_of(0.004)
    x[:f] *= np.linspace(0, 1, f)
    x[-f:] *= np.linspace(1, 0, f)
    x = x * 10 ** ((loudness_db - speaker_loudness(x)) / 20)
    ceiling = 10 ** (PEAK_CEILING_DB / 20)
    peak = np.max(np.abs(x))
    return x * (ceiling / peak) if peak > ceiling else x


# ---------------------------------------------------------------------------------------------------------- weight

def hall_ir(seconds, tau, darkness=3_000):
    """A synthetic impulse response of a stone hall: a few early reflections, then exponentially dying noise, darker
    as it goes (high frequencies die first in a real room: here the tail is low-passed)."""
    n = n_of(seconds)
    tail = lowpass(noise(n), darkness, order=2) * exp_decay(n, tau)
    ir = np.zeros(n)
    for delay, gain in ((0.011, 0.6), (0.023, 0.45), (0.037, 0.35), (0.051, 0.25)):
        ir[n_of(delay)] += gain
    ir += tail * np.clip(np.arange(n) / n_of(0.03), 0, 1) * 0.5   # the tail grows in after the early reflections
    ir *= np.clip((n - np.arange(n)) / (0.25 * n), 0, 1)           # and fades to zero: a cut tail is heard as a stop
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x, wet, seconds=1.2, tau=0.35, darkness=3_000):
    """Convolution with hall_ir: every sample of x leaves the room's echo behind it. The result is longer than x."""
    ir = hall_ir(seconds, tau, darkness)
    w = signal.fftconvolve(x, ir)
    dry = np.pad(x, (0, len(w) - len(x)))
    return dry * (1 - wet) + w / (np.max(np.abs(w)) + 1e-12) * np.max(np.abs(x)) * wet


def modal(seconds, modes, strike=0.004, strike_cutoff=4_000):
    """Modal synthesis: a struck object rings at its own modes. modes = [(frequency, amplitude, decay tau)].
    Plus the strike itself: a few ms of filtered noise (the hammer hitting)."""
    t = t_of(seconds)
    n = len(t)
    body = sum(a * np.sin(2 * np.pi * f * t) * exp_decay(n, tau) for f, a, tau in modes)
    hit = np.zeros(n)
    k = n_of(strike)
    hit[:k] = lowpass(noise(k), strike_cutoff) * np.linspace(1, 0, k)
    return fade_out(body + hit)


# Free-free metal bar: modes at 1, 2.756, 5.404, 8.933 x the fundamental (Euler-Bernoulli beam theory).
BAR = (1.0, 2.756, 5.404, 8.933)

# Church bell (relative to the "prime"): hum 0.5, prime 1, tierce 1.2 (a MINOR third: why bells sound sombre),
# quint 1.5, nominal 2, and two higher partials. The hum rings the longest.
BELL = ((0.5, 0.55, 1.0), (1.0, 0.7, 0.6), (1.2, 0.5, 0.45), (1.5, 0.35, 0.35), (2.0, 0.6, 0.3),
        (2.5, 0.25, 0.18), (3.0, 0.2, 0.12))


def bar(f0, seconds, taus=(0.25, 0.12, 0.06, 0.03), amps=(1.0, 0.5, 0.3, 0.15)):
    return modal(seconds, [(f0 * r, a, tau) for r, a, tau in zip(BAR, amps, taus)])


def bell(prime, seconds, length=1.0):
    return modal(seconds, [(prime * r, a, tau * length) for r, a, tau in BELL], strike=0.006, strike_cutoff=2_500)


def organ(freqs, seconds):
    """A pipe-organ chord, additively: each note = harmonics 1, 2, 3, 4, 6, 8 (like organ stops 8', 4', 2 2/3', 2',
    1 1/3', 1'), slightly out of tune with each other."""
    t = t_of(seconds)
    out = np.zeros(len(t))
    for i, f in enumerate(freqs):
        for h, a in ((1, 1.0), (2, 0.55), (3, 0.3), (4, 0.25), (6, 0.12), (8, 0.08)):
            out += a * np.sin(2 * np.pi * f * h * (1 + 0.0008 * i) * t + i)
    return out


# ---------------------------------------------------------------------------------------------------------- the sounds

def boot_hum():
    """The cogitator wakes: a relay closing (a small iron bar, NOT driven), the wind of the organ starting, and an organ
    chord in D minor swelling in a stone hall. 2.4 s. The only long sound of the app.

    Version 2.0 also had a machine 'spinning up' (a driven sawtooth 55 -> 110 Hz): half its energy was below 300 Hz and
    SUSTAINED, and the phone speaker rattled. Here nothing below 250 Hz survives (floor_hz), and nothing low is driven.
    """
    d = 2.0
    n = n_of(d)
    chord = organ((146.8, 220.0, 293.7, 349.2), d) / 4                # D3 A3 D4 F4: D minor, open and dark
    chord = lowpass(chord, 1_800) * adsr(n, 0.6, 0.2, 0.85, 0.9) * 0.8
    wind = bandpass(noise(n), 600, 2_500) * adsr(n, 0.3, 0.3, 0.5, 0.9) * 0.06   # the bellows: air in the pipes
    clank = bar(370, 0.4, taus=(0.2, 0.1, 0.05, 0.02)) * 0.6
    dry = place(d, (0, clank), (0.1, wind), (0.15, chord))
    return finish(reverb(dry, 0.35, seconds=1.0, tau=0.3, darkness=2_000)[:n_of(2.4)], CEREMONY, floor_hz=250)


def boot_line():
    """A heavy typebar striking the platen: a dull 'thock' (body modes 340, 910, 2100 Hz), muffled. 0.11 s."""
    thock = modal(0.11, [(340, 1.0, 0.018), (910, 0.55, 0.01), (2_100, 0.15, 0.005)], strike=0.003, strike_cutoff=2_500)
    return finish(drive(thock, 1.8), QUIET)


def boot_done():
    """A cathedral bell (prime D4 = 293.7 Hz, hum an octave below), struck once, in the hall. 2.0 s."""
    b = bell(293.7, 1.6, length=1.2)
    return finish(reverb(lowpass(b, 3_000), 0.4, seconds=1.0, tau=0.35, darkness=1_800)[:n_of(2.0)], CEREMONY)


def key():
    """A heavy mechanical key: a dull 'thock' (modes 420 / 1130 / 2600 Hz) with a muffled contact tick. 60 ms."""
    thock = modal(0.06, [(420, 1.0, 0.011), (1_130, 0.45, 0.007), (2_600, 0.12, 0.003)], strike=0.002, strike_cutoff=3_000)
    return finish(drive(thock, 2.0), QUIET)


def signed_in():
    """Accepted: an open fifth on the organ (A2 E3 A3: no third, neither major nor minor, archaic), short, in the hall.
    0.9 s. (Version 2.0 sang 101010 with a choir first; it went: too long, and not liked.)"""
    n = n_of(0.75)
    fifth = lowpass(organ((110.0, 164.8, 220.0), 0.75) / 3, 2_200) * adsr(n, 0.04, 0.1, 0.8, 0.35)
    return finish(reverb(fifth, 0.3, seconds=0.8, tau=0.22, darkness=2_200)[:n_of(0.9)], EVENT)


def denied():
    """Refused: ONE short organ stab on a TRITONE ('diabolus in musica'), A3 + D#4: the same organ as 'accepted',
    dissonant and low. 0.3 s with its tail.

    History: v2.0 was a driven sawtooth klaxon (it rattled the speaker); v2.1 two stabs falling a semitone (approved,
    but "too long: one note, and lower"). The floor is 200 Hz, not 250: the A3 fundamental (220 Hz) stays a little,
    which is what makes it sound lower. Safe for the speaker: pure sines, not driven, and only 0.2 s long."""
    n = n_of(0.2)
    stab = lowpass(organ((220.0, 311.1), 0.2) / 2, 1_800) * adsr(n, 0.012, 0.05, 0.75, 0.08)
    return finish(reverb(stab, 0.15, seconds=0.3, tau=0.07, darkness=1_800)[:n_of(0.3)], EVENT, floor_hz=200)


def dtmf(digit, seconds):
    """A telephone key: DTMF = the sum of a ROW tone and a COLUMN tone of the keypad (Bell Labs, 1963)."""
    rows, cols = (697, 770, 852, 941), (1_209, 1_336, 1_477)
    i = "123456789*0#".index(digit)
    t = t_of(seconds)
    return (np.sin(2 * np.pi * rows[i // 3] * t) + np.sin(2 * np.pi * cols[i % 3] * t)) * adsr(len(t), 0.004, 0, 1, 0.006)


def fsk(bits_per_s, f0, f1, seconds):
    """Frequency-shift keying (V.21, 300 bit/s): each bit is a tone, f0 or f1. Random bits from the seeded RNG; the
    phase is ACCUMULATED, so switching tones never clicks (section 3.1)."""
    n = n_of(seconds)
    bits = RNG.integers(0, 2, int(seconds * bits_per_s) + 1)
    freq = np.where(np.repeat(bits, int(SR / bits_per_s))[:n] == 1, f1, f0)
    return sine(np.pad(freq, (0, n - len(freq)), mode='edge')) * adsr(n, 0.005, 0, 1, 0.01)


def scan():
    """Loading a ligand = a dial-up modem connecting, compressed to 2.6 s (it is cut the moment the molecule arrives):
    dial 4, 2 (DTMF); the 2100 Hz answer tone with its 15 Hz wobble (V.8 ANSam); two FSK bursts (V.21, one per side);
    the 'bong' of line probing (a comb of tones every 150 Hz, V.34); then the hiss of training, pulsing. All through a
    TELEPHONE band, 300-3400 Hz: what the line carried, and what a phone speaker plays well."""
    parts = [(0.00, dtmf('4', 0.08) * 0.5), (0.13, dtmf('2', 0.08) * 0.5)]
    t = t_of(0.34)
    ans = np.sin(2 * np.pi * 2_100 * t) * (1 + 0.2 * np.sin(2 * np.pi * 15 * t)) * adsr(len(t), 0.01, 0, 1, 0.02)
    parts.append((0.28, ans * 0.3))
    parts.append((0.66, fsk(300, 1_180, 980, 0.16) * 0.45))          # caller: 0 = 1180 Hz, 1 = 980 Hz
    parts.append((0.84, fsk(300, 1_850, 1_650, 0.16) * 0.35))        # answerer: 0 = 1850 Hz, 1 = 1650 Hz
    t = t_of(0.32)
    probe = sum(np.sin(2 * np.pi * 150 * k * t + k * 1.7) for k in range(2, 23) if k not in (5, 9, 14, 20)) / 12
    parts.append((1.03, probe * adsr(len(t), 0.01, 0.05, 0.8, 0.06)))
    n = n_of(1.2)
    t = t_of(1.2)
    hiss = bandpass(noise(n), 500, 3_200, order=4)
    chatter = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * 5.5 * t))      # the 'ka-king ka-king' rhythm
    parts.append((1.40, hiss * lowpass(chatter, 40) * adsr(n, 0.02, 0.1, 0.8, 0.3) * 0.5))
    line = bandpass(place(2.6, *parts), 300, 3_400, order=4)
    return finish(line, BACKGROUND, floor_hz=250)


def loaded():
    """Target acquired: a mechanism LATCHING (two clunks of a small bar), then a ping (A4) in a short hall. 0.6 s."""
    clunk = bar(520, 0.15, taus=(0.05, 0.03, 0.015, 0.008))
    n = n_of(0.45)
    ping = fade_out(lowpass(sine(const(440.0, 0.45)) + 0.2 * sine(const(880.0, 0.45)), 1_500) * exp_decay(n, 0.13)
                    * adsr(n, 0.004, 0, 1, 0))
    dry = place(0.6, (0, clunk * 0.7), (0.05, clunk * 0.45), (0.1, ping * 0.8))
    return finish(reverb(dry, 0.25, seconds=0.4, tau=0.1, darkness=1_800)[:n_of(0.6)], EVENT, floor_hz=250)


def select():
    """Tapping an atom: a short dull metal 'tock' (a small bar at 330 Hz), muffled. 0.15 s."""
    tock = bar(330, 0.15, taus=(0.05, 0.025, 0.012, 0.006), amps=(1.0, 0.35, 0.12, 0.05))
    return finish(lowpass(drive(tock, 1.5), 2_500), QUIET)


def shutter():
    """Pict-capture, like a reflex camera: the mirror slaps up and the shutter fires, two dry clicks 45 ms apart.
    Small, dense modes (no low servo any more: it rattled), almost no room. 0.2 s."""
    def click(scale, seconds):
        return modal(seconds, [(700 * scale, 0.5, 0.012), (1_250 * scale, 1.0, 0.007), (2_600 * scale, 0.35, 0.004)],
                     strike=0.002, strike_cutoff=3_000)
    dry = place(0.18, (0.0, click(1.0, 0.06)), (0.045, click(0.85, 0.08) * 0.8))
    return finish(reverb(dry, 0.08, seconds=0.2, tau=0.04, darkness=2_500)[:n_of(0.2)], EVENT, floor_hz=250)


SOUNDS = {
    "snd_boot_hum": boot_hum,
    "snd_boot_line": boot_line,
    "snd_boot_done": boot_done,
    "snd_key": key,
    "snd_signed_in": signed_in,
    "snd_denied": denied,
    "snd_scan": scan,
    "snd_loaded": loaded,
    "snd_select": select,
    "snd_shutter": shutter,
}


# ---------------------------------------------------------------------------------------------------------- output

def write_wav(path, x):
    pcm = np.clip(np.round(x * 32_767), -32_768, 32_767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def to_ogg(wav_path, ogg_path):
    """Ogg Vorbis q5, no metadata, bit-exact (without it the Ogg stream gets a random serial number every run)."""
    subprocess.run(
        ['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '5',
         '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', ogg_path],
        check=True,
    )


def speaker_share(x):
    """Share of the energy above 300 Hz: roughly what a phone's small speaker can actually play."""
    f, p = signal.welch(x, SR, nperseg=min(4096, len(x)))
    return np.sum(p[f >= 300]) / np.sum(p)


def plot(sounds, path):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    rows = len(sounds)
    fig, axes = plt.subplots(rows, 2, figsize=(12, 1.6 * rows), gridspec_kw={'width_ratios': [1, 1.3]})
    fig.patch.set_facecolor('#050806')
    for (name, x), (ax_w, ax_s) in zip(sounds.items(), axes):
        for ax in (ax_w, ax_s):
            ax.set_facecolor('#08120B')
            ax.tick_params(colors='#7A8A7E', labelsize=7)
            for s in ax.spines.values():
                s.set_color('#7A5A2E')
        t = np.arange(len(x)) / SR
        ax_w.plot(t, x, color='#43E07A', linewidth=0.6)
        ax_w.set_ylim(-1, 1)
        ax_w.set_title(name, color='#E0B65C', fontsize=9, loc='left')
        f, tt, s = signal.spectrogram(x, SR, nperseg=1024, noverlap=960)
        ax_s.pcolormesh(tt, f, 10 * np.log10(s + 1e-12), shading='auto', cmap='magma', vmin=-110, vmax=-30)
        ax_s.set_ylim(0, 3_000)
    axes[-1][0].set_xlabel('time (s)', color='#7A8A7E', fontsize=8)
    axes[-1][1].set_xlabel('time (s) / frequency 0-3 kHz', color='#7A8A7E', fontsize=8)
    fig.tight_layout()
    fig.savefig(path, dpi=110, facecolor=fig.get_facecolor())


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    default_out = os.path.normpath(os.path.join(here, '..', '..', 'app', 'src', 'main', 'res', 'raw'))
    ap = argparse.ArgumentParser()
    ap.add_argument('out', nargs='?', default=default_out)
    ap.add_argument('--wav-dir', default=None, help='also keep the WAV files here')
    ap.add_argument('--plots', default=None, help='write a waveform + spectrogram sheet (PNG)')
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    wav_dir = args.wav_dir or os.path.join(args.out, '.wav_tmp')
    os.makedirs(wav_dir, exist_ok=True)
    made = {}
    global RNG
    for name, make in SOUNDS.items():
        RNG = generator_for(name)
        x = make()
        made[name] = x
        wav = os.path.join(wav_dir, name + '.wav')
        write_wav(wav, x)
        to_ogg(wav, os.path.join(args.out, name + '.ogg'))
        print(f'{name:16s} {len(x) / SR:5.2f} s  loud {speaker_loudness(x):6.1f}  peak {20 * np.log10(np.max(np.abs(x))):6.1f} dBFS  '
              f'>300 Hz {100 * speaker_share(x):5.1f} %  {os.path.getsize(os.path.join(args.out, name + ".ogg")) / 1024:5.1f} KiB')
    if not args.wav_dir:
        for f in os.listdir(wav_dir):
            os.remove(os.path.join(wav_dir, f))
        os.rmdir(wav_dir)
    if args.plots:
        plot(made, args.plots)


if __name__ == '__main__':
    sys.exit(main())
