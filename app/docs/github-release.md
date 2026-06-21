# Publicar un release en GitHub

Guía para versiones de BeatMyBeat. Repo: [imontalvodev/BeatMyBeat](https://github.com/imontalvodev/BeatMyBeat).

## Historial de versiones publicadas

| Tag | versionName | versionCode | Notas |
|-----|-------------|-------------|--------|
| `v1.0.0` | `1.0` | 1 | Primer release público |
| `v1.0.1` | `1.0.1` | 2 | Perfil, aviso de actualización, beatmybeat.com |
| `v1.0.2` | `1.0.2` | 3 | Descarga APK desde GitHub, cola al reabrir, analizar |
| **`v1.0.3`** | **`1.0.3`** | **4** | **Próximo release** (cola manual, metadatos artista) |

> `releases/latest` y el aviso in-app deben apuntar al tag marcado como **Latest** en GitHub.

## 1. Versión en código

Edita `app/composeApp/build.gradle.kts` (o Android Studio → **Module** → `composeApp` → **defaultConfig**):

```kotlin
versionCode = 4        // siempre +1 respecto al release anterior en GitHub
versionName = "1.0.3"
```

Actualiza también:

- `CHANGELOG.md` (raíz)
- `app/fastlane/metadata/android/en-US/changelogs/4.txt` (nombre = `versionCode`)
- Texto del release: [`release-notes-v1.0.3.md`](./release-notes-v1.0.3.md)

## 2. Merge a `main`

```bash
git checkout develop
git pull origin develop
git push origin develop

# PR develop → main en GitHub → merge
```

## 3. Compilar APK firmado

```bash
cd app
./gradlew :composeApp:assembleRelease
```

APK sin firmar: `composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk`

Copia el APK **firmado** a `composeApp/release/BeatMyBeat.apk` (gitignored) o fírmalo con Android Studio.

Comprueba en el dispositivo: **Perfil → Acerca de** → `1.0.3`.

## 4. Tag en `main`

```bash
git checkout main
git pull origin main
git tag -a v1.0.3 -m "BeatMyBeat 1.0.3"
git push origin v1.0.3
```

## 5. Crear GitHub Release

1. [Draft a new release](https://github.com/imontalvodev/BeatMyBeat/releases/new)
2. **Tag:** `v1.0.3`
3. **Title:** `BeatMyBeat 1.0.3`
4. **Description:** copia la sección **English** o **Español** de [`release-notes-v1.0.3.md`](./release-notes-v1.0.3.md) (desde el encabezado de idioma hasta **Links** / **Enlaces**)
5. Adjunta **`BeatMyBeat.apk`** (nombre exacto recomendado para la descarga in-app)
6. SHA-256 en la descripción:

```bash
sha256sum app/composeApp/release/BeatMyBeat.apk
```

7. Marca como **Latest** y publica.

### Con `gh` CLI (opcional)

Pega manualmente el texto de la sección deseada en `--notes-file` o en `--notes`.

```bash
gh release create v1.0.3 app/composeApp/release/BeatMyBeat.apk \
  --repo imontalvodev/BeatMyBeat \
  --title "BeatMyBeat 1.0.3" \
  --notes-file app/docs/release-notes-v1.0.3.md
```

> `gh` incluirá el documento completo; si prefieres solo un idioma, copia esa sección a un archivo temporal.

## 6. Probar actualizaciones in-app

La app consulta `https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest` y compara versiones.

Con **`1.0.2`** instalado y **`v1.0.3`** en GitHub:

```bash
curl -s https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest \
  | jq '{tag_name, html_url, apk: [.assets[]? | select(.name | endswith(".apk")) | .name]}'
```

- Al abrir la app, o
- **Perfil → Buscar actualizaciones**

El botón **Descargar actualización** baja la APK desde GitHub y abre el instalador al terminar.

Detalle: [`actualizaciones-github.md`](./actualizaciones-github.md).

## 7. F-Droid

Tras publicar, actualiza el YAML en fdroiddata (`commit`, `versionCode`, `CurrentVersion*`). Ver [`fdroid-publicacion.md`](./fdroid-publicacion.md).

## Checklist

- [ ] `versionCode = 4`, `versionName = "1.0.3"`
- [ ] `CHANGELOG.md` + fastlane `changelogs/4.txt`
- [ ] PR `develop` → `main`
- [ ] APK firmado probado
- [ ] Tag `v1.0.3` + Release con **`BeatMyBeat.apk`** y SHA-256
- [ ] `releases/latest` → `v1.0.3`
- [ ] Aviso de actualización probado (1.0.2 → 1.0.3): descarga + instalador
