# Richa v2.0 — Offline Android Assistant

Richa is designed as a lightweight, offline-first Android voice assistant.

## v2 upgrades
- “Hey Richa” offline wake-word flow
- Offline Vosk speech recognition
- Local on-device memory using SharedPreferences
- Battery, RAM and free-storage queries
- Open installed apps with fuzzy name matching
- Settings, Wi-Fi and Bluetooth settings shortcuts
- Timer and dialer actions
- Floating assistant bubble
- Android Text-to-Speech with a preferred female voice when the installed TTS engine exposes one
- No cloud AI/API is required by the app

## Important Android limits
Richa cannot bypass Android's security sandbox, read passwords from secure apps, or silently control every protected system function. Features requiring special permissions are requested explicitly.

## Build an installable APK
This repository includes `.github/workflows/build-apk.yml`. On GitHub, run **Actions → Build Richa APK → Run workflow**. The workflow builds `app-debug.apk` and uploads it as an artifact.

A local Android SDK + JDK 17 + Gradle 8.13 environment is required for local compilation.
