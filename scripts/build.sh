#!/bin/bash
# Build script for Galaxy A56 AI

set -e

echo "========================================="
echo "Galaxy A56 AI - Build Script"
echo "========================================="

# Configuration
BUILD_TYPE="${1:-debug}"
GRADLE_OPTS=""

case $BUILD_TYPE in
    debug)
        GRADLE_OPTS="assembleDebug"
        ;;
    release)
        GRADLE_OPTS="assembleRelease"
        ;;
    bundle)
        GRADLE_OPTS="bundleRelease"
        ;;
    test)
        GRADLE_OPTS="test"
        ;;
    lint)
        GRADLE_OPTS="lint"
        ;;
    clean)
        GRADLE_OPTS="clean"
        ;;
    *)
        echo "Usage: $0 [debug|release|bundle|test|lint|clean]"
        exit 1
        ;;
esac

# Check prerequisites
echo "Checking prerequisites..."

# Check for Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "WARNING: ANDROID_HOME not set"
fi

# Check for NDK
NDK_VERSION="27.2.12479018"
if [ ! -d "$ANDROID_HOME/ndk/$NDK_VERSION" ] && [ ! -d "$ANDROID_SDK_ROOT/ndk/$NDK_VERSION" ]; then
    echo "WARNING: NDK $NDK_VERSION not found"
fi

# Check for Java 17
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "WARNING: Java 17+ required, found $JAVA_VERSION"
fi

echo "Starting build: $GRADLE_OPTS"

# Run Gradle
./gradlew $GRADLE_OPTS --no-daemon --parallel --configure-on-demand

echo "========================================="
echo "Build completed successfully!"
echo "========================================="

# Output APK location
if [ "$BUILD_TYPE" = "debug" ]; then
    echo "APK: app/build/outputs/apk/debug/app-debug.apk"
elif [ "$BUILD_TYPE" = "release" ]; then
    echo "APK: app/build/outputs/apk/release/app-release.apk"
elif [ "$BUILD_TYPE" = "bundle" ]; then
    echo "AAB: app/build/outputs/bundle/release/app-release.aab"
fi