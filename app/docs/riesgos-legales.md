# Riesgos legales — BeatMyBeat

> **Aviso:** este documento es orientación general para el proyecto. **No sustituye asesoramiento legal.** Si publicas la app de forma amplia, conviene revisarlo con un profesional del derecho en tu jurisdicción.

## Resumen ejecutivo

BeatMyBeat es una aplicación **gratuita, sin anuncios y open source** que descarga audio desde **YouTube / YouTube Music** directamente en el dispositivo (InnerTube, NewPipe Extractor, FFmpeg). El riesgo legal principal **no viene de cobrar dinero**, sino de:

1. **Infringir los términos de servicio de YouTube/Google** al descargar contenido fuera de los métodos autorizados.
2. **Posible infracción de derechos de autor** cuando el usuario descarga obras musicales protegidas sin licencia del titular.
3. **Visibilidad y canal de distribución** (web, GitHub, F-Droid): a mayor exposición, mayor probabilidad de recibir un aviso de retirada.

Ser gratis y sin monetización **reduce** el atractivo de demandas por lucro ilícito, pero **no elimina** el marco legal ni las condiciones de uso de YouTube.

---

## Qué hace la app (contexto técnico)

| Función | Origen de datos |
|---|---|
| Búsqueda | InnerTube (`music.youtube.com`, `youtube.com`) |
| URL / playlists | InnerTube + oEmbed |
| Descarga de audio | NewPipe Extractor + descarga HTTP en el teléfono |
| Conversión / tags | FFmpeg en el dispositivo |
| Letras | LRCLIB + api.lyrics.ovh |
| Servidor propio | **No** — todo opera desde el móvil |

No hay middleware ni backend de BeatMyBeat. La app es un **cliente que facilita** el acceso a contenido alojado en plataformas de terceros.

---

## Áreas de riesgo legal

### 1. Términos de servicio de YouTube / Google

Los ToS de YouTube restringen la descarga de contenido salvo donde la plataforma lo permita explícitamente (YouTube Premium en contextos autorizados, botones oficiales de descarga, etc.).

- El uso de **InnerTube** y extractores tipo NewPipe **no constituye una API pública autorizada** para descargas.
- Esto es un **incumplimiento contractual** (ToS), independiente de si la app es gratis.
- Google/YouTube **podría** enviar avisos de cese, aunque en proyectos pequeños y gratuitos es **poco frecuente** que actúen contra el autor individual.

**Riesgo teórico:** medio–alto.  
**Riesgo práctico (proyecto hobby pequeño):** bajo–medio.

---

### 2. Derechos de autor (propiedad intelectual)

La mayor parte de la música en YouTube está **protegida por copyright**. Descargarla y guardarla localmente puede constituir **reproducción no autorizada** según la legislación aplicable (España / UE).

Puntos relevantes:

- **Gratis ≠ permitido.** La ausencia de pago no convierte automáticamente la descarga en legal.
- **Open source ≠ permiso sobre el contenido.** La licencia del código (GPL, Apache, etc.) regula el **software**, no las obras que los usuarios descargan con él.
- **Responsabilidad del usuario vs. del desarrollador:** en muchos casos el uso infractor lo realiza el usuario final; no obstante, **distribuir una herramienta diseñada para ello** puede considerarse facilitación, según el caso y la jurisdicción.
- **Copia privada** en España/UE tiene matices y **no encaja bien** con apps que permiten descargas masivas desde una plataforma de streaming.

**Riesgo teórico:** medio–alto.  
**Riesgo práctico (hobby sin promoción agresiva):** bajo–medio.

---

### 3. Marca y descripción

- Mencionar YouTube / YouTube Music en la UI o documentación **no es ilegal por sí solo**, pero describe con claridad la funcionalidad.
- **Ocultar** la marca manteniendo la misma función puede interpretarse como **descripción engañosa** en tiendas (Google Play) o ante reclamaciones.
- En web/GitHub/F-Droid, la **transparencia** (qué hace la app y bajo qué limitaciones) es preferible a ocultar el origen del contenido.

**Riesgo:** bajo–medio (más reputacional y de tienda que penal).

---

### 4. Open source y licencia del código

