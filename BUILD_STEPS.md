# Build steps and quick verification

1. **Project structure:** Android app module, Kotlin activity, custom game view, manifest and theme.
2. **Game loop:** frame delta-time update; pause/resume with Activity lifecycle.
3. **Rendering:** gradient sky, distant temple, jungle silhouettes, perspective road, lane markings, runner, obstacles, coins and HUD.
4. **Controls:** lane changes, jump arc, slide timer, collision checks, coin collection, restart.
5. **Performance choices:** no external assets, no third-party libraries, simple Canvas drawing, bounded frame delta, hardware-accelerated View.
6. **Build:** open in Android Studio and Run, or use `gradle assembleDebug` with Android SDK configured.

This environment does not include the Android SDK or Gradle executable, so an APK could not be compiled here. The source project is packaged and ready to open in an Android build environment.
