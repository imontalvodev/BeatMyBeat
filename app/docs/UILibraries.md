# 🎵 BeatMyBeat — Librerías UI para KMP/Android

> Guía de referencia para mejorar la interfaz de BeatMyBeat: descargador y reproductor de música local, con soporte de YouTube/YT Music, biblioteca offline y personalización de temas.

---

## Stack base recomendado

```
Compose Multiplatform
    ├── Material 3 (tema base + componentes)
    ├── Palette API (tema dinámico por artwork)
    ├── Coil (carga de imágenes/artwork)
    ├── Lottie (animaciones de estados)
    ├── Shimmer (loading skeletons)
    └── Accompanist (permisos + system UI)
```

---

## 1. Compose Multiplatform

**Repositorio:** [github.com/JetBrains/compose-multiplatform](https://github.com/JetBrains/compose-multiplatform)

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}
```

### ✅ Pros
- Base oficial de JetBrains para KMP: máxima compatibilidad y soporte a largo plazo.
- Permite compartir UI entre Android, iOS, Desktop y Web desde una sola codebase.
- Acceso completo al ecosistema Jetpack Compose en Android.
- Hot reload y preview en Android Studio.
- Comunidad grande y creciente con abundante documentación.

### ❌ Contras
- El soporte iOS todavía está en fase **Beta** — puede haber bugs o APIs faltantes.
- Curva de aprendizaje si el proyecto aún usa Views/XML.
- Mayor tamaño del APK comparado con una implementación nativa pura.
- Algunas APIs de Android específicas (ej. `MediaStyle` notifications) requieren código `expect/actual`.

---

## 2. Material 3 (Material You)

**Repositorio:** [github.com/material-components/material-components-android](https://github.com/material-components/material-components-android)

```kotlin
implementation("androidx.compose.material3:material3:<version>")
```

### ✅ Pros
- Soporte nativo para **Dynamic Color** (Android 12+): la paleta de la app se adapta al wallpaper del usuario.
- Componentes listos para el reproductor: `Slider`, `BottomSheet`, `NavigationBar`, `Card`.
- Sistema de tipografía y espaciado consistente desde el primer día.
- Accesibilidad incorporada (contrast ratios, touch targets).
- Mantenido por Google, actualizaciones frecuentes.

### ❌ Contras
- Dynamic Color solo disponible en **Android 12+**, los dispositivos anteriores reciben un fallback estático.
- La curva de migración desde Material 2 puede ser significativa (cambios en tokens de color y forma).
- Algunos componentes aún están marcados como `@ExperimentalMaterial3Api` y pueden cambiar su API.
- El estilo "Google" puede sentirse genérico si no se personaliza correctamente.

---

## 3. Palette API

**Repositorio:** [developer.android.com/reference/androidx/palette](https://developer.android.com/reference/androidx/palette/graphics/Palette)

```kotlin
implementation("androidx.palette:palette-ktx:1.0.0")
```

### ✅ Pros
- Extrae automáticamente la paleta de colores del artwork de la canción activa.
- Permite crear un **tema dinámico por canción** (fondo, accent, texto) — efecto premium similar a Spotify/Apple Music.
- API simple y bien documentada.
- Muy ligera (~30 KB), sin dependencias pesadas.
- Compatible con Coil: se puede encadenar extracción al momento de cargar la imagen.

### ❌ Contras
- Proceso de extracción es **asíncrono** y puede causar un ligero flash de color si no se gestiona bien.
- La calidad de los colores extraídos depende del artwork: imágenes monocromáticas dan paletas pobres.
- No hay integración directa con `MaterialTheme` de Compose — requiere lógica custom para aplicar los colores al tema.
- Artwork de baja resolución puede producir resultados imprecisos.

---

## 4. Coil

**Repositorio:** [github.com/coil-kt/coil](https://github.com/coil-kt/coil)

```kotlin
implementation("io.coil-kt:coil-compose:<version>")
```

### ✅ Pros
- Primera opción para carga de imágenes en proyectos Kotlin/Compose: escrito 100% en Kotlin con coroutines.
- Cache en memoria y disco automático — importante para una biblioteca con muchos artworks locales.
- Transformaciones built-in: `BlurTransformation`, `RoundedCornersTransformation`, `CircleCropTransformation`.
- Soporte para imágenes locales (file://), URLs y assets sin configuración extra.
- Integración directa con `AsyncImage` en Compose.

### ❌ Contras
- Para KMP (iOS/Desktop), se requiere `coil3` que aún está en desarrollo activo y puede tener breaking changes.
- Menos opciones de transformación avanzada comparado con Glide (aunque cubre el 90% de los casos).
- El sistema de cache puede consumir bastante almacenamiento si no se configura con límites.
- Soporte para GIFs animados es más limitado que en Glide.

---

## 5. Lottie Compose

**Repositorio:** [github.com/airbnb/lottie-android](https://github.com/airbnb/lottie-android)

```kotlin
implementation("com.airbnb.android:lottie-compose:<version>")
```

### ✅ Pros
- Animaciones vectoriales de alta calidad basadas en archivos JSON de After Effects.
- Ideal para: animaciones de descarga con progreso, visualizador de ecualizador, estados vacíos de biblioteca.
- Los archivos `.json` son muy ligeros comparados con GIFs o videos.
- Repositorio [LottieFiles](https://lottiefiles.com) con miles de animaciones gratuitas listas para usar.
- Control preciso del playback: `speed`, `progress`, `loop`, `clipSpec`.

### ❌ Contras
- **No es KMP puro** — solo disponible para Android/iOS por separado, no compartido.
- Algunos efectos de After Effects no se exportan correctamente a Lottie (gradientes, blur, etc.).
- Para animaciones muy complejas, el renderizado puede impactar performance en dispositivos de gama baja.
- Añade ~1.5 MB al APK final.
- Requiere diseñador o acceso a recursos pre-hechos; crear animaciones propias tiene curva de aprendizaje.

---

## 6. Accompanist

**Repositorio:** [github.com/google/accompanist](https://github.com/google/accompanist)

```kotlin
// Permisos en Compose
implementation("com.google.accompanist:accompanist-permissions:<version>")

