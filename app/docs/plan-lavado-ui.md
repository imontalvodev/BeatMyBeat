# BeatMyBeat — Lavado general de UI

> Referencia de diseño: [Rhythm](https://github.com/cromaguy/Rhythm) (Material 3 Expressive, Kotlin +
> Compose, LRCLIB — el proyecto abierto más cercano a lo que hace esta app).
>
> Complementa a `[mejoras.md](mejoras.md)` (pulido visual con librerías, Fases 1–3) y a
> `[plan-bugs-karaoke.md](plan-bugs-karaoke.md)` (Fases A–G, karaoke). Este documento cubre el
> **rediseño transversal**: jerarquía tipográfica, sistema de espaciado, superficies y color.
>
> **Restricción dura:** la paleta es del usuario. `ThemeCustomizerScreen` y `ThemeProfilesStore`
> siguen mandando sobre el color — este plan no introduce colores nuevos, solo tamaño, peso,
> espaciado, forma y movimiento.

---

## Diagnóstico medido

No es una impresión: son cuentas sobre el código actual (`ui/`, 13.372 líneas).

### 1. La escala tipográfica existe pero no se usa

`Type.kt` define la escala M3 completa, hasta `displayLarge` (57sp). Reparto real de los 112 usos
de `MaterialTheme.typography` en la app:

| Rango                              | Usos    | %       |
| ---------------------------------- | ------- | ------- |
| `body*` + `label*` (11–16sp)       | **96**  | **86 %** |
| `title*` (14–22sp)                 | 16      | 14 %    |
| `headline*` (24–32sp)              | 5       | 4 %     |
| `display*` (36–57sp)               | **0**   | 0 %     |

Cuatro de los cinco `headline*` son del reproductor expandido reci&eacute;n rehecho. Antes de eso, la app
entera vivía entre 11 y 16sp. **Esa es la causa principal de que se vea densa y plana**, no el color.

### 2. El espaciado no tiene rejilla

22 valores de `dp` distintos. Los cuatro dominantes (`8`, `12`, `4`, `16` — 135 usos) sí forman una
rejilla de 4, pero conviven con `10` (20 usos), `6` (18), `18` (18), `14` (11), `20` (10), y sueltos de
`2`, `3`, `7`. Resultado: márgenes que no alinean entre pantallas.

### 3. Los radios de esquina son ruido

12 radios distintos, **7 de ellos por debajo de 20dp**: `4`, `6`, `7`, `8`, `10`, `12`, `14`, `16`, `18`,
`20`, `28`, `999`. Un `RoundedCornerShape(7.dp)` no se distingue de uno de 8 — solo impide que nada
parezca del mismo sistema.

### 4. Colores fijos que rompen la personalización — *esto es un bug, no un gusto*

14 usos de `Color.Black`/`Color.White` literales fuera de la capa de tema:

| Archivo                    | Usos | Qué son                                       |
| -------------------------- | ---- | --------------------------------------------- |
| `PlayerLibraryUi.kt`       | 7    | Fondos de tarjeta, pills y miniaturas          |
| `PlayerScreen.kt`          | 4    | Contenedores de sección y chips                |
| `PlayerMiniBar.kt`         | 2    | Contenedor de la barra + fondo de miniatura    |
| `ThemeCustomizerScreen.kt` | 1    | Texto blanco fijo                              |

> **Estado tras U2–U5: cerrado.** Los 14 usos están sustituidos por roles de `colorScheme`. El único
> `Color.White` que queda fuera de `Theme.kt` es el tirador del círculo de tono del personalizador,
> que es deliberado (ver Fase U5). Se añadieron de paso los 4 del sheet de cola en `PlayerScreen.kt`,
> que no estaban asignados a ninguna fase.

Todos son del tipo `Color.Black.copy(alpha = 0.35f)` o `Color.White.copy(alpha = 0.12f)`: asumen fondo
oscuro. `ThemeProfilesStore` guarda ARGB arbitrario elegido por el usuario y solo valida contraste
mínimo — **un perfil de fondo claro deja estos scrims oscuros encima, con el texto ilegible**. Se corrige
sustituyéndolos por `colorScheme.surface`/`surfaceVariant` con alpha, que sí siguen la paleta activa.

