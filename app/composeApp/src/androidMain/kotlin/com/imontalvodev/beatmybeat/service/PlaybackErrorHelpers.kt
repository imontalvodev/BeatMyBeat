package com.imontalvodev.beatmybeat.service

/**
 * Cola no vacía en el JSON de entrada pero 0 items válidos tras el parseo = entrada corrupta,
 * a diferencia de una cola legítimamente vacía (JSON en blanco).
 */
internal fun isQueueJsonCorrupt(queueJson: String, parsedItemCount: Int): Boolean =
    queueJson.isNotBlank() && parsedItemCount == 0

/** Evita repetir el mismo toast de error en recomposiciones sucesivas de la UI. */
internal fun shouldShowPlaybackError(newErrorId: Long, lastShownErrorId: Long?): Boolean =
    newErrorId != lastShownErrorId
