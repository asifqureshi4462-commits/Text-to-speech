#!/usr/bin/env python3
"""Generate every logo/icon file the project needs from ONE image of YOUR logo.

Usage:
    pip install pillow numpy
    python3 tools/make_logo_assets.py path/to/your_logo.png

Input: a square PNG/WEBP/JPG, at least 1024x1024, ideally the full logo tile like the original
(rounded square on a plain, usually white, background). The script does NOT draw or invent artwork;
it only crops, resizes and re-formats your picture.

Optional:
    --mark-box x0,y0,x1,y1   Fractions (0-1) of the picture that contain the icon MARK only
                             (no text). Default 0.147,0.155,0.861,0.614 matches the shipped logo.
    --bg-threshold N         Brightness (0-255) below which the dark background is made transparent
                             when cutting out the mark (default 38). Raise it if a dark fringe remains.

Files written (paths relative to the project root):
    app/src/main/res/drawable-nodpi/brand_logo.png            full logo tile, transparent corners, 768x768
    app/src/main/res/drawable-nodpi/brand_mark.png            mark only, transparent, 512 px wide
    app/src/main/res/drawable-nodpi/ic_launcher_foreground.png  adaptive-icon foreground, 432x432
    app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png  Android 13+ themed icon silhouette, 432x432
    backend/public/logo.png, backend/public/favicon.png        web logo (256x256) and favicon (64x64)
    docs/play_store_icon_512.png                              512x512 icon for Google Play listings
Then set the launcher background colour in app/src/main/res/values/colors.xml (ic_launcher_background):
the script prints the colour it measured from your logo.
"""
import argparse, os, sys
from collections import deque
import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi')


def transparent_corners(im):
    """Make the near-white area outside the rounded tile transparent (flood fill from the edges)."""
    a = np.array(im.convert('RGB')).astype(int)
    h, w, _ = a.shape
    near = a.min(axis=2) > 225
    mask = np.zeros((h, w), bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if near[y, x] and not mask[y, x]: mask[y, x] = True; q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if near[y, x] and not mask[y, x]: mask[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and near[ny, nx] and not mask[ny, nx]:
                mask[ny, nx] = True; q.append((ny, nx))
    alpha = Image.fromarray(np.where(mask, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(max(1.0, w / 1000)))
    out = im.convert('RGBA'); out.putalpha(alpha)
    return out.crop(out.getbbox())


def cut_mark(im, box, threshold):
    w, h = im.size
    x0, y0, x1, y1 = [int(round(v)) for v in (box[0] * w, box[1] * h, box[2] * w, box[3] * h)]
    crop = im.convert('RGB').crop((x0, y0, x1, y1))
    m = np.array(crop).astype(float)
    alpha = np.clip((m.max(axis=2) - threshold) / 70.0, 0, 1)
    return Image.fromarray(np.dstack([m.astype(np.uint8), (alpha * 255).astype(np.uint8)]), 'RGBA')


def on_canvas(img, size, target_w):
    c = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    mk = img.resize((target_w, max(1, int(img.height * target_w / img.width))), Image.LANCZOS)
    c.paste(mk, ((size - mk.width) // 2, (size - mk.height) // 2), mk)
    return c


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print('wrote', os.path.relpath(path, ROOT), img.size)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('logo')
    ap.add_argument('--mark-box', default='0.147,0.155,0.861,0.614')
    ap.add_argument('--bg-threshold', type=float, default=38)
    a = ap.parse_args()
    src = Image.open(a.logo)
    if src.width != src.height: sys.exit(f'Logo must be square (yours is {src.width}x{src.height}). Crop it to a square first.')
    if src.width < 512: print('WARNING: logo is smaller than 512 px; icons will look soft. 1024x1024 or larger is recommended.')
    box = [float(v) for v in a.mark_box.split(',')]
    rgb = src.convert('RGB')

    tile = transparent_corners(rgb)
    save(tile.resize((768, 768), Image.LANCZOS), os.path.join(RES, 'brand_logo.png'))

    mark = cut_mark(rgb, box, a.bg_threshold)
    save(mark.resize((512, max(1, int(mark.height * 512 / mark.width))), Image.LANCZOS), os.path.join(RES, 'brand_mark.png'))

    # Adaptive icons: the visible area is a ~66% safe zone of the 108dp canvas, so keep the mark inside ~290/432 px.
    fg = on_canvas(mark, 432, 290)
    save(fg, os.path.join(RES, 'ic_launcher_foreground.png'))
    arr = np.array(fg).astype(float)
    # Themed (monochrome) icons need a crisp silhouette: drop the soft glow, keep only the bright shapes.
    crisp = np.clip((arr[:, :, :3].max(axis=2) - 100) / 30.0, 0, 1) * (arr[:, :, 3] / 255.0)
    alpha = (crisp * 255).astype(np.uint8)
    mono = np.dstack([np.full_like(alpha, 255)] * 3 + [alpha])
    save(Image.fromarray(mono.astype(np.uint8), 'RGBA'), os.path.join(RES, 'ic_launcher_monochrome.png'))

    web = tile.resize((256, 256), Image.LANCZOS)
    save(web, os.path.join(ROOT, 'backend', 'public', 'logo.png'))
    save(tile.resize((64, 64), Image.LANCZOS), os.path.join(ROOT, 'backend', 'public', 'favicon.png'))
    save(tile.resize((512, 512), Image.LANCZOS), os.path.join(ROOT, 'docs', 'play_store_icon_512.png'))

    px = np.array(rgb)[int(rgb.height * 0.3), int(rgb.width * 0.25)]
    print('\nMeasured background colour: #%02X%02X%02X' % tuple(int(v) for v in px))
    print('Set it as <color name="ic_launcher_background"> (and splash_bg) in app/src/main/res/values/colors.xml if your logo background differs.')


if __name__ == '__main__':
    main()