### 5. Lo que **no** está mal

Conviene acotar el trabajo: la navegación ya es correcta. `MainActivity` usa `Scaffold` +
`NavigationBar` M3 estándar con `NavHost`, `enableEdgeToEdge()` está activo y el proyecto va a
`targetSdk 36`. **No hay que rehacer la navegación.** El problema está dentro de cada pantalla.

---

## Qué tomamos de Rhythm y qué no

**Sí:**

- Jerarquía tipográfica agresiva: el título domina, lo secundario se aparta.
- Una superficie por zona. El reproductor tenía tres tarjetas apiladas; ahora una.
- Portada cuadrada con margen y sombra, nunca recortada a un rectángulo arbitrario.
- Acción primaria con peso visual propio (play relleno frente a prev/next planos).
- Iconografía de peso variable y formas más redondeadas en superficies grandes.
- Scroll rápido A–Z en la biblioteca — Rhythm lo tiene y aquí falta; con bibliotecas largas se nota.

**No:**

- Dynamic Color / Material You por wallpaper. Chocaría de frente con `ThemeCustomizerScreen`, que es
  una función propia y deliberada. La paleta la elige el usuario, no el fondo de pantalla.
- Layout multi-panel de tablet. Fuera del alcance actual.
- Páginas de artista/álbum y dashboard "Smart Home". Son features, no lavado de cara.

---

## Fases

Cada fase **incluye la limpieza de sus propios colores fijos y radios**, en vez de dejar un pase final
de limpieza. Motivo: son los mismos archivos que se van a reescribir de todos modos; separarlo obligaría
a tocarlos dos veces.

### Fase U0 — Tokens de diseño ✅ Completada

`ui/theme/DesignTokens.kt`: `Spacing` (4/8/12/16/24/32), `Radius` (12/18/24/28/pill) y `AppText` con
roles semánticos (`playerTitle`, `playerTitleCompact`, `playerArtist`, `trackTitle`, `trackArtist`,
`sectionHeader`, `meta`). Nombrados por función y no por escala, para que ajustar un tamaño sea un
cambio en un solo sitio.

**Pendiente de U0:** añadir `Elevation` y `Motion` (duraciones y curvas estándar) cuando la segunda
pantalla los necesite — no antes, para no inventar tokens sin caso de uso.

### Fase U1 — Reproductor expandido (piloto) ✅ Completada

`PlayerExpandedOverlay.kt`. Título 14sp → `headlineSmall` bold; artista 12sp → `titleMedium`; portada
cuadrada `aspectRatio(1f)` con radio 28dp y sombra; tres superficies → una; play como círculo relleno
de 68dp en `primary`; márgenes 16 → 24dp.

Efecto lateral: al no llevar `weight`, la portada la colapsa `AnimatedVisibility` de verdad en Modo
Karaoke — desaparece el truco del peso `0.001f` de la Fase E.

### Fase U2 — Mini reproductor ✅ Completada

`PlayerMiniBar.kt`.

1. Título y artista **separados en dos líneas** (`AppText.trackTitle` / `trackArtist`). Antes iban
   concatenados con `·` en una sola línea de `labelLarge`, sin jerarquía y compitiendo por el ancho.
2. Los 2 colores fijos fuera: contenedor a `surfaceContainerHigh`, miniatura a `surfaceVariant`.
   Ambos derivan ya del perfil del usuario en `Theme.kt`.
3. Miniatura 40 → 44dp con `Radius.sm`, coherente con la portada del expandido.
4. Progreso movido al borde inferior a 12dp de alto. **Sigue siendo un `Slider`**: se puede seguir
   buscando desde la mini barra, no se perdió funcionalidad.

**Cambio con pérdida deliberada:** se quitó la fila de tiempos (posición / duración) que ocupaba una
línea entera. El tiempo exacto está en el reproductor expandido; en una barra mini competía con el
título por el espacio vertical.

**Riesgo:** bajo. Archivo pequeño y aislado.

### Fase U3 — Biblioteca ✅ Completada

`PlayerLibraryUi.kt` + el `LazyColumn` de pistas en `PlayerScreen.kt`.

