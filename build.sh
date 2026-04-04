#!/bin/bash

# Load environment variables ($PLATFORM, $BUILD_TOOLS)
source ~/.bashrc

KOTLIN_HOME=/opt/kotlinc
KOTLIN_STDLIB=$KOTLIN_HOME/lib/kotlin-stdlib.jar

echo "Cleaning up old build files..."
rm -rf out/*
rm -f classes.dex app-unsigned.apk aligned.apk
mkdir -p out

echo "Compiling Kotlin..."
$KOTLIN_HOME/bin/kotlinc \
    src/com/example/app/MainActivity.kt \
    -classpath $PLATFORM \
    -d out || { echo "Compilation failed"; exit 1; }

echo "Converting to DEX..."
# Include kotlin-stdlib.jar and all compiled classes
$BUILD_TOOLS/d8 \
    --lib $PLATFORM \
    --min-api 24 \
    $KOTLIN_STDLIB \
    $(find out -name "*.class") \
    --output . || { echo "DEX conversion failed"; exit 1; }

echo "Packaging Resources & Manifest..."
$BUILD_TOOLS/aapt package -f -M AndroidManifest.xml -I $PLATFORM -F app-unsigned.apk || { echo "AAPT packaging failed"; exit 1; }

echo "Adding DEX to APK..."
zip -u app-unsigned.apk classes.dex || { echo "APK update failed"; exit 1; }

echo "Aligning APK..."
$BUILD_TOOLS/zipalign -f -v 4 app-unsigned.apk aligned.apk || { echo "Zipalign failed"; exit 1; }

echo "Signing APK... (Enter your keystore password when prompted)"
$BUILD_TOOLS/apksigner sign --ks mykey.keystore aligned.apk || { echo "Signing failed"; exit 1; }

echo "Installing to device..."
adb install -r aligned.apk || { echo "Installation failed"; exit 1; }

echo "Success! The newly built app is installed."
