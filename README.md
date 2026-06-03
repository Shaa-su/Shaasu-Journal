# Shaasu Journal 📖

A private, offline Android journaling app with mood tracking, goals, and rich story entries.

## Features
- 📝 Daily entries with images and custom wallpapers
- 😊 Mood tracking with monthly statistics
- 🎯 Goal setting with progress tracking
- 🔔 Reminders with custom time picker
- 🔒 Export/Import with encryption
- 📅 Calendar view with filters

## Tech Stack
- Java (Android)
- AndroidX + Material Design Components
- SharedPreferences (local storage)

## Security
**Updated May 2026**
- 🔒 Stories stored with `EncryptedSharedPreferences` (AES-256-GCM via Android Keystore)
- 📦 Exports encrypted with AES-GCM + PBKDF2/HMAC-SHA256 (password-derived key)
- 🔄 Auto-migration from legacy plaintext storage to encrypted store
- ⚠️ Encrypted backups are not recoverable if the export password is forgotten

## Getting Started

### Prerequisites
- Android Studio
- Android SDK

### Run
1. Clone the repo and open in Android Studio
2. Sync Gradle
3. Run on an emulator or physical device

## Build
```bash
./gradlew assembleDebug
```
## Upcoming Features
- 🗄️ Vault — a hidden section accessible via menu; tapping the Vault button prompts PIN or Biometric authentication to access stored passwords, account info, and private text/images separate from journal entries
- 📅 Events — add past or upcoming events to the calendar with user notifications
- 🌤️ Weather Log — view upcoming weather tied to calendar dates
- ☁️ Dropbox Integration — one-click encrypted backup and restore; auto-save entries to Dropbox with a single recover button
  
## License
Private — all rights reserved.
