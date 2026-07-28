#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
EXPECTED_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
CACHE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/mehene-bootstrap"
GRADLE_HOME="$CACHE_ROOT/gradle-$GRADLE_VERSION"
ARCHIVE="$CACHE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Mehene: install sha256sum or shasum to verify Gradle." >&2
    exit 1
  fi
}

verify_archive() {
  [ -f "$ARCHIVE" ] && [ "$(checksum "$ARCHIVE")" = "$EXPECTED_SHA256" ]
}

mkdir -p "$CACHE_ROOT"
if [ -f "$ARCHIVE" ] && ! verify_archive; then
  echo "Mehene: cached Gradle archive has an invalid checksum; removing it." >&2
  rm -f "$ARCHIVE"
fi

if [ ! -f "$ARCHIVE" ]; then
  TEMP_ARCHIVE="$ARCHIVE.tmp.$$"
  rm -f "$TEMP_ARCHIVE"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$TEMP_ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget --tries=3 "$URL" -O "$TEMP_ARCHIVE"
  else
    echo "Mehene: install curl or wget to download Gradle." >&2
    exit 1
  fi
  ACTUAL_SHA256="$(checksum "$TEMP_ARCHIVE")"
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    rm -f "$TEMP_ARCHIVE"
    echo "Mehene: Gradle checksum verification failed." >&2
    exit 1
  fi
  mv "$TEMP_ARCHIVE" "$ARCHIVE"
fi

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  command -v unzip >/dev/null 2>&1 || {
    echo "Mehene: install unzip to extract Gradle." >&2
    exit 1
  }
  EXTRACT_ROOT="$CACHE_ROOT/extract-$$"
  rm -rf "$EXTRACT_ROOT" "$GRADLE_HOME"
  mkdir -p "$EXTRACT_ROOT"
  unzip -q "$ARCHIVE" -d "$EXTRACT_ROOT"
  mv "$EXTRACT_ROOT/gradle-$GRADLE_VERSION" "$GRADLE_HOME"
  rm -rf "$EXTRACT_ROOT"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
