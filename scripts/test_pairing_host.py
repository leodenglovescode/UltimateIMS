#!/usr/bin/env python3
"""构建并运行第一方配对 JNI 主机测试；不读取设备或配对秘密。"""
from pathlib import Path
import os, subprocess
root = Path(__file__).resolve().parents[1]
build = root / "app/build/pairing-host"
subprocess.run(["cmake","-S",str(root / "app/src/main/cpp"),"-B",str(build),"-G","Ninja","-DCMAKE_BUILD_TYPE=Release",f"-DBORINGSSL_DIR={root / '.deps/boringssl'}"],check=True)
subprocess.run(["cmake","--build",str(build),"--target","uims_pairing","-j","6"],check=True)
classes = build / "classes"
classes.mkdir(exist_ok=True)
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run([str(java/"javac"),"-d",str(classes),str(root/"app/src/main/java/io/github/leodenglovescode/ultimateims/adb/PairingCrypto.java"),str(root/"tests/pairing/PairingHostTest.java")],check=True)
subprocess.run([str(java/"java"),f"-Djava.library.path={build}","-cp",str(classes),"io.github.leodenglovescode.ultimateims.adb.PairingHostTest"],check=True)
