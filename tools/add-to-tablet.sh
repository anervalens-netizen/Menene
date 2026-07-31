#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Utilizare: $0 SOURCE [NUME_BIBLIOTECA]" >&2
  exit 1
fi

SOURCE=$1
PREFIX=${2:-Menene-Library}
ADB_BIN=${ADB:-adb}
REQUESTED_SERIAL=${ADB_SERIAL:-}

if [[ ! -d "$SOURCE" ]]; then
  echo "Folder sursă inexistent: $SOURCE" >&2
  exit 1
fi
if [[ ! "$PREFIX" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Numele bibliotecii poate conține doar litere, cifre, punct, _ și -." >&2
  exit 1
fi

mapfile -t CONNECTED_DEVICES < <(
  "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}'
)
if [[ -n "$REQUESTED_SERIAL" ]]; then
  if [[ ! " ${CONNECTED_DEVICES[*]} " =~ " $REQUESTED_SERIAL " ]]; then
    echo "Device-ul ADB_SERIAL=$REQUESTED_SERIAL nu este conectat și autorizat." >&2
    exit 1
  fi
  DEVICE_SERIAL=$REQUESTED_SERIAL
elif [[ ${#CONNECTED_DEVICES[@]} -eq 1 ]]; then
  DEVICE_SERIAL=${CONNECTED_DEVICES[0]}
else
  echo "Conectează exact o tabletă sau setează ADB_SERIAL. Detectate: ${#CONNECTED_DEVICES[@]}." >&2
  exit 1
fi
ADB_CMD=("$ADB_BIN" -s "$DEVICE_SERIAL")

"${ADB_CMD[@]}" get-state >/dev/null
VOLUME=$("${ADB_CMD[@]}" shell sm list-volumes all | awk '/^public:.* mounted / {print $3; exit}' | tr -d '\r')
if [[ -z "$VOLUME" || "$VOLUME" == */* ]]; then
  echo "Cardul microSD public nu este montat." >&2
  exit 1
fi

STAMP=$(date +%Y%m%d-%H%M%S)
REMOTE_FOLDER="$PREFIX-$STAMP"
STAGING_ROOT=$(mktemp -d)
trap 'rm -rf "$STAGING_ROOT"' EXIT
STAGING_LIBRARY="$STAGING_ROOT/$REMOTE_FOLDER"

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
python3 "$SCRIPT_DIR/mehene_builder.py" "$SOURCE" "$STAGING_LIBRARY" --audio-language ron

LOCAL_CATALOG_HASH=$(sha256sum "$STAGING_LIBRARY/catalog.json" | awk '{print $1}')
"${ADB_CMD[@]}" push "$STAGING_LIBRARY/." "/storage/$VOLUME/$REMOTE_FOLDER/"
DEVICE_CATALOG_HASH=$("${ADB_CMD[@]}" shell sha256sum "/storage/$VOLUME/$REMOTE_FOLDER/catalog.json" | awk '{print $1}' | tr -d '\r')
if [[ "$LOCAL_CATALOG_HASH" != "$DEVICE_CATALOG_HASH" ]]; then
  echo "Copiere incompletă: hash catalog diferit." >&2
  exit 1
fi

echo "Tabletă: $DEVICE_SERIAL"
echo "Bibliotecă pregătită: /storage/$VOLUME/$REMOTE_FOLDER"
echo "Pe tabletă: atinge sigla Menene de 5 ori -> Schimbă folderul -> $REMOTE_FOLDER"
