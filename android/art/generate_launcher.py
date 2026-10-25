#!/usr/bin/env python3
"""Generate Android launcher assets from the user-provided PNG (requires Pillow)."""
from pathlib import Path

from PIL import Image, ImageChops, ImageOps

ROOT = Path(__file__).resolve().parent
RES = ROOT.parent / 'native-app/src/main/res'
source = Image.open(ROOT / 'launcher-source.png').convert('RGBA')

for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    icons = RES / f'mipmap-{density}'
    layers = RES / f'drawable-{density}'
    icons.mkdir(parents=True, exist_ok=True)
    layers.mkdir(parents=True, exist_ok=True)
    source.resize((int(48 * scale),) * 2, Image.Resampling.LANCZOS).save(icons / 'ic_launcher.png')

    # The supplied wordmark occupies about 71% of the source width. At 84dp,
    # it fits inside the adaptive icon's central 66dp safe circle.
    side = int(108 * scale)
    foreground = Image.new('RGBA', (side, side))
    inset = ImageOps.contain(source, (int(84 * scale),) * 2, Image.Resampling.LANCZOS)
    foreground.alpha_composite(inset, ((side - inset.width) // 2, (side - inset.height) // 2))
    foreground.save(layers / 'ic_launcher_foreground.png')

    # Android 13 themed icons need the lettering mask, without the black square.
    mask = ImageChops.multiply(ImageOps.grayscale(foreground), foreground.getchannel('A'))
    monochrome = Image.new('RGBA', foreground.size, 'white')
    monochrome.putalpha(mask)
    monochrome.save(layers / 'ic_launcher_monochrome.png')

print('Generated launcher icons for five densities, adaptive and themed launchers.')