1. **`TrackRow`**: título de `bodySmall` (12sp, el peor caso de la app) → `AppText.trackTitle`
   (16sp semibold); artista → `trackArtist` (14sp). Miniatura 32 → 48dp: a 32 no se leía como
   portada. La pista en curso ahora se distingue además por el título en `primary`.
2. **Las filas dejan de ser tarjetas.** Eran una `Card` cada una: en una lista, eso es un borde y
   una superficie por fila, que es justo lo que hacía que la biblioteca se viera densa. Ahora la fila
   solo se tiñe cuando significa algo (seleccionada o sonando).
3. Los **7 colores fijos** fuera → `surfaceContainer`/`surfaceContainerHigh`/`surfaceVariant`. El velo
   de selección sobre la miniatura pasa de `Color.Black.copy(0.35f)` a `primary` con `onPrimary`
   en el icono.
4. Radios `14`/`16`/`999` → `Radius.md` / `Radius.pill`.
5. `LibraryEmptyState` con jerarquía real: icono 72 → 88dp, título a `sectionHeader`, cuerpo a
   `bodyMedium`.
6. **Scroll rápido A–Z** (`AlphabetFastScroller`), tomado de Rhythm. Táctil y arrastrable; la letra
   se deduce de la posición vertical del dedo sobre el rail y no de acertar una diana de pocos dp.
   Solo aparece con la lista ordenada por nombre (`NAME_ASC`/`NAME_DESC`): con orden por fecha las
   iniciales no son monótonas y saltar a una letra dejaría al usuario en un sitio arbitrario.

**Tests:** `ui/feature/player/AlphabetIndexTest.kt` (7 tests) sobre `buildAlphabetIndex`, extraída
como función pura justo para poder probarla — iniciales no alfabéticas a `#`, mayúsculas/minúsculas,
espacios por delante, títulos en blanco y conservación del orden en `NAME_DESC`.

**Riesgo:** medio. Es la pantalla con más estados simultáneos (selección múltiple, descarga en curso,
menús de overflow, skeletons). **Verificar cada estado, no solo la lista en reposo.**

### Fase U4 — Descargas ✅ Completada

`AnalyzeScreen.kt` + `DownloadProgressUi.kt`.

1. Filas de resultado de búsqueda y de vista previa de URL alineadas con `TrackRow` de U3: título a
   `AppText.trackTitle`, artista a `trackArtist`, duración a `meta`. Antes eran `bodyMedium`/`bodySmall`
   — el mismo contenido con dos estilos distintos según la pantalla.
2. Cabecera de la vista previa de URL: `titleSmall` → `AppText.sectionHeader`.
3. Pista en descarga (`DownloadProgressUi`) con la misma jerarquía: es la tercera pantalla donde
   aparece "título + artista" y ahora las tres coinciden.
4. Miniatura de sugerencia 54 → 56dp con `Radius.sm`; badge de fuente (YouTube / YT Music) a `pill`,
   que es la forma que le corresponde a una etiqueta.
5. Radios `6`/`10`/`12`/`14`/`20`/`28`/`999` → tokens.

**Riesgo:** bajo-medio. Dependía de U3 para no inventar un segundo estilo de fila.

### Fase U5 — Perfil y personalizador ✅ Completada

`ProfileScreen.kt` y `ThemeCustomizerScreen.kt`.

1. `ProfileOption`: etiqueta a `AppText.trackTitle`, subtítulo a `trackArtist`. Son filas de lista, así
   que usan los mismos roles que las filas de canción — la coherencia es entre *formas*, no entre
   contenidos.
2. Cabeceras del personalizador (`titleMedium`, `titleSmall`) → `AppText.sectionHeader`.
3. Radios `16`/`999` → `Radius.md` / `Radius.pill`.

**El `Color.White` del personalizador se queda, a propósito.** No es estilo: es el tirador del círculo
de tono, dibujado sobre un anillo de arcoíris a pantalla completa. Ahí el blanco es legible sobre
cualquier matiz y no depende del tema de la app — es el caso "color como dato" que este plan
distingue del "color como estilo". Tokenizarlo lo haría desaparecer sobre los amarillos.

