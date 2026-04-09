#!/bin/bash
set -euo pipefail

echo "Building with Gradle..."
./gradlew :app:assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo "Installing to device..."
adb install -r "$APK_PATH"

echo "Success! Gradle build installed."
