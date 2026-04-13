# Cosas a corregir

## IMPORTANTE LEER EL FICHERO PDF CON TODAS LAS RESPUESTAS DE LOS BETA TESTERS

## Bugs con el reproductor

### Agregar filtro de audios
Se reporta que la app detecta audios de Whatsapp, Instagram, etc

### Reproducir desde cualquier punto
Al reproducir una cancion los usuarios reportan que solo se puede reproducir desde el principio y no desde cualquier punto

### Falta el tiempo de duracion de la cancion
Los usuarios reportan la falta del indicador de duracion de la cancion

### Arreglar botones de shuffle y loop
Reportan que falla el boton de aleatorio y el de reproducir en bucle

## Descargas

### Miniaturas
Mostrar las miniaturas de las canciones que se desee descargar para no descargar el fichero equivocado

### Pete al descargar playlists de YT
Cuando el usuario inserta una URL de una playlist de YT la app directamente peta

### Implementar compatibilidad con playlists de YT y de YTMusic
Algunos usuarios intentaron descargar canciones de YTMusic y daba error, habria q aplicar a nivel interno filtros para mirar la URL y de ahi la app sepa donde debe buscar

# Cosas a implementar

## V1

### FFmpeg
Implementar esta herramienta para poder convertir los audios descargados de m4a a mp3 u otros formatos (.flac, .ogg, etc)

### Sincronizar la letra con el ritmo de la cancion
Buscar como poder sincronizar las letras con lo que va cantando la cancion

### Poder ver la cola de las canciones a reproducir y editarla
Opcion para que el usuario vea las canciones en cola y poder editar esa cola de reproduccion a gusto del usuario (usar FIFO)

### Historial de busqueda
Implementar un historial de las canciones buscadas a nivel local y ¿para descargar?

### Informar de que se requiere el permiso de notificaciones
Si el usuario no acepta recibir notificaciones informarle q es necesario y obligarle para que asi la app le pueda avisar de cuando se descargan las canciones y le aparezca la bar menu en el area de notificaciones sobre la cancion en reproduccion

### Cambiar entre diferentes idiomas
Implementar diferentes opciones de idiomas dentro de la app (Español, ingles, croata, aleman, etc)

### Personalizar el fondo de la app
Dejar que el usuario implemente de fondo una foto personalizada para verse cuando se ejecuta la app al igual que pueda personalizar cada color y detalle de la app a su propio gusto

### Poner foto de perfil

### Compartir informacion
Compartir como un documento PDF con las playlists y el nombre de las canciones para conseguir que la app luego ese PDF lo reconozca y poder descargar las diferentes canciones (PDF interactivo, posiblemente implementar hipervinculos dentro de el para la APP)
En caso de q el usuario no tenga la app cuando pinche al enlace, le lleve a la playstore para descargar la app automaticamente

## Futuras actualizaciones

### Implementacion de Spotify API
**Se requiere de una cuenta de Spotify Premium para poder usar la API**
Para esto necesitaremos pensar una forma de financiar el pago de la cuenta y ademas el mantener el servidor a montar con un backend sencillo para obtener los datos de la API

### Modo karaoke (futura update)
Al tener la cancion y la letra se puede crear un karaoke, habria que ver como aislar la voz o algo y asi permitimos al usuario el tener el karaoke

### Detectar una cancion como Shazam
Implementar una funcion para poder escanear las canciones como hace Shazam
