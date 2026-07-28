# BeatMyBeat 1.2

Notas para el [GitHub Release](https://github.com/imontalvodev/BeatMyBeat/releases/new) · tag `v1.2`

---

## English

**Title:** BeatMyBeat 1.2 — Full-screen queue & playlist picker, no more mic

### Added

- **Playback queue screen.** "Up next" is now a dedicated full-screen view instead of a bottom sheet. Drag the handle on a track to reorder the queue (long-press, then drag up or down); the currently playing track stays pinned at the top.
- **Add to playlist screen.** Also a dedicated full-screen view now, replacing the old sheet: a search field appears once you have more than a handful of playlists, "create a new playlist" is a separate collapsible row instead of a field mixed in with the picker, and each playlist shows a small cover mosaic built from its first songs so they're no longer all identical at a glance.
- **Screen stays on** while BeatMyBeat is in the foreground — no more losing your place because the phone locked itself mid-session. This only applies while the app is actually on screen; it has no effect once you switch away or lock the phone yourself.

### Changed

- **Karaoke Mode drops voice recording.** The ability to record your own takes over a song — added in 1.1 — has been removed together with saved takes, the per-song takes list, and the storage-usage entry in Profile. Pitch/speed transposition and synced-lyrics highlighting during Karaoke Mode are untouched.

### Removed

- **Microphone permission.** BeatMyBeat no longer requests or uses `RECORD_AUDIO` — the app has no use for the microphone anymore.

### Permissions

This release **removes** the microphone permission granted in 1.1. If you had granted it, Android will drop it automatically since the app no longer declares it.

### Install

1. Download `BeatMyBeat.apk` from this release.
2. Allow installation from unknown sources if prompted.
3. On Android 13+, grant media permissions when asked.

Updating from 1.1 keeps all your data — no need to uninstall. Any saved karaoke recordings on your device are left untouched on disk; the app just no longer shows or manages them.

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

**Título:** BeatMyBeat 1.2 — Cola y selector de playlist a pantalla completa, sin micrófono

### Novedades

- **Pantalla de cola de reproducción.** "A continuación" ahora es una pantalla propia en vez de una hoja inferior. Arrastra el asa de una canción para reordenar la cola (mantén pulsado y arrastra arriba o abajo); la canción que suena queda fija arriba.
- **Pantalla de añadir a playlist.** También pasa a ser una pantalla propia en vez de la hoja anterior: aparece un buscador en cuanto tienes más de unas pocas playlists, "crear nueva playlist" es una fila colapsable separada de elegir una existente, y cada playlist muestra un pequeño mosaico de portada hecho con sus primeras canciones, así ya no se ven todas iguales de un vistazo.
- **La pantalla no se apaga** mientras BeatMyBeat está en primer plano — se acabó perder el sitio porque el móvil se bloqueó solo a media sesión. Solo aplica mientras la app está realmente en pantalla; no tiene efecto si cambias de app o bloqueas el teléfono tú mismo.

### Cambiado

- **El Modo Karaoke deja de grabar voz.** La opción de grabarte cantando sobre una canción — añadida en 1.1 — se ha eliminado junto con las tomas guardadas, el listado de tomas por canción y la entrada de espacio usado en Perfil. El control de tono/velocidad y el resaltado de letra sincronizada del Modo Karaoke no se tocan.

### Eliminado

- **Permiso de micrófono.** BeatMyBeat ya no pide ni usa `RECORD_AUDIO` — la app no tiene ningún uso para el micrófono.

### Permisos

Esta versión **elimina** el permiso de micrófono concedido en 1.1. Si lo habías concedido, Android lo retira automáticamente al dejar de declararse en la app.

### Instalación

1. Descarga `BeatMyBeat.apk` de este release.
2. Permite instalar desde orígenes desconocidos si el sistema lo pide.
3. En Android 13+, concede permisos de medios cuando se soliciten.

Actualizar desde 1.1 conserva todos tus datos — no hace falta desinstalar. Las grabaciones de karaoke que ya tuvieras guardadas se quedan tal cual en el disco del teléfono; la app simplemente deja de mostrarlas y gestionarlas.

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
| Tag | `v1.2` |
| versionCode | `7` |
| Asset | `BeatMyBeat.apk` |

En GitHub, pega la sección **English** o **Español** (desde el título de sección hasta **Links** / **Enlaces**) en la descripción del release.

### Antes de publicar

- [ ] APK firmado **con el mismo keystore que 1.1** — comprobar que coinciden:
      `apksigner verify --print-certs <apk> | grep -i SHA-256`
- [ ] Actualización probada **sobre una 1.1 instalada**, no sobre una instalación limpia
- [ ] Probar reordenar la cola arrastrando, y añadir canciones a una playlist nueva y a una existente, en dispositivo/emulador real
- [ ] Confirmar que el permiso de micrófono **no aparece** en Ajustes → Apps → BeatMyBeat → Permisos tras instalar limpio
- [ ] SHA-256 sustituido en ambas secciones
