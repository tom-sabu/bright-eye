#!/bin/bash
set -e

echo "Cleaning up old build files..."
rm -rf bin/ out/
mkdir -p bin/

# Load environment variables (mostly for fallback, setting specific ones below)
source ~/.bashrc || true

# 1. Find android.jar
ANDROID_JAR=$HOME/tools/Android/sdk/platforms/android-33/android.jar
if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR"
    exit 1
fi

# 2. Find build tools
BUILD_TOOLS=$HOME/tools/Android/sdk/build-tools/36.0.0
KOTLIN_HOME=/opt/kotlinc
if [ ! -d "$KOTLIN_HOME" ]; then
    echo "ERROR: KOTLIN_HOME not found at $KOTLIN_HOME"
    exit 1
fi

echo "Compiling Kotlin..."
# 3. Compile Kotlin sources
# Since we might use CameraManager.kt from before or delete it, we compile all files in src.
$KOTLIN_HOME/bin/kotlinc $(find src -name "*.kt") \
    -classpath "$ANDROID_JAR" \
    -d bin/classes.jar || { echo "Compilation failed"; exit 1; }

KOTLIN_STDLIB=$KOTLIN_HOME/lib/kotlin-stdlib.jar

echo "Converting to DEX..."
# 4. Convert to DEX
$BUILD_TOOLS/d8 \
    bin/classes.jar \
    $KOTLIN_STDLIB \
    --lib "$ANDROID_JAR" \
    --output bin/ || { echo "DEX conversion failed"; exit 1; }

echo "Packaging with AAPT2..."
# 5. Package with aapt2
mkdir -p bin/res bin/gen

# Check if res directory has any files
if [ -d "res" ] && [ "$(find res -type f | wc -l)" -gt 0 ]; then
    find res -type f | xargs $BUILD_TOOLS/aapt2 compile -o bin/res.zip
    
    $BUILD_TOOLS/aapt2 link bin/res.zip \
        -I "$ANDROID_JAR" \
        --manifest AndroidManifest.xml \
        --java bin/gen/ \
        -o bin/app-unsigned.apk || { echo "AAPT2 linking failed"; exit 1; }
else
    # Link directly without passing res.zip
    $BUILD_TOOLS/aapt2 link \
        -I "$ANDROID_JAR" \
        --manifest AndroidManifest.xml \
        --java bin/gen/ \
        -o bin/app-unsigned.apk || { echo "AAPT2 linking failed"; exit 1; }
fi

echo "Adding DEX to APK..."
# 6. Add DEX to APK
cd bin
zip -u app-unsigned.apk classes.dex || { echo "APK update failed"; exit 1; }
cd ..

echo "Signing the APK..."
# 7. Sign the APK using existing 'mykey.keystore'
echo "(Enter your keystore password when prompted)"
$BUILD_TOOLS/apksigner sign \
    --ks mykey.keystore \
    --out bin/app-release.apk \
    bin/app-unsigned.apk || { echo "Signing failed"; exit 1; }

echo "Installing to device..."
# 8. Install
adb install -r bin/app-release.apk || { echo "Installation failed"; exit 1; }

echo "Success! Pure Camera2 app installed."
