# Changelog

All notable changes to BeatMyBeat are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
