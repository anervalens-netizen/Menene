#!/usr/bin/env python3
"""Fast offline validation for the Mehene repository."""
from __future__ import annotations

import argparse
import importlib.util
import json
import py_compile
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path


def fail(message: str) -> None:
    raise RuntimeError(message)


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        fail(f"Modulul {name} nu poate fi încărcat")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def validate_xml(root: Path) -> int:
    xml_files = sorted((root / "app/src/main").rglob("*.xml"))
    if not xml_files:
        fail("Nu au fost găsite resurse XML Android")
    for path in xml_files:
        ET.parse(path)
    return len(xml_files)


def validate_manifest(root: Path) -> None:
    manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if "android.permission.INTERNET" in manifest:
        fail("Manifestul nu trebuie să solicite INTERNET")
    if ".MeheneApplication" not in manifest:
        fail("MeheneApplication lipsește din manifest")


def validate_policy(root: Path) -> None:
    policy = root / "docs/NO_SECURITY.md"
    if not policy.is_file():
        fail("docs/NO_SECURITY.md lipsește")
    text = policy.read_text(encoding="utf-8").lower()
    for term in ("nu folosește pin", "nu se vor adăuga"):
        if term not in text:
            fail("Politica fără securitate nu conține regula obligatorie")


def minimal_catalog() -> dict:
    return {
        "schemaVersion": 1,
        "generatedAtEpochMs": 1,
        "series": [{
            "id": "series-1",
            "title": "Serial",
            "path": "Serial",
            "cover": None,
            "seasons": [{
                "number": 1,
                "title": "Sezonul 1",
                "episodes": [{
                    "id": "episode-1",
                    "number": 1,
                    "sortOrder": 1,
                    "title": "Episod",
                    "media": "Serial/Episod.mp4",
                    "artwork": None,
                    "subtitle": None,
                    "durationMs": 1000,
                }],
            }],
        }],
    }


def validate_builder(root: Path) -> None:
    tools = root / "tools"
    legacy_path = tools / "mehene_library.py"
    safe_path = tools / "mehene_builder.py"
    py_compile.compile(str(legacy_path), doraise=True)
    py_compile.compile(str(safe_path), doraise=True)
    sys.path.insert(0, str(tools))
    try:
        builder = load_module("mehene_builder_validation", safe_path)
    finally:
        sys.path.pop(0)
    if builder.BUILDER_VERSION != "2.4.0":
        fail(f"Versiune Builder neașteptată: {builder.BUILDER_VERSION}")

    with tempfile.TemporaryDirectory(prefix="mehene-validation-") as directory:
        root_path = Path(directory)
        source = root_path / "source"
        destination = root_path / "destination"
        source.mkdir()
        series = destination / "Serial"
        series.mkdir(parents=True)
        source_subtitle = source / "Episod.SRT"
        source_subtitle.write_text("1\n00:00:00,000 --> 00:00:01,000\nText\n", encoding="utf-8")
        (source / "Episod.en.vtt").write_text("language-tagged", encoding="utf-8")
        stale_subtitle = series / "Episod.VTT"
        orphan_subtitle = series / "Orphan.SRT"
        orphan_subtitle.write_text("orphan", encoding="utf-8")
        stale_subtitle.write_text("stale", encoding="utf-8")
        ignored = builder.legacy.discard_subtitles(source, destination)
        if ignored != 2 or stale_subtitle.exists() or orphan_subtitle.exists():
            fail("Builderul nu aplică regula permanentă fără subtitrări, inclusiv extensii uppercase, sufix de limbă și fișiere orfane")

        media = series / "Episod.mp4"
        media.write_bytes(b"validation")
        catalog = minimal_catalog()
        episode = catalog["series"][0]["seasons"][0]["episodes"][0]
        episode["subtitle"] = "Serial/Episod.srt"
        try:
            builder.validate_catalog(catalog, destination)
            fail("Validatorul catalogului acceptă subtitrări")
        except ValueError as error:
            if "subtitrările sunt interzise" not in str(error):
                raise
        episode["subtitle"] = None
        builder.validate_catalog(catalog, destination)

        target = destination / "catalog.json"
        builder.atomic_write_json(target, catalog)
        if json.loads(target.read_text(encoding="utf-8"))["series"][0]["id"] != "series-1":
            fail("Publicarea JSON atomică a eșuat")

        with builder.BuildLock(destination):
            try:
                with builder.BuildLock(destination):
                    fail("Lock-ul Builder permite două execuții simultane")
            except SystemExit:
                pass

        previous = target.read_bytes()
        original_build = builder.legacy.build_library

        def failing_build(_source: Path, output: Path, _language: str, _media_profile: str) -> dict:
            broken = minimal_catalog()
            broken["series"][0]["seasons"][0]["episodes"][0]["media"] = "missing.mp4"
            builder.atomic_write_json(output / "catalog.json", broken)
            return {
                "series": 1,
                "episodes": 0,
                "compatibleCopied": 0,
                "reused": 0,
                "converted": 0,
                "thumbnailsGenerated": 0,
                "warnings": [],
                "errors": [{"file": "test", "error": "simulated"}],
            }

        builder.legacy.build_library = failing_build
        try:
            report = builder.build_library(source, destination, "ron")
        finally:
            builder.legacy.build_library = original_build
        if report["catalogPublished"] or target.read_bytes() != previous:
            fail("Builderul nu a păstrat catalogul anterior după eroare")


