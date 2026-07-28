# Changelog

All notable changes to BeatMyBeat are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.2] — 2026-07-28

### Added
- **Playback queue screen.** "Up next" is now a dedicated full-screen view instead of a bottom sheet, with drag-to-reorder on the up-next list (long-press the handle and drag).
- **Add to playlist screen.** Also a dedicated full-screen view now: search when you have many playlists, a collapsible "create new playlist" row kept separate from picking an existing one, and each playlist shows a cover mosaic built from its first songs.
- **Screen stays on** while BeatMyBeat is in the foreground.

### Changed
- **Karaoke Mode is voice-recording-free.** Recording your own takes over a song — added in 1.1 — has been removed, along with the **microphone** permission. Pitch/speed transposition and synced-lyrics highlighting are unaffected.

### Removed
- **Microphone permission** and everything that used it (recording, saved takes, per-song takes list, storage usage in Profile).

## [1.0.4] — 2026-07-17

### Added
- **In-app updates:** old pending/downloaded APK is now removed before starting a new update download; after downloading, the app asks for confirmation before installing (in-app dialog + notification action) instead of launching the installer automatically; the download now shows a graphical progress indicator

### Fixed
- **Playback:** playback errors (moved/deleted file, unsupported format) and a corrupted playback queue now show a clear message instead of silently doing nothing
- **Downloads:** M4A tags (title/artist/cover art) are now written in the correct place inside the file — previous downloads showed no metadata in most players despite the app reporting success
- **Downloads:** chunked downloads can no longer duplicate/corrupt the output file when the source doesn't support partial content (HTTP range) requests
- **Library & lyrics:** fixed a few background race conditions in library sync and lyrics fetching that could show stale data or waste network requests
- **Update check:** version comparison is now more robust against non-standard release tags

### Security
- **In-app updates:** the downloaded APK is now verified to be BeatMyBeat's own package before installing (an unexpected release asset is rejected), and the installer no longer exposes raw `file://` URIs

## [1.0.3] — 2026-06-11

### Fixed
- **Playback queue:** adding tracks to the end of the queue and **Play next** work correctly again (session queue stays in sync with the UI and ExoPlayer)
- **Library metadata:** artist names no longer show mojibake from YouTube tags (e.g. `Â€¢` instead of a bullet separator)

## [1.0.2] — 2026-06-11

### Added
- **In-app updates:** **Download update** fetches `BeatMyBeat.apk` from GitHub Releases and opens the system installer when the download finishes

### Fixed
- **Playback queue:** resuming the app no longer resets the queue to the first track; shuffle order and position are restored
- **Queue counter:** “tracks in queue” and “Up next” counts update correctly while shuffle is on
- **Analyze screen:** toast when URL or song field is empty before running analysis

## [1.0.1] — 2026-06-12

### Added
- In-app update check via GitHub Releases (on startup, max. every 12 h)
- **Profile → Check for updates** for an immediate version check
- Update dialog opens [beatmybeat.com](https://beatmybeat.com) to download the new APK

### Fixed
- Profile screen layout on small devices (scroll + responsive header)
- Update check no longer blocks retries for 12 h when the GitHub API request fails

## [1.0] — 2026-06-09

### Added
- Local music player with queue, shuffle, repeat, playlists, and favorites
- YouTube / YouTube Music search and audio download (multiple formats)
- Synced lyrics (LRCLIB) with on-device cache and batch download
- Custom themes, multilingual UI (ES, EN, PT, DE, HR)
- Background download and lyrics batch services

### Notes
- First public release on GitHub (`v1.0.0`)
- License: GPL-3.0-only
