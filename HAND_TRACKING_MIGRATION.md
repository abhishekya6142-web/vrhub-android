# Kotlin hand-tracking migration

This branch ports the reusable hand-interaction concepts from the Unity/C# project into the existing Android/Kotlin app.

## Added modules

- `handtracking/HandLandmark.kt`: MediaPipe's 21-landmark data model and named landmark indices.
- `handtracking/OneEuroFilter3D.kt`: adaptive low-pass smoothing for 3D landmark positions.
- `handtracking/MediaPipeHandAdapter.kt`: converts MediaPipe Tasks results into app-owned data types.
- `handtracking/HandTrackingEngine.kt`: combines result conversion, gesture recognition and pointer updates.
- `gestures/PinchDetector.kt`: hysteresis-based pinch state.
- `gestures/GestureRecognizer.kt`: initial pinch, open-palm, fist and point classification.
- `interaction/HandPointer.kt`: smoothed pointer from thumb/index midpoint.
- `interaction/PinchInteractionController.kt`: pinch/open-palm transition callbacks.
- `windows/FloatingWindowState.kt`: platform-neutral floating-window state.

## Important migration notes

The original Unity scripts depend on Unity XR, AR Foundation, XR Interaction Toolkit, scene objects and Unity physics. Those files cannot run in Android/Kotlin as-is. Their behavior must be reimplemented against Android APIs and the renderer selected by this app.

The current `MainActivity.kt` and Gradle setup are intentionally preserved. The MediaPipe adapter is a separate layer and still needs to be connected to the existing live-stream result callback.

The model file `hand_landmarker.task` is a runtime asset dependency. Verify that it exists under `app/src/main/assets/` before building/running. Do not assume Unity model files or Unity metadata can be used directly.

## Remaining port work

- Connect `HandTrackingEngine` to the live CameraX/MediaPipe callback.
- Add 3D joint pose construction and hand-ray hit testing in the chosen Android renderer.
- Port grab/release routing, depth/palm estimation and world-locked window transforms.
- Add calibration, tracking-loss grace period and lifecycle cleanup.
- Build and test on a physical Android device; no successful build is claimed by this migration scaffold alone.
