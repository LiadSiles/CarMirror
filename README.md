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
1. Open Android Auto settings on your phone. On most phones that's **Settings → Connected devices → Connection preferences → Android Auto**. On Samsung, search Settings for "Android Auto".
2. Scroll to the bottom and tap **Version** about 10 times. When asked "Allow development settings?", tap **OK**.
3. Tap **⋮ (top right) → Developer settings**, then turn on **Unknown sources**.
4. Go back and open **Customize launcher**. Make sure **Car Mirror** is checked.
5. Unplug the phone from the car, or turn the car off and on, so Android Auto reloads.

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
| Car Mirror isn't in the car's app list | Check step 3 (Unknown sources and Customize launcher), then reconnect the phone. |
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
