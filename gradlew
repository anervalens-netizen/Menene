#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/mehene-bootstrap"
GRADLE_HOME="$CACHE_ROOT/gradle-$GRADLE_VERSION"
ARCHIVE="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_ROOT"
  if [ ! -f "$ARCHIVE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "$URL" -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
      wget "$URL" -O "$ARCHIVE"
    else
      echo "Mehene: install curl or wget to bootstrap Gradle." >&2
      exit 1
    fi
  fi
  command -v unzip >/dev/null 2>&1 || {
    echo "Mehene: install unzip to bootstrap Gradle." >&2
    exit 1
  }
  rm -rf "$GRADLE_HOME"
  unzip -q "$ARCHIVE" -d "$CACHE_ROOT"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
