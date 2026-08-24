# Mobile Architecture (Android)

## Stack

Kotlin, Jetpack Compose, Material 3, CameraX, ARCore, ViewModel + StateFlow,
Coroutines, Room (local cache), DataStore (preferences), Hilt (DI).

See `docs/decisions/ADRs/ADR-001-android-native-architecture.md` for why
native Android was chosen over cross-platform.

## Module boundaries

```
android/
├── app/     UI shell, navigation, screen composables, DI wiring
├── core/    Shared models, Room database, DataStore, networking client
├── ai/      Gemma on-device inference wrapper, local memory/preferences
├── camera/  CameraX capture, ARCore session management, scan pipeline
├── spatial/ (planned) room-model geometry, measurement reconciliation
├── design/  (planned) design-state view models, constraint handling
├── shopping/(planned) product browsing, try-in-space UI
├── budget/  (planned) budget tracking UI/state
├── visualization/ (planned) rendering AI-generated room previews
├── preferences/ (planned) settings, privacy controls, local memory UI
└── ui/      (planned) shared design-system components
```

Modules marked "(planned)" are declared in `settings.gradle.kts` intent but
not yet scaffolded with source — they will be added when the corresponding
roadmap phase starts, to avoid empty ceremony modules.

## State flow

```
User action (Compose UI)
   → ViewModel (StateFlow)
   → Repository (core/)
   → NetworkClient → Backend /api/v1/*
   → Room (local cache) for offline-visible state
```

## Camera-first flow

`camera/` owns CameraX preview + capture and, where the device supports it,
an ARCore session for depth/plane data. Captured frames and any AR
measurements are packaged into a `RoomScanPayload` and sent to
`POST /api/v1/rooms/analyze` (see `docs/api/api-specification.md`).

## Build status

This module tree has been scaffolded with buildable Gradle files and Kotlin
source, but **has not been compiled** in this environment (no JDK / Android
SDK present at scaffold time). Open in Android Studio to sync and verify —
see `docs/development/development-log.md` for the exact verification gap.
