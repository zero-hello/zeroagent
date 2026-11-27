#!/usr/bin/env python3
"""Build the vendored PRoot and its static dependencies with Android NDK r28c.

No Gradle, shell, host make, downloads, or target execution are required.
The exact commands and source hashes are written alongside the output.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

REPO = Path(__file__).resolve().parents[1]
SOURCE = REPO / "workspace/native/proot"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(ndk, output, abi, host_tools=None):
    host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
    tools = host_tools or ndk / "toolchains/llvm/prebuilt" / host / "bin"
    exe = ".exe" if os.name == "nt" else ""
    clang, readelf, strip = [tools / (name + exe) for name in ("clang", "llvm-readelf", "llvm-strip")]
    if not clang.is_file():
        raise SystemExit(f"Missing NDK clang: {clang}")
    version = (ndk / "source.properties").read_text()
    if "28.2.13676358" not in version:
        raise SystemExit("This recipe requires Android NDK 28.2.13676358 (r28c).")
    out = output / abi
    out.mkdir(parents=True, exist_ok=True)
    src = SOURCE / "proot/src"
    dep = SOURCE / "talloc"
    shmem = SOURCE / "libandroid-shmem"
    (out / "sys").mkdir(exist_ok=True)
    shutil.copyfile(shmem / "shm.h", out / "sys/shm.h")
    target = "aarch64-linux-android31" if abi == "arm64-v8a" else "x86_64-linux-android31"
    existing_prebuilt = ndk / "toolchains/llvm/prebuilt/linux-x86_64"
    sysroot = existing_prebuilt / "sysroot" if host_tools else tools.parent / "sysroot"
    resource = existing_prebuilt / "lib/clang/19" if host_tools else tools.parent / "lib/clang/19"
    driver = [str(clang), "--target=" + target, "--sysroot=" + str(sysroot), "-resource-dir=" + str(resource), "-fuse-ld=lld"]
    common = driver + ["-O2", "-fPIE", "-D_GNU_SOURCE", "-D_FILE_OFFSET_BITS=64", "-DARG_MAX=131072", "-ffile-prefix-map=" + str(SOURCE) + "=zeroagent-proot-source", "-I" + str(out), "-I" + str(src), "-I" + str(dep)]
    commands = []

    def run(args, capture=False, allow_failure=False):
        commands.append([str(a) for a in args])
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if result.returncode and not allow_failure:
            print(result.stdout, file=sys.stderr)
            raise RuntimeError(f"Command failed ({result.returncode}): {args}")
        if not capture and result.stdout:
            print(result.stdout, end="", flush=True)
        return result

    def compile_file(source, dest, extra=()):
        dest.parent.mkdir(parents=True, exist_ok=True)
        run(common + list(extra) + ["-c", str(source), "-o", str(dest)])
        return dest

    # Upstream probes only compile and link; they do not execute cross binaries.
    features = []
    for feature in ("process_vm", "seccomp_filter"):
        probe = run(common + [str(src / (".check_" + feature + ".c")), "-o", str(out / ("probe_" + feature))], capture=True, allow_failure=True)
        if probe.returncode == 0:
            features.append("#define HAVE_" + feature.upper())
    (out / "build.h").write_text('#ifndef BUILD_H\n#define BUILD_H\n#define VERSION "5.1.107.92-zeroagent"\n' + "\n".join(features) + "\n#endif\n")
    loader_objects = [compile_file(src / name, out / (name + ".o"), ("-fPIC", "-ffreestanding", "-fno-stack-protector")) for name in ("loader/loader.c", "loader/assembly.S")]
    loader = out / "libproot_loader.so"
    address = "0x2000000000" if abi == "arm64-v8a" else "0x600000000000"
    run(driver + ["-static", "-nostdlib", "-Wl,--build-id=none,--rosegment,-z,noexecstack,-Ttext=" + address, *map(str, loader_objects), "-o", str(loader)])
    makefile = (src / "GNUmakefile").read_text()
    object_block = makefile.split("OBJECTS +=", 1)[1].split("define define_from_arch.h", 1)[0]
    source_names = re.findall(r"([\w/-]+)\.o", object_block)
    extra = ['-DPROOT_UNBUNDLE_LOADER="/zeroagent-unused-loader-path"', "-DWITH_LIBANDROID_SHMEM", "-include", "string.h"]
    objects = []
    if abi == "arm64-v8a":
        symbols = run([str(readelf), "-s", str(loader)], capture=True).stdout
        offsets = {}
        for line in symbols.splitlines():
            fields = line.split()
            if fields and fields[-1] in ("_start", "pokedata_workaround"):
                offsets[fields[-1]] = int(fields[1], 16)
        offset = offsets["pokedata_workaround"] - offsets["_start"]
        info = out / "loader-info.c"
        info.write_text(f"#include <unistd.h>\nconst ssize_t offset_to_pokedata_workaround={offset};\n")
        objects.append(compile_file(info, out / "loader-info.o", extra))
    for name in source_names:
        objects.append(compile_file(src / (name + ".c"), out / (name + ".o"), extra))
    objects.append(compile_file(dep / "talloc.c", out / "talloc.o"))
    objects.append(compile_file(shmem / "shmem.c", out / "shmem.o", ("-I" + str(shmem),)))
    proot = out / "libproot_exec.so"
    run(common + [*map(str, objects), "-pie", "-llog", "-landroid", "-Wl,-z,noexecstack,-z,max-page-size=16384", "-o", str(proot)])
    run([str(strip), "--strip-unneeded", str(proot)])
    run([str(strip), "--strip-unneeded", str(loader)])
    metadata = {}
    for binary in (proot, loader):
        metadata[binary.name] = {"sha256": digest(binary), "bytes": binary.stat().st_size, "elf": run([str(readelf), "-h", "-l", "-d", str(binary)], capture=True).stdout}
    report = {"abi": abi, "ndk": version, "clang_version": run([str(clang), "--version"], capture=True).stdout, "sources": {p.relative_to(SOURCE).as_posix(): digest(p) for p in sorted(SOURCE.rglob("*")) if p.is_file()}, "commands": commands, "products": metadata, "runtime_verified": False}
    (out / "BUILD_EVIDENCE.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"Built {abi}: {proot}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--abi", choices=("arm64-v8a", "x86_64"), required=True)
    parser.add_argument("--host-tools", type=Path, help="Optional matching Windows r28c binaries; reuse the NDK's existing Linux sysroot and resources.")
    args = parser.parse_args()
    build(args.ndk.resolve(), args.output.resolve(), args.abi, args.host_tools.resolve() if args.host_tools else None)
