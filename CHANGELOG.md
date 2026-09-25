# Changelog

All notable changes to BeatMyBeat are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed
- **UI redesign.** The app opens straight into the library (the start-up "Download / Go to player" choice screen is gone). Tabs are now Library · Download · Settings, and switch with a fade instead of a sideways slide.
- **Library:** large title with song count, a filled search field, Songs / Favorites / Playlists chips (no more combined dropdown), sort behind an icon, and separate **Play** and **Shuffle** buttons. Empty favorites, empty playlists and searches with no results now explain what to do.
- **Multi-select:** long-pressing a song opens a contextual bar with the count, "select all" and every bulk action, instead of hiding them behind the ⋮ of a selected row.
- **Mini player:** floating card with play/pause and next; tap or swipe up to open the full player. The thin progress line is display-only, so a stray tap no longer seeks.
- **Full player:** the bottom navigation hides while it is open; new favorite (heart) and queue buttons; lyrics actions moved to a ⋮ menu; "repeat one" has its own icon; slimmer seek bar; clear "Find lyrics" button when a song has no lyrics.
- **Queue:** same row style as the library, tap a row to play it, smoother drag-to-reorder with haptics.
- **Download & Settings:** screen titles instead of the repeated logo, settings grouped into sections, a language picker that shows the current language, sentence-case buttons.

- **Downloads keep the original quality.** M4A and AAC are copied straight from YouTube's AAC stream and OGG from its Opus stream, with no re-encoding. MP3/FLAC/WAV are converted in a single pass from the best source, instead of going through an intermediate 192 kbps MP3.
- **Clear download errors.** Age-restricted, region-blocked, private, Premium-only and rate-limited videos now say so instead of "try again".
- **Synced lyrics first.** LRCLIB lookups now prefer time-synced lyrics over plain text, use the real song duration for downloads, remember instrumental tracks, and don't cache a temporary LRCLIB outage as "no lyrics".
- **Cleaner tags.** Songs found on YouTube Music get the right artist and album (the artist used to include "• Album • 2:06"), and video titles like "Artist - Song (Official Video)" are split properly.
- Paste YouTube links without `https://`; "Mix" links download the song instead of failing as a playlist.

### Fixed
- Several strings that were hard-coded in Spanish (favorites menu item, playlist labels) are now translated in every supported language.
- **Truncated downloads.** A network hiccup mid-download used to save a cut-off song as if it were complete; chunks are now retried and incomplete files are discarded.
- Cancelling a download now stops it immediately.
- Playlist links pick up every track (not just the first ~100) and no longer include unrelated recommended videos.
- Streams delivered as DASH/HLS manifests or dubbed audio tracks are no longer chosen for download.

### Internal
- Shared, platform-independent logic (LRCLIB and lyrics.ovh clients on Ktor, lyrics matching, LRC parsing, YouTube link/metadata parsing, stream selection, ffmpeg arguments, ranged downloads) moved to Kotlin Multiplatform `commonMain` with `commonTest` coverage.
- NewPipe Extractor 0.26.5.

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
