# PadMapper

Maps a gamepad (DualShock 4 etc.) to taps and swipes on Android games, per game.
Buttons only for now: each controller button becomes one tap or one swipe at a spot you choose.

## Get the APK (no Android Studio needed)

1. On GitHub, create a **new repository** (private is fine).
2. Upload **everything in this folder** to it (keep the folder structure, and include the hidden `.github` folder).
   On a computer: unzip this project, open the repository page, choose **Add file > Upload files**, and drag the *contents* of the unzipped folder in. Click **Commit changes**.
3. Open the **Actions** tab. A run called **Build APK** starts by itself (about 3-6 minutes). Wait for the green check.
   - If you don't see it, open Actions, pick **Build APK** and press **Run workflow**.
4. When it is green, open the **Releases** section on the repository's main page and download **PadMapper.apk** on your phone.
5. Install it (Android will ask to allow installing apps from your browser/files app).

If the run shows a red X, open it, copy the red error text and send it to me. I'll fix the code.

## Use it

See the steps shown inside the app. Short version: turn the service on in Accessibility settings, pair the controller, tap "Edit buttons", open the game, and assign each button.

## Limits (version 0.1)

- Buttons only (including L2/R2 if your phone reports them as buttons). Analog sticks are not mapped yet.
- Only one tap/swipe is sent at a time, so holding movement while using skills is not possible with this method.
- Debug-signed APK: if a later build won't install over the old one, uninstall the old one first.
- Games with anti-cheat may dislike automated input. Use at your own risk.
