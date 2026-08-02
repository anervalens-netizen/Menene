#!/usr/bin/env python3
"""Build and validate a premium offline Mehene library.

Uses only the Python standard library plus ffmpeg/ffprobe available in PATH.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
VIDEO_EXTENSIONS = {
    '.3g2', '.3gp', '.asf', '.avi', '.divx', '.flv', '.m2ts', '.m4v', '.mkv',
    '.mov', '.mp4', '.mpeg', '.mpg', '.mts', '.ogv', '.rm', '.rmvb', '.ts',
    '.vob', '.webm', '.wmv',
}
IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp'}
SUBTITLE_EXTENSIONS = {'.srt', '.vtt'}
COVER_NAMES = ('cover', 'poster', 'folder', 'serial')
ROMANIAN_LANGUAGE_CODES = {'ron', 'rum', 'ro'}
ENGLISH_LANGUAGE_CODES = {'eng', 'en'}

def language_aliases(language: str) -> set[str]:
    normalized = language.strip().lower()
    if normalized in ROMANIAN_LANGUAGE_CODES:
        return set(ROMANIAN_LANGUAGE_CODES)
    if normalized in ENGLISH_LANGUAGE_CODES:
        return set(ENGLISH_LANGUAGE_CODES)
    return {normalized} if normalized else set()

@dataclass
class MediaInfo:
    duration_ms: int
    video_codec: str
    width: int
    height: int
    fps: float
    audio_stream_index: int | None
    audio_codec: str | None
    audio_language: str | None
    audio_channels: int | None
    audio_sample_rate: int | None
    audio_bit_rate: int | None
    subtitle_streams: int

def run(command: list[str], *, capture: bool=False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE if capture else None, stderr=subprocess.PIPE if capture else None)

def require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise SystemExit(f'{name} lipsește din PATH')
    return path

def fraction(value: str | None) -> float:
    if not value or value in {'0/0', 'N/A'}:
        return 0.0
    if '/' in value:
        numerator, denominator = value.split('/', 1)
        try:
            return float(numerator) / float(denominator)
        except (ValueError, ZeroDivisionError):
            return 0.0
    try:
        return float(value)
    except ValueError:
        return 0.0

def inspect_media(ffprobe: str, path: Path, preferred_language: str) -> MediaInfo:
    result = run([ffprobe, '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)], capture=True)
    payload = json.loads(result.stdout)
    streams = payload.get('streams', [])
    video = next((stream for stream in streams if stream.get('codec_type') == 'video'), None)
    if not video:
        raise ValueError('lipsește pista video')
    audio_streams = [stream for stream in streams if stream.get('codec_type') == 'audio']
    preferred_codes = language_aliases(preferred_language)
    audio = next((stream for stream in audio_streams if preferred_codes and str(stream.get('tags', {}).get('language', '')).lower() in preferred_codes), audio_streams[0] if audio_streams else None)
    duration = payload.get('format', {}).get('duration') or video.get('duration') or 0
    def integer(value: object) -> int | None:
        try:
            return int(value) if value not in (None, '', 'N/A') else None
        except (TypeError, ValueError):
            return None

    return MediaInfo(
        duration_ms=max(0, int(float(duration) * 1000)),
        video_codec=str(video.get('codec_name', '')),
        width=int(video.get('width', 0)),
        height=int(video.get('height', 0)),
        fps=fraction(video.get('avg_frame_rate') or video.get('r_frame_rate')),
        audio_stream_index=int(audio['index']) if audio else None,
        audio_codec=str(audio.get('codec_name')) if audio else None,
        audio_language=str(audio.get('tags', {}).get('language')) if audio else None,
        audio_channels=integer(audio.get('channels')) if audio else None,
        audio_sample_rate=integer(audio.get('sample_rate')) if audio else None,
        audio_bit_rate=integer(audio.get('bit_rate')) if audio else None,
        subtitle_streams=sum(1 for stream in streams if stream.get('codec_type') == 'subtitle'),
    )

def is_tv_video_compatible(info: MediaInfo) -> bool:
    return info.video_codec == 'h264' and info.width <= 1920 and info.height <= 1080 and (info.fps <= 60.1 or info.fps == 0)

def is_compatible(path: Path, info: MediaInfo, media_profile: str = 'tablet') -> bool:
    if media_profile not in {'tablet', 'tv'}:
        raise ValueError(f'profil media necunoscut: {media_profile}')
    if path.suffix.lower() not in {'.mp4', '.m4v'} or info.subtitle_streams:
        return False
    if media_profile == 'tv':
        audio_ok = info.audio_stream_index is None or (
            info.audio_codec == 'aac'
            and info.audio_channels == 2
            and (info.audio_bit_rate is None or 160000 <= info.audio_bit_rate <= 210000)
        )
        return is_tv_video_compatible(info) and audio_ok
    return info.video_codec == 'h264' and (info.width <= 1280) and (info.height <= 720) and (info.fps <= 30.1 or info.fps == 0) and (info.audio_stream_index is None or info.audio_codec == 'aac')

def safe_id(value: str) -> str:
    readable = ''.join((character.lower() if character.isalnum() else '-' for character in value))
    readable = '-'.join((part for part in readable.split('-') if part))[:40] or 'item'
    digest = hashlib.sha256(value.encode('utf-8')).hexdigest()[:10]
    return f'{readable}-{digest}'

def display_name(name: str) -> str:
    stem = Path(name).stem.replace('_', ' ')
    return ' '.join(stem.replace('–', '-').replace('—', '-').split()).strip() or 'Fără titlu'

def season_number(name: str) -> int | None:
    normalized = name.lower().replace('season', 's').replace('sezon', 's').strip()
    digits = ''.join((character for character in normalized if character.isdigit()))
    if normalized.startswith('s') and digits:
        return int(digits)
    return None

def episode_number(name: str, fallback: int) -> int:
    stem = Path(name).stem.lower()
    patterns = (
        r's\s*0*\d{1,3}[\s._-]*e\s*0*(\d{1,4})',
        r'(?:episode|episod(?:ul)?)\s*0*(\d{1,4})',
        r'(?:^|[^0-9])e\s*0*(\d{1,4})',
        r'(?:^|[^0-9])0*(\d{1,4})(?:[^0-9]|$)',
    )
    for pattern in patterns:
        match = re.search(pattern, stem, re.IGNORECASE)
        if match:
            return int(match.group(1))
    return fallback

def choose_cover(directory: Path) -> Path | None:
    images = [path for path in directory.iterdir() if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS]
    for priority in COVER_NAMES:
        match = next((path for path in images if path.stem.lower() == priority), None)
        if match:
            return match
    return None

def sidecar(path: Path, extensions: set[str]) -> Path | None:
    for extension in extensions:
        candidate = path.with_suffix(extension)
        if candidate.is_file():
            return candidate
    return None

def discard_subtitles(source: Path, destination: Path) -> int:
    source_count = sum(
        1
        for candidate in source.rglob("*")
        if candidate.is_file() and candidate.suffix.lower() in SUBTITLE_EXTENSIONS
    )
    for candidate in destination.rglob("*"):
        if candidate.is_file() and candidate.suffix.lower() in SUBTITLE_EXTENSIONS:
            candidate.unlink()
    return source_count

def copy_asset(source: Path, destination: Path) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.resolve() == destination.resolve():
        return destination
    if destination.is_file():
        source_stat = source.stat()
        destination_stat = destination.stat()
        if destination_stat.st_size == source_stat.st_size and destination_stat.st_mtime_ns >= source_stat.st_mtime_ns:
            return destination
    shutil.copy2(source, destination)
    return destination

def optimize_image(ffmpeg: str, source: Path, destination: Path, max_width: int, max_height: int, quality: int=82) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and destination.stat().st_size > 0 and (destination.stat().st_mtime_ns >= source.stat().st_mtime_ns):
        return destination
    temporary = destination.with_suffix('.tmp.webp')
    try:
        run([ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y', '-i', str(source), '-frames:v', '1', '-vf', f'scale={max_width}:{max_height}:force_original_aspect_ratio=decrease', '-c:v', 'libwebp', '-quality', str(quality), str(temporary)])
        if not temporary.is_file() or temporary.stat().st_size <= 0:
            raise RuntimeError(f'Nu s-a putut optimiza imaginea {source}')
        os.replace(temporary, destination)
        os.utime(destination, ns=(source.stat().st_atime_ns, source.stat().st_mtime_ns))
        return destination
    finally:
        temporary.unlink(missing_ok=True)

def transcode(ffmpeg: str, source: Path, destination: Path, info: MediaInfo, media_profile: str = 'tablet') -> bool:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix('.tmp.mp4')
    command = [ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y', '-i', str(source)]
    command += ['-map', '0:v:0']
    if info.audio_stream_index is not None:
        command += ['-map', f'0:{info.audio_stream_index}']
    video_stream_copied = media_profile == 'tv' and is_tv_video_compatible(info)
    if video_stream_copied:
        command += ['-c:v', 'copy']
    else:
        if media_profile == 'tv':
            video_filter = "scale='min(1920,iw)':'min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"
            if info.fps > 60.1:
                video_filter += ',fps=60'
        else:
            video_filter = "scale='min(1280,iw)':'min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2"
            if info.fps > 30.1:
                video_filter += ',fps=30'
        if media_profile == 'tv':
            encoder_args = ['-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-profile:v', 'high', '-level', '4.2']
        else:
            encoder_args = ['-c:v', 'libx264', '-preset', 'medium', '-crf', '22', '-profile:v', 'main', '-level', '3.1']
        command += ['-vf', video_filter, *encoder_args, '-pix_fmt', 'yuv420p']
    if info.audio_stream_index is not None:
        command += ['-c:a', 'aac', '-b:a', '192k' if media_profile == 'tv' else '128k', '-ac', '2', '-ar', '48000']
    else:
        command += ['-an']
    command += ['-sn', '-movflags', '+faststart', str(temporary)]
    try:
        run(command)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    return video_stream_copied

def generate_thumbnail(ffmpeg: str, source: Path, destination: Path, duration_ms: int) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    duration_seconds = max(0.0, duration_ms / 1000.0)
    if duration_seconds > 0:
        seek_seconds = max(0.25, min(60.0, duration_seconds * 0.15))
        seek_seconds = min(seek_seconds, max(0.0, duration_seconds - 0.25))
    else:
        seek_seconds = 0.0
    temporary = destination.with_suffix('.tmp.webp')

    def extract_at(position_seconds: float) -> bool:
        temporary.unlink(missing_ok=True)
        run([ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y', '-ss', f'{position_seconds:.3f}', '-i', str(source), '-frames:v', '1', '-vf', 'scale=1280:720:force_original_aspect_ratio=decrease', '-c:v', 'libwebp', '-quality', '82', str(temporary)])
        return temporary.is_file() and temporary.stat().st_size > 0
    try:
        if not extract_at(seek_seconds) and seek_seconds > 0:
            extract_at(0.0)
        if not temporary.is_file() or temporary.stat().st_size <= 0:
            raise RuntimeError(f'Nu s-a putut genera miniatura pentru {source}')
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)

def video_files(directory: Path) -> list[Path]:
    return sorted([path for path in directory.iterdir() if path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS], key=lambda path: path.name.lower())

def relative_posix(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()

def build_library(source: Path, destination: Path, preferred_language: str, media_profile: str = 'tablet') -> dict[str, Any]:
    ffmpeg = require_tool('ffmpeg')
    ffprobe = require_tool('ffprobe')
    if media_profile not in {'tablet', 'tv'}:
        raise SystemExit(f'Profil media necunoscut: {media_profile}')
    source = source.resolve()
    destination = destination.resolve()
    if source == destination or source in destination.parents:
        raise SystemExit('Destinația trebuie să fie în afara folderului sursă')
    destination.mkdir(parents=True, exist_ok=True)
    subtitles_ignored = discard_subtitles(source, destination)
    report: dict[str, Any] = {'series': 0, 'episodes': 0, 'compatibleCopied': 0, 'reused': 0, 'converted': 0, 'videoStreamCopied': 0, 'thumbnailsGenerated': 0, 'subtitlesIgnored': subtitles_ignored, 'subtitleStreamsDropped': 0, 'mediaProfile': media_profile, 'warnings': [], 'errors': []}
    catalog_series: list[dict[str, Any]] = []
    for series_source in sorted((path for path in source.iterdir() if path.is_dir()), key=lambda path: path.name.lower()):
        season_directories = [(season_number(path.name), path) for path in series_source.iterdir() if path.is_dir() and season_number(path.name) is not None]
        season_sources = sorted(season_directories, key=lambda item: item[0]) if season_directories else [(1, series_source)]
        series_destination = destination / series_source.name
        series_destination.mkdir(parents=True, exist_ok=True)
        cover_source = choose_cover(series_source)
        cover_destination: Path | None = None
        if cover_source:
            cover_destination = optimize_image(ffmpeg, cover_source, series_destination / 'cover.webp', max_width=960, max_height=640, quality=84)
        series_id = safe_id(series_source.name)
        seasons_payload: list[dict[str, Any]] = []
        for season_index, season_source in season_sources:
            season_destination = series_destination if season_source == series_source else series_destination / f'Season {season_index:02d}'
            season_destination.mkdir(parents=True, exist_ok=True)
            episodes_payload: list[dict[str, Any]] = []
            for order, source_video in enumerate(video_files(season_source), start=1):
                try:
                    source_info = inspect_media(ffprobe, source_video, preferred_language)
                    destination_video = season_destination / f'{source_video.stem}.mp4'
                    info: MediaInfo
                    reusable = False
                    if destination_video.is_file() and destination_video.stat().st_mtime_ns >= source_video.stat().st_mtime_ns:
                        try:
                            existing_info = inspect_media(ffprobe, destination_video, preferred_language)
                            reusable = is_compatible(destination_video, existing_info, media_profile)
                            if reusable:
                                info = existing_info
                                report['reused'] += 1
                        except Exception:
                            reusable = False
                    if not reusable:
                        if is_compatible(source_video, source_info, media_profile):
                            copy_asset(source_video, destination_video)
                            info = inspect_media(ffprobe, destination_video, preferred_language)
                            report['compatibleCopied'] += 1
                        else:
                            copied = transcode(ffmpeg, source_video, destination_video, source_info, media_profile)
                            info = inspect_media(ffprobe, destination_video, preferred_language)
                            report['videoStreamCopied'] += int(copied)
                            report['subtitleStreamsDropped'] += source_info.subtitle_streams
                            if not is_compatible(destination_video, info, media_profile):
                                raise RuntimeError(f'Fișierul convertit nu respectă profilul Mehene {media_profile}')
                            report['converted'] += 1
                    preferred_codes = language_aliases(preferred_language)
                    if preferred_codes and info.audio_stream_index is not None and ((info.audio_language or '').lower() not in preferred_codes):
                        report['warnings'].append(f"{source_video}: nu există pistă audio {preferred_language}; a fost folosită {info.audio_language or 'necunoscută'}")
                    artwork_source = sidecar(source_video, IMAGE_EXTENSIONS)
                    artwork_destination = season_destination / f'{source_video.stem}.webp'
                    if artwork_source:
                        artwork_destination = optimize_image(ffmpeg, artwork_source, artwork_destination, 1280, 720, quality=82)
                    elif not (artwork_destination.is_file() and artwork_destination.stat().st_size > 0 and (artwork_destination.stat().st_mtime_ns >= destination_video.stat().st_mtime_ns)):
                        generate_thumbnail(ffmpeg, destination_video, artwork_destination, info.duration_ms)
                        report['thumbnailsGenerated'] += 1
                    number = episode_number(source_video.name, order)
                    episode_id = safe_id(f'{series_id}|{season_index}|{number}|{source_video.stem}')
                    episodes_payload.append({'id': episode_id, 'number': number, 'sortOrder': order, 'title': display_name(source_video.name), 'media': relative_posix(destination_video, destination), 'artwork': relative_posix(artwork_destination, destination), 'subtitle': None, 'durationMs': info.duration_ms, 'audioLanguage': info.audio_language})
                    report['episodes'] += 1
                except Exception as error:
                    report['errors'].append({'file': str(source_video), 'error': str(error)})
            if episodes_payload:
                seasons_payload.append({'number': season_index, 'title': f'Sezonul {season_index}', 'episodes': episodes_payload})
        if seasons_payload:
            catalog_series.append({'id': series_id, 'title': display_name(series_source.name), 'path': relative_posix(series_destination, destination), 'cover': relative_posix(cover_destination, destination) if cover_destination else None, 'seasons': seasons_payload})
            report['series'] += 1
            if cover_destination is None:
                report['warnings'].append(f'{series_source.name}: lipsește cover/poster')
    catalog = {'schemaVersion': 1, 'generatedAtEpochMs': int(time.time() * 1000), 'series': catalog_series}
    for file_name, payload in (('catalog.json', catalog), ('mehene-report.json', report)):
        final_path = destination / file_name
        temporary_path = destination / f'.{file_name}.tmp'
        temporary_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')
        os.replace(temporary_path, final_path)
    return report

def main(argv: Iterable[str] | None=None) -> int:
    parser = argparse.ArgumentParser(description='Construiește biblioteca offline Mehene')
    parser.add_argument('source', type=Path, help='Folderul cu fișierele originale')
    parser.add_argument('destination', type=Path, help='Folderul final Mehene')
    parser.add_argument('--audio-language', default='ron', help='Limba audio preferată, implicit ron')
    parser.add_argument('--media-profile', choices=('tablet', 'tv'), default='tablet', help='Profilul de ieșire: tablet (implicit) sau tv')
    args = parser.parse_args(argv)
    report = build_library(args.source, args.destination, args.audio_language, args.media_profile)
    print(f"✓ {report['series']} seriale")
    print(f"✓ {report['episodes']} episoade")
    print(f"✓ {report['compatibleCopied']} compatibile copiate")
    print(f"✓ {report['reused']} reutilizate fără procesare")
    print(f"✓ {report['converted']} convertite")
    print(f"✓ {report['videoStreamCopied']} fluxuri video copiate fără recodare")
    print(f"✓ {report['thumbnailsGenerated']} miniaturi generate")
    print(f"✓ {report['subtitlesIgnored']} subtitrări ignorate")
    print(f"✓ {report['subtitleStreamsDropped']} piste subtitle eliminate")
    if report['warnings']:
        print(f"⚠ {len(report['warnings'])} avertismente")
    if report['errors']:
        print(f"✗ {len(report['errors'])} erori; vezi mehene-report.json", file=sys.stderr)
        return 2
    return 0
if __name__ == '__main__':
    raise SystemExit(main())
