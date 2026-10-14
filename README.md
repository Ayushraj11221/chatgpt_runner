# Temple Rush — native Android Kotlin endless runner

A playable, asset-free Temple Run-inspired prototype. It uses a custom Android `View` and Canvas to draw a pseudo-3D jungle path, temple, runner, obstacles, coins, HUD, particles, and menus. No external game engine or third-party runtime library is required.

## Gameplay
- Swipe left/right: change lanes
- Swipe up: jump over rocks and gaps
- Swipe down: slide under logs
- Collect gold coins; speed increases as distance grows
- Avoid obstacles; tap after a run ends to restart

## Build with Android Studio (easiest)
1. Unzip `TempleRush.zip`.
2. Open the `TempleRush` folder in Android Studio.
3. Allow Gradle sync. Install Android SDK Platform 35 and a compatible Android Gradle Plugin if prompted.
4. Connect your Android phone with USB debugging enabled, or create an emulator.
5. Press **Run ▶**. To make an APK, choose **Build → Build APK(s)**.
6. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Build on Android using Termux
Android builds require a JDK, Android SDK platform/build-tools, and Gradle. If you already have those configured, from this project directory run:

```sh
gradle assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`. If your Termux environment doesn't have a working Android SDK/Gradle setup, Android Studio on a PC is the simplest build route. You can also open this project in a browser-based code-server, but the Android SDK still needs to be configured.

## Install the APK
- Transfer `app-debug.apk` to your phone and tap it, then allow installation from that source if Android asks.
- Or with ADB enabled, run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Technical notes
- Package: `com.ayush.templerush`
- Min Android version: Android 6.0 (API 23)
- Target SDK: 35
- Portrait orientation, immersive fullscreen
- Rendering is pseudo-3D rather than a full polygonal 3D engine. This keeps the prototype small, fast to build, and dependency-free. The artwork is drawn procedurally in Kotlin, so there are no missing asset files.

## Build an APK with GitHub Actions (no Android SDK on your phone needed)

1. Create a new repository on GitHub (for example, `TempleRush`).
2. Upload the contents of this project folder to the repository root. Make sure `.github/workflows/android.yml` is included. If uploading from a phone, GitHub's web interface may require uploading files individually; using GitHub's upload-files page or GitHub Desktop on a computer is easier.
3. Open the repository's **Actions** tab. If prompted, enable workflows.
4. Select **Build Temple Rush APK** and tap **Run workflow**. A push to `main` or `master` also triggers the workflow automatically.
5. Wait for the workflow to finish. Open the successful run and scroll to **Artifacts**.
6. Download `TempleRush-debug-apk`. Extract the downloaded artifact ZIP to get `app-debug.apk`.
7. Open `app-debug.apk` on your Android phone and install it. You may need to allow your browser or file manager to install unknown apps.

The workflow builds a debug APK using Java 17, Gradle 8.7, and Android SDK 35. The artifact is retained for 14 days. This workflow has been added to the project but has not been executed from this environment, so a successful remote build is not yet verified.
