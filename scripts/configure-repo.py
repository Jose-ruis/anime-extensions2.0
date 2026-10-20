#!/usr/bin/env python3
"""Sustituye los marcadores TU_USUARIO / TU_REPOSITORIO por los de tu repositorio de GitHub.

Uso (desde la raíz del proyecto):
    python scripts/configure-repo.py                       # autodetecta desde `git remote origin`
    python scripts/configure-repo.py --owner juan --repo mis-extensiones
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXT_SUFFIXES = {".md", ".yml", ".yaml", ".kts", ".gradle", ".json", ".txt", ".py", ".kt"}
SKIP_DIRS = {".git", "build", ".gradle", ".kotlin"}
THIS_FILE = Path(__file__).resolve()


def detect_from_git() -> tuple[str, str] | None:
    try:
        url = subprocess.check_output(["git", "-C", str(ROOT), "remote", "get-url", "origin"], text=True).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    match = re.search(r"github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?$", url)
    return (match[1], match[2]) if match else None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--owner", help="usuario u organización de GitHub")
    parser.add_argument("--repo", help="nombre del repositorio")
    args = parser.parse_args()

    owner, repo = args.owner, args.repo
    if not (owner and repo):
        detected = detect_from_git()
        if not detected:
            sys.exit("No pude detectar el remoto. Pasa --owner y --repo.")
        owner, repo = detected

    changed = 0
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.resolve() == THIS_FILE:
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts) or path.suffix not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text("utf-8")
        except UnicodeDecodeError:
            continue
        new = text.replace("TU_USUARIO", owner).replace("TU_REPOSITORIO", repo)
        if new != text:
            path.write_text(new, "utf-8")
            changed += 1
            print("actualizado", path.relative_to(ROOT))

    print(f"\nListo: {changed} archivo(s) apuntan ahora a github.com/{owner}/{repo}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
