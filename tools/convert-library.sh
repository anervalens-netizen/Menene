#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Utilizare: $0 <folder-sursa> <folder-destinatie>" >&2
  exit 1
fi
command -v ffmpeg >/dev/null || { echo "ffmpeg lipsește din PATH." >&2; exit 1; }

source_dir="$(cd "$1" && pwd)"
mkdir -p "$2"
destination_dir="$(cd "$2" && pwd)"

find "$source_dir" -type f -print0 | while IFS= read -r -d '' file; do
  relative="${file#$source_dir/}"
  relative_dir="$(dirname "$relative")"
  mkdir -p "$destination_dir/$relative_dir"
  extension="${file##*.}"
  extension="${extension,,}"

  case "$extension" in
    mp4|mkv|avi|mov|m4v|webm)
      output="$destination_dir/$relative_dir/$(basename "${file%.*}").mp4"
      echo "Conversie: $relative"
      ffmpeg -hide_banner -loglevel warning -y -i "$file" \
        -map 0:v:0 -map '0:a:0?' \
        -vf 'scale=1280:720:force_original_aspect_ratio=decrease:force_divisible_by=2' \
        -c:v libx264 -preset medium -crf 22 -profile:v main -level 3.1 -pix_fmt yuv420p \
        -c:a aac -b:a 128k -ac 2 -ar 48000 \
        -movflags +faststart "$output"
      ;;
    jpg|jpeg|png|webp)
      cp -f "$file" "$destination_dir/$relative_dir/"
      ;;
  esac
done

echo "Biblioteca optimizată este în: $destination_dir"
