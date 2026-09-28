# BeatMyBeat

Aplicación **Android** de descarga y reproducción de música, construida con **Jetpack Compose** y
**Material 3**. Permite buscar/descargar audio (NewPipe Extractor + transcodificado con `ffmpeg-kit`),
gestionar la biblioteca local (MediaStore), reproducir con **Media3/ExoPlayer** en un servicio en
primer plano y mostrar letras sincronizadas (LRCLIB / lyrics.ovh).

> **Kotlin Multiplatform:** el único target activo es Android (iOS se descartó, ver Fase A en
> [`docs/optimizacion-limpieza.md`](./docs/optimizacion-limpieza.md)), pero la lógica que no depende
> de la plataforma vive en `commonMain` y se prueba en `commonTest`. Añadir otro target (desktop JVM,
> iOS) solo exige aportar las piezas de plataforma: extractor de streams, ffmpeg, reproductor y UI.

## Estructura

- [`composeApp/src/commonMain`](./composeApp/src/commonMain/kotlin/com/imontalvodev/beatmybeat/shared) — lógica compartida (`shared/`):
  - `lyrics/` — cliente **LRCLIB** y lyrics.ovh (Ktor + kotlinx.serialization), emparejamiento de candidatos, parser LRC.
  - `download/` — elección del stream de origen, argumentos de ffmpeg y descarga por rangos con reintentos.
  - `youtube/` — parsing de enlaces y de metadatos (título, artista, álbum) de YouTube / YouTube Music.
  - `net/`, `text/` — cliente Ktor base y normalización de texto (`expect`/`actual`).
- [`composeApp/src/androidMain`](./composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat) — plataforma Android:
  - `ui/feature/` — pantallas (biblioteca, reproductor, descargar, ajustes, tema).
  - `ui/network/` — NewPipe Extractor, descargador (ffmpeg-kit), caché de letras, motor OkHttp para Ktor.
  - `service/`, `playback/`, `notifications/` — reproducción Media3, descargas y notificaciones.
  - `ui/data/`, `ui/storage/`, `ui/theme/`, `core/` — biblioteca, preferencias, tema y utilidades.

## Flujo de descarga

1. **Resolver** el vídeo (enlace, búsqueda en YouTube Music o playlist vía NewPipe).
2. **Extraer** los streams con NewPipe Extractor. Los errores se clasifican (restricción de edad,
   geobloqueo, privado, Premium, límite de YouTube…) para mostrar un mensaje útil.
3. **Elegir** el stream (`chooseSourceStream`): solo descargas directas (nunca manifiestos DASH/HLS),
   audio antes que vídeo, pista original antes que dobladas, y el códec que se pueda copiar sin
   recodificar al formato pedido (AAC → M4A/AAC, Opus → OGG).
4. **Descargar** por rangos con reintentos; un fichero incompleto nunca se procesa.
5. **Convertir** en una sola pasada de ffmpeg (copia o recodificación directa) con etiquetas y carátula.
6. **Guardar** en la carpeta configurada junto al `.meta.json` que lee el escáner.
7. **Letra**: LRCLIB con la duración real del vídeo, para usar sus endpoints de coincidencia exacta.

## Letras (LRCLIB)

Por cada combinación título × artista: `/api/get-cached` → `/api/search` → `/api/get` (solo la
principal). Se busca la letra **sincronizada**; una plana queda como reserva mientras se sigue
buscando. Las pistas marcadas como instrumentales se recuerdan. Un 429/5xx o un fallo de red no
se cachea como "sin letra".

## Compilar y ejecutar

Genera e instala la versión de depuración del APK:

```shell
./gradlew :composeApp:assembleDebug
```

En Windows: `.\gradlew.bat :composeApp:assembleDebug`.

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/optimizacion-limpieza.md`](./docs/optimizacion-limpieza.md) | Plan e historial de limpieza/optimización del código (Fases A–D). |
| [`docs/cambios.md`](./docs/cambios.md) | Histórico de features y mejoras de rendimiento implementadas. |
| [`docs/mejoras.md`](./docs/mejoras.md) | Ideas de mejora de UI (parcialmente histórico; ver banner del propio documento). |
| [`docs/riesgos-legales.md`](./docs/riesgos-legales.md) | Consideraciones de distribución (APK, F-Droid, GitHub). |

## Tecnologías clave

Jetpack Compose · Material 3 · Media3/ExoPlayer · NewPipe Extractor · ffmpeg-kit · Coil · OkHttp.

## Licencia

Distribuido bajo **GNU General Public License v3.0** (ver [`LICENSE`](./LICENSE)). Se elige GPL-3.0
por compatibilidad con **NewPipe Extractor** (GPL-3.0), del que depende la app.

## Distribución y uso responsable

> BeatMyBeat es un proyecto **gratuito y open source**, sin anuncios ni monetización. Permite buscar
> y descargar audio desde YouTube y YouTube Music en el dispositivo del usuario.
>
> La descarga de contenido de terceros puede **infringir los términos de servicio de YouTube** y la
> **legislación de propiedad intelectual** aplicable. Los desarrolladores **no alojan contenido
> protegido**; solo distribuyen el software.
>
> El **usuario es responsable** del uso que haga de la aplicación conforme a la ley y a las
> condiciones de las plataformas de origen.

Consideraciones de distribución por canal (web, GitHub, F-Droid/IzzyOnDroid, Google Play) en
[`docs/riesgos-legales.md`](./docs/riesgos-legales.md).
