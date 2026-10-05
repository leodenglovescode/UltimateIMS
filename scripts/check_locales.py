#!/usr/bin/env python3
"""检查翻译键与格式占位符；不能代替设备上的字体扩展和可访问性验收。"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
resources = root / "app/src/main/res"
base = {item.attrib["name"]: item.text or "" for item in ET.parse(resources / "values/strings.xml").getroot()}
pattern = re.compile(r"%(?:[0-9]+\$)?[sd]")
for path in sorted(resources.glob("values-*/strings.xml")):
    translated = {item.attrib["name"]: item.text or "" for item in ET.parse(path).getroot()}
    if base.keys() != translated.keys():
        raise SystemExit(f"Locale keys mismatch: {path.name}")
    for key, value in translated.items():
        if sorted(pattern.findall(base[key])) != sorted(pattern.findall(value)):
            raise SystemExit(f"Locale format mismatch: {key}")
    print(f"Locale keys and format placeholders verified: {path.parent.name}, {len(base)} strings")
