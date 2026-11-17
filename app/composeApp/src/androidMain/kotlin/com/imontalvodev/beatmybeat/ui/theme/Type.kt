package com.imontalvodev.beatmybeat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imontalvodev.beatmybeat.R

/**
 * Bricolage Grotesque (OFL): carácter para títulos y cifras grandes. Instancias estáticas
 * generadas desde la fuente variable para que funcionen igual desde API 24.
 */
val BricolageFamily = FontFamily(
    Font(R.font.bricolage_grotesque_700, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_800, FontWeight.ExtraBold),
)

/** Inter (OFL): todo lo que se lee deprisa (filas, etiquetas, controles). */
val InterFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    // Inter solo se empaqueta hasta 600: los pesos mayores caen en el más cercano.
    Font(R.font.inter_600, FontWeight.Bold),
)

/**
 * Escala «Charca»: títulos en Bricolage Grotesque 700, resto en Inter.
 *
 * display 38 · título de pantalla 34 · título 29 · sección 20 · cuerpo 16 · secundario 14 ·
 * etiqueta 12. Los pesos y el tracking salen del lienzo de diseño, no de la escala M3 por defecto.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.03).em(52),
    ),
    displayMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.025).em(45),
    ),
    // Título de Biblioteca.
    displaySmall = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.025).em(38),
    ),
    headlineLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em(34),
    ),
    // Título de pantalla de nivel superior (Analizar, Perfil, Cola).
    headlineMedium = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em(34),
    ),
    // Título de la canción en el reproductor.
    headlineSmall = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em(29),
    ),
    // Cabecera de sección.
    titleLarge = TextStyle(
        fontFamily = BricolageFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Tracking en em (como en el lienzo de diseño) convertido a sp para un tamaño dado. */
private fun Double.em(sizeSp: Int) = (this * sizeSp).sp
