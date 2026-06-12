# Publicar BeatMyBeat en F-Droid

Guía paso a paso para la primera inclusión en el repositorio oficial de [F-Droid](https://f-droid.org).

## Requisitos previos (en este repo)

| Elemento | Ubicación |
|----------|-----------|
| Licencia GPL-3.0 | `LICENSE` (raíz) |
| Política de privacidad | `PRIVACY.md` |
| Changelog | `CHANGELOG.md` |
| Descripciones tienda | `app/fastlane/metadata/android/` |
| Plantilla fdroiddata | `app/docs/fdroid/com.imontalvodev.beatmybeat.yml` |
| Build release | `cd app && ./gradlew :composeApp:assembleRelease` |

## 1. Preparar release en GitHub

Guía completa: [`github-release.md`](./github-release.md).

Resumen:

1. Sube `versionCode` / `versionName` en `composeApp/build.gradle.kts`.
2. Actualiza `CHANGELOG.md` y fastlane changelog.
3. Merge `develop` → `main`.
4. Tag `vX.Y.Z` y **GitHub Release** con APK firmado + SHA-256.
5. F-Droid compila desde el tag; el APK de GitHub es para instalación directa.

## 2. Assets gráficos (pendiente manual)

Añade en `app/fastlane/metadata/android/en-US/images/`:

| Archivo | Tamaño |
|---------|--------|
| `icon.png` | 512×512 — **ya incluido** (desde `app/docs/assets/logo.png`) |
| `featureGraphic.png` | 1024×500 — opcional |
| `phoneScreenshots/*.png` | Capturas reales del reproductor, descargas y letras |

## 3. Fork de fdroiddata

1. Cuenta en [GitLab](https://gitlab.com).
2. Fork de [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata).
3. Rama nueva: `beatmybeat`.
4. Copia `app/docs/fdroid/com.imontalvodev.beatmybeat.yml` → `metadata/com.imontalvodev.beatmybeat.yml`.
5. Ajusta `commit:` al SHA o tag exacto (`v1.0.0`).

### Instalar fdroidserver (opcional, recomendado)

```bash
sudo apt install fdroidserver   # o pip install fdroidserver
fdroid readmeta
fdroid lint com.imontalvodev.beatmybeat
fdroid build -v -l com.imontalvodev.beatmybeat
```

## 4. Merge request

1. Push de tu rama al fork.
2. MR en GitLab hacia `fdroid/fdroiddata` master.
3. Título sugerido: `New App: BeatMyBeat (com.imontalvodev.beatmybeat)`.
4. En la descripción:
   - Enlace al repo y tag
   - Resumen de funciones
   - Anti-feature `NonFreeNet` (YouTube + LRCLIB + lyrics.ovh)
   - Sin analíticas ni servidores propios
   - `subdir: app`, módulo Gradle `:composeApp`

## 5. Qué esperar en la revisión

- **Plazo:** semanas o meses; los maintainers son voluntarios.
- **ffmpeg-kit:** pueden pedir aclaraciones sobre binarios nativos (LGPL/GPL).
- **JitPack / NewPipe:** posible `prebuild` para compilar Extractor desde fuente.
- **Reproducible builds:** `dependenciesInfo` desactivado en `build.gradle.kts` para facilitarlo.

## Alternativa más rápida: IzzyOnDroid

Si quieres visibilidad FOSS antes de entrar en F-Droid oficial:

- [IzzyOnDroid](https://apt.izzysoft.de/fdroid/) — MR similar, revisión más ágil.
- Misma plantilla YAML con repo Izzy.

## Checklist rápido

- [ ] Tag `v1.0.0` en GitHub
- [x] Icon 512 en fastlane (`en-US/images/icon.png`)
- [x] Screenshots en `en-US/images/phoneScreenshots/` (Cap1–Cap7.jpeg)
- [ ] MR fdroiddata con YAML validado (`fdroid lint`)
- [ ] Respuesta a comentarios de maintainers (ffmpeg, build, descripción)

Más contexto legal: [`riesgos-legales.md`](./riesgos-legales.md).
