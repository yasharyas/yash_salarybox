"""
Generates the PWA icons from the same pixel face the app renders.

The icon is not a separate asset anyone has to keep in sync: it is the FACE
sprite from src/ui/pixel/art.ts, upscaled with nearest-neighbour so the pixels
stay square. Run this again if the sprite changes.
"""
import os
import re
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, '..', 'src', 'ui', 'pixel', 'art.ts')
OUT = os.path.join(HERE, '..', 'public', 'icons')

# Read the sprite straight out of the TypeScript so the two cannot drift.
source = open(ART, encoding='utf-8').read()
block = re.search(r'export const FACE: Sprite = \[(.*?)\];', source, re.S)
if not block:
    raise SystemExit('Could not find the FACE sprite in art.ts')
rows = re.findall(r"'([.a-z]+)'", block.group(1))
if len(rows) != 16:
    raise SystemExit('Expected 16 rows, found %d' % len(rows))

BACKGROUND = (11, 11, 15, 255)
PALETTE = {
    'o': (13, 13, 20, 255),
    'f': (207, 204, 228, 255),
    'h': (123, 120, 160, 255),
    'a': (124, 121, 255, 255),
}

# The sprite is 16px of face on a 16px canvas, which leaves no breathing room
# once a phone rounds the corners. Two pixels of padding each side gives the
# 20x20 field that iOS and Android masks expect.
PAD = 2
FIELD = 16 + PAD * 2

base = Image.new('RGBA', (FIELD, FIELD), BACKGROUND)
for y, row in enumerate(rows):
    for x, key in enumerate(row):
        if key in PALETTE:
            base.putpixel((x + PAD, y + PAD), PALETTE[key])

os.makedirs(OUT, exist_ok=True)
for size in (192, 512):
    # NEAREST, never a smooth filter: the whole point is hard pixel edges.
    base.resize((size, size), Image.NEAREST).save(os.path.join(OUT, 'icon-%d.png' % size))
    print('wrote icon-%d.png' % size)

# A maskable icon is cropped to a circle by Android, so the face needs to sit
# inside the safe zone: 80% of the width, centred.
MASK_FIELD = 26
maskable = Image.new('RGBA', (MASK_FIELD, MASK_FIELD), BACKGROUND)
offset = (MASK_FIELD - 16) // 2
for y, row in enumerate(rows):
    for x, key in enumerate(row):
        if key in PALETTE:
            maskable.putpixel((x + offset, y + offset), PALETTE[key])
maskable.resize((512, 512), Image.NEAREST).save(os.path.join(OUT, 'icon-maskable-512.png'))
print('wrote icon-maskable-512.png')

base.resize((180, 180), Image.NEAREST).save(os.path.join(OUT, 'apple-touch-icon.png'))
print('wrote apple-touch-icon.png')

base.resize((32, 32), Image.NEAREST).save(os.path.join(OUT, 'favicon-32.png'))
print('wrote favicon-32.png')