El swatch de color conserva su `RoundedCornerShape(4.dp)`: es una muestra de 24dp, y `Radius.sm` (12dp)
la convertiría casi en un círculo. No se inventó un token para un caso único.

**Riesgo:** bajo.

### Fase U6 — Movimiento y estados de carga (prioridad baja, hacer al final)

`LoadingSkeletons.kt`, transiciones entre pantallas y `Crossfade`/`AnimatedContent` ya presentes.
Unificar duraciones y curvas en tokens de `Motion`. Al final a propósito: el movimiento se afina sobre
un layout ya estable, nunca antes.

**Riesgo:** bajo.

---

## Deuda estructural detectada (decisión aparte)

`PlayerScreen.kt` son **2.713 líneas en una sola función `@Composable`**. No es un problema visual, pero
sí encarece cada fase que lo toque: no se puede leer de una sentada, y todo su estado es local a esa
función.

Recomendación: **no** abordarlo como refactor propio. Extraer únicamente los bloques que U3/U4 vayan a
reescribir de todas formas, y solo cuando toque reescribirlos. Un refactor de 2.700 líneas sin tests de
UI (no hay Robolectric en el proyecto) es justo el tipo de cambio que rompe algo en silencio.

---

## Resumen priorizado

| Fase | Contenido                              | Complejidad | Depende de | Estado       |
| ---- | -------------------------------------- | ----------- | ---------- | ------------ |
| U0   | Tokens (`Spacing`/`Radius`/`AppText`)  | Baja        | —          | ✅ Completada |
| U1   | Reproductor expandido (piloto)         | Media       | U0         | ✅ Completada |
| U2   | Mini reproductor                       | Baja        | U0         | ✅ Completada |
| U3   | Biblioteca + scroll A–Z                | Media-Alta  | U0, U2     | ✅ Completada |
| U4   | Descargas                              | Media       | U3         | ✅ Completada |
| U5   | Perfil + personalizador                | Baja        | U0         | ✅ Completada |
| U6   | Movimiento y skeletons                 | Baja        | U2–U5      | Pendiente    |

---

## Hallazgo lateral (fuera de alcance de U3)

`PlayerLibraryUi.kt` tiene **cadenas en castellano incrustadas en el código**, no en `strings.xml`:
`"Aún no tienes playlists"`, `"Crear una playlist"`, `"Mis playlists"`. El proyecto mantiene 6 locales
(`values`, `-es`, `-en`, `-de`, `-pt`, `-hr`), así que un usuario en inglés o alemán ve estas tres
cadenas en español. No se tocó en U3 porque es un bug de localización, no de UI, y merece su propio
cambio — conviene barrer el resto de pantallas buscando más casos antes de arreglarlo.

---

## Riesgos

1. **Sin red de seguridad automática.** No hay Robolectric ni tests de UI: toda verificación es visual
   en dispositivo. Cada fase debe revisarse a ojo antes de dar la siguiente por buena.
2. **Legibilidad al quitar las tarjetas oscuras.** El contraste pasa a depender del blur y el gradiente
   de fondo. Con portadas muy claras puede quedar justo. Si ocurre, la solución es un scrim degradado
   **derivado de la paleta**, no volver a las tarjetas ni a `Color.Black`.
3. **Perfiles de tema claros.** Es el escenario que hoy rompe (punto 4 del diagnóstico) y el que hay que
   probar explícitamente en cada fase: crear un perfil de fondo claro y recorrer la pantalla.
4. **Espacio en el emulador.** `/data` al 92%; el APK debug pesa 136 MB y `adb install -r` falla con
   `INSTALL_FAILED_INSUFFICIENT_STORAGE`. Hay que resolverlo antes de la verificación visual de U2.

---

## Al cerrar este plan

Con U2–U6 terminadas, el siguiente trabajo es **cerrar `[plan-bugs-karaoke.md](plan-bugs-karaoke.md)`
con su Fase F** (grabación de voz: permiso `RECORD_AUDIO`, `MediaRecorder` en paralelo a la
reproducción, guardado y revisión de la toma). Se dejó deliberadamente para el final: es la fase más
cara del karaoke y añade pantallas nuevas, que ahora se diseñarán ya sobre la UI lavada en vez de sobre
la antigua.

---

*Documento creado — Julio 2026*
