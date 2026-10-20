# Plantilla para una nueva página de anime

Modelo para agregar una fuente nueva (extensions-lib 16: hosters + videos). Contiene:

| Archivo | Contenido |
|---|---|
| `TemplateSource.kt.tmpl` | Clase Kotlin completa y comentada: buscar, populares, últimos, detalles, episodios, servidores/embeds, descarga, orden por calidad y preferencias |
| `build.gradle.tmpl` | Metadatos del módulo (`extName`, `extClass`, `extVersionCode`, `libVersion = 16`) y dependencias |
| `res/mipmap-*/ic_launcher.png` | Icono provisional (cámbialo por uno propio) |

La plantilla está **fuera de `src/`** a propósito: Gradle solo carga lo que hay bajo `src/<idioma>/<fuente>`, así que no se compila ni se publica hasta que la generes.

## Crear una fuente

```bash
python scripts/new-source.py --lang es --name "Mi Anime" --base-url https://mianime.example
# opcional: --folder mianime --class-name MiAnime
```

Se crea `src/es/mianime/` con el paquete correcto (`eu.kanade.tachiyomi.animeextension.es.mianime`).
**No cambies el prefijo `eu.kanade.tachiyomi.animeextension`**: la app lo exige para detectar extensiones.

## Qué completar (busca `TODO(fuente)`)

| Método | Qué hace | Qué ajustar |
|---|---|---|
| `popularAnimeRequest/Parse`, `latestUpdatesRequest/Parse` | Listados con paginación | URL, `listItemSelector`, `nextPageSelector`, `animeFromElement` |
| `searchAnimeRequest/Parse`, `getFilterList` | Búsqueda y filtros | URL/parámetros de búsqueda; filtros opcionales |
| `animeDetailsParse` | Ficha del anime | Selectores de título, portada, sinopsis, géneros, estado |
| `episodeListParse` | Lista de episodios (más nuevo primero) | `episodeSelector`, número, nombre, fecha |
| `hosterListRequest/Parse` | Servidores/embeds del episodio | Selectores de iframes/botones |
| `getVideoList(hoster)` | Convierte un servidor en `Video`s | Conecta extractores de `/lib` por dominio; HLS (`.m3u8`) y mp4 ya resueltos |
| `downloadHosters` | Servidores de **descarga** (enlaces directos) | Selector de los enlaces de descarga |

### Descargas

La app descarga lo que devuelve `getVideoList`: mp4/mkv directos o HLS (`.m3u8`). La plantilla añade además
servidores `Descarga …` con enlaces directos. Si el enlace caduca, devuelve el `Video` con `initialized = false` y
resuélvelo en `resolveVideo`.

### Extractores listos (`/lib`)

`streamwishextractor`, `streamtapeextractor`, `okruextractor`, `filemoonextractor`, `mp4uploadextractor`,
`voeextractor`, `doodextractor`, `universalextractor`, etc. Añade `implementation(project(':lib:<nombre>'))` en `build.gradle`
y úsalo con `by lazy { XExtractor(client, headers) }`.

## Verificar

```bash
./gradlew :src:es:mianime:compileDebugKotlin     # compila
./gradlew :src:es:mianime:spotlessApply          # formatea
./gradlew :src:es:mianime:assembleDebug          # APK
```

Cada cambio posterior debe subir `extVersionCode` en `build.gradle`. Más reglas: `CONTRIBUTING.md`.
