# Publicar un release en GitHub

Guía para versiones de BeatMyBeat posteriores a `v1.0.0`. Repo: [imontalvodev/BeatMyBeat](https://github.com/imontalvodev/BeatMyBeat).

## 1. Versión en código

Edita `app/composeApp/build.gradle.kts`:

```kotlin
versionCode = 5        // siempre +1 respecto al release anterior
versionName = "1.0.4"  // semver; debe coincidir con el tag sin la «v»
```

| Release | versionCode | versionName | Tag |
|---------|-------------|-------------|-----|
| 1.0.0 | 1 | `1.0` | `v1.0.0` |
| 1.0.3 | 4 | `1.0.3` | `v1.0.3` |
| **próximo** | **5** | **`1.0.4`** | **`v1.0.4`** |

Actualiza también:

- `CHANGELOG.md` (raíz)
- `app/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
- Texto del release: `app/docs/release-notes-v1.0.4.md` (o crea el de la versión nueva)

## 2. Merge a `main`

```bash
git checkout develop
git pull origin develop
# commit de versión + changelog si falta
git push origin develop

# PR develop → main en GitHub, revisar y mergear
```

## 3. Compilar APK firmado

Desde `app/`:

```bash
cd app
./gradlew :composeApp:assembleRelease
```

El APK sin firmar queda en:

`composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk`

**APK firmado** (si usas keystore local, no versionado):

- Copia/rename a `composeApp/release/BeatMyBeat.apk` (carpeta gitignored), **o**
- Firma con `apksigner` / Android Studio → **Generate Signed APK**

Comprueba en el dispositivo que **Perfil → Acerca de** muestra la `versionName` correcta.

## 4. Tag en `main`

Tras el merge, en local:

```bash
git checkout main
git pull origin main
git tag -a v1.0.4 -m "BeatMyBeat 1.0.4"
git push origin v1.0.4
```

El tag debe apuntar al commit de `main` que incluye el `versionCode` / `versionName` del release.

## 5. Crear GitHub Release

1. [Releases → Draft a new release](https://github.com/imontalvodev/BeatMyBeat/releases/new)
2. **Choose a tag:** `v1.0.4`
3. **Release title:** `BeatMyBeat 1.0.4`
4. **Description:** copia desde `app/docs/release-notes-v1.0.4.md` (bloque EN o ES)
5. Adjunta **`BeatMyBeat.apk`**
6. Calcula y pega el SHA-256 en la descripción:

```bash
sha256sum composeApp/release/BeatMyBeat.apk
# o la ruta de tu APK firmado
```

7. **Publish release** (no draft)

### Con `gh` CLI (opcional)

```bash
SHA=$(sha256sum app/composeApp/release/BeatMyBeat.apk | awk '{print $1}')
gh release create v1.0.4 app/composeApp/release/BeatMyBeat.apk \
  --repo imontalvodev/BeatMyBeat \
  --title "BeatMyBeat 1.0.4" \
  --notes-file app/docs/release-notes-v1.0.4.md
# Edita la release en la web para añadir SHA-256 si no está en el .md
```

## 6. Comprobar actualizaciones in-app

La app consulta:

`https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest`

Verifica:

```bash
curl -s https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest \
  | jq '{tag_name, html_url}'
```

Prueba con un APK **anterior** instalado (p. ej. `1.0.2`):

- Al abrir la app (máx. cada 12 h), o
- **Perfil → Buscar actualizaciones** (inmediato)

Más detalle: [`actualizaciones-github.md`](./actualizaciones-github.md).

## 7. F-Droid (cuando corresponda)

Tras un release nuevo, actualiza el YAML en [fdroiddata](https://gitlab.com/fdroid/fdroiddata) con el nuevo `commit:` / `versionCode` / `CurrentVersion*`. Ver [`fdroid-publicacion.md`](./fdroid-publicacion.md).

## Checklist rápido

- [ ] `versionCode` y `versionName` en `build.gradle.kts`
- [ ] `CHANGELOG.md` + fastlane changelog
- [ ] PR `develop` → `main` mergeado
- [ ] APK firmado probado en dispositivo
- [ ] Tag `vX.Y.Z` en `main`
- [ ] GitHub Release con APK + SHA-256
- [ ] `releases/latest` devuelve el tag nuevo
- [ ] Aviso de actualización probado desde Perfil
