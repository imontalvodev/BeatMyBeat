# BeatMyBeat 1.0.4

Notas para el [GitHub Release](https://github.com/imontalvodev/BeatMyBeat/releases/new) · tag `v1.0.4`

---

## English

**Title:** BeatMyBeat 1.0.4

### Fixed

- **Playback:** playback errors (moved/deleted file, unsupported format) and a corrupted playback queue now show a clear message instead of silently doing nothing.
- **Downloads:** M4A tags (title/artist/cover art) are now written in the correct place inside the file — previous downloads showed no metadata in most players despite the app reporting success.
- **Downloads:** chunked downloads can no longer duplicate/corrupt the output file when the source doesn't support partial content (HTTP range) requests.
- **Library & lyrics:** fixed a few background race conditions in library sync and lyrics fetching that could show stale data or waste network requests.
- **Update check:** version comparison is now more robust against non-standard release tags.

### Security

- **In-app updates:** the downloaded APK is now verified to be BeatMyBeat's own package before installing (an unexpected release asset is rejected), and the installer no longer exposes raw `file://` URIs.

### Install

1. Download `BeatMyBeat.apk` from this release.
2. Allow installation from unknown sources if prompted.
3. On Android 13+, grant media permissions when asked.

### Checksum (SHA-256)

```
(replace after signing the APK — sha256sum BeatMyBeat.apk)
```

### Links

- [Full changelog](https://github.com/imontalvodev/BeatMyBeat/blob/main/CHANGELOG.md)
- [Privacy policy](https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md)
- [Source code](https://github.com/imontalvodev/BeatMyBeat)

---

## Español

**Título:** BeatMyBeat 1.0.4

### Corregido

- **Reproducción:** los errores de reproducción (archivo movido/borrado, formato no compatible) y una cola de reproducción corrupta ahora muestran un aviso claro en vez de no hacer nada en silencio.
- **Descargas:** las etiquetas M4A (título/artista/carátula) ahora se escriben en el sitio correcto dentro del archivo — antes las descargas no mostraban metadatos en la mayoría de reproductores pese a que la app indicaba éxito.
- **Descargas:** la descarga por fragmentos ya no puede duplicar/corromper el archivo de salida cuando el origen no soporta peticiones de rango parcial (HTTP range).
- **Biblioteca y letras:** corregidas varias condiciones de carrera en segundo plano en la sincronización de biblioteca y la descarga de letras que podían mostrar datos obsoletos o desperdiciar peticiones de red.
- **Comprobación de versión:** más robusta frente a tags de release no estándar.

### Seguridad

- **Actualizaciones in-app:** el APK descargado ahora se verifica como paquete propio de BeatMyBeat antes de instalarlo (se rechaza cualquier asset de release inesperado), y el instalador ya no expone URIs `file://` sin proteger.

### Instalación

1. Descarga `BeatMyBeat.apk` de este release.
2. Permite instalar desde orígenes desconocidas si el sistema lo pide.
3. En Android 13+, concede permisos de medios cuando se soliciten.

### Checksum (SHA-256)

```
(sustituir tras firmar el APK — sha256sum BeatMyBeat.apk)
```

### Enlaces

- [Changelog completo](https://github.com/imontalvodev/BeatMyBeat/blob/main/CHANGELOG.md)
- [Política de privacidad](https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md)
- [Código fuente](https://github.com/imontalvodev/BeatMyBeat)

---

## Publicación

| Campo | Valor |
|-------|--------|
| Tag | `v1.0.4` |
| versionCode | `5` |
| Asset | `BeatMyBeat.apk` |

En GitHub, pega la sección **English** o **Español** (desde el título de sección hasta **Links** / **Enlaces**) en la descripción del release.
