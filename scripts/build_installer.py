#!/usr/bin/env python3
"""Construit l'installeur Windows.

1. zippe le projet Android (launcher/) dans installer/project.zip (embarqué dans l'exe) ;
2. compile l'exécutable Go (Windows 64 bits par défaut) ;
3. produit dist/Installeur-TV-Launcher.zip (exe + LISEZ-MOI.txt) et dist/SHA256SUMS.txt.

Prérequis : Python 3.8+ et Go 1.22+.   Usage : python scripts/build_installer.py [--goos windows|linux|darwin]
"""
import argparse, hashlib, os, pathlib, shutil, subprocess, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
LAUNCHER = ROOT / "launcher"
INSTALLER = ROOT / "installer"
DIST = ROOT / "dist"
SKIP_DIRS = {"build", ".gradle", ".idea", ".git"}
SKIP_FILES = {"local.properties", "local.properties.example"}


def make_project_zip():
    out = INSTALLER / "project.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for d, dirs, files in os.walk(LAUNCHER):
            dirs[:] = sorted(x for x in dirs if x not in SKIP_DIRS)
            for f in sorted(files):
                if f in SKIP_FILES:
                    continue
                p = pathlib.Path(d) / f
                arc = p.relative_to(LAUNCHER).as_posix()
                info = zipfile.ZipInfo(arc, (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o755 if arc == "gradlew" else 0o644) << 16
                z.writestr(info, p.read_bytes())
        z.writestr("README.txt", "Projet TV Launcher (Android TV).\nCompiler : installer Android Studio puis lancer compiler.bat.\n"
                   "Le fichier local.properties contient tes clés API : ne le partage pas.\n")
        z.writestr("compiler.bat", "@echo off\r\nsetlocal\r\nif exist \"%ProgramFiles%\\Android\\Android Studio\\jbr\" set JAVA_HOME=%ProgramFiles%\\Android\\Android Studio\\jbr\r\n"
                   "call gradlew.bat assembleRelease\r\necho.\r\necho APK : app\\build\\outputs\\apk\\release\\app-release.apk\r\npause\r\n")
    print("project.zip :", out.stat().st_size, "octets")


def build_exe(goos):
    DIST.mkdir(exist_ok=True)
    name = "Installeur-TV-Launcher" + (".exe" if goos == "windows" else "")
    env = dict(os.environ, GOOS=goos, GOARCH="amd64", CGO_ENABLED="0")
    flags = "-s -w" + (" -H=windowsgui" if goos == "windows" else "")
    subprocess.check_call(["go", "build", "-trimpath", "-ldflags", flags, "-o", str(DIST / name), "."], cwd=INSTALLER, env=env)
    shutil.copy(INSTALLER / "LISEZ-MOI.txt", DIST / "LISEZ-MOI.txt")
    zpath = DIST / "Installeur-TV-Launcher.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(DIST / name, name)
        z.write(DIST / "LISEZ-MOI.txt", "LISEZ-MOI.txt")
    digest = hashlib.sha256(zpath.read_bytes()).hexdigest()
    (DIST / "SHA256SUMS.txt").write_text(f"{digest}  {zpath.name}\n")
    print("SHA-256 :", digest)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--goos", default="windows", choices=["windows", "linux", "darwin"])
    a = ap.parse_args()
    make_project_zip()
    build_exe(a.goos)
