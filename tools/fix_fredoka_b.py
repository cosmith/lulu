# Usage: pip install fonttools && python tools/fix_fredoka_b.py app/src/main/res/font/fredoka_*.ttf
"""Close the gap between the middle bar and the stem of Fredoka's capital B.

Fredoka draws B with a middle bar that stops short of the stem, so at heavy weights
the two counters merge. Children learning letters need the usual shape: this adds a
filled rectangle (same direction as the outer contour) bridging the gap.
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.pens.recordingPen import RecordingPen

for path in sys.argv[1:]:
    font = TTFont(path)
    glyf = font["glyf"]
    name = font.getBestCmap()[ord("B")]
    glyph = glyf[name]
    coords, ends, _ = glyph.getCoordinates(glyf)
    assert len(ends) == 2, f"{path}: B already patched or unexpected shape"
    counter = list(coords[ends[0] + 1:ends[1] + 1])
    stem_inner = min(x for x, _ in counter)
    # The bar's left tip: the leftmost point of the counter away from the stem.
    tip = [(x, y) for x, y in counter if x > stem_inner + 20]
    tip_x = min(x for x, _ in tip)
    tip_ys = [y for x, y in counter if abs(x - tip_x) < 1]
    mid = sum(tip_ys) / len(tip_ys)
    # Bar edges: the horizontal runs of the counter (several points on one y) next to the tip.
    from collections import Counter
    runs = Counter(y for x, y in counter if tip_x <= x <= tip_x + 110)
    flat = [y for y, n in runs.items() if n >= 2]
    bottom = max(y for y in flat if y < mid)
    top = min(y for y in flat if y > mid)
    rec = RecordingPen()
    glyph.draw(rec, glyf)
    pen = TTGlyphPen(font.getGlyphSet())
    rec.replay(pen)
    left, right = stem_inner - 10, tip_x + 40
    pen.moveTo((left, bottom)); pen.lineTo((left, top)); pen.lineTo((right, top)); pen.lineTo((right, bottom)); pen.closePath()
    glyf[name] = pen.glyph()
    font.save(path)
    print(f"{path}: bar {bottom}..{top}, bridge x {left}..{right}")
