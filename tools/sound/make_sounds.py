"""Synthesises the cogitator sounds of Swifty Proteins from nothing but maths (document 14).

No samples, no recordings: every sound is built from oscillators (sine, square, sawtooth), noise, filters and
envelopes, with numpy and scipy. The random noise uses a FIXED seed and the encoder runs in bit-exact mode, so running the
script again gives the SAME files, byte for byte (with the same ffmpeg version).

    python3 tools/sound/make_sounds.py [output_dir] [--wav-dir DIR] [--plots FILE]

Default output: app/src/main/res/raw/snd_*.ogg (Ogg Vorbis, mono, 44.1 kHz), encoded with ffmpeg (libvorbis).
"""
import argparse
import os
import subprocess
import sys
import wave

import numpy as np
from scipy import signal

SR = 44_100                       # samples per second (CD rate; Android plays it natively)
RNG = np.random.default_rng(42)   # fixed seed: reproducible noise (and 42, of course)


# ---------------------------------------------------------------------------------------------------------- basics

def t_of(seconds):
    """Time axis: one value per sample."""
    return np.arange(int(round(seconds * SR))) / SR


def phase_of(freq):
    """Phase of an oscillator whose frequency may CHANGE over time (glides): the running sum of frequency/SR.
    With a constant frequency this is just 2*pi*f*t; with a varying one it avoids the clicks of recomputing sin(2*pi*f(t)*t)."""
    return 2 * np.pi * np.cumsum(freq) / SR


def sine(freq):
    return np.sin(phase_of(freq))


def square(freq, softness=6):
    """A square wave with ROUNDED edges (tanh of a sine): the buzz of a square without its harsh 8-bit edge."""
    return np.tanh(softness * np.sin(phase_of(freq))) / np.tanh(softness)


def saw(freq):
    """Sawtooth: rises, drops. Rich in harmonics: good raw material for buzzes and motors (filtered afterwards)."""
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


