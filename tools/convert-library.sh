#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Utilizare: $0 <folder-sursa> <folder-destinatie>" >&2
  exit 1
fi
command -v ffmpeg >/dev/null || { echo "ffmpeg lipsește din PATH." >&2; exit 1; }
command -v ffprobe >/dev/null || { echo "ffprobe lipsește din PATH." >&2; exit 1; }

source_dir="$(cd "$1" && pwd)"
mkdir -p "$2"
destination_dir="$(cd "$2" && pwd)"

if [[ "$destination_dir" == "$source_dir" || "$destination_dir/" == "$source_dir/"* ]]; then
  echo "Folderul destinație nu poate fi identic cu sursa sau în interiorul ei." >&2
  exit 1
fi

is_valid_output() {
  local file="$1" video_info audio_codec codec width height
  video_info="$(ffprobe -v error -select_streams v:0 \
    -show_entries stream=codec_name,width,height -of csv=p=0:s=, "$file" 2>/dev/null || true)"
  IFS=',' read -r codec width height <<< "$video_info"
  audio_codec="$(ffprobe -v error -select_streams a:0 \
    -show_entries stream=codec_name -of default=nw=1:nk=1 "$file" 2>/dev/null || true)"
  [[ "$codec" == "h264" ]] || return 1
  [[ "${width:-0}" -le 1280 && "${height:-0}" -le 720 ]] || return 1
  [[ -z "$audio_codec" || "$audio_codec" == "aac" ]] || return 1
}

converted=0
skipped=0
failed=0

while IFS= read -r -d '' file; do
  relative="${file#$source_dir/}"
  relative_dir="$(dirname "$relative")"
  mkdir -p "$destination_dir/$relative_dir"
  extension="${file##*.}"
  extension="${extension,,}"

  case "$extension" in
    mp4|mkv|avi|mov|m4v|webm)
      output="$destination_dir/$relative_dir/$(basename "${file%.*}").mp4"
      if [[ -f "$output" && "$output" -nt "$file" ]] && is_valid_output "$output"; then
        echo "Păstrat: $relative"
        ((skipped += 1))
        continue
      fi

      temporary="$output.tmp.mp4"
      rm -f "$temporary"
      echo "Conversie: $relative"
      if ffmpeg -hide_banner -loglevel warning -y -i "$file" \
        -map 0:v:0 -map '0:a:0?' \
        -vf "scale='min(1280,iw)':'min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2" \
        -fpsmax 30 \
        -c:v libx264 -preset medium -crf 22 -profile:v main -level 3.1 -pix_fmt yuv420p \
        -c:a aac -b:a 128k -ac 2 -ar 48000 \
        -movflags +faststart "$temporary" && is_valid_output "$temporary"; then
        mv -f "$temporary" "$output"
        ((converted += 1))
      else
        rm -f "$temporary"
        echo "Eșec: $relative" >&2
        ((failed += 1))
      fi
      ;;
    jpg|jpeg|png|webp)
      output="$destination_dir/$relative_dir/$(basename "$file")"
      if [[ ! -f "$output" || "$file" -nt "$output" ]]; then
        cp -f "$file" "$output"
      fi
      ;;
  esac
done < <(find "$source_dir" -type f -print0)

echo "Finalizat: $converted convertite, $skipped păstrate, $failed eșuate."
echo "Biblioteca optimizată este în: $destination_dir"
[[ "$failed" -eq 0 ]]
