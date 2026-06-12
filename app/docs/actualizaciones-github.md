# Comprobación de actualizaciones (GitHub Releases)

## Cómo funciona en la app

| Paso | Detalle |
|------|---------|
| API | `GET https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest` |
| Versión instalada | `BuildConfig.VERSION_NAME` (p. ej. `1.0.3`) |
| Versión remota | `tag_name` del release sin prefijo `v` (p. ej. `v1.0.0` → `1.0.0`) |
| Comparación | `VersionCompare.isNewer(remota, instalada)` — semver numérico |
| Frecuencia | Como máximo cada **12 horas** (`UpdatePrefs`) |
| UI | `ReleaseUpdatePrompt` en `MainActivity` — diálogo al arrancar |

Código: `ReleaseUpdateClient.kt`, `UpdateChecker.kt`, `ReleaseUpdatePrompt.kt`.

## Cuándo aparece el aviso

Solo si **la versión del latest release en GitHub es mayor** que la instalada.

| Instalada | Latest en GitHub | ¿Aviso? |
|-----------|------------------|---------|
| `1.0.0` | `1.0.0` | No |
| `1.0.0` | `1.0.3` | **Sí** |
| `1.0.3` | `1.0.0` | No (vas por delante) |
| `1.0.2` | `1.0.3` | **Sí** |

## Cómo probar el aviso de actualización

1. **En el dispositivo:** instala un APK **antiguo** (p. ej. `1.0.0` del release actual en GitHub).
2. **En GitHub:** publica release **`v1.0.3`** (o cualquier versión **posterior** a la instalada).
3. **En el dispositivo:** borra datos de BeatMyBeat *o* espera 12 h (o desinstala/reinstala para resetear `UpdatePrefs`).
4. Abre la app → debe salir el diálogo con la versión nueva y enlace al release.

> Si compilas e instalas `1.0.3` mientras GitHub sigue en `1.0.0`, **no verás aviso** (comportamiento correcto).

## Reset rápido del intervalo de 12 h (desarrollo)

```bash
adb shell run-as com.imontalvodev.beatmybeat rm /data/data/com.imontalvodev.beatmybeat/shared_prefs/beatmybeat_update_prefs.xml
```

O: Ajustes → Apps → BeatMyBeat → Almacenamiento → Borrar datos.

## Verificar el endpoint manualmente

```bash
curl -s https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest \
  | jq '{tag_name, html_url, draft, prerelease}'
```

Respuesta actual (ejemplo): `tag_name: "v1.0.0"`.

Tras publicar `v1.0.3`, el mismo comando debe devolver `v1.0.3`.

## Publicar release en GitHub (resumen)

Ver `app/docs/release-notes-v1.0.3.md` para texto listo para pegar.
