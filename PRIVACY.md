# BeatMyBeat — Privacy policy

**Last updated:** 2026-06-09

BeatMyBeat does **not** operate backend servers and does **not** collect personal data for the developer.

## What stays on your device

- Music files you download or already have on storage
- Lyrics cache (`lyrics_cache/`)
- Playlists, favorites, playback queue, and app preferences (local storage)
- Custom theme profiles

## Network use

The app connects directly from your device to third-party services when you use related features:

| Service | Purpose |
|---------|---------|
| YouTube / YouTube Music (InnerTube, oEmbed) | Search and resolve streams for download |
| `i.ytimg.com` | Thumbnails |
| [LRCLIB](https://lrclib.net) | Synced lyrics |
| [lyrics.ovh](https://lyrics.ovh) | Fallback plain lyrics |
| [GitHub Releases API](https://api.github.com/repos/imontalvodev/BeatMyBeat/releases/latest) | Optional in-app check for newer app versions (at most once every 12 hours) |

No account is required. BeatMyBeat does not send your library or listening history to the developer.

## Analytics and tracking

- No Firebase, Crashlytics, Sentry, ads, or analytics SDKs
- Debug logging is disabled in release builds

## Permissions

- **Internet** — downloads, lyrics, stream resolution
- **Read media audio** — scan local music library
- **Notifications** — download and playback progress (optional at runtime on Android 13+)
- **Foreground service** — playback and background downloads

## Contact

Issues and privacy questions: [GitHub Issues](https://github.com/imontalvodev/BeatMyBeat/issues)

Source code: [https://github.com/imontalvodev/BeatMyBeat](https://github.com/imontalvodev/BeatMyBeat)
