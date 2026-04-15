# Backlog matriz unificada

Matriz consolidada de `TESTERS_BUGS_Y_ERRORES.md`, `MEJORAS_VERSION_FINAL.md` y `TODO.md`.

## Criterio de lectura

- **Fuente**: origen principal del item (`testers`, `todo`, `ambos`).
- **Prioridad**:
  - `P0`: bloquea uso principal o provoca crash.
  - `P1`: degrada flujo principal, pero con workaround.
  - `P2`: mejora relevante de UX/calidad para v1.0.
  - `P3`: evolución futura (post v1.0).
- **Fase objetivo**: referencia a plan técnico actual (Fase 2-5 de KMP + release).

## Matriz


| ID   | Item                                                         | Tipo        | Fuente  | Prioridad | Estado                                                              | Fase objetivo                         |
| ---- | ------------------------------------------------------------ | ----------- | ------- | --------- | ------------------------------------------------------------------- | ------------------------------------- |
| B-01 | Crash/fallo al descargar playlists de YouTube                | Bug         | ambos   | P0        | ✅ Resuelto                                                          | Release Android (pre-Fase 2 profunda) |
| B-02 | Descarga de canciones falla en algunos casos                 | Bug         | testers | P0        | ✅ Resuelto (flujo URL)                                              | Release Android (pre-Fase 2 profunda) |
| B-03 | Player se queda atascado (play/pause no responde)            | Bug         | testers | P0        | ✅ Resuelto                                                          | Release Android                       |
| B-04 | Shuffle no funciona correctamente                            | Bug         | ambos   | P1        | 🟡 Parcial (pendiente de sincronización final con nueva UI)         | Release Android                       |
| B-05 | Loop no repite correctamente                                 | Bug         | ambos   | P1        | ⏳ Pendiente                                                         | Release Android                       |
| B-06 | Incompatibilidad URL YouTube Music                           | Bug         | ambos   | P1        | ✅ Resuelto (playlist + song URL)                                    | Release Android                       |
| B-07 | Permiso de notificaciones afecta descargas sin feedback      | Bug/UX      | ambos   | P1        | ⏳ Pendiente                                                         | Release Android                       |
| B-08 | Crear playlist desde ciertos flujos falla/UX confusa         | Bug/UX      | ambos   | P1        | ⏳ Pendiente                                                         | Release Android                       |
| B-09 | Seek no permite reproducir desde cualquier punto             | Bug         | ambos   | P1        | ✅ Resuelto                                                          | Release Android                       |
| B-10 | No se muestra duración de canción                            | Bug/UX      | ambos   | P1        | ✅ Resuelto                                                          | Release Android                       |
| B-11 | Detecta audios no musicales (WhatsApp/Instagram)             | Bug         | ambos   | P1        | ✅ Resuelto (filtro estricto)                                        | Release Android                       |
| B-12 | No se entiende dónde se guardan descargas                    | UX          | ambos   | P2        | 🟡 Parcial (progreso visible, falta acceso carpeta)                 | Release Android                       |
| B-13 | Botón principal Downloader descentrado                       | UI          | testers | P2        | ⏳ Pendiente                                                         | Release Android                       |
| B-14 | Tema no persiste al cerrar app                               | Bug/UX      | testers | P2        | ⏳ Pendiente                                                         | Release Android                       |
| B-15 | Se pierde posición de scroll al cambiar pestañas             | UX          | testers | P2        | ⏳ Pendiente                                                         | Release Android                       |
| V-01 | Miniaturas y preview al buscar para descargar                | Mejora v1.0 | ambos   | P2        | ✅ Resuelto (miniaturas)                                             | Fase producto v1.0                    |
| V-02 | Etiquetas/acciones claras en Downloader ("Descargar")        | Mejora v1.0 | testers | P2        | 🟡 Parcial                                                          | Fase producto v1.0                    |
| V-03 | Configuración de formato/calidad (MP3, M4A, FLAC, OGG)       | Mejora v1.0 | ambos   | P2        | 🟡 Parcial(de momento descarga en formato .mp3)Fase producto v1.0 |                                       |
| V-04 | Renombrar canción antes de descargar                         | Mejora v1.0 | testers | P2        | Fase producto v1.0                                                  |                                       |
| V-05 | Gestión de almacenamiento (abrir carpeta/borrar descargadas) | Mejora v1.0 | ambos   | P2        | Fase producto v1.0                                                  |                                       |
| V-06 | Añadir canción a varias playlists más rápido                 | Mejora v1.0 | ambos   | P2        | Fase producto v1.0                                                  |                                       |
| V-07 | Mostrar playlists con nombres reales en menú                 | Mejora v1.0 | testers | P2        | Fase producto v1.0                                                  |                                       |
| V-08 | Ajustes básicos (idioma, tamaño UI, notificaciones)          | Mejora v1.0 | ambos   | P2        | Fase producto v1.0                                                  |                                       |
| V-09 | Cola de reproducción editable                                | Mejora v1.0 | todo    | P2        | Fase producto v1.0                                                  |                                       |
| F-01 | Historial de búsqueda                                        | Futuro      | ambos   | P3        | Fase 5 / post v1.0                                                  |                                       |
| F-02 | Versión iOS completa                                         | Futuro      | testers | P3        | Fase 4-5                                                            |                                       |
| F-03 | Detección tipo Shazam                                        | Futuro      | ambos   | P3        | Post v1.0                                                           |                                       |
| F-04 | Integración Spotify API                                      | Futuro      | todo    | P3        | Post v1.0                                                           |                                       |
| F-05 | Letras sincronizadas                                         | Futuro      | todo    | P3        | Post v1.0                                                           |                                       |
| F-06 | Karaoke mode                                                 | Futuro      | todo    | P3        | Post v1.0                                                           |                                       |
| F-07 | Recomendaciones inteligentes                                 | Futuro      | testers | P3        | Post v1.0                                                           |                                       |
| F-08 | Ecualizador y vistas por carpetas/grupos                     | Futuro      | testers | P3        | Post v1.0                                                           |                                       |
| F-09 | Compartir playlists/documentos con deep links                | Futuro      | todo    | P3        | Post v1.0                                                           |                                       |
| F-10 | Personalización avanzada y perfil de usuario                 | Futuro      | todo    | P3        | Post v1.0                                                           |                                       |


