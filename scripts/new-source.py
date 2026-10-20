#!/usr/bin/env python3
"""Genera el módulo de una nueva página de anime a partir de template/new-source.

Uso:
    python scripts/new-source.py --lang es --name "Mi Anime" --base-url https://mianime.example

Crea:
    src/<lang>/<carpeta>/build.gradle
    src/<lang>/<carpeta>/res/mipmap-*/ic_launcher.png
    src/<lang>/<carpeta>/src/eu/kanade/tachiyomi/animeextension/<lang>/<carpeta>/<Clase>.kt

Después abre el .kt y completa los `TODO(fuente)`.
"""

import argparse
import re
import shutil
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "template" / "new-source"


def slugify(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", value.lower())


def class_name(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    parts = re.findall(r"[A-Za-z0-9]+", value)
    name = "".join(p[:1].upper() + p[1:] for p in parts)
    return name if name and not name[0].isdigit() else f"Source{name}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", required=True, help="código de idioma (es, en, pt, all...)")
    parser.add_argument("--name", required=True, help="nombre visible de la fuente, p. ej. 'Mi Anime'")
    parser.add_argument("--base-url", required=True, help="URL base del sitio, p. ej. https://mianime.example")
    parser.add_argument("--folder", help="nombre de carpeta/paquete (por defecto, derivado de --name)")
    parser.add_argument("--class-name", help="nombre de la clase Kotlin (por defecto, derivado de --name)")
    args = parser.parse_args()

    lang = args.lang.strip().lower()
    folder = slugify(args.folder or args.name)
    cls = args.class_name or class_name(args.name)
    base_url = args.base_url.strip().rstrip("/")

    if not re.fullmatch(r"[a-z]{2,3}(-[a-z]{2,4})?|all|other", lang) or "-" in lang:
        sys.exit("--lang debe ser un código simple como es, en, pt, all (sin guiones).")
    if not folder:
        sys.exit("No se pudo derivar un nombre de carpeta; usa --folder.")
    if not re.fullmatch(r"https?://[^\s/]+(/\S*)?", base_url):
        sys.exit("--base-url debe empezar por http:// o https://")
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", cls):
        sys.exit("--class-name no es un identificador Kotlin válido.")

    target = ROOT / "src" / lang / folder
    if target.exists():
        sys.exit(f"Ya existe {target.relative_to(ROOT)}; elige otro --folder.")

    tokens = {
        "__EXT_NAME__": args.name.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"'),
        "__CLASS_NAME__": cls,
        "__LANG__": lang,
        "__PKG_NAME__": folder,
        "__BASE_URL__": base_url,
    }

    def render(text: str) -> str:
        for key, value in tokens.items():
            text = text.replace(key, value)
        return text

    package_dir = target / "src" / "eu" / "kanade" / "tachiyomi" / "animeextension" / lang / folder
    package_dir.mkdir(parents=True)

    (target / "build.gradle").write_text(render((TEMPLATE / "build.gradle.tmpl").read_text("utf-8")), "utf-8")
    (package_dir / f"{cls}.kt").write_text(render((TEMPLATE / "TemplateSource.kt.tmpl").read_text("utf-8")), "utf-8")
    shutil.copytree(TEMPLATE / "res", target / "res")

    rel = target.relative_to(ROOT)
    print(f"Módulo creado en {rel}")
    print(f"  1. Edita {rel}/src/.../{cls}.kt y completa los TODO(fuente).")
    print(f"  2. Compila:  ./gradlew :src:{lang}:{folder}:compileDebugKotlin")
    print(f"  3. APK:      ./gradlew :src:{lang}:{folder}:assembleDebug")
    print("  4. Cambia el icono en res/mipmap-*/ic_launcher.png (usa uno propio).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
