# Bugs y errores detectados por testers

Documento consolidado desde `Savetune Beta (respuestas).xlsx`.

## Criticos (rompen flujo)

- **Descarga de playlists falla o cierra la app** (muy repetido).
  - Sintomas: crash al pegar URL de playlist, no carga playlists de YouTube, no descarga.
  - Impacto: bloquea una funcionalidad principal.
- **Descarga de canciones no funciona en algunos casos**.
  - Sintomas: no descarga, comportamiento inconsistente.
  - Impacto: bloquea la propuesta principal de valor.
- **Player se queda atascado / no deja iniciar canción**.
  - Sintomas: play/pause deja de responder, bloqueo tras cambios de estado.
  - Impacto: reproducción inestable.

## Altos (degradan experiencia principal)

- **Shuffle (aleatorio) no funciona correctamente**.
  - Sintomas: deja de reproducir, dice que no encuentra más canciones, requiere acciones manuales para recuperarse.
- **Loop (repetición) no funciona bien**.
  - Sintomas: no repite la misma canción, estado inconsistente.
- **Permisos/notificaciones afectan descargas sin feedback claro**.
  - Sintomas: con notificaciones denegadas no descarga o no informa adecuadamente.
- **Crear playlist desde ciertos flujos falla o es confuso**.
  - Sintomas: desde pantalla de canción no deja crear al principio; UX de creación poco clara.

## Medios (funciona, pero con fricción)

- **No se puede reproducir desde cualquier punto (seek limitado)**.
- **No se muestra duración de la canción en el reproductor**.
- **El reproductor detecta audios no musicales (WhatsApp/Instagram, etc.)**.
- **No queda claro dónde se guardan las descargas / acceso confuso**.
- **Problemas de estado UI**:
  - botón principal descentrado en Downloader.
  - cambio de tema no persiste al reiniciar.
  - al volver a listas se pierde posición de scroll.

## Bajos (detalle / UX menor)

- Texto y acciones poco claros (ejemplo: botón "Enter" poco intuitivo).
- Mezcla de idiomas en la interfaz.

## Lista de corrección sugerida (orden de ejecución)

1. Estabilizar descargas (song + playlist) y evitar crash.
2. Arreglar máquina de estados de reproducción (play/pause, shuffle, loop).
3. Corregir permisos/notificaciones y feedback al usuario.
4. Filtrar MediaStore para excluir audios no musicales.
5. Mejorar UX de creación de playlist y accesibilidad de descargas.
6. Ajustes visuales y persistencia de tema.
