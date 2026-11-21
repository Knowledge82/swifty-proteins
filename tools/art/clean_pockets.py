"""Second cleanup pass for the light background patches left inside figures (servitor, tech-priest).
A pixel is 'background-like' if it is light (L > 0.55), nearly neutral (saturation < 0.16) and not warm
(R - B < 40: parchment, bone, skin and candle wax are warmer, > 45). The patch is removed only if it is CONNECTED to
the transparent background (region growing from alpha==0), so light details INSIDE the figure stay."""
import sys
from PIL import Image
import numpy as np
from scipy import ndimage as ndi

def select(a):
    rgb = a[..., :3] / 255.0
    L = rgb @ [0.299, 0.587, 0.114]
    mx, mn = rgb.max(2), rgb.min(2)
    sat = (mx - mn) / np.maximum(mx, 1e-3)
    rb = a[..., 0].astype(int) - a[..., 2].astype(int)
    light = (a[..., 3] > 0) & (L > 0.55)
    # The generator's haze is light and a bit pinkish (R - B up to ~36); parchment, bone, skin and wax are warmer (> 45).
    edge_cand = light & (sat < 0.20) & (rb < 40)
    cand = edge_cand
    seed = ndi.binary_dilation(a[..., 3] == 0, iterations=2) & edge_cand
    connected = ndi.binary_propagation(seed, mask=edge_cand)
    # Enclosed pockets (background seen between cables, inside a halo): a light neutral blob of some size.
    lab, n = ndi.label(cand & ~connected)
    sizes = ndi.sum(np.ones_like(lab), lab, range(1, n + 1)) if n else np.array([])
    pockets = np.isin(lab, np.where(sizes >= 20)[0] + 1)
    return connected | pockets

def clean(src, dst, preview):
    im = Image.open(src).convert('RGBA'); a = np.array(im)
    sel = select(a)
    # a 2-pixel soft rim around what was removed, so no hard light edge stays
    rim = ndi.binary_dilation(sel, iterations=2) & ~sel & (a[..., 3] > 0)
    out = a.copy()
    out[sel, 3] = 0
    rgb = a[..., :3] / 255.0; L = rgb @ [0.299, 0.587, 0.114]
    lightrim = rim & (L > 0.45)
    out[lightrim, 3] = (out[lightrim, 3] * 0.35).astype(np.uint8)
    Image.fromarray(out).save(dst, 'WEBP', lossless=True)
    vis = a.copy(); vis[sel] = [255, 0, 255, 255]; vis[lightrim] = [0, 255, 255, 255]
    bg = Image.new('RGBA', im.size, (12, 40, 24, 255)); bg.alpha_composite(Image.fromarray(vis))
    bg.resize((im.width * 2, im.height * 2), Image.NEAREST).save(preview)
    print(src.split('/')[-1], 'removed', int(sel.sum()), 'softened', int(lightrim.sum()))

if __name__ == '__main__':
    clean(*sys.argv[1:4])
