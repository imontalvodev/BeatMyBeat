# Quitar la voz de la pista (modo karaoke) — APARCADO

**Estado:** aparcado tras V0. No continuar por la vía DSP.
**Fecha:** 2026-07-18

## Qué se quería

En modo karaoke, cuando el usuario se pone a grabar, dejar del fichero de música
**solo la música**, sin voz. Es lo que separa un karaoke de cantar encima de la canción.

## Estrategias evaluadas

| Vía | Coste | Resultado |
|---|---|---|
| A. Cancelación de centro (DSP) | ~0 MB, tiempo real | ❌ descartada en V0 |
| B. Cancelación por bandas (DSP) | ~0 MB, tiempo real | ❌ descartada en V0 |
| C. Separación ML (htdemucs) | decenas de MB + minutos CPU | ⏸️ sin evaluar |

## Decisión de arquitectura (sigue siendo válida)

Si algún día se retoma: **offline vs tiempo real** no es lo mismo.

- **Offline (ffmpeg sobre fichero)** → genera un segundo fichero instrumental por canción.
  Duplica la biblioteca en disco. Solo compensa si el cálculo es caro (caso ML).
- **Tiempo real (`AudioProcessor` de Media3)** → DSP dentro del pipeline de ExoPlayer, vía
  `DefaultAudioSink.Builder().setAudioProcessors(...)` desde un `RenderersFactory` propio.
  Cero disco, activar/desactivar instantáneo, sin esperas.

Para DSP barato ganaba tiempo real sin discusión. Para ML habría que ir a offline con caché.

## V0 — prueba de escucha (ejecutada)

Script en `scratchpad/v0/run.sh`. Extractos de 40 s desde el minuto 1 de 5 canciones de la
biblioteca real (Du hast, Enter Sandman, I Was Made For Lovin' You, Nada que perder,
Remember the Name). Todas stereo 44.1 kHz — requisito, sobre mono no hay nada que cancelar.

Tres variantes por canción:

- **A** original (referencia)
- **B** cancelación de centro cruda: `stereotools=mlev=0.015625`
- **C** cancelación solo en banda de voz (200 Hz – 8 kHz), graves y agudos intactos

> Nota: `stereotools` **no acepta `mlev=0`**, el mínimo es `0.015625` (≈ −36 dB). Con 0 ffmpeg
> rechaza el filtro.

### Medición (energía por debajo de 200 Hz)

```
CANCION                    VARIANTE    TOTAL_dB GRAVES_dB
Du hast                    A-original     -13.1     -16.0
Du hast                    B-centro       -20.7     -25.6
Du hast                    C-bandas       -15.7     -16.9
I Was Made For Lovin' You  A-original     -14.1     -18.9
I Was Made For Lovin' You  B-centro       -27.8     -40.9
I Was Made For Lovin' You  C-bandas       -18.4     -19.6
Remember the Name          A-original      -9.4     -11.3
Remember the Name          B-centro       -24.3     -34.5
Remember the Name          C-bandas       -11.1     -11.7
```

B se lleva entre 10 y 23 dB de graves — el bajo y el bombo desaparecen, porque también están
centrados en la mezcla. C conserva los graves (~1 dB de pérdida), que era su objetivo.

### Veredicto de escucha

**Negativo, en ambas variantes.** Verificado a oído:

- La voz **sigue oyéndose**. La cancelación de centro solo elimina lo perfectamente centrado
  y en fase; la voz real lleva reverb, doblajes y coros repartidos en el estéreo, y todo eso
  sobrevive.
- La música **suena peor**. C protege los graves pero deja un agujero audible en la banda media,
  donde también viven guitarras, caja, teclados y armonías.

Es decir: pierde por los dos lados a la vez. No hay ajuste de la banda ni del `mlev` que arregle
esto — el problema es que "centrado" y "voz" no son el mismo conjunto de sonido.

## Consecuencia

**V1 y V2 quedan canceladas, no pospuestas.** No merecen otro intento; el resultado no es cuestión
de afinar parámetros.

La única vía que puede funcionar es **separación ML** (htdemucs / Demucs v4 vía ONNX Runtime o
ExecuTorch; referencia: [demixr-app](https://github.com/demixr/demixr-app), MIT, activo). Coste real:
modelo de decenas de MB sobre un APK de release ya en 136 MB, más minutos de CPU por canción,
más caché de stems en disco. No se toca hasta cerrar karaoke y pasar por beta testers.

## Detalle que afecta al diseño (independiente de la vía)

`KaraokeRecorder` graba por `MediaRecorder.AudioSource.MIC`. **Sin auriculares el micro capta el
altavoz**, así que quitar la voz de la reproducción no evita que la pista se cuele en la grabación.

Quitar voz y grabar limpio son problemas distintos y no se resuelven el uno al otro. Por eso los
auriculares siguen siendo "opcionales pero recomendados".

## Estado actual del karaoke sin esto

El modo karaoke funciona cantando **encima** de la canción original, con letra sincronizada por
línea, tono y velocidad ajustables, y grabación de tomas. Es un karaoke usable. La pista
instrumental es una mejora, no un requisito.
