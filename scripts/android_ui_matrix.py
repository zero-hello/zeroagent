"""Capture the welcome screen across the approved emulator sizing matrix.

Uses only app.zeroagent.debug. Configuration is restored even if capture fails.
Run after installing the candidate APK; all captures include UI XML and metadata.
"""

import os
from pathlib import Path
import subprocess
import sys


def main():
    serial = sys.argv[1] if len(sys.argv) > 1 else "emulator-5556"
    if not serial.startswith("emulator-"):
        raise SystemExit("A dedicated emulator is required")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))
    adb = [str(sdk / "platform-tools/adb.exe"), "-s", serial]
    package = "app.zeroagent.debug"

    def shell(*args):
        return subprocess.check_output(adb + ["shell", *args]).decode().strip()

    old_font = shell("settings", "get", "system", "font_scale")
    sizes = [(w, 900, f) for w in (320, 360, 412) for f in (1.0, 1.3, 2.0)]
    sizes += [(w, 900, f) for w in (840, 1200) for f in (1.0, 2.0)]
    sizes += [(840, 412, 1.0), (360, 480, 2.0)]
    try:
        for width, height, font in sizes:
            shell("am", "force-stop", package)
            shell("wm", "density", "240")
            shell("wm", "size", f"{round(width * 1.5)}x{round(height * 1.5)}")
            shell("settings", "put", "system", "font_scale", str(font))
            shell("am", "start", "-W", "-n", f"{package}/me.rerere.zeroagent.RouteActivity")
            subprocess.run([
                sys.executable, "scripts/android_ui_capture.py", "--serial", serial,
                "--name", f"matrix-{width}x{height}-font{font}",
            ], check=True)
    finally:
        shell("wm", "size", "reset")
        shell("wm", "density", "reset")
        shell("settings", "put", "system", "font_scale", old_font)


if __name__ == "__main__":
    main()