// Control de System UI (statusbar/navigationbar)
implementation("com.google.accompanist:accompanist-systemuicontroller:<version>")
```

### ✅ Pros
- `accompanist-permissions`: manejo declarativo de permisos en Compose — esencial para acceso al almacenamiento.
- `accompanist-systemuicontroller`: permite hacer la statusbar transparente para el full-screen player.
- Mantenido por Google, muy bien integrado con Compose.
- Modular: se instalan solo los módulos que se necesitan.

### ❌ Contras
- Varios módulos han sido **deprecados** a medida que Jetpack Compose los absorbe nativamente (ej. `Pager`, `FlowLayout`).
- `systemuicontroller` tiene comportamiento inconsistente en algunos OEMs Android (Samsung, Xiaomi).
- La versión de `accompanist-permissions` no cubre todos los edge cases de permisos en Android 13+ (necesita lógica adicional).
- El proyecto está en modo mantenimiento para algunos módulos — verificar estado antes de adoptar.

---

## 7. Compose Shimmer

**Repositorio:** [github.com/valentinilk/compose-shimmer](https://github.com/valentinilk/compose-shimmer)

```kotlin
implementation("com.valentinilk.shimmer:compose-shimmer:<version>")
```

### ✅ Pros
- Loading skeletons con efecto shimmer en una sola línea: `Modifier.shimmer()`.
- Compatible con cualquier composable — se aplica como modificador, sin reestructurar el layout.
- Animación sincronizada entre múltiples skeletons en pantalla (efecto cohesivo).
- Muy ligera (~20 KB) y sin dependencias adicionales.
- Perfecto para carga inicial de la biblioteca y metadatos de canciones.

### ❌ Contras
- Librería de terceros pequeña, sin respaldo de una empresa grande — riesgo de abandono.
- Poca flexibilidad para personalizar la dirección o velocidad del shimmer sin profundizar en la API interna.
- Para KMP no-Android, requiere implementación separada o alternativa.
- No incluye composables de skeleton pre-hechos — hay que construir los shapes manualmente.

---

## Comparativa rápida

| Librería | KMP Support | Tamaño | Dificultad | Impacto Visual |
|---|---|---|---|---|
| Compose Multiplatform | ✅ Completo | Grande | Alta | ⭐⭐⭐⭐⭐ |
| Material 3 | ✅ Android/iOS | Mediano | Media | ⭐⭐⭐⭐⭐ |
| Palette API | ⚠️ Android only | Mínimo | Baja | ⭐⭐⭐⭐ |
| Coil | ⚠️ Parcial (coil3) | Pequeño | Baja | ⭐⭐⭐ |
| Lottie | ⚠️ Android/iOS sep. | Mediano | Media | ⭐⭐⭐⭐ |
| Accompanist | ⚠️ Android only | Mínimo | Baja | ⭐⭐ |
| Shimmer | ⚠️ Android only | Mínimo | Baja | ⭐⭐⭐ |

---

## Orden de implementación sugerido

```
Fase 1 — Base sólida
  ├── Compose Multiplatform (si no está ya)
  └── Material 3 (componentes del reproductor + biblioteca)

Fase 2 — Experiencia visual
  ├── Coil (artwork de canciones)
  └── Palette API (tema dinámico por canción)

Fase 3 — Polish y detalles
  ├── Shimmer (loadings de biblioteca)
  ├── Lottie (animaciones de estados y descarga)
  └── Accompanist (permisos + statusbar del full-screen player)
```

---

*Documento generado para BeatMyBeat — Mayo 2026*