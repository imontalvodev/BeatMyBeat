# 📄 Análisis técnico–legal del backend de descargas musicales

---

# 1. Situación técnica actual (resumen)

## Backend Python (FastAPI + yt-dlp + Selenium)

Recibe:

- URL de playlist de Spotify
- Texto de búsqueda para YouTube

### Spotify
- Scraping con Selenium
- Obtiene:
  - título
  - artista
  - álbum
  - duración
  - carátula

### YouTube
- Uso de yt_dlp (scraping web, NO YouTube Data API) para:
  - buscar (ytsearch)
  - descargar audio
- Genera MP3 temporal con:
  - metadatos
  - portada
- Envía el audio al cliente
- Borra el fichero inmediatamente

## Middleware Node
- Proxy HTTP entre app y backend Python
- No almacena audio
- No hace scraping
- Solo reenvía peticiones

## App Android

### Home
- Lista de canciones descargadas (escaneo local)
- Barra de reproductor siempre visible

### Download
- Campo único:
  - pegar URL de playlist Spotify
  - escribir título de canción
- Lista de resultados con opción de descargar

## Propiedad clave del sistema
⚠️ No se guarda audio de forma persistente en ningún servidor.
Solo existe temporalmente para conversión y envío.

---

# 2. Escenario legal actual (sin caché central)

## 2.1 Qué hace realmente SaveTune

Facilita que el usuario:

- Obtenga lista de canciones desde Spotify (scraping)
- Descargue audio desde YouTube (scraping/yt_dlp)

El usuario:

- Decide qué descargar
- Guarda los MP3 en su propio dispositivo

El servidor:

- Actúa como herramienta de automatización y conversión
- Elimina los archivos temporales tras la descarga

---

## 2.2 Riesgos legales

### Derechos de autor
Se crean copias de obras protegidas (MP3).

En muchos países, la copia privada:
- es zona gris
- más defendible cuando:
  - la copia la hace el usuario
  - no hay redistribución

### Términos de servicio
YouTube y Spotify:
- prohíben scraping
- prohíben descargas no autorizadas

Se incumplen ToS contractuales.

### Perfil de riesgo actual
Tu rol se parece más a:
- herramienta de automatización
- software open source
- backend de conversión bajo demanda

No eres:
- catálogo público
- distribuidor de música
- servicio tipo Spotify pirata

👉 Riesgo presente pero moderado.

---

# 3. Escenario alternativo: servidor con canciones cacheadas

## Idea
Guardar canciones ya descargadas en servidor y reutilizarlas para otros usuarios.

## 3.1 Implicaciones técnicas
El servidor pasaría a:
- almacenar copias permanentes
- servirlas a múltiples usuarios

Te conviertes de facto en:
👉 proveedor de catálogo centralizado de MP3

## 3.2 Riesgos añadidos

### Reproducción y distribución no autorizada
Cada archivo:
- es una reproducción con copyright
- cada descarga es distribución pública

Sin licencias:
👉 infracción directa de copyright

### Posibles consecuencias
- DMCA takedowns
- cierre del hosting
- reclamaciones formales
- demandas

### Cambio de rol legal
Pasas de:
> herramienta

a:
> proveedor activo de contenido musical

👉 Riesgo alto.

---

# 4. Variantes de caché “por usuario”

Ideas:
- guardar canciones solo por usuario
- borrarlas tras X días

## Problemas

### Técnicos
- sigues creando copias en tu servidor
- sigues sirviendo contenido desde tu infraestructura

### Legales
- reproducción no autorizada
- comunicación pública

Además:
- gestión de datos personales
- historial de descargas

👉 Más riesgo que el modelo efímero.

---

# 5. Estrategia recomendada para minimizar riesgos

## 5.1 Backend stateless
Mantener:
- audio solo temporal
- borrado inmediato
- sin almacenamiento persistente

Evitar:
- bases de datos de canciones
- catálogos
- descargas directas desde el servidor

## 5.2 Posicionamiento del proyecto

### Código abierto
- repositorio público
- instalación self-host

### Rol
Eres:
- desarrollador de herramienta

No:
- proveedor de contenido musical

## 5.3 Middleware ligero
Node solo:
- proxy
- CORS
- errores
- despliegue

No:
- almacenamiento
- procesamiento de audio

## 5.4 Logs
Mantener mínimos:
- solo debug técnico
- evitar historiales persistentes ligados a usuarios

---

# 6. Qué NO hacer sin asesoría y licencias

Evitar sin asesoría legal:
- catálogo central de MP3
- biblioteca compartida
- buscador propio de música
- distribución masiva

Eso implicaría:
- licencias discográficas
- acuerdos con editoriales
- costes altos
- complejidad legal grande

---

# 7. Resumen ejecutivo

## Modelo actual
Backend efímero + almacenamiento local:
- riesgo legal moderado
- más defendible
- similar a herramienta de descarga personal

## Modelo con caché compartida
- servidor con copias permanentes
- distribución a terceros
- equivalente a servicio pirata

👉 riesgo alto de demandas y cierre

---

# ✅ Recomendación final

Para el MVP:

✔ Backend sin almacenamiento permanente  
✔ Conversión efímera  
✔ Descargas solo al dispositivo  
✔ Sin catálogo central  
✔ Enfocarse en UX  

❌ No implementar biblioteca compartida  
❌ No cachear canciones en servidor