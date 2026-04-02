# Changelog

All notable changes to this project are documented in this file.

## [2.7] - 2026-04-02

### Added
- BCC recipient batch splitting: messages with more BCC recipients than `smtp.batch.size` (default 50) are automatically split into multiple messages with a configurable delay (`smtp.batch.delay`, default 5s) between batches
- Batch-aware delivery callbacks: a single callback fires when all batches complete (or immediately on first failure)

## [2.6] - 2026-04-02

### Added
- Inline images support via `Map<String, File>` parameter, allowing direct file-based image embedding in HTML emails

## [2.5] - 2026-01-05

### Added
- `smtp.sslCheck` configuration property to control SSL hostname verification (default `true`, set to `false` for proxies/tunnels)

## [2.4] - 2026-01-05

### Added

- Full documentation in README.md
- CHANGELOG.md

## [2.3] - 2025

### Fixed
- CC/BCC parameters handling

### Added
- CC and BCC recipient support in EmailSender API

## [2.1] - 2024

### Fixed
- SSL/STARTTLS usage: port 465 now uses SSL, port 587 uses STARTTLS

## [2.0] - 2024

### Changed
- Migrated from javax.mail to jakarta.mail (angus-mail)
- Updated all dependencies and plugins

### Fixed
- Image URL handling

## [1.4] - 2023

### Fixed
- jsoup usage for HTML-to-plain-text conversion

### Changed
- Exit SMTP loop after three failed connection attempts

### Security
- Force jsoup version to address transitive dependency vulnerability

## [1.0] - Initial Release

### Added
- Asynchronous email sending via background SMTP thread
- Plain text, HTML, and URL-based email content
- File and URL attachments
- Inline image extraction and embedding for HTML emails
- HTML-to-plain-text conversion for multipart alternatives
- SMTP authentication support
- Retry mechanism with configurable delays
- Delivery status callbacks
- Multiple recipient support (comma-separated addresses)
