#!/usr/bin/env python3
"""Check drawable coverage and Android vector structure for the pinned icon set."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
manifest = json.loads((ROOT / 'third-party/material-symbols/manifest.json').read_text())
ANDROID = '{http://schemas.android.com/apk/res/android}'
drawables = ROOT / 'app/src/main/res/drawable'
vectors = {path.stem for path in drawables.glob('*.xml') if ET.parse(path).getroot().tag == 'vector'}
icons = set(manifest['icons'])
brands = set(manifest['brandAssetsPreserved'])
assert vectors == icons | brands, f'Unclassified or missing vectors: {vectors ^ (icons | brands)}'
assert not icons & brands
assert len(manifest['revision']) == 40
assert (ROOT / 'third-party/material-symbols/LICENSE').is_file()
for name in icons:
    root = ET.parse(drawables / f'{name}.xml').getroot()
    assert root.attrib[ANDROID + 'width'] == '24dp', name
    assert root.attrib[ANDROID + 'height'] == '24dp', name
    assert ANDROID + 'tint' not in root.attrib, name
    assert root.findall('path'), name
    assert all(path.attrib.get(ANDROID + 'pathData') for path in root.findall('path')), name
    assert '/materialsymbolsrounded/' in manifest['icons'][name]['source'], name
print(f'Validated {len(icons)} Material Symbols Rounded icons; {len(brands)} identified brand assets preserved.')
