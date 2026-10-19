"""Provision the pinned GGUF for v1.1.1 memory tests on a dedicated debug emulator.

Downloads are deliberately separate. This checks the current production model metadata,
copies a local verified file into app.zeroagent.debug, and verifies the device copy.
It never changes the production package or enables memory/voice features.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--model", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a dedicated emulator serial")
    root = Path(__file__).resolve().parents[1]
    source = (root / "app/src/main/java/me/rerere/zeroagent/data/memory/MemoryManager.kt").read_text(encoding="utf-8")
    expected_size = int(re.search(r"const val MODEL_SIZE = (\d+)L", source)[1])
    expected_sha = re.search(r'const val MODEL_SHA = "([a-f0-9]{64})"', source)[1]
    digest = hashlib.sha256()
    with args.model.open("rb") as model:
        for chunk in iter(lambda: model.read(1024 * 1024), b""):
            digest.update(chunk)
    if args.model.stat().st_size != expected_size or digest.hexdigest() != expected_sha:
        parser.error("Model does not match current pinned production metadata")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))
    adb = [str(sdk / "platform-tools/adb.exe"), "-s", args.serial]
    package = "app.zeroagent.debug"
    target = "no_backup/contextual-memory/multilingual-e5-small-q8_0.gguf"
    subprocess.run(adb + ["shell", "run-as", package, "mkdir", "-p", "no_backup/contextual-memory"], check=True)
    # adb push preserves binary bytes on Windows; shell stdin can truncate at control bytes.
    temporary = f"/data/local/tmp/zeroagent-memory-test-{os.getpid()}.gguf"
    try:
        subprocess.run(adb + ["push", str(args.model), temporary], check=True, capture_output=True)
        subprocess.run(adb + ["shell", "run-as", package, "cp", temporary, target], check=True)
    finally:
        subprocess.run(adb + ["shell", "rm", "-f", temporary], check=True)
    actual = subprocess.check_output(adb + ["shell", "run-as", package, "sha256sum", target], text=True).split()[0]
    if actual != expected_sha:
        raise RuntimeError("Device model digest differs from the verified source")
    print(json.dumps({"serial": args.serial, "package": package, "size_bytes": expected_size,
                      "sha256": actual, "device_path": target, "verified": True}, indent=2))


if __name__ == "__main__":
    main()
