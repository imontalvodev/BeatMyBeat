# BeatMyBeat 1.1

Notas para el [GitHub Release](https://github.com/imontalvodev/BeatMyBeat/releases/new) · tag `v1.1`

---

## English

**Title:** BeatMyBeat 1.1 — Karaoke Mode & new look

### Added

- **Karaoke Mode.** A dedicated mode in the expanded player, available on any song with synced lyrics. The artwork collapses to give the lyrics real room, and the current line is highlighted as the song plays.
- **Pitch and speed controls.** Transpose up to ±6 semitones and play between 0.5× and 1.5× speed. The two are independent: transposing a song does not speed it up.
- **Record your takes.** Record yourself singing straight from the player. When you stop, the song pauses and your take plays back on its own — listen to it as many times as you want, then save or discard it.
- **Takes are kept per song.** Saved takes are listed for the song you recorded them on, with playback and delete.
- **Recordings go to your own music folder** as `REC-YYYY-MM-DD-HH-MM-SS.m4a`, so you can copy them off the phone from any file manager. They are automatically hidden from your BeatMyBeat library so they never show up as songs.
- **Headphones are recommended, never required.** You can record with or without them. Without headphones the mic picks up the speaker, so the song bleeds into your take — the app tells you inline, without blocking anything.
- **Storage control.** Profile shows how much space your takes use and lets you delete them all.
- **A–Z fast scroller** in the library, skipping letters with no songs.

### Changed

- **Full UI overhaul.** Rebuilt around Material 3 Expressive: square artwork with depth, a clearer type scale, one surface instead of three stacked cards, and a filled circular play button. Your colour palette and custom colours are untouched — everything remains themeable.
- **Lyrics have real space now.** The lyrics area no longer competes with the artwork for room.
- **Lyrics at song start.** During the intro you now see the first line and the one after it, instead of an undifferentiated block of text.

### Fixed

- **Lyrics: karaoke could become permanently unavailable for a song.** When LRCLIB timed out, the app silently fell back to a plain-text-only source and cached that result — so the song was stuck without synced lyrics forever. Fixed, and songs already affected recover on their own.
- **Tapping the artwork played a different song.** Touches on the expanded player fell through to the library list underneath.
- **Sliders could not be dragged**, only tapped.
- **Recording review restarted the song.** Stopping a recording rewound the track and started playing it over your take.

### Permissions

This release adds the **microphone** permission, used only for recording karaoke takes. It is requested the first time you record and never used otherwise — recording only runs with the player on screen.

### Install

1. Download `BeatMyBeat.apk` from this release.
2. Allow installation from unknown sources if prompted.
3. On Android 13+, grant media permissions when asked.

Updating from 1.0.4 keeps all your data — no need to uninstall.

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

**Título:** BeatMyBeat 1.1 — Modo Karaoke y nueva imagen

### Novedades

- **Modo Karaoke.** Un modo propio dentro del reproductor expandido, disponible en cualquier canción con letra sincronizada. La carátula se repliega para dar sitio de verdad a la letra, y la línea que suena se resalta sobre la marcha.
- **Control de tono y velocidad.** Transporta hasta ±6 semitonos y reproduce entre 0,5× y 1,5×. Son independientes: transportar una canción no la acelera.
- **Graba tus tomas.** Grábate cantando desde el propio reproductor. Al parar, la canción se pausa y tu toma suena sola — escúchala las veces que quieras y luego guárdala o descártala.
- **Las tomas se guardan por canción.** Cada grabación queda asociada a la canción en la que la hiciste, con reproducción y borrado.
- **Las grabaciones van a tu carpeta de música** con el nombre `REC-AAAA-MM-DD-HH-MM-SS.m4a`, así que puedes sacarlas del teléfono desde cualquier explorador de archivos. Se ocultan automáticamente de tu biblioteca de BeatMyBeat para que nunca aparezcan como canciones.
- **Los auriculares se recomiendan, pero no hacen falta.** Puedes grabar con o sin ellos. Sin auriculares el micro capta el altavoz y la canción se cuela en tu toma — la app te lo avisa junto al botón, sin cortarte el paso.
- **Control del espacio.** En Perfil puedes ver cuánto ocupan tus tomas y borrarlas todas.
- **Índice A–Z** en la biblioteca, saltando las letras sin canciones.

### Cambiado

- **Lavado completo de la UI.** Rehecha siguiendo Material 3 Expressive: carátula cuadrada con profundidad, jerarquía tipográfica más clara, una sola superficie en vez de tres tarjetas apiladas y botón de reproducción circular. Tu paleta y tus colores personalizados no se tocan — todo sigue siendo configurable.
- **La letra tiene sitio de verdad.** Ya no compite con la carátula por el espacio.
- **La letra al empezar la canción.** Durante la intro ahora se ven la primera frase y la siguiente, en vez de un bloque de texto indistinguible.

### Corregido

- **Letras: el karaoke podía quedar inutilizable para siempre en una canción.** Si LRCLIB no respondía a tiempo, la app caía en silencio a una fuente que solo da texto plano y guardaba ese resultado en caché — dejando esa canción sin letra sincronizada de forma permanente. Corregido, y las canciones ya afectadas se recuperan solas.
- **Tocar la carátula reproducía otra canción.** Los toques sobre el reproductor expandido llegaban a la lista de la biblioteca que había detrás.
- **Los sliders no se podían arrastrar**, solo tocar.
- **La revisión de una grabación reiniciaba la canción.** Al parar de grabar, la canción se rebobinaba y arrancaba encima de tu toma.

### Permisos

Esta versión añade el permiso de **micrófono**, usado solo para grabar tomas de karaoke. Se pide la primera vez que grabas y no se usa para nada más — la grabación solo funciona con el reproductor en pantalla.

### Instalación

1. Descarga `BeatMyBeat.apk` de este release.
2. Permite instalar desde orígenes desconocidos si el sistema lo pide.
3. En Android 13+, concede permisos de medios cuando se soliciten.

Actualizar desde 1.0.4 conserva todos tus datos — no hace falta desinstalar.

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
| Tag | `v1.1` |
| versionCode | `6` |
| Asset | `BeatMyBeat.apk` |

En GitHub, pega la sección **English** o **Español** (desde el título de sección hasta **Links** / **Enlaces**) en la descripción del release.

### Antes de publicar

- [ ] APK firmado **con el mismo keystore que 1.0.4** — comprobar que coinciden:
      `apksigner verify --print-certs <apk> | grep -i SHA-256`
- [ ] Actualización probada **sobre una 1.0.4 instalada**, no sobre una instalación limpia
- [ ] Grabar, guardar una toma y comprobar que **no aparece** en la biblioteca
- [ ] SHA-256 sustituido en ambas secciones
