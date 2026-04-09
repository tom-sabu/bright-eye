# bright_eye

An Android app that controls screen brightness using hand gestures.
Built with Kotlin, Camera2, and MediaPipe. Compiled from the command line via Gradle.

## Project layout

- `app/` - Android application module (source, manifest, resources, assets)
- `gradle/`, `gradlew`, `gradlew.bat` - Gradle wrapper
- `build.sh` - convenience script (`assembleDebug` + `adb install`)
- `context/` - project notes and reports
- `scripts/` - archived helper scripts

## Build from command line

```bash
./gradlew :app:assembleDebug
```

Install to device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or run both in one step:

```bash
./build.sh
```
