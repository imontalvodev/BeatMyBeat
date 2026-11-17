#!/usr/bin/env bash
# Sube NewPipe Extractor a $1 (p. ej. v0.27.0) y prepara la versión patch siguiente de la app:
# catálogo de versiones, versionCode/versionName, CHANGELOG, fastlane y notas de release.
# Deja en $GITHUB_OUTPUT (o stdout) old_ext, new_app, new_code y notes_file. No hace git.
set -euo pipefail

NEW_EXT="${1:?uso: bump-newpipe.sh vX.Y.Z}"
[[ "$NEW_EXT" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "versión inválida: $NEW_EXT" >&2; exit 2; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOML="$ROOT/app/gradle/libs.versions.toml"
GRADLE="$ROOT/app/composeApp/build.gradle.kts"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"

OLD_EXT="$(sed -nE 's/^newpipeextractor = "(v[^"]+)".*/\1/p' "$TOML")"
OLD_APP="$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' "$GRADLE")"
OLD_CODE="$(sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' "$GRADLE")"
[[ -n "$OLD_EXT" && -n "$OLD_APP" && -n "$OLD_CODE" ]] || { echo "no pude leer versiones actuales" >&2; exit 2; }

if [[ "$(printf '%s\n%s\n' "$OLD_EXT" "$NEW_EXT" | sort -V | tail -1)" != "$NEW_EXT" || "$OLD_EXT" == "$NEW_EXT" ]]; then
  echo "$NEW_EXT no es posterior a $OLD_EXT" >&2
  exit 3
fi

# 1.3 -> 1.3.1, 1.3.1 -> 1.3.2
IFS=. read -r MAJ MIN PAT <<<"$OLD_APP"
NEW_APP="$MAJ.$MIN.$(( ${PAT:-0} + 1 ))"
NEW_CODE=$(( OLD_CODE + 1 ))
TODAY="$(date -u +%F)"

sed -i -E "s/^(newpipeextractor = \")v[^\"]+/\1$NEW_EXT/" "$TOML"
sed -i -E "s/^( *versionCode = )[0-9]+/\1$NEW_CODE/; s/^( *versionName = \")[^\"]+/\1$NEW_APP/" "$GRADLE"

# CHANGELOG: nueva sección justo debajo de [Unreleased]
python3 - "$ROOT/CHANGELOG.md" "$NEW_APP" "$TODAY" "$OLD_EXT" "$NEW_EXT" <<'PY'
import sys
path, app, today, old, new = sys.argv[1:]
text = open(path, encoding="utf-8").read()
entry = (
    f"## [{app}] — {today}\n\n"
    f"### Fixed\n"
    f"- **YouTube downloads and search.** Updated NewPipe Extractor from {old[1:]} to {new[1:]} "
    f"to keep up with YouTube changes. No other changes.\n\n"
)
marker = "## [Unreleased]\n\n"
assert marker in text, "CHANGELOG sin sección [Unreleased]"
open(path, "w", encoding="utf-8").write(text.replace(marker, marker + entry, 1))
PY

FASTLANE="$ROOT/app/fastlane/metadata/android"
echo "Updated NewPipe Extractor ($NEW_EXT) to keep YouTube search and downloads working." > "$FASTLANE/en-US/changelogs/$NEW_CODE.txt"
echo "Actualizado NewPipe Extractor ($NEW_EXT) para que la búsqueda y las descargas de YouTube sigan funcionando." > "$FASTLANE/es-ES/changelogs/$NEW_CODE.txt"

NOTES="$ROOT/app/docs/release-notes-v$NEW_APP.md"
cat > "$NOTES" <<EOF
# BeatMyBeat $NEW_APP

Notas para el [GitHub Release](https://github.com/imontalvodev/BeatMyBeat/releases/new) · tag \`v$NEW_APP\`

Generado automáticamente al publicarse NewPipe Extractor $NEW_EXT.

---

## English

**Title:** BeatMyBeat $NEW_APP — YouTube compatibility update

### Fixed

- **YouTube downloads and search.** Updated NewPipe Extractor from ${OLD_EXT#v} to ${NEW_EXT#v} to keep up with YouTube changes. No other changes.

Updating keeps all your data (library, playlists, favorites, lyrics) — no need to uninstall.

### Links

- [Full changelog](https://github.com/imontalvodev/BeatMyBeat/blob/main/CHANGELOG.md)
- [Privacy policy](https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md)
- [Source code](https://github.com/imontalvodev/BeatMyBeat)

---

## Español

**Título:** BeatMyBeat $NEW_APP — Actualización de compatibilidad con YouTube

### Corregido

- **Descargas y búsqueda de YouTube.** Actualizado NewPipe Extractor de ${OLD_EXT#v} a ${NEW_EXT#v} para seguir el ritmo de los cambios de YouTube. Sin más cambios.

Al actualizar se conservan todos tus datos (biblioteca, playlists, favoritos, letras); no hace falta desinstalar.

### Enlaces

- [Changelog completo](https://github.com/imontalvodev/BeatMyBeat/blob/main/CHANGELOG.md)
- [Política de privacidad](https://github.com/imontalvodev/BeatMyBeat/blob/main/PRIVACY.md)
- [Código fuente](https://github.com/imontalvodev/BeatMyBeat)
EOF

{
  echo "old_ext=$OLD_EXT"
  echo "new_app=$NEW_APP"
  echo "new_code=$NEW_CODE"
  echo "notes_file=${NOTES#"$ROOT/"}"
} >> "$OUT"
