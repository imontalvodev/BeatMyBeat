# Backlog matriz unificada — BeatMyBeat

Matriz consolidada de `TESTERS_BUGS_Y_ERRORES.md`, `MEJORAS_VERSION_FINAL.md` y `TODO.md`.

---

## Contexto del proyecto (leer primero)

### Nombre de la app
La app se llamaba **Savetune** y ha sido renombrada a **BeatMyBeat**. El renombrado es completo a nivel de código.

### Estructura de carpetas relevante

```
(raíz del repositorio)/        ← carpeta pendiente de renombrar de Savetune → BeatMyBeat
├── app/                       ← ✅ PROYECTO ACTIVO KMP (Android + iOS base)
│   ├── composeApp/
│   │   └── src/
│   │       ├── androidMain/   ← todo el código Android real de la app
│   │       ├── commonMain/    ← código compartido KMP
│   │       ├── iosMain/       ← base iOS
│   │       └── commonTest/
│   ├── gradle/libs.versions.toml
│   ├── composeApp/build.gradle.kts
│   └── settings.gradle.kts
├── backend/                   ← backend Python (FastAPI)
└── middleware/                ← middleware Node.js (Express)
```

### Por qué `app/` es el proyecto activo
Se creó desde cero como proyecto KMP limpio con nombre BeatMyBeat, y se migró dentro todo el código fuente del proyecto anterior. `frontend/` y `docker-compose.home.yml` han sido eliminados.

### Paquete Android
- `applicationId` y `namespace`: `com.imontalvodev.beatmybeat`
- Todos los paquetes Kotlin usan `com.imontalvodev.beatmybeat.*`

### Estado del repositorio Git
- Repositorio: `git@MrCalvooo:MrCalvooo/Savetune.git` — **pendiente renombrar a BeatMyBeat en GitHub**
- Tras renombrar en GitHub, actualizar remote local con:
  ```
  git remote set-url origin git@MrCalvooo:MrCalvooo/BeatMyBeat.git
  ```
- La carpeta raíz también está pendiente de renombrar de `Savetune/` a `BeatMyBeat/`
- El `.venv` de `backend/` tendrá la ruta hardcodeada rota tras mover carpeta — recrear con `python -m venv .venv`

### Dependencias clave de `app/` (libs.versions.toml)
- Kotlin Multiplatform + Compose Multiplatform 1.10.3
- ExoPlayer / Media3 1.6.1
- OkHttp 5.3.2
- NewPipeExtractor v0.26.0 (vía JitPack)
- FFmpegKit 6.1.4
- Navigation Compose 2.9.7
- Desugar JDK libs 2.0.4

---

## Criterio de lectura de la matriz

- **Fuente**: origen principal del item (`testers`, `todo`, `ambos`).
- **Prioridad**:
  - `P0`: bloquea uso principal o provoca crash.
  - `P1`: degrada flujo principal, pero con workaround.
  - `P2`: mejora relevante de UX/calidad para v1.0.
  - `P3`: evolución futura (post v1.0).
- **Fase objetivo**: referencia a plan técnico actual (Fase 2-5 de KMP + release).

---

## Matriz

