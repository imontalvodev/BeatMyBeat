package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

/** Radios de esquina. Cuanto más grande la superficie, más redondeada — criterio M3 Expressive. */
object Radius {
    val sm = 12.dp
    val md = 18.dp
    val lg = 24.dp
    val xl = 28.dp
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

    /** Respuesta inmediata a una acción, y salidas. Lo que desaparece no se hace esperar. */
    const val QUICK = 180

    /** Entradas y crossfades de contenido. Es la duración por defecto. */
    const val STANDARD = 240

    /** Cambios estructurales de layout: expandir/colapsar, barras de progreso. */
    const val LAYOUT = 320

    /**
     * Cambios de ambiente que no deben llamar la atención — el color dominante que tiñe el fondo
     * del reproductor al cambiar de canción. Lento a propósito: si se nota, molesta.
     */
    const val AMBIENT = 600
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
        get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

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

    /** Cabecera de sección dentro de una pantalla. */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    /** Datos secundarios: duración, contadores, estados. */
    val meta: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium
}
