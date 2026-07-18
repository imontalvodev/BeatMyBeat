# Letras palabra a palabra — análisis de coste

**Estado:** análisis, sin implementar.
**Fecha:** 2026-07-18

## El código ya está hecho

Pintar la palabra que suena **no requiere escribir lógica nueva**:

| Pieza | Dónde |
|---|---|
| Regex `<mm:ss.xx>` (LRC enhanced) | `LrcParser.kt:26` |
| `karaokeHighlightLength()` | `LrcParser.kt:92` |
| Render del resaltado | `SyncedLyricsView.kt:129` |
| Degradado a línea si no hay datos | `PlayerExpandedOverlay.kt:545` |

Lo que falta es **el dato**, no el código.

## LRCLIB no sirve timestamps por palabra

Comprobado contra la API real (`/api/search`), contando ocurrencias de `<mm:ss` en la respuesta:

```
shape of you: 0
blinding lights: 0
despacito: 0
```

El esquema es `plainLyrics` + `syncedLyrics`, ambos a nivel de línea.

> ⚠️ Hay blogs SEO que afirman que LRCLIB soporta word-level. **Es falso.** No fiarse sin
> comprobar contra la API.

## No hay API gratis alternativa

| Fuente | Problema |
|---|---|
| Musixmatch `richsync` | endpoint no oficial, key rotativa, ToS lo prohíbe |
| NetEase `yrc` / QQ `qrc` | cifrado, catálogo chino, casi nada occidental |
| Apple Music TTML silábico | cerrado |

Ninguna es base para una app que se reparte públicamente.

## Vía viable: alineación forzada local

Se tiene el audio **y** la letra plana. Emparejar ambos es *forced alignment* — no transcripción.
El modelo empareja fonemas del audio contra el texto que ya conoces, así que la variabilidad
entre canciones la absorbe el modelo acústico; no hay heurística que ajustar por canción.

Se ejecutaría **una vez por canción al descargar**, guardando un `.lrc` enhanced en caché. El
parser ya lo consume sin tocar nada.

### Tamaño de modelo (cifras reales de Hugging Face)

**Alineadores CTC dedicados** (`mms-300m-1130-forced-aligner-ONNX`):

| Variante | Tamaño |
|---|---|
| fp32 | 1204 MB |
| **q8 (int8)** | **340 MB** |

**Whisper vía whisper.cpp** (ggml, `ggerganov/whisper.cpp`):

| Modelo | Tamaño |
|---|---|
| **tiny q5_1** | **30.7 MB** |
| tiny q8_0 | 41.5 MB |
| base q5_1 | 56.9 MB |
| base q8_0 | 78.0 MB |
| small q5_1 | 181.3 MB |

### Impacto en el APK: **0 MB**

Punto clave: **el modelo no viaja en el APK**. Se descarga bajo demanda la primera vez que el
usuario activa la función. La app ya descarga audio, la infraestructura de descarga existe.

Lo que sí entra en el APK es el runtime nativo:

| Runtime | Peso aproximado por ABI |
|---|---|
| whisper.cpp (libwhisper.so) | ~1–2 MB |
| ONNX Runtime Mobile | ~5–10 MB |

Multiplicado por las 4 ABIs de release. whisper.cpp es claramente el más ligero, y el APK ya
está en 136 MB por ffmpeg-kit.

### Impacto en rendimiento

Lo que **no** se ve afectado: la reproducción. El cálculo ocurre al descargar, en background,
nunca durante el playback. No hay riesgo de cortes de audio ni de latencia en el karaoke.

Lo que sí cuesta, por canción de ~4 min:

| Recurso | Estimación (tiny q5_1, arm64) |
|---|---|
| CPU | varios minutos, multihilo |
| RAM pico | ~150–250 MB |
| Disco (caché `.lrc`) | ~pocos KB por canción |
| Batería / temperatura | CPU sostenida varios minutos |

> ⚠️ **Estas cifras son estimaciones, no medidas.** No se han encontrado benchmarks fiables de
> whisper.cpp en Android reales. Antes de comprometerse hay que medir en dispositivo: tiempo de
> pared para una canción de 4 min y temperatura tras procesar varias seguidas.

El riesgo real no es una canción — es **descargar un álbum de 15**. Eso son potencialmente 30+
minutos de CPU al 100 %, con el teléfono caliente y la batería cayendo. Necesitaría cola en
background, límite de concurrencia, y probablemente exigir carga + WiFi (como hace la sincronización
de fotos de cualquier app seria).

## Valoración

El karaoke **ya funciona** con letra sincronizada por línea. Word-level es pulido, no requisito.

El coste no es el APK (0 MB) — es la descarga de 31 MB, los minutos de CPU por canción, y toda la
infraestructura de cola/batería/térmica que hay que construir alrededor. Para pulido, es caro.

**Recomendación: no antes de la beta.** Cerrar karaoke y UI, pasar por beta testers, y retomar esto
solo si los testers lo piden. Si se retoma, empezar midiendo whisper.cpp tiny en un dispositivo real
antes de escribir integración.