| ID   | Item                                                         | Tipo        | Fuente  | Prioridad | Estado                                                             | Fase objetivo                         |
| ---- | ------------------------------------------------------------ | ----------- | ------- | --------- | ------------------------------------------------------------------ | ------------------------------------- |
| B-01 | Crash/fallo al descargar playlists de YouTube                | Bug         | ambos   | P0        | ✅ Resuelto                                                         | Release Android (pre-Fase 2 profunda) |
| B-02 | Descarga de canciones falla en algunos casos                 | Bug         | testers | P0        | ✅ Resuelto (flujo URL)                                             | Release Android (pre-Fase 2 profunda) |
| B-03 | Player se queda atascado (play/pause no responde)            | Bug         | testers | P0        | ✅ Resuelto                                                         | Release Android                       |
| B-04 | Shuffle no funciona correctamente                            | Bug         | ambos   | P1        | 🟡 Parcial (pendiente sincronización final con nueva UI)           | Release Android                       |
| B-05 | Loop no repite correctamente                                 | Bug         | ambos   | P1        | ⏳ Pendiente                                                        | Release Android                       |
| B-06 | Incompatibilidad URL YouTube Music                           | Bug         | ambos   | P1        | ✅ Resuelto (playlist + song URL)                                   | Release Android                       |
| B-07 | Permiso de notificaciones afecta descargas sin feedback      | Bug/UX      | ambos   | P1        | ⏳ Pendiente                                                        | Release Android                       |
| B-08 | Crear playlist desde ciertos flujos falla/UX confusa         | Bug/UX      | ambos   | P1        | ⏳ Pendiente                                                        | Release Android                       |
| B-09 | Seek no permite reproducir desde cualquier punto             | Bug         | ambos   | P1        | ✅ Resuelto                                                         | Release Android                       |
| B-10 | No se muestra duración de canción                            | Bug/UX      | ambos   | P1        | ✅ Resuelto                                                         | Release Android                       |
| B-11 | Detecta audios no musicales (WhatsApp/Instagram)             | Bug         | ambos   | P1        | ✅ Resuelto (filtro estricto)                                       | Release Android                       |
| B-12 | No se entiende dónde se guardan descargas                    | UX          | ambos   | P2        | 🟡 Parcial (progreso visible, falta acceso carpeta)                | Release Android                       |
| B-13 | Botón principal Downloader descentrado                       | UI          | testers | P2        | ⏳ Pendiente                                                        | Release Android                       |
| B-14 | Tema no persiste al cerrar app                               | Bug/UX      | testers | P2        | ⏳ Pendiente                                                        | Release Android                       |
| B-15 | Se pierde posición de scroll al cambiar pestañas             | UX          | testers | P2        | ⏳ Pendiente                                                        | Release Android                       |
| V-01 | Miniaturas y preview al buscar para descargar                | Mejora v1.0 | ambos   | P2        | ✅ Resuelto (miniaturas)                                            | Fase producto v1.0                    |
| V-02 | Etiquetas/acciones claras en Downloader ("Descargar")        | Mejora v1.0 | testers | P2        | 🟡 Parcial                                                         | Fase producto v1.0                    |
| V-03 | Configuración de formato/calidad (MP3, M4A, FLAC, OGG)       | Mejora v1.0 | ambos   | P2        | 🟡 Parcial (descarga en .mp3 por ahora)                            | Fase producto v1.0                    |
| V-04 | Renombrar canción antes de descargar                         | Mejora v1.0 | testers | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| V-05 | Gestión de almacenamiento (abrir carpeta/borrar descargadas) | Mejora v1.0 | ambos   | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| V-06 | Añadir canción a varias playlists más rápido                 | Mejora v1.0 | ambos   | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| V-07 | Mostrar playlists con nombres reales en menú                 | Mejora v1.0 | testers | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| V-08 | Ajustes básicos (idioma, tamaño UI, notificaciones)          | Mejora v1.0 | ambos   | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| V-09 | Cola de reproducción editable                                | Mejora v1.0 | todo    | P2        | ⏳ Pendiente                                                        | Fase producto v1.0                    |
| F-01 | Historial de búsqueda                                        | Futuro      | ambos   | P3        | ⏳ Pendiente                                                        | Fase 5 / post v1.0                    |
| F-02 | Versión iOS completa                                         | Futuro      | testers | P3        | ⏳ Pendiente                                                        | Fase 4-5                              |
| F-03 | Detección tipo Shazam                                        | Futuro      | ambos   | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-04 | Integración Spotify API                                      | Futuro      | todo    | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-05 | Letras sincronizadas                                         | Futuro      | todo    | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-06 | Karaoke mode                                                 | Futuro      | todo    | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-07 | Recomendaciones inteligentes                                 | Futuro      | testers | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-08 | Ecualizador y vistas por carpetas/grupos                     | Futuro      | testers | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-09 | Compartir playlists/documentos con deep links                | Futuro      | todo    | P3        | ⏳ Pendiente                                                        | Post v1.0                             |
| F-10 | Personalización avanzada y perfil de usuario                 | Futuro      | todo    | P3        | 🟡 Parcial (lógica implementada)                                   | Post v1.0                             |

---

## Historial de sesiones

### Sesión anterior (funcionalidades core)
- ✅ Estabilizado crash en flujo de descarga por URL.
- ✅ Validación robusta de URL (YouTube y YouTube Music; canción/playlist/álbum).
- ✅ Descarga por URL funcional en los casos reportados de song + playlist/álbum.
- ✅ Miniaturas visibles en resultados de búsqueda de canciones.
- ✅ Feedback de descarga dentro de la app (progreso, conteo y estado en segundo plano con foreground service).
- ✅ Seekbar funcional en drag + tap puntual (sin saltar de canción), migrado a ExoPlayer con servicio ligado.
- ✅ Filtro de audios no musicales en MediaStore (IS_MUSIC + duración + heurísticas de ruta/MIME).

### Sesión actual (renombrado y migración)
- ✅ Renombrado completo de **Savetune → BeatMyBeat** en todos los archivos de código fuente, configuración y documentación.
  - Paquetes Kotlin: `com.imontalvodev.savetune` → `com.imontalvodev.beatmybeat`
  - Clases: `SavetuneNotification` → `BeatMyBeatNotification`, `SavetuneForegroundService` → `BeatMyBeatForegroundService`, `SavetuneTheme` → `BeatMyBeatTheme`, etc.
  - Strings internos: canales de notificación, SharedPreferences keys, constantes de actions
  - Gradle: `namespace`, `applicationId`, `rootProject.name`
  - iOS: `Config.xcconfig`, `project.pbxproj`
  - Docker: nombres de contenedores y red
  - Documentación: `KMP_MIGRATION_PLAN.md`, `TESTERS_BUGS_Y_ERRORES.md`, docs de middleware y backend
- ✅ Migración del código al nuevo proyecto limpio en `app/`.
  - Copiados los 31 archivos Kotlin fuente con el paquete ya correcto.
  - Copiados todos los recursos Android (mipmap, themes, colors, xml configs).
  - `app/gradle/libs.versions.toml` actualizado con todas las dependencias reales.
  - `app/composeApp/build.gradle.kts` configurado con dependencias reales + desugaring.
  - `app/settings.gradle.kts` con JitPack añadido (necesario para NewPipeExtractor).
  - `app/composeApp/src/androidMain/AndroidManifest.xml` completo (permisos, servicios, network config).
- ✅ `frontend/` (proyecto KMP antiguo) eliminado.
- ✅ `docker-compose.home.yml` eliminado.
- ✅ Remote Git actualizado a `git@github.com:imontalvodev/BeatMyBeat.git`.
- ⏳ Pendiente: renombrar carpeta raíz del proyecto de `Savetune/` a `BeatMyBeat/` y recrear `.venv` del backend tras el movimiento.

---

## Orden de ejecución recomendado (próxima sesión)

1. Verificar que `app/` compila correctamente: `cd app && .\gradlew.bat :composeApp:assembleDebug`
2. **Bloque P1 pendiente**: B-04 (shuffle), B-05 (loop), B-07 (permisos notificaciones), B-08 (UX playlist).
3. **Bloque P2 técnico**: B-12 (acceso carpeta descargas), B-14 (persistencia tema), B-15 (scroll).
4. **Bloque P2 producto v1.0**: V-02 a V-09.
5. **Bloque P3**: F-01 en adelante.

## Nota de continuidad

El proyecto activo es `app/`. Ignorar `frontend/` salvo para consultar código original.

Bugs con mayor impacto pendiente: **B-04/B-05** (shuffle/loop) y **B-07/B-08** (feedback permisos + UX playlist). Ese bloque concentra la mayor parte de fricción reportada por testers aún sin resolver.
