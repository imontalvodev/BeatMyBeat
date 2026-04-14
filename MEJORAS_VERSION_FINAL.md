# Mejoras a implementar para la version final

Mejoras orientadas a una `v1.0` solida, basadas en feedback de testers.

## Prioridad alta (v1.0)

- **Mostrar miniatura y preview al buscar canción para descargar**.
  - Reduce descargas equivocadas.
- **Compatibilidad robusta YouTube + YouTube Music**.
  - Detectar tipo de URL y aplicar estrategia de extracción correcta.
- **Mejorar UX del flujo Downloader**.
  - Etiquetas claras en botones (ej. "Descargar").
  - Indicar explícitamente que se acepta URL para descarga.
  - Definir pantalla inicial más útil para usuarios (descarga de canción vs playlist).
- **Gestión clara de almacenamiento y descargas**.
  - Mostrar ruta/carpeta de guardado.
  - Acción directa para abrir ubicación del archivo descargado.
  - Opción para borrar canciones descargadas y liberar espacio.
- **Mejorar gestión de playlists de usuario**.
  - Crear playlist sin fricción.
  - Añadir una canción a varias playlists de forma rápida.
  - Mostrar playlists con nombres reales en menú principal.
- **Mejoras del reproductor**.
  - Mostrar duración total y tiempo actual.
  - Permitir seek completo.
  - Mantener posición de scroll al cambiar pestañas.
- **Permisos y dependencias funcionales explicadas en UI**.
  - Avisar claramente sobre permiso de notificaciones y su impacto en descargas.

## Prioridad media (si entra en v1.0)

- **Persistencia de preferencias de interfaz**.
  - Tema/color mantenido tras cerrar app.
- **Calidad y formato de descarga configurable**.
  - Selector de formato (MP3, M4A, FLAC, OGG) y calidad.
  - Renombrado manual de canción antes de guardar.
- **Compatibilidad de búsqueda y nombres no latinos**.
  - Mejoras para localizar canciones por transliteración.
- **Ajustes básicos en app**.
  - Idioma, tamaño de interfaz/letra, notificaciones.
- **Cola de reproducción editable**.
  - Ver cola actual y reordenar/eliminar canciones (FIFO o modo configurable).

## Criterio de "version final lista"

- Descarga individual y por playlist estable.
- Reproductor sin bloqueos en flujo normal.
- Feedback y permisos claros.
- UX de playlist y descarga comprensible sin explicación externa.
