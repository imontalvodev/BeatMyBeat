# Publicar un release en GitHub

Guía para versiones de BeatMyBeat. Repo: [imontalvodev/BeatMyBeat](https://github.com/imontalvodev/BeatMyBeat).

## Historial de versiones publicadas

| Tag | versionName | versionCode | Notas |
|-----|-------------|-------------|--------|
| `v1.0.0` | `1.0` | 1 | Primer release público |
| `v1.0.1` | `1.0.1` | 2 | Perfil, actualizaciones, beatmybeat.com |
| **`v1.0.2`** | **`1.0.2`** | **3** | **Próximo release** (descarga APK desde GitHub, cola, analizar) |

> Si publicaste por error un tag intermedio (p. ej. `v1.0.3`), bórralo o desmárcalo como *Latest* en GitHub antes de publicar la versión correcta, para que `releases/latest` y el aviso in-app apunten al release bueno.

## 1. Versión en código

Edita `app/composeApp/build.gradle.kts` (o Android Studio → **Module** → `composeApp` → **defaultConfig**):

```kotlin
versionCode = 3        // siempre +1 respecto al release anterior en GitHub
versionName = "1.0.2"
```

Actualiza también:

- `CHANGELOG.md` (raíz)
- `app/fastlane/metadata/android/en-US/changelogs/3.txt` (nombre = `versionCode`)
- Texto del release: [`release-notes-v1.0.2.md`](./release-notes-v1.0.2.md)

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

Comprueba en el dispositivo: **Perfil → Acerca de** → `1.0.2`.

## 4. Tag en `main`

```bash
git checkout main
git pull origin main
git tag -a v1.0.2 -m "BeatMyBeat 1.0.2"
git push origin v1.0.2
```

## 5. Crear GitHub Release

1. [Draft a new release](https://github.com/imontalvodev/BeatMyBeat/releases/new)
2. **Tag:** `v1.0.2`
3. **Title:** `BeatMyBeat 1.0.2`
4. **Description:** copia la sección **English** o **Español** de [`release-notes-v1.0.2.md`](./release-notes-v1.0.2.md) (desde el encabezado de idioma hasta **Links** / **Enlaces**)
5. Adjunta **`BeatMyBeat.apk`** (nombre exacto recomendado para la descarga in-app)
6. SHA-256 en la descripción:

```bash
sha256sum app/composeApp/release/BeatMyBeat.apk
```

7. Marca como **Latest** y publica.

### Con `gh` CLI (opcional)

Pega manualmente el texto de la sección deseada en `--notes-file` o en `--notes`.

```bash
gh release create v1.0.2 app/composeApp/release/BeatMyBeat.apk \
  --repo imontalvodev/BeatMyBeat \
  --title "BeatMyBeat 1.0.2" \
  --notes-file app/docs/release-notes-v1.0.2.md
```

> `gh` incluirá el documento completo; si prefieres solo un idioma, copia esa sección a un archivo temporal.

## 6. Probar actualizaciones in-app

La app consulta `https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest` y compara versiones.

Con **`1.0.1`** instalado y **`v1.0.2`** en GitHub:

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

- [ ] `versionCode = 3`, `versionName = "1.0.2"`
- [ ] `CHANGELOG.md` + fastlane `changelogs/3.txt`
- [ ] PR `develop` → `main`
- [ ] APK firmado probado
- [ ] Tag `v1.0.2` + Release con **`BeatMyBeat.apk`** y SHA-256
- [ ] `releases/latest` → `v1.0.2`
- [ ] Aviso de actualización probado (1.0.1 → 1.0.2): descarga + instalador
