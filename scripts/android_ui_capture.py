"""Capture reproducible UI evidence from a dedicated Android emulator.

Usage: python scripts/android_ui_capture.py --serial emulator-5556 --name welcome
Optional sizing uses dp at 240dpi. Restore with adb shell wm size/density reset and
adb shell settings put system font_scale 1.0 after the verification matrix.
"""

import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--width", type=int)
    parser.add_argument("--height", type=int, default=900)
    parser.add_argument("--font", type=float)
    parser.add_argument("--output", type=Path, default=Path("build/ui-polish/screenshots"))
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Use a dedicated emulator; this helper does not alter physical devices.")
    if Path(args.name).name != args.name:
        parser.error("name must be a simple file stem")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))
    base = [str(sdk / "platform-tools/adb.exe"), "-s", args.serial]

    def adb(*command):
        return subprocess.check_output(base + list(command))

    args.output.mkdir(parents=True, exist_ok=True)
    if args.width:
        adb("shell", "wm", "density", "240")
        adb("shell", "wm", "size", f"{round(args.width * 1.5)}x{round(args.height * 1.5)}")
    if args.font is not None:
        adb("shell", "settings", "put", "system", "font_scale", str(args.font))
    # The caller waits for the intended UI state before capture; no arbitrary timing is hidden here.
    image = args.output / f"{args.name}.png"
    # UIAutomator waits for an idle accessibility tree before we capture the matching pixels.
    for attempt in range(3):
        try:
            result = adb("shell", "uiautomator", "dump", "/sdcard/zeroagent-ui.xml")
            if b"dumped to" not in result:
                raise RuntimeError("UIAutomator did not return a current hierarchy")
            break
        except (subprocess.CalledProcessError, RuntimeError) as error:
            # Android can kill the shell helper while a large app is cold-starting.
            if attempt == 2 or (isinstance(error, subprocess.CalledProcessError) and error.returncode != 137):
                raise
            time.sleep(0.75)
    (args.output / f"{args.name}.xml").write_bytes(adb("exec-out", "cat", "/sdcard/zeroagent-ui.xml"))
    image.write_bytes(adb("exec-out", "screencap", "-p"))
    metadata = {
        "serial": args.serial,
        "size": adb("shell", "wm", "size").decode().strip(),
        "density": adb("shell", "wm", "density").decode().strip(),
        "font_scale": adb("shell", "settings", "get", "system", "font_scale").decode().strip(),
        "image": str(image.resolve()),
    }
    (args.output / f"{args.name}.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps(metadata))


if __name__ == "__main__":
    main()