## Progreso actual (sesión)

- ✅ Estabilizado crash en flujo de descarga por URL.
- ✅ Validación robusta de URL (YouTube y YouTube Music; canción/playlist/álbum).
- ✅ Descarga por URL funcional en los casos reportados de song + playlist/álbum.
- ✅ Miniaturas visibles en resultados de búsqueda de canciones.
- ✅ Feedback de descarga dentro de la app (progreso, conteo y estado en segundo plano con foreground service).
- ✅ Seekbar funcional en drag + tap puntual (sin saltar de canción), migrado a ExoPlayer con servicio ligado.
- ✅ Filtro de audios no musicales en MediaStore (IS_MUSIC + duración + heurísticas de ruta/MIME).
- ⏳ Pendiente: bloque de reproductor (shuffle/loop), y UX final de ajustes/almacenamiento.

## Orden de ejecución recomendado (rápido)

1. **Bloque P0 restante**: B-03.
2. **Bloque P1**: B-04, B-05, B-07, B-08.
3. **Bloque P2 técnico**: B-12, B-14, B-15.
4. **Bloque P2 producto v1.0**: V-02 a V-09.
5. **Bloque P3**: F-01 en adelante.

## Nota de continuidad

Si se retoma el proyecto con contexto nuevo, empezar por:

- `B-03` (bloqueos en player),
- `B-04/B-05` (shuffle/loop),
- `B-07/B-08` (feedback permisos + UX playlist).

Ese trío concentra la mayor parte de fricción pendiente reportada por testers.