Publicar el código en GitHub con licencia FOSS (p. ej. GPL-3.0, Apache-2.0):

| Protege | No protege |
|---|---|
| Uso, modificación y redistribución del **código fuente** | Descarga de **contenido ajeno** protegido |
| Transparencia ante la comunidad FOSS | Incumplimiento de ToS de YouTube |
| Coherencia con ecosistemas como F-Droid | Demandas o avisos de titulares de derechos |

Proyectos similares (NewPipe, LibreTube, etc.) han convivido años en FOSS **a pesar** de la misma tensión legal, no porque esté “legalizado” por ser open source.

---

## Distribución por canal

### APK en web personal

Publicar el `.apk` en una web propia (enlace directo, landing del proyecto).

| Aspecto | Detalle |
|---|---|
| **Riesgo legal sustantivo** | Medio — misma funcionalidad de descarga desde YouTube |
| **Riesgo práctico** | Bajo–medio en proyectos pequeños y poco promocionados |
| **Escenario más probable** | Que no pase nada, o un **aviso de retirada** (email / DMCA-style) |
| **Escenario menos probable** | Demanda civil por daños (sobre todo sin ingresos) |

Recomendaciones:

- No alojar **contenido protegido**, solo el binario de la app.
- Incluir aviso de **uso bajo responsabilidad del usuario**.
- Evitar marketing del tipo “descarga toda la música gratis”.
- Política de privacidad mínima si la web recoge datos (analytics, formularios, logs).

---

### GitHub (repositorio abierto + Releases con APK)

| Aspecto | Detalle |
|---|---|
| **Código fuente** | Encaja con open source; GitHub tolera proyectos tipo NewPipe |
| **APK en Releases** | Mismo riesgo sustantivo que en web; **mayor visibilidad** |
| **DMCA / aviso a GitHub** | GitHub puede deshabilitar releases o el repo tras una reclamación válida |
| **Riesgo práctico** | Bajo–medio; sube si el repo gana estrellas, forks o prensa |

Ventajas: historial, transparencia, builds reproducibles, credibilidad FOSS.  
Inconvenientes: más fácil de encontrar para quien quiera enviar un aviso.

Muchos proyectos publican **solo el código** en GitHub y dejan el APK a F-Droid/IzzyOnDroid o a builds locales del usuario.

---

### F-Droid (repositorio oficial)

F-Droid **no certifica legalidad**; exige software libre, builds reproducibles y declaración de anti-features.

| Requisito F-Droid | BeatMyBeat |
|---|---|
| 100 % FOSS | Viable (NewPipe, OkHttp, Media3, Compose, etc.) |
| Licencia en el repo | Necesaria (`LICENSE`) |
| Build reproducible | Requiere trabajo (metadata en fdroiddata) |
| Anti-feature `NonFreeNet` | Aplicable — YouTube no es “red libre” |
| Dependencias nativas (ffmpeg) | Puede requerir receta especial |

| Aspecto | Detalle |
|---|---|
| **Riesgo legal** | Medio — misma funcionalidad de descarga |
| **Riesgo de inclusión** | Proceso lento; revisión técnica exigente |
| **Riesgo práctico de reclamación** | Medio — **más visibilidad** que una web de hobby |
| **Precedente** | NewPipe y clientes similares están en F-Droid |

F-Droid acepta este **tipo** de aplicación en el ecosistema FOSS; eso **no** implica que descargar desde YouTube sea legal en todos los países.

---

### IzzyOnDroid (alternativa FOSS)

Repositorio compatible con F-Droid, **más rápido y flexible** que el repo oficial.

- Misma filosofía FOSS y mismos **riesgos legales de fondo**.
- Menos burocracia; habitual para primeras publicaciones.
- Anti-features y transparencia igualmente recomendables.

---

### Google Play Store

| Variante | Viabilidad |
|---|---|
| **App completa** (búsqueda + URL + descarga YouTube) | **Alta probabilidad de rechazo** por políticas de Play y contenido de terceros |
| **Variante reducida** (solo reproductor local / importar archivos) | Mucho más viable; requiere flavor distinto |

Publicar primero una versión “limpia” y **actualizar después con descarga YouTube** se considera eludir políticas y puede costar la app o la cuenta de desarrollador.

