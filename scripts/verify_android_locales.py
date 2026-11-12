"""Validate supported Android string coverage, duplicates and Java format arguments.

Run with Python 3: python scripts/verify_android_locales.py [--json REPORT_PATH]
Uses only the standard library. Locale inheritance respects Android's Chinese script matching.
"""
import argparse
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

SUPPORTED = ("zh-TW", "zh-CN", "ja", "ko", "ru", "ar")
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([bBhHsScCdoxXeEfgGaAn%])")


def format_arguments(element):
    if element.get("formatted") == "false":
        return {}
    arguments = {}
    implicit = 0
    for match in FORMAT.finditer("".join(element.itertext())):
        kind = match[2].lower()
        if kind in ("%", "n"):
            continue
        if match[1]:
            index = int(match[1])
        else:
            implicit += 1
            index = implicit
        arguments.setdefault(index, set()).add(kind)
    return {str(index): sorted(kinds) for index, kinds in arguments.items()}


def verify(resource_root):
    directories, duplicates = {}, []
    for directory in resource_root.glob("values*"):
        entries = {}
        for file in sorted(directory.glob("*.xml")):
            for element in ET.parse(file).getroot().findall("string"):
                name = element.get("name")
                if name in entries:
                    duplicates.append(f"{directory.name}/{name}")
                entries[name] = element
        directories[directory.name] = entries
    base = {name: entry for name, entry in directories["values"].items()
            if entry.get("translatable") != "false"}
    report = {"base_strings": len(base), "duplicates": duplicates, "locales": {}}
    for tag in SUPPORTED:
        language, _, region = tag.partition("-")
        # Unqualified zh is Simplified Chinese (Hans), not a Hant fallback on Android.
        entries = {} if tag == "zh-TW" else dict(directories.get(f"values-{language}", {}))
        if tag == "zh-TW":
            entries.update(directories.get("values-b+zh+Hant", {}))
        if region:
            entries.update(directories.get(f"values-{language}-r{region}", {}))
        missing, mismatches = [], []
        for name, expected in base.items():
            actual = entries.get(name)
            if actual is None:
                missing.append(name)
            elif format_arguments(expected) != format_arguments(actual):
                mismatches.append({"name": name, "expected": format_arguments(expected),
                                   "actual": format_arguments(actual)})
        report["locales"][tag] = {"missing": missing, "format_mismatches": mismatches}
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    report = verify(Path(__file__).resolve().parents[1] / "app/src/main/res")
    output = json.dumps(report, ensure_ascii=True, indent=2) + "\n"
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(output, encoding="utf-8")
    print(output, end="")
    sys.exit(bool(report["duplicates"]) or any(
        locale["missing"] or locale["format_mismatches"]
        for locale in report["locales"].values()))
