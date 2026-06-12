# Changelog

All notable changes to BeatMyBeat are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.4] — 2026-06-12

### Fixed
- Update check: do not record a successful check when the GitHub API request fails (avoids a 12 h silent cooldown)
- Profile: **Check for updates** runs an immediate GitHub Releases check

## [1.0.3] — 2026-06-12

### Changed
- Release packaging and in-app update check validation

### Notes
- Same features as 1.0.2 (profile layout fix + GitHub release update prompt)

## [1.0.2] — 2026-06-11

### Added
- In-app update prompt: checks GitHub Releases on startup and notifies when a newer version is published

## [1.0.1] — 2026-06-11

### Fixed
- Profile screen layout on small devices (scroll + responsive header sizing)

## [1.0] — 2026-06-09

### Added
- Local music player with queue, shuffle, repeat, playlists, and favorites
- YouTube / YouTube Music search and audio download (multiple formats)
- Synced lyrics (LRCLIB) with on-device cache and batch download
- Custom themes, multilingual UI (ES, EN, PT, DE, HR)
- Background download and lyrics batch services

### Notes
- First public release candidate for GitHub and F-Droid distribution
- License: GPL-3.0-only
