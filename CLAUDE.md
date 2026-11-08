# Car Mirror — notes for development sessions

Personal, sideloaded Android app that mirrors the phone screen to an Android Auto car display.
Owner: Liad (LiadSiles). Not for the Play Store (Google forbids screen mirroring on Android Auto).

## Architecture
- `car/MirrorCarAppService`, `car/MirrorScreen` — Car App Library (androidx.car.app 1.4.0). Registered as a
  NAVIGATION app because only navigation apps get a raw `Surface` (via `AppManager.setSurfaceCallback`).
  Uses `NavigationTemplate` with Back/Home/Apps actions. Reads vehicle speed (CarHardwareManager) to pause while driving.
- `MirrorController` — main-thread singleton joining the car `Surface` and the `MediaProjection`.
  One mirror `VirtualDisplay` per projection (Android 14+ allows only one); reused with `resize`/`setSurface`.
  When not mirroring it shows a message via a private VirtualDisplay + `Presentation`.
- `MirrorService` — foreground service (type mediaProjection) that owns the projection and a screen wake lock.
- `TouchService` — AccessibilityService; injects taps/swipes with `dispatchGesture`.
- `car/TouchForwarder` — maps car coordinates to phone (letterboxed fit) and replays scroll/fling as swipes.
- `MainActivity` — phone setup screen built in code (no XML layouts).
- Two flavors: `full` (declares TouchService in `src/full/AndroidManifest.xml`) and `lite` (no accessibility
  service, because Play Protect blocks browser-sideloaded APKs that declare one). `R.bool.has_touch` tells code which.

## Install gotcha
Android Auto hides Car App Library apps unless the installer is recorded as Play (`com.android.vending`);
AA's "Unknown sources" toggle isn't enough. Users install via UnknownAuto (Shizuku) or
`adb install -i com.android.vending`. A future option: build that installer flow into the app itself.

## Rules
- Keep the parked-only safety (video hidden while the car moves). Don't add a way to bypass it.
- Every push to `main` builds and publishes a GitHub release; `versionCode` = CI run number.
- The signing key `app/carmirror.jks` must not change, or updates won't install over the old app.
- The development sandbox can't reach Google's Maven, so compile checks happen in GitHub Actions.
  Every branch push runs the build (only `main` publishes a release). Make changes on a branch, check its
  Actions run passes, then merge to `main`.
