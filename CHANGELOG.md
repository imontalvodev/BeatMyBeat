# Changelog

All notable changes to BeatMyBeat are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
