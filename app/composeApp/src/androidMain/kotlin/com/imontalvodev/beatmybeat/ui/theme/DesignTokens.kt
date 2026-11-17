package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens de diseño de BeatMyBeat.
 *
 * Motivo: `Type.kt` define la escala Material 3 completa (hasta `displayLarge`, 57sp) pero las
 * pantallas casi no usaban nada por encima de `titleMedium` (16sp) — el título de una canción
 * llegó a pintarse a 12sp. Todo aplanado en el mismo rango tipográfico es lo que hacía que la app
 * se viera densa y sin jerarquía.
 *
 * Estos tokens **no tocan el color**: la paleta sigue viniendo de `ThemeProfilesStore` y del
 * `ColorScheme`, que el usuario personaliza desde `ThemeCustomizerScreen`. Aquí solo se fija
 * tamaño, peso, espaciado y forma.
 */

/** Escala de espaciado (múltiplos de 4). Usar estos valores en vez de dp sueltos. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Radios de esquina «Charca»: cuatro en lugar de doce (12 · 20 · 28 · 32). Cuanto más grande la
 * superficie, más redondeada.
 */
object Radius {
    val sm = 12.dp
    val md = 20.dp
    val lg = 28.dp
    val xl = 32.dp
    val pill = 999.dp
}

/**
 * Duraciones de animación, en milisegundos.
 *
 * Había 9 valores distintos repartidos por 12 sitios (180, 200, 220, 240, 280, 320, 600, 700), pero
 * al mirarlos de cerca seguían un criterio correcto que **no** hay que aplanar: las entradas duran
 * más que las salidas (240/200, 280/220, 220/160). Es práctica Material: aparecer se acompaña,
 * desaparecer se quita de en medio. Por eso los tokens se nombran por rol y conservan esa asimetría
 * en vez de unificar todo a un único número.
 */
object Motion {

    /** Pulsaciones y retroalimentación táctil. */
    const val INSTANT = 100

    /** Respuesta inmediata a una acción, y salidas. Lo que desaparece no se hace esperar. */
    const val QUICK = 180

    /** Entradas y crossfades de contenido. Es la duración por defecto. */
    const val STANDARD = 280

    /** Cambios estructurales de layout: expandir/colapsar, barras de progreso. */
    const val LAYOUT = 280

    /** El momento de marca: el Splash y sus ondas de charca. */
    const val BRAND = 700

    /**
     * Cambios de ambiente que no deben llamar la atención — el color dominante que tiñe el fondo
     * del reproductor al cambiar de canción. Lento a propósito: si se nota, molesta.
     */
    const val AMBIENT = 600
}

/**
 * Colores de «Charca» que no cambian con el tema: el ámbar marca favoritos, karaoke y avisos, y la
 * tinta es el color del texto que va sobre un relleno de acento. El resto sale del `colorScheme`.
 */
object CharcaColors {
    private val amberOnDark = Color(0xFFFFC25C)

    /** Ámbar más hondo para fondos claros: el vivo no llega a 3:1 sobre Estanque (B45309 = 4,7:1). */
    private val amberOnLight = Color(0xFFB45309)

    /** Ámbar del tema activo: vivo sobre fondos oscuros, hondo sobre claros. */
    val amber: Color
        @Composable
        get() = if (currentBeatMyBeatThemeProfile().isLight) amberOnLight else amberOnDark

    val onAmber = Color(0xFF1A1203)

    val amberContainer: Color
        @Composable
        get() = amber.copy(alpha = 0.16f)

    /** Etiquetas y líneas de letra por llegar (≈5,5:1 sobre la superficie oscura). */
    val faint = Color(0xFF7C939B)
}

/**
 * Roles tipográficos por función, no por tamaño.
 *
 * Se nombran por lo que son ("el título en el reproductor") y no por su escala, para que subir o
 * bajar un tamaño sea un cambio en un solo sitio y no una cacería por las pantallas.
 */
object AppText {

    /** Título de la canción en el reproductor expandido. Es el elemento dominante de la pantalla. */
    val playerTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall

    /** Título en el reproductor cuando compite por espacio (Modo Karaoke). */
    val playerTitleCompact: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Artista bajo el título del reproductor. */
    val playerArtist: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium

    /** Título de una canción en una fila de lista. */
    val trackTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

    /** Artista/álbum en una fila de lista. */
    val trackArtist: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    /** Título de una pantalla de nivel superior (Biblioteca, Descargar, Ajustes). */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineMedium

    /** Etiqueta que agrupa filas dentro de una lista. */
    val sectionLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

    /** Cabecera de sección dentro de una pantalla. */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Etiqueta en mayúsculas con tracking amplio («SONANDO AHORA», «LETRA»). */
    val kicker: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp)

    /** Línea de letra sincronizada (grande, Bricolage). */
    val lyricLine: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 32.sp)

    /** Datos secundarios: duración, contadores, estados. */
    val meta: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium
}
