# CardboardXR to VR Hub: Kotlin port

The original repository `abhishekya6142-web/Xr-android-` is read-only and must not be modified.
All Android implementation belongs in `abhishekya6142-web/vrhub-android`.

## Source features identified

- `HandTracker.cs` + `HandGeometry.cs`: BlazeHand palm detector/landmarker, 21 joints, 3D fitting against camera intrinsics, One Euro filtering and motion prediction.
- `HandSubsystemFeed.cs`: handedness smoothing, pinch values, aim/grip/poke poses.
- `PinchDetector.cs`: 2.5 cm pinch-close and 4.5 cm release hysteresis.
- `HandInteractorRig.cs`: gaze-first UI click routing, near/far hand grab, short tracking-loss grace.
- `HandRayView.cs`: target-only cursor.
- `HeadPoseFusion.cs`: gyro 3DoF plus ARCore 6DoF fusion, yaw alignment, position prediction/re-anchoring.
- `RoomBoundary.cs`: plane scanning, floor detection, surface warning grid and scan lifecycle.
- Unity rendering also supplies separate eye projections and lens distortion.

## Kotlin foundation committed in this branch

- Optional ARCore dependency and manifest declaration.
- `ArCoreSessionController`: ARCore session lifecycle, horizontal/vertical plane finding and hit-test anchors.
- `CardboardHeadTracker`: display-corrected rotation-vector orientation and recenter.
- `StereoViewportMath`: independent left/right eye viewports and aspect-preserving center-crop UVs.
- `PinchGestureDetector`: source-matched pinch hysteresis.

## Important image-fill behavior

The Cardboard eye background must use the `StereoViewportMath.centerCropUv` result independently for each eye. This fills the full eye viewport without stretching. If aspect ratios differ, edge cropping is mathematically unavoidable; the source camera texture should remain at its native capture resolution. Lens distortion must be applied after the crop in the eye shader, with per-eye lens centers.

## Remaining work before feature parity

1. Build the ARCore camera-background renderer and route its camera texture to MediaPipe without opening a second camera session.
2. Port BlazeHand model inference, 21-joint 3D reconstruction from metric landmarks and camera intrinsics, One Euro filtering and prediction.
3. Port handedness, aim/grip/poke pose feed, gaze-first pinch routing, near/far grab and target cursor.
4. Port gyro/ARCore pose fusion and relocalization protection from HeadPoseFusion.
5. Port RoomBoundary plane scanning, floor alignment, room-surface visualization and persistent-in-session anchored panels.
6. Implement the stereo OpenGL renderer: per-eye projection, lens distortion, camera passthrough and world-space UI.
7. Wire a Phone AR / Cardboard VR selector into the home screen and test on an ARCore-certified device and actual viewer.

The current foundation is not a complete Cardboard renderer or complete hand-tracking port. Do not label either mode fully implemented until its rendering and interaction path is connected and device-tested.
