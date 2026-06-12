# Publicar un release en GitHub

Guía para versiones de BeatMyBeat. Repo: [imontalvodev/BeatMyBeat](https://github.com/imontalvodev/BeatMyBeat).

## Historial de versiones publicadas

| Tag | versionName | versionCode | Notas |
|-----|-------------|-------------|--------|
| `v1.0.0` | `1.0` | 1 | Primer release público |
| **`v1.0.1`** | **`1.0.1`** | **2** | **Próximo release** (perfil, actualizaciones, beatmybeat.com) |

> Si publicaste por error un tag intermedio (p. ej. `v1.0.3`), bórralo o desmárcalo como *Latest* en GitHub antes de publicar `v1.0.1`, para que `releases/latest` y el aviso in-app apunten a la versión correcta.

## 1. Versión en código

Edita `app/composeApp/build.gradle.kts`:

```kotlin
versionCode = 2        // siempre +1 respecto al release anterior en GitHub
versionName = "1.0.1"
```

Actualiza también:

- `CHANGELOG.md` (raíz)
- `app/fastlane/metadata/android/en-US/changelogs/2.txt`
- Texto del release: [`release-notes-v1.0.1.md`](./release-notes-v1.0.1.md)

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

Comprueba en el dispositivo: **Perfil → Acerca de** → `1.0.1`.

## 4. Tag en `main`

```bash
git checkout main
git pull origin main
git tag -a v1.0.1 -m "BeatMyBeat 1.0.1"
git push origin v1.0.1
```

## 5. Crear GitHub Release

1. [Draft a new release](https://github.com/imontalvodev/BeatMyBeat/releases/new)
2. **Tag:** `v1.0.1`
3. **Title:** `BeatMyBeat 1.0.1`
4. **Description:** copia desde [`release-notes-v1.0.1.md`](./release-notes-v1.0.1.md)
5. Adjunta **`BeatMyBeat.apk`**
6. SHA-256 en la descripción:

```bash
sha256sum app/composeApp/release/BeatMyBeat.apk
```

7. Marca como **Latest** y publica.

### Con `gh` CLI (opcional)

```bash
gh release create v1.0.1 app/composeApp/release/BeatMyBeat.apk \
  --repo imontalvodev/BeatMyBeat \
  --title "BeatMyBeat 1.0.1" \
  --notes-file app/docs/release-notes-v1.0.1.md
```

## 6. Probar actualizaciones in-app

La app consulta `https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest` y compara versiones.

Con **`1.0.0`** instalado y **`v1.0.1`** en GitHub:

```bash
curl -s https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest \
  | jq '{tag_name, html_url}'
```

- Al abrir la app, o
- **Perfil → Buscar actualizaciones**

El botón del aviso abre **https://beatmybeat.com** (no GitHub).

Detalle: [`actualizaciones-github.md`](./actualizaciones-github.md).

## 7. F-Droid

Tras publicar, actualiza el YAML en fdroiddata (`commit`, `versionCode`, `CurrentVersion*`). Ver [`fdroid-publicacion.md`](./fdroid-publicacion.md).

## Checklist

- [ ] `versionCode = 2`, `versionName = "1.0.1"`
- [ ] `CHANGELOG.md` + fastlane `changelogs/2.txt`
- [ ] PR `develop` → `main`
- [ ] APK firmado probado
- [ ] Tag `v1.0.1` + Release con APK y SHA-256
- [ ] `releases/latest` → `v1.0.1`
- [ ] Aviso de actualización probado (1.0.0 → 1.0.1)
