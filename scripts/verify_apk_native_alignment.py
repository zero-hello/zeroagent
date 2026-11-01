"""Check actual APK ELF load segments, including libraries replaced during packaging.

python scripts/verify_apk_native_alignment.py APK [APK ...] [--json REPORT_PATH]
No Android SDK or third-party Python packages required.
"""
import argparse
import json
from pathlib import Path
import struct
import sys
import zipfile


def load_alignments(binary):
    if binary[:4] != b"\x7fELF" or binary[4] not in (1, 2) or binary[5] not in (1, 2):
        raise ValueError("Native library has an invalid ELF header")
    wide = binary[4] == 2
    endian = "<" if binary[5] == 1 else ">"
    word = "Q" if wide else "I"
    offset = struct.unpack_from(endian + word, binary, 32 if wide else 28)[0]
    size, count = struct.unpack_from(endian + "HH", binary, 54 if wide else 42)
    if size < (56 if wide else 32) or offset + size * count > len(binary):
        raise ValueError("Native library has an invalid program header table")
    return [struct.unpack_from(endian + word, binary, offset + i * size + (48 if wide else 28))[0]
            for i in range(count)
            if struct.unpack_from(endian + "I", binary, offset + i * size)[0] == 1]


def verify(apk):
    libraries = []
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not (name.startswith("lib/") and name.endswith(".so")):
                continue
            alignments = load_alignments(archive.read(name))
            libraries.append({"library": name, "load_alignment": alignments,
                              "aligned_16kb": bool(alignments) and all(a >= 16384 for a in alignments)})
    return {"apk": apk.name, "libraries": libraries,
            "passed": bool(libraries) and all(lib["aligned_16kb"] for lib in libraries)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", nargs="+", type=Path)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    reports = [verify(apk) for apk in args.apks]
    for report in reports:
        print(f"{report['apk']}: {len(report['libraries'])} native libraries, passed={report['passed']}")
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    sys.exit(not all(report["passed"] for report in reports))