def validate_media_profiles(root: Path) -> None:
    tools = root / "tools"
    builder = load_module("mehene_builder_media_profile_validation", tools / "mehene_builder.py")
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        fail("ffmpeg/ffprobe sunt necesare pentru gate-ul profilurilor media")

    with tempfile.TemporaryDirectory(prefix="mehene-media-profile-") as directory:
        root_path = Path(directory)
        source = root_path / "source" / "Serial" / "Season 01"
        source.mkdir(parents=True)
        subtitle = root_path / "captions.srt"
        subtitle.write_text("1\n00:00:00,000 --> 00:00:00,500\nignored\n", encoding="utf-8")
        source_video = source / "Episode.mkv"
        subprocess.run(
            [
                ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=24",
                "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=48000",
                "-i", str(subtitle), "-t", "1",
                "-map", "0:v:0", "-map", "1:a:0", "-map", "2:0",
                "-metadata:s:a:0", "language=ron", "-metadata:s:s:0", "language=eng",
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-ac", "2", "-c:s", "srt",
                str(source_video),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
        )

        def streams(path: Path) -> list[dict]:
            result = subprocess.run(
                [ffprobe, "-v", "error", "-show_streams", "-of", "json", str(path)],
                check=True,
                capture_output=True,
                text=True,
            )
            return json.loads(result.stdout)["streams"]

        tv_destination = root_path / "tv-destination"
        tv_report = builder.build_library(source.parent.parent, tv_destination, "ron", "tv")
        tv_media = next(tv_destination.rglob("*.mp4"))
        tv_streams = streams(tv_media)
        tv_video = next(stream for stream in tv_streams if stream.get("codec_type") == "video")
        tv_audio = next(stream for stream in tv_streams if stream.get("codec_type") == "audio")
        if tv_report.get("mediaProfile") != "tv" or tv_report.get("videoStreamCopied") != 1:
            fail(f"profilul TV nu raportează copierea fluxului video: {tv_report}")
        if tv_report.get("subtitleStreamsDropped") != 1 or any(stream.get("codec_type") == "subtitle" for stream in tv_streams):
            fail("profilul TV nu elimină pista subtitle internă")
        if (tv_video.get("codec_name"), tv_video.get("width"), tv_video.get("height")) != ("h264", 1920, 1080):
            fail("profilul TV nu păstrează video H.264 1080p")
        if tv_audio.get("codec_name") != "aac" or tv_audio.get("channels") != 2:
            fail("profilul TV nu produce AAC stereo")
        tv_audio_rate = int(tv_audio.get("bit_rate") or 0)
        if not 160000 <= tv_audio_rate <= 210000:
            fail(f"profilul TV nu produce audio AAC 192k: {tv_audio_rate}")

        tablet_destination = root_path / "tablet-destination"
        tablet_report = builder.build_library(source.parent.parent, tablet_destination, "ron")
        tablet_media = next(tablet_destination.rglob("*.mp4"))
        tablet_streams = streams(tablet_media)
        tablet_video = next(stream for stream in tablet_streams if stream.get("codec_type") == "video")
        if tablet_report.get("mediaProfile") != "tablet" or tablet_report.get("videoStreamCopied") != 0:
            fail("profilul tablet nu a rămas implicit")
        if tablet_video.get("width", 0) > 1280 or tablet_video.get("height", 0) > 720:
            fail("profilul tablet nu păstrează limita 720p")
        if any(stream.get("codec_type") == "subtitle" for stream in tablet_streams):
            fail("profilul tablet publică o pistă subtitle")


def validate_version(root: Path) -> None:
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'versionName = "2.4.0"' not in gradle or "versionCode = 8" not in gradle:
        fail("Versiunea Android nu este 2.4.0 / 8")


def run_android(root: Path) -> None:
    wrapper = root / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
    subprocess.run(
        [
            str(wrapper),
            "clean",
            "test",
            "lintDebug",
            "assembleDebug",
            "lintRelease",
            "assembleRelease",
        ],
        cwd=root,
        check=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Validează repository-ul Mehene")
    parser.add_argument("--android", action="store_true", help="Rulează și buildul Android complet")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]

    xml_count = validate_xml(root)
    validate_manifest(root)
    validate_policy(root)
    validate_builder(root)
    validate_media_profiles(root)
    validate_version(root)
    if args.android:
        run_android(root)

    print(f"✓ {xml_count} fișiere XML valide")
    print("✓ manifest offline și Application corecte")
    print("✓ politica fără securitate prezentă")
    print("✓ Builder: sintaxă, catalog, scriere atomică, lock și fail-closed")
    print("✓ versiune Android 2.4.0")
    if args.android:
        print("✓ teste, lint și APK debug/release")
    else:
        print("ℹ buildul Android nu a fost rulat; folosește --android pe PC/server")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
