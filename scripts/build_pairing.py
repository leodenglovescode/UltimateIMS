#!/usr/bin/env python3
"""从固定官方密码依赖构建 JNI；密钥协议由第一方适配器实现，算法由 BoringSSL 提供。"""
from pathlib import Path
import hashlib
import os
import subprocess
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
dependency = root / ".deps/boringssl"
revision = "ecc1358826150d6a1851c517c325b0e6c0e1b8be"
checksum = "961384159d8952274dac3ddc48ccb6c172af0c1932e18d004c8a371c7bdeb6fc"
if not (dependency / "CMakeLists.txt").exists():
    archive = root / ".deps/boringssl.tar.gz"
    archive.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(f"https://android.googlesource.com/platform/external/boringssl/+archive/{revision}.tar.gz", archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum:
        raise SystemExit("Official crypto archive checksum mismatch")
    dependency.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as source:
        source.extractall(dependency, filter="data")
sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
if not sdk:
    for line in (root / "local.properties").read_text().splitlines():
        if line.startswith("sdk.dir="): sdk = line.split("=", 1)[1]
if not sdk: raise SystemExit("Android SDK location required")
ndk = Path(sdk) / "ndk/28.1.13356709"
for abi in ["arm64-v8a", "x86_64"]:
    build = root / "app/build/pairing" / abi
    output = root / "app/build/pairing-jni" / abi
    subprocess.run(["cmake", "-S", str(root / "app/src/main/cpp"), "-B", str(build), "-G", "Ninja",
        f"-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake", f"-DANDROID_ABI={abi}",
        "-DANDROID_PLATFORM=android-33", "-DCMAKE_BUILD_TYPE=Release", f"-DBORINGSSL_DIR={dependency}",
        f"-DCMAKE_LIBRARY_OUTPUT_DIRECTORY={output}"], check=True)
    subprocess.run(["cmake", "--build", str(build), "--target", "uims_pairing", "-j", "6"], check=True)
