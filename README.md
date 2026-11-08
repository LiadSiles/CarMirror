# Car Mirror

Mirrors your Android phone to your car's Android Auto screen, with touch control from the car screen.
The picture hides while the car is moving and comes back when you stop.

## 📲 Download
There are two versions. Open the link on your phone; it always points to the newest build.

| Version | Touch from car screen | Link |
|---|---|---|
| **Car Mirror** | ✅ | https://github.com/LiadSiles/CarMirror/releases/latest/download/CarMirror.apk |
| **Car Mirror Lite** | ❌ (control from the phone) | https://github.com/LiadSiles/CarMirror/releases/latest/download/CarMirror-Lite.apk |

**"App blocked by Play Protect"?** Play Protect blocks downloaded apps that use Accessibility, which the full version needs for touch control. You have three options:
1. **Install Lite.** It installs normally; you just control the phone from the phone.
2. **Turn off Play Protect while you install the full version:** open the *Play Store → profile icon → Play Protect → ⚙️* and switch off **Scan apps with Play Protect**. Install `CarMirror.apk`, then switch scanning back on.
3. **Install from a computer** with `adb install CarMirror.apk`. Play Protect's fraud check only covers APKs installed from a browser, messaging app or file manager.

Both versions use the same signature, so either one installs over the other, and new versions install over old ones.

## First-time setup

### 1. Install the app (phone)
1. Open the link above and download `CarMirror.apk`.
2. Open the file. If asked, allow your browser or Files app to **Install unknown apps**.
3. If Play Protect warns you, tap **More details → Install anyway**. It warns because the app isn't from the Play Store.

### 2. Allow permissions (phone)
Open **Car Mirror** on your phone, then:
1. Tap **Allow notifications**.
2. *(Full version only)* Tap **Turn on touch control**. Under *Downloaded apps*, open **Car Mirror touch control**, switch it on and confirm.
   - If it's greyed out or says "Restricted setting": go back to Car Mirror, tap **Open app info → ⋮ (top right) → Allow restricted settings**, then try again.

### 3. Let Android Auto show the app (one time)
Android Auto only runs apps like this one if the phone thinks they were installed from Google Play.
The "Unknown sources" switch alone isn't enough, so Car Mirror has to be installed with **UnknownAuto**,
which marks it as installed from Play. No computer is needed, but the phone must be on Wi-Fi.

1. **Android Auto developer mode:** in Android Auto settings, tap **Version** about 10 times, then go to *⋮ → Developer settings* and turn on **Unknown sources**.
2. **Phone developer options:** *Settings → About phone*, tap **Build number** (MIUI/HyperOS: **OS version**) 7 times.
3. **Install UnknownAuto:** download `UnknownAuto-*.apk` from https://github.com/GitMag/UnknownAuto/releases/latest and install it. It's a third-party tool, not ours.
4. **Install and start Shizuku**, either from inside UnknownAuto or from Google Play:
   - Open *Developer options → Wireless debugging* and turn it on.
   - In Shizuku, tap **Pairing**. In Wireless debugging, tap *Pair device with pairing code*, then type the 6-digit code into Shizuku's notification.
   - In Shizuku, tap **Start**.
   - On Xiaomi/POCO, also turn off battery optimization for Shizuku.
5. **In UnknownAuto:** tap **Load APK**, pick the Car Mirror APK you downloaded, then tap **Install**.
   - Allow UnknownAuto in Shizuku ("Allow all the time").
   - If Play Protect asks, choose *More details → Install without scanning*. This also lets the full version (with touch) install.
   - Wait for the ✔ banner. It means Android Auto will accept the app.
6. **Restart Android Auto:** open *Settings → Apps → Android Auto → Force stop*, then go to Android Auto settings → **Customize launcher**. Car Mirror is now in the list; make sure it's checked.

Every Car Mirror update must be installed through UnknownAuto in the same way. Installing from the browser removes the Play mark.
With a computer, you can skip steps 2-5 and run: `adb install -r -i com.android.vending CarMirror.apk`

## Every time in the car
1. Connect the phone to the car (cable or wireless).
2. On the car screen, open the app list and tap **Car Mirror**. It shows "Open Car Mirror on your phone".
3. On the phone, open Car Mirror, tap **Start mirroring**, choose **Entire screen** and tap **Start**.
4. The phone screen now shows on the car. Tap and swipe on the car screen to control it. Use the **Back / Home / Apps** buttons on the car screen.
5. Turn the phone sideways for full-width video.
6. To stop, tap **Stop mirroring** in the app or **Stop** in the notification.

## Limits
- Netflix, Disney+, Prime Video and other DRM-protected apps show a black screen because they block screen capture. YouTube works.
- The phone has to stay unlocked.
- Swipes are replayed through the centre of the phone screen. Scrolling works, but edge swipes like pulling down notifications don't.
- If your car doesn't share its speed with Android Auto, the drive-time pause can't kick in. Only watch while parked.

## Troubleshooting
| Problem | Fix |
|---|---|
| Car Mirror isn't in Customize launcher | It was installed from the browser. Reinstall it through UnknownAuto (step 3). |
| Car screen stays black or shows the message | Tap **Start mirroring** on the phone. If it's already on, tap Stop, then Start. |
| Taps on the car do nothing | Touch control is off. Repeat step 2.2. (Lite has no touch control.) |
| "App blocked by Play Protect" | See the options under **Download**. |
| "App not installed" / "problem parsing" | The app needs Android 10 or newer. |
| Picture disappears when driving | That's the safety pause. It comes back when the car stops. |
| Update won't install | Uninstall Car Mirror once, then install the new APK. |

Something else broken? [Open an issue](https://github.com/LiadSiles/CarMirror/issues/new?template=car-test.md) with a photo of the car screen.

## Development
- Code is in `app/src/main/java/com/liad/carmirror` (see `CLAUDE.md` for the architecture).
- Every push to `main` builds the app in GitHub Actions and publishes a new release.
- To build locally, open the folder in Android Studio and choose *Build → Build APK(s)*.
