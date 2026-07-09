#!/bin/sh
# Gradle wrapper script

GRADLE_HOME="$HOME/.gradle/wrapper/dists"
GRADLE_VERSION="8.2"
GRADLE_ZIP="gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_DIST="$GRADLE_HOME/$GRADLE_ZIP"
GRADLE_EXTRACT="$GRADLE_HOME/gradle-${GRADLE_VERSION}"

if [ ! -d "$GRADLE_EXTRACT" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$GRADLE_HOME"
    curl -L "https://services.gradle.org/distributions/${GRADLE_ZIP}" -o "$GRADLE_DIST" 2>/dev/null || \
    wget "https://services.gradle.org/distributions/${GRADLE_ZIP}" -O "$GRADLE_DIST"
    echo "Extracting..."
    unzip -q "$GRADLE_DIST" -d "$GRADLE_HOME"
fi

GRADLE_BIN="$GRADLE_EXTRACT/bin/gradle"
if [ -f "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
else
    echo "Error: Gradle not found"
    exit 1
fi