**iOS / App Store:** mismo núcleo de riesgo; además BeatMyBeat no está orientada a iOS en el roadmap actual.

---

## Matriz de riesgo resumida

Proyecto **gratuito, sin anuncios, open source, sin ánimo de lucro**:

| Canal | Riesgo legal (teórico) | Riesgo práctico (que ocurra algo) | Notas |
|---|---|---|---|
| APK en web personal | Medio | Bajo–medio | Adecuado para distribución directa |
| GitHub (código) | Medio | Bajo | Estándar para FOSS |
| GitHub (Releases + APK) | Medio | Bajo–medio | Más visible que web sola |
| IzzyOnDroid | Medio | Medio | FOSS-friendly, más ojos |
| F-Droid oficial | Medio | Medio | Lento; más prestigio FOSS |
| Google Play (full) | Alto | Alto (rechazo) | No recomendable versión completa |
| Google Play (solo reproductor) | Bajo | Bajo | Producto distinto |

**Escenarios más habituales (no exclusivos):**

1. **Nada** — proyecto pequeño, poca difusión.
2. **Aviso de retirada** — web, GitHub Releases o repo F-Droid; suele bastar con retirar el binario o el listing.
3. **Rechazo en tienda** — Play Store con versión full.
4. **Demanda / procedimiento** — poco habitual en hobbies gratuitos sin tráfico masivo.

---

## Unión Europea / España

- **Directiva de copyright / reproducción:** la descarga no autorizada de obras protegidas puede ser infracción; las excepciones (copia privada, etc.) **no cubren bien** una app de descarga masiva desde YouTube.
- **Directiva DSM (art. 17):** orientada sobre todo a **plataformas** grandes; impacto limitado en un desarrollador individual, pero no supone permiso implícito.
- **RGPD:** relevante si se tratan datos personales (cuentas, analytics, servidor). BeatMyBeat actual **no usa backend propio**; riesgo RGPD bajo en la app, mayor si la web recoge datos.
- **Sin monetización:** reduce incentivos de titulares para acciones costosas, pero **no crea exención legal automática**.

---

## Estrategia recomendada para BeatMyBeat

1. **GitHub abierto** con `LICENSE`, README claro y sección de limitaciones legales.
2. **APK** en GitHub Releases y/o web del proyecto, con texto de uso responsable.
3. **F-Droid / IzzyOnDroid** solo si se quiere visibilidad FOSS y se asume el trabajo técnico y la mayor exposición.
4. **Google Play** únicamente con variante reproductor local, si algún día interesa tienda oficial.
5. **No promocionar** la app como herramienta de piratería; enfatizar reproductor offline, stack técnico y software libre.
6. **Responder con prontitud** a avisos de retirada (DMCA, ToS, etc.) retirando el binario o el listing afectado.

---

## Texto sugerido (README / web)

> BeatMyBeat es un proyecto **gratuito y open source**, sin anuncios ni monetización. Permite buscar y descargar audio desde YouTube y YouTube Music en el dispositivo del usuario.  
>  
> La descarga de contenido de terceros puede **infringir los términos de servicio de YouTube** y la **legislación de propiedad intelectual** aplicable. Los desarrolladores **no alojan contenido protegido**; solo distribuyen el software.  
>  
> El **usuario es responsable** del uso que haga de la aplicación conforme a la ley y a las condiciones de las plataformas de origen.

Este párrafo **no elimina riesgos**, pero documenta transparencia y ausencia de ánimo comercial de piratería.

---

## Conclusión

Distribuir BeatMyBeat **gratis** por web, GitHub o F-Droid es **coherente** con proyectos FOSS similares y, en la práctica, suele ser **asumible** para un hobby sin ingresos si se acepta la posibilidad de un aviso de retirada. El riesgo **sustantivo** permanece mientras la app facilite descargas desde YouTube; cambiar textos o ocultar marcas **no lo resuelve**. La vía más restrictiva sigue siendo **Google Play con la versión completa**; la más alineada con el producto actual es **GitHub + APK directo ± IzzyOnDroid/F-Droid**.

---

*Documento generado para uso interno del proyecto BeatMyBeat. Última revisión: mayo 2026.*
