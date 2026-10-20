<div align="center">

| Instalar en Aniyomi | Instalar en Anikku | Build |
|:-------------------:|:------------------:|:-----:|
| [![Instalar](https://img.shields.io/badge/Instalar%20repo-gray?style=flat&labelColor=red)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/Jose-ruis/anime-extensions2.0/repo/index.min.json) | [![Instalar](https://img.shields.io/badge/Instalar%20repo-gray?style=flat&labelColor=red)](https://intradeus.github.io/http-protocol-redirector/?r=anikku://add-repo?url=https://raw.githubusercontent.com/Jose-ruis/anime-extensions2.0/repo/index.min.json) | ![CI](https://github.com/Jose-ruis/anime-extensions2.0/actions/workflows/build_push.yml/badge.svg) |

</div>

# ![app icon](./.github/readme-images/app-icon.png) Extensiones de anime para Anikku/Aniyomi

Repositorio de extensiones (Kotlin + Jsoup/OkHttp) para [Anikku](https://github.com/komikku-app/anikku) y [Aniyomi](https://github.com/aniyomiorg/aniyomi).

> **Primer paso:** busca `Jose-ruis` y `anime-extensions2.0` en el proyecto y cámbialos por tus datos con
> `python scripts/configure-repo.py` (autodetecta el remoto `origin`, o usa `--owner` y `--repo`).

## Puesta en marcha en tu GitHub

1. Crea el repositorio vacío en GitHub y haz `git init`, `git add .`, `git commit`, `git remote add origin ...`, `git push -u origin master`.
2. Ejecuta `python scripts/configure-repo.py` y vuelve a hacer commit/push.
3. En **Settings → Actions → General → Workflow permissions** elige *Read and write permissions* (necesario para publicar la rama `repo`).
4. (Opcional) Firma de los APK: genera un keystore y guarda en **Settings → Secrets and variables → Actions** los secretos
   `SIGNING_KEY` (keystore en base64), `ALIAS`, `KEY_STORE_PASSWORD` y `KEY_PASSWORD`. Sin ellos se firma con la clave debug.
5. (Opcional) Secretos para extensiones concretas: `MEGACLOUD_API`, `KISSKH_API`, `KISSKH_SUB_API`, `KAISVA`, `TMDB_API`, `WYZIE_API`.
   Si no los defines se usan los valores por defecto de `PluginExtensionLegacy.kt` (`TMDB_API` y `WYZIE_API` quedan vacíos).
6. Cada push a `master` compila las extensiones modificadas y publica los APK y el índice en la rama **`repo`** de este mismo repositorio.
   **El primer push compila todas las extensiones** (tarda bastante); para probar antes, reduce los módulos en `settings.gradle.kts` (ver `AGENTS.md`).

URL del repositorio de extensiones para añadir en la app:

```
https://raw.githubusercontent.com/Jose-ruis/anime-extensions2.0/repo/index.min.json
```

## Agregar una nueva página de anime

```bash
python scripts/new-source.py --lang es --name "Mi Anime" --base-url https://mianime.example
```

Genera `src/es/mianime/` a partir de [`template/new-source`](./template/new-source) con búsqueda, populares, últimos,
detalles, lista de episodios, servidores/embeds, enlaces de descarga y preferencia de calidad. Completa los `TODO(fuente)`
y compila con `./gradlew :src:es:mianime:compileDebugKotlin`. Guía completa: [`template/new-source/README.md`](./template/new-source/README.md).

## Desarrollo

* Guía de contribución y arquitectura: [CONTRIBUTING.md](./CONTRIBUTING.md) y [AGENTS.md](./AGENTS.md).
* Compila siempre un solo módulo: `./gradlew :src:<idioma>:<fuente>:assembleDebug`.
* Requisitos: JDK 25 y Android SDK (el workflow de CI los configura solo).

## Licencia

Apache License 2.0 (ver [LICENSE](./LICENSE)). El proyecto se basa en código abierto de terceros bajo esa misma licencia;
conserva los avisos de copyright al redistribuirlo.

## Aviso legal

Este proyecto no tiene ninguna afiliación con los proveedores de contenido ni con Anikku/Aniyomi. No pidas soporte
sobre estas extensiones en los canales oficiales de Anikku/Aniyomi.
