#!/bin/bash
# Install and run script for Galaxy A56 AI

set -e

echo "========================================="
echo "Galaxy A56 AI - Install & Run"
echo "========================================="

# Configuration
PACKAGE_NAME="com.galaxya56.ai"
MAIN_ACTIVITY="com.galaxya56.ai.ui.MainActivity"

# Build first
echo "Building debug APK..."
./gradlew assembleDebug --no-daemon

# Find APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo "APK found: $APK_PATH"

# Check for connected devices
echo "Checking for connected devices..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo "No devices connected. Please connect your Galaxy A56 via USB with USB debugging enabled."
    exit 1
fi

echo "Found $DEVICES device(s)"

# Install APK
echo "Installing APK..."
adb install -r "$APK_PATH"

# Check if installation succeeded
if [ $? -eq 0 ]; then
    echo "Installation successful!"
else
    echo "Installation failed!"
    exit 1
fi

# Start app
echo "Starting app..."
adb shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"

echo ""
echo "========================================="
echo "App installed and launched!"
echo "========================================="
echo ""
echo "Logs: adb logcat -s A56AI-LLM A56AI-HW A56AI-JNI"
