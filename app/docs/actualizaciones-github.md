# Comprobación de actualizaciones (GitHub Releases)

## Cómo funciona en la app

| Paso | Detalle |
|------|---------|
| API | `GET https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest` |
| Versión instalada | `BuildConfig.VERSION_NAME` (p. ej. `1.0.3`) |
| Versión remota | `tag_name` del release sin prefijo `v` (p. ej. `v1.0.3` → `1.0.3`) |
| Comparación | `VersionCompare.isNewer(remota, instalada)` — semver numérico |
| Frecuencia | Como máximo cada **12 horas** (`UpdatePrefs`) |
| UI | `ReleaseUpdatePrompt` en `MainActivity` — diálogo al arrancar |
| Acción al actualizar | Descarga `BeatMyBeat-{versión}.apk` a **Descargas** y abre el instalador al terminar |
| APK en el release | Se usa `BeatMyBeat.apk` del array `assets` de la API (o el primer `.apk` disponible) |
| Fallback | Si el release no tiene APK adjunta, abre la página del release en GitHub |

Código: `ReleaseUpdateClient.kt`, `UpdateChecker.kt`, `ReleaseUpdatePrompt.kt`, `ApkUpdateDownloader.kt`, `ApkUpdateInstaller.kt`.

## Cuándo aparece el aviso

Solo si **la versión del latest release en GitHub es mayor** que la instalada.

**Perfil → Buscar actualizaciones** fuerza la comprobación (sin esperar 12 h) y distingue error de red de «ya actualizado».

### Comportamiento al pulsar «Descargar actualización» (1.0.2+)

1. `DownloadManager` descarga la APK desde `browser_download_url` del asset en GitHub.
2. Al completarse, `ApkUpdateDownloadReceiver` / `MainActivity.onResume` llama a `ApkUpdateInstaller`.
3. Se lanza `ACTION_VIEW` con la APK descargada (instalador del sistema).
4. Si falta permiso de **instalar apps desconocidas**, se abre Ajustes; al volver, se reintenta la instalación.

### Bug corregido (1.0.1+)

Si la petición a GitHub **fallaba** (red, rate limit), antes se guardaba igual la hora de comprobación y **no volvía a intentar durante 12 h**. Ahora solo se registra el intervalo cuando la API responde correctamente.

| Instalada | Latest en GitHub | ¿Aviso? |
|-----------|------------------|---------|
| `1.0.3` | `1.0.3` | No |
| `1.0.2` | `1.0.3` | **Sí** |
| `1.0.3` | `1.0.2` | No (vas por delante) |

## Cómo probar el aviso de actualización

1. **En el dispositivo:** instala un APK **antiguo** (p. ej. `1.0.2` del release anterior en GitHub).
2. **En GitHub:** publica release **`v1.0.3`** (o cualquier versión **posterior** a la instalada) con **`BeatMyBeat.apk`** adjunto.
3. **En el dispositivo:** borra datos de BeatMyBeat *o* espera 12 h (o desinstala/reinstala para resetear `UpdatePrefs`).
4. Abre la app → debe salir el diálogo con la versión nueva.
5. Pulsa **Descargar actualización** → la APK aparece en Descargas y se abre el instalador.

> Si compilas e instalas `1.0.3` mientras GitHub sigue en `1.0.2`, **no verás aviso** (comportamiento correcto).

## Reset rápido del intervalo de 12 h (desarrollo)

```bash
adb shell run-as com.imontalvodev.beatmybeat rm /data/data/com.imontalvodev.beatmybeat/shared_prefs/beatmybeat_update_prefs.xml
```

O: Ajustes → Apps → BeatMyBeat → Almacenamiento → Borrar datos.

## Verificar el endpoint manualmente

```bash
curl -s https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest \
  | jq '{tag_name, html_url, draft, prerelease, apk: [.assets[]? | select(.name | endswith(".apk")) | {name, browser_download_url}]}'
```

Último release publicado: comprobar con el comando anterior (`tag_name` y URL del APK).

## Publicar release en GitHub

Ver [`github-release.md`](./github-release.md) y [`release-notes-v1.0.3.md`](./release-notes-v1.0.3.md) para el texto del release.
