# ADR-001: Android-Native Client Architecture

## Context
The client needs CameraX + ARCore access, on-device Gemma inference via
MediaPipe/AI Edge, and smooth camera-first UX. Product spec section 57
specifies Kotlin/Compose explicitly.

## Options considered
- Cross-platform (Flutter/React Native) with native plugins for camera/AR/Gemma
- Native Android (Kotlin + Jetpack Compose)

## Decision
Native Android (Kotlin + Jetpack Compose, CameraX, ARCore, Hilt, Room,
DataStore, Coroutines/StateFlow).

## Reason
ARCore and MediaPipe LLM Inference have first-class, best-supported Android
SDKs; cross-platform would require maintaining native bridge plugins for
exactly the two hardest parts of the app (AR + on-device inference) anyway,
eliminating most of the cross-platform benefit. The product spec also
explicitly calls for a native stack. iOS is out of scope for this pass.

## Consequences
- No iOS client from this codebase without a separate effort.
- Full access to ARCore Depth API and MediaPipe without bridge overhead.
- Requires JDK 17 + Android SDK 34 to build — not available in the scaffold
  environment, so this ADR's implementation is unverified until opened in
  Android Studio.