def bandpass(x, low, high, order=4):
    b, a = signal.butter(order, [low / (SR / 2), high / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def adsr(n, attack, decay, sustain, release):
    """Attack-Decay-Sustain-Release envelope (seconds; sustain is a LEVEL 0..1), n samples long."""
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
    """e^(-t/tau): how a struck or plucked thing dies away."""
    return np.exp(-np.arange(n) / SR / tau)


def fade_out(x, seconds=0.012):
    """Ends a part at zero: a sound cut while still ringing is heard as a click (seen as a vertical line in the
    spectrogram: a sudden jump contains ALL frequencies)."""
    n = min(int(seconds * SR), len(x))
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n)
    return x


def place(total_seconds, *parts):
    """Mixes (start_seconds, samples) parts into one buffer of total_seconds."""
    out = np.zeros(int(round(total_seconds * SR)))
    for start, x in parts:
        i = int(round(start * SR))
        j = min(i + len(x), len(out))
        out[i:j] += x[: j - i]
    return out


def finish(x, peak_db):
    """Removes any DC offset, fades 4 ms in and out (a jump from/to silence is heard as a click), and normalises the
    PEAK to peak_db decibels below full scale (0 dBFS = the loudest value a file can hold)."""
    x = x - np.mean(x)
    f = int(0.004 * SR)
    x[:f] *= np.linspace(0, 1, f)
    x[-f:] *= np.linspace(1, 0, f)
    return x / np.max(np.abs(x)) * 10 ** (peak_db / 20)


# ---------------------------------------------------------------------------------------------------------- the sounds

def boot_hum():
    """CRT power-on: a mains hum (50 Hz and its harmonics) that swells while a sawtooth 'spins up' from 55 to 110 Hz,
    and the 'thunk' of the degaussing coil (a burst of low filtered noise) at the very start. 1.6 s."""
    t = t_of(1.6)
    n = len(t)
    mains = sum(sine(np.full(n, 50.0 * k)) / k for k in (1, 2, 3, 4))
    spin = lowpass(saw(np.linspace(55, 110, n)), 900) * 0.6
    body = (mains * 0.7 + spin) * adsr(n, 0.35, 0.3, 0.7, 0.6)
    thunk = lowpass(noise(int(0.25 * SR)), 180) * exp_decay(int(0.25 * SR), 0.06) * 3
    return finish(place(1.6, (0, body), (0, thunk)), -4)


def boot_line():
    """A teletype key: a short burst of bright noise plus a 1.2 kHz blip. 70 ms."""
    n = int(0.07 * SR)
    tick = bandpass(noise(n), 2_000, 6_000) * exp_decay(n, 0.008)
    blip = square(np.full(n, 1_200.0)) * adsr(n, 0.002, 0.02, 0.0, 0.0) * 0.35
    return finish(tick + blip, -10)


def fm_bell(freq, seconds, ratio=1.41, index=3.0):
    """Frequency modulation (FM): a carrier whose frequency is pushed back and forth by a modulator. A non-integer
    ratio gives the metallic partials of a bell; the index (depth) falls with time, so the strike is bright and the
    tail pure."""
    t = t_of(seconds)
    n = len(t)
    depth = index * exp_decay(n, seconds / 3)
    mod = depth * np.sin(2 * np.pi * freq * ratio * t)
    return fade_out(np.sin(2 * np.pi * freq * t + mod) * exp_decay(n, seconds / 2.5))


def boot_done():
    """'DONE': two FM bell strikes, E5 then B5 (a rising fifth: resolution, 'ready'). 0.75 s."""
    return finish(place(0.75, (0.0, fm_bell(659.3, 0.45)), (0.16, fm_bell(987.8, 0.59))), -6)


def key():
    """A relay: two tiny contact clicks 7 ms apart (contact bounce) over a short low thump. 45 ms."""
    n = int(0.045 * SR)
    click = highpass(noise(int(0.003 * SR)), 3_000)
    thump = sine(np.full(n, 120.0)) * exp_decay(n, 0.012) * 0.5
    return finish(place(0.045, (0, click), (0.007, click * 0.6), (0, thump)), -14)


def signed_in():
    """Binharic acknowledgement: the number 42 in binary, 101010, as six tones (1 = 1320 Hz, 0 = 880 Hz), then a short
    upward glide. 0.6 s. Yes, the app says '42' every time you sign in."""
    parts = []
    tone = 0.055
    for i, bit in enumerate("101010"):
        n = int(tone * SR)
        f = 1_320.0 if bit == "1" else 880.0
        parts.append((i * 0.065, lowpass(square(np.full(n, f)), 4_000) * adsr(n, 0.003, 0.01, 0.7, 0.012)))
    n = int(0.16 * SR)
    glide = sine(np.linspace(1_320, 2_640, n)) * adsr(n, 0.005, 0.03, 0.6, 0.08)
    parts.append((0.40, glide))
    return finish(place(0.6, *parts), -8)


def denied():
    """'The machine spirit is displeased': two low buzzes (filtered sawtooth with a 30 Hz tremolo), the second lower.
    0.5 s. Low and rough: 'no', without being shrill."""
    def buzz(f, seconds):
        t = t_of(seconds)
        n = len(t)
        tremolo = 0.75 + 0.25 * np.sin(2 * np.pi * 30 * t)
        return lowpass(saw(np.full(n, f)), 1_400) * tremolo * adsr(n, 0.01, 0.05, 0.8, 0.05)
    return finish(place(0.5, (0.0, buzz(150, 0.18)), (0.24, buzz(112, 0.24))), -8)


def scan():
    """The servo-skull's auspex: a sine sweeping up from 500 to 1500 Hz and back, with a 14 Hz flutter, over the soft
    whirr of a servo (band-passed noise). 1.0 s."""
    t = t_of(1.0)
    n = len(t)
    f = 1_000 + 500 * np.sin(2 * np.pi * 1.0 * t - np.pi / 2)
    beam = sine(f) * (0.8 + 0.2 * np.sin(2 * np.pi * 14 * t))
    whirr = bandpass(noise(n), 300, 1_200) * 0.35
    return finish((beam + whirr) * adsr(n, 0.08, 0.2, 0.7, 0.3), -12)


def loaded():
    """Auspex ping, like a sonar: a pure 1760 Hz tone with a long exponential tail, and a softer echo 120 ms later.
    0.7 s."""
    n = int(0.55 * SR)
    ping = fade_out(sine(np.full(n, 1_760.0)) * exp_decay(n, 0.12) * adsr(n, 0.002, 0, 1, 0))
    return finish(place(0.7, (0, ping), (0.12, ping * 0.35)), -8)


def select():
    """Tapping an atom: a soft blip gliding up a major third (2093 -> 2637 Hz, C7 -> E7). 90 ms."""
    n = int(0.09 * SR)
    return finish(sine(np.linspace(2_093, 2_637, n)) * exp_decay(n, 0.03) * adsr(n, 0.003, 0, 1, 0), -14)


def shutter():
    """Pict-capture: a shutter click, a short wind-down of a motor (sawtooth 220 -> 90 Hz), another click. 0.32 s."""
    click = highpass(noise(int(0.006 * SR)), 2_500) * exp_decay(int(0.006 * SR), 0.002)
    n = int(0.18 * SR)
    motor = lowpass(saw(np.linspace(220, 90, n)), 1_500) * adsr(n, 0.01, 0.05, 0.6, 0.06) * 0.5
    return finish(place(0.32, (0.0, click), (0.04, motor), (0.25, click * 0.8)), -9)


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
    """16-bit PCM WAV: each sample from -1..1 to an integer -32767..32767."""
    pcm = np.clip(np.round(x * 32_767), -32_768, 32_767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def to_ogg(wav_path, ogg_path):
    """Ogg Vorbis, quality 5 (variable bit rate, transparent for these sounds). Without -map_metadata -1 ffmpeg would
    write its name and version into the file; without bitexact the Ogg stream gets a RANDOM serial number, so every run
    would give different bytes for the same sound (document 14, section 6)."""
    subprocess.run(
        ['ffmpeg', '-y', '-loglevel', 'error', '-i', wav_path, '-c:a', 'libvorbis', '-q:a', '5',
         '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', ogg_path],
        check=True,
    )


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
        f, tt, s = signal.spectrogram(x, SR, nperseg=512, noverlap=448)
        ax_s.pcolormesh(tt, f, 10 * np.log10(s + 1e-12), shading='auto', cmap='magma', vmin=-110, vmax=-30)
        ax_s.set_ylim(0, 8_000)
    axes[-1][0].set_xlabel('time (s)', color='#7A8A7E', fontsize=8)
    axes[-1][1].set_xlabel('time (s) / frequency 0-8 kHz', color='#7A8A7E', fontsize=8)
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
    for name, make in SOUNDS.items():
        x = make()
        made[name] = x
        wav = os.path.join(wav_dir, name + '.wav')
        write_wav(wav, x)
        to_ogg(wav, os.path.join(args.out, name + '.ogg'))
        print(f'{name:16s} {len(x) / SR:5.2f} s  peak {20 * np.log10(np.max(np.abs(x))):6.1f} dBFS  '
              f'{os.path.getsize(os.path.join(args.out, name + ".ogg")) / 1024:5.1f} KiB')
    if not args.wav_dir:
        for f in os.listdir(wav_dir):
            os.remove(os.path.join(wav_dir, f))
        os.rmdir(wav_dir)
    if args.plots:
        plot(made, args.plots)


if __name__ == '__main__':
    sys.exit(main())
