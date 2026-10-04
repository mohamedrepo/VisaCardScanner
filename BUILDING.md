# Visa Card Scanner — build instructions

## Quick start (Android Studio)

1. Open Android Studio → **File ▸ Open** → select the `VisaCardScanner` folder.
2. Let Gradle sync finish (first sync downloads dependencies; requires internet once).
3. Connect an Android 10+ device with USB debugging, or start an emulator with a camera.
4. **Run ▸ app**.

## Command line

```bash
# one-time: create the Gradle wrapper binaries (needs any local Gradle 8.9+,
# or just open the project once in Android Studio which does this for you)
gradle wrapper

./gradlew assembleDebug          # build the debug APK
./gradlew test                   # JVM unit tests
./gradlew lint                   # Android lint
./gradlew connectedAndroidTest   # instrumented tests (device required)
```

Windows: use `gradlew.bat`.

## APK build configuration

- Debug: `assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Release: `assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  - R8 minify + resource shrinking are **on** (`isMinifyEnabled = true`).
  - Currently signed with the debug keystore for convenience.
    To distribute, add your own signing config in `app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("your-release-key.jks")
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "your-alias"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

## Toolchain versions used

| Component | Version |
| --- | --- |
| Kotlin | 2.0.20 |
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 (Android 10) |
| JDK | 17 |
| CameraX | 1.3.4 |
| ML Kit text-recognition (bundled) | 16.0.1 |
| Room | 2.6.1 |
| Compose BOM | 2024.09.03 |
