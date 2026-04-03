#!/bin/bash

# Load environment variables ($PLATFORM, $BUILD_TOOLS)
source ~/.bashrc

echo "Cleaning up old build files..."
rm -rf out/*
rm -f classes.dex app-unsigned.apk aligned.apk
mkdir -p out

echo "Compiling Java..."
javac -source 8 -target 8 -bootclasspath $PLATFORM -d out src/com/example/app/MainActivity.java || { echo "Compilation failed"; exit 1; }

echo "Converting to DEX..."
# Using $(find out -name "*.class") to ensure BOTH MainActivity.class and MainActivity$1.class are packaged!
$BUILD_TOOLS/d8 --lib $PLATFORM --min-api 24 $(find out -name "*.class") --output . || { echo "DEX conversion failed"; exit 1; }

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
