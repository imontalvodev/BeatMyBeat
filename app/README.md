# BeatMyBeat

Aplicación **Android** de descarga y reproducción de música, construida con **Jetpack Compose** y
**Material 3**. Permite buscar/descargar audio (NewPipe Extractor + transcodificado con `ffmpeg-kit`),
gestionar la biblioteca local (MediaStore), reproducir con **Media3/ExoPlayer** en un servicio en
primer plano y mostrar letras sincronizadas (LRCLIB / lyrics.ovh).

> **Sobre el origen "Kotlin Multiplatform":** el proyecto nació de un template KMP, pero **iOS se
> descartó** y el target junto con el módulo `iosApp/` y el `commonMain` del template se eliminaron
> (ver Fase A en [`docs/optimizacion-limpieza.md`](./docs/optimizacion-limpieza.md)). Toda la app vive
> hoy en `composeApp/src/androidMain`. El plugin de Compose Multiplatform se conserva por comodidad,
> pero **no hay otros targets activos**.

## Estructura

- [`composeApp/src/androidMain`](./composeApp/src/androidMain/kotlin/com/imontalvodev/beatmybeat) — todo el código de la app:
  - `ui/feature/` — pantallas (reproductor, analizar/descargar, perfil, tema, splash).
  - `ui/network/` — clientes HTTP (unificados en `AppHttpClient`), NewPipe, letras, descarga de audio.
  - `service/`, `playback/`, `notifications/` — reproducción Media3, descargas y notificaciones.
  - `ui/data/`, `ui/storage/`, `ui/theme/`, `core/` — biblioteca, preferencias, tema y utilidades.

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
