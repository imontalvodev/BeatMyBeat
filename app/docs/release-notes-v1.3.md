# BeatMyBeat 1.3

Notas para el [GitHub Release](https://github.com/imontalvodev/BeatMyBeat/releases/new) · tag `v1.3`

---

## English

**Title:** BeatMyBeat 1.3 — Redesign, word-by-word lyrics & sleep timer

### Added

- **Word-by-word lyrics.** When LRCLIB has word-level timing, the current line lights up word by word as it is sung.
- **Sleep timer.** Moon button in the full player: stop after 5–90 minutes (with a short fade-out) or at the end of the current song.
- **Add to several playlists at once.** The "Add to playlist" screen now uses checkboxes: pick any number of playlists (and optionally a new one) and add in one go.
- **Mini player on every tab.** Download and Settings show the mini player too; tap it to open the full player.

### Changed

- **New look.** The app opens straight into your library. Tabs are now Library · Download · Settings.
- **Library:** large title with song count, Songs / Favorites / Playlists chips, sort behind an icon, and separate **Play** and **Shuffle** buttons. Empty lists and searches with no results explain what to do.
- **Multi-select:** long-press a song to get a bar with the count, "select all" and every bulk action.
- **Full player:** favorite and queue buttons, lyrics actions in a ⋮ menu, a dedicated "repeat one" icon and a clear "Find lyrics" button when a song has none.
- **Downloads keep the original quality.** M4A/AAC and OGG are copied straight from YouTube's stream with no re-encoding; MP3/FLAC/WAV are converted in a single pass.
- **Clear download errors.** Age-restricted, region-blocked, private, Premium-only and rate-limited videos now say so.
- **Synced lyrics first**, cleaner artist/album tags for YouTube Music songs, and links pasted without `https://` or from a "Mix" now work.
- **Lower memory use.** Cached album art is released when the app goes to the background, so Android is less likely to close it.

### Fixed

- **Downloads on Android 7–9** to the default `Music/BeatMyBeat/` folder failed; they now work and show up in the library.
- **Music permission on first launch (Android 13+).** The notification and music-access prompts were fired at the same time and Android dropped one, so the library stayed empty without ever asking. Both are now requested together.
- **Playback position is kept** when Android closes the app in the background, instead of going back to the start of the song.
- **Album art:** large embedded covers no longer appear corrupted, and the full player no longer shows a blurry cover.
- **Create playlist** works every time ("My playlist 2", "My playlist 3"…) and asks you for a name.
- **Capitalization:** artist and song names are no longer rewritten ("AC/DC" stays "AC/DC", "y"/"feat." stay lowercase).
- **In-app updates** download the APK directly instead of opening the GitHub page.
- **Truncated downloads** after a network hiccup, cancel not stopping a download, playlist links stopping at ~100 songs, and several untranslated texts and plurals.

### Install

1. Download `BeatMyBeat.apk` from this release.
2. Allow installation from unknown sources if prompted.
3. On Android 13+, allow notifications and music access when asked.

Updating from 1.2 keeps all your data (library, playlists, favorites, lyrics) — no need to uninstall.

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

**Título:** BeatMyBeat 1.3 — Rediseño, letra palabra por palabra y temporizador

### Novedades

- **Letra palabra por palabra.** Cuando LRCLIB tiene tiempos por palabra, la línea actual se ilumina palabra a palabra mientras se canta.
- **Temporizador de apagado.** Botón de luna en el reproductor: parar tras 5–90 minutos (con un breve fundido) o al terminar la canción actual.
- **Añadir a varias playlists a la vez.** La pantalla "Añadir a playlist" usa casillas: elige todas las playlists que quieras (y, si quieres, una nueva) y añade de una vez.
- **Mini reproductor en todas las pestañas.** Descargar y Ajustes también lo muestran; tócalo para abrir el reproductor completo.

### Cambiado

- **Nuevo diseño.** La app abre directamente tu biblioteca. Las pestañas son Biblioteca · Descargar · Ajustes.
- **Biblioteca:** título grande con el número de canciones, chips Canciones / Favoritos / Playlists, orden tras un icono y botones separados de **Reproducir** y **Aleatorio**. Las listas vacías y las búsquedas sin resultados explican qué hacer.
- **Selección múltiple:** mantén pulsada una canción para ver una barra con el recuento, "seleccionar todo" y todas las acciones.
- **Reproductor:** botones de favorito y cola, acciones de letra en un menú ⋮, icono propio para "repetir una" y botón claro de "Buscar letra" cuando una canción no la tiene.
- **Las descargas mantienen la calidad original.** M4A/AAC y OGG se copian tal cual del stream de YouTube, sin recodificar; MP3/FLAC/WAV se convierten en una sola pasada.
- **Errores de descarga claros.** Los vídeos con restricción de edad, bloqueados por región, privados, solo Premium o con límite de peticiones lo indican.
- **Letra sincronizada primero**, etiquetas de artista/álbum más limpias en canciones de YouTube Music, y funcionan los enlaces pegados sin `https://` o de un "Mix".
- **Menos consumo de memoria.** Las carátulas en caché se liberan al pasar la app a segundo plano, así Android la cierra con menos frecuencia.

### Corregido

- **Descargas en Android 7–9** a la carpeta por defecto `Music/BeatMyBeat/` fallaban; ahora funcionan y aparecen en la biblioteca.
- **Permiso de música en el primer arranque (Android 13+).** Se pedían a la vez las notificaciones y el acceso a la música, Android descartaba una petición y la biblioteca quedaba vacía sin llegar a preguntar. Ahora se piden juntas.
- **Se conserva la posición de reproducción** cuando Android cierra la app en segundo plano, en vez de volver al principio de la canción.
- **Carátulas:** las portadas grandes ya no salen corruptas y el reproductor ya no muestra la carátula borrosa.
- **Crear playlist** funciona siempre ("Mi playlist 2", "Mi playlist 3"…) y te pide un nombre.
- **Mayúsculas:** los nombres de artistas y canciones ya no se reescriben ("AC/DC" sigue siendo "AC/DC", "y"/"feat." quedan en minúscula).
- **Actualizaciones desde la app:** descargan el APK directamente en vez de abrir la página de GitHub.
- **Descargas cortadas** tras un corte de red, cancelar que no paraba la descarga, enlaces de playlist que se quedaban en ~100 canciones y varios textos y plurales sin traducir.

### Instalación

1. Descarga `BeatMyBeat.apk` de este release.
2. Permite instalar desde orígenes desconocidos si el sistema lo pide.
3. En Android 13+, permite las notificaciones y el acceso a la música cuando se soliciten.

Actualizar desde 1.2 conserva todos tus datos (biblioteca, playlists, favoritos, letras) — no hace falta desinstalar.

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
| Tag | `v1.3` |
| versionCode | `8` |
| Asset | `BeatMyBeat.apk` |

En GitHub, pega la sección **English** o **Español** (desde el título de sección hasta **Links** / **Enlaces**) en la descripción del release.

### Antes de publicar

- [ ] **Sube el APK como `BeatMyBeat.apk`**, no como `BeatMyBeat-1.3.apk`. La 1.2 instalada solo reconoce ese nombre exacto: con otro, sus usuarios verán la página del release en vez de la descarga directa. (La 1.3 acepta los dos nombres.)
- [ ] APK firmado **con el mismo keystore que 1.2** — comprobar que coinciden:
      `apksigner verify --print-certs <apk> | grep -i SHA-256`
- [ ] Actualización probada **sobre una 1.2 instalada**, no sobre una instalación limpia
- [ ] Probar una descarga en un emulador con **Android 7–9 (API 24–28)** y comprobar que aparece en la biblioteca
- [ ] Instalación limpia en Android 13+: sale el diálogo de acceso a la música y la biblioteca se llena
- [ ] SHA-256 sustituido en ambas secciones
