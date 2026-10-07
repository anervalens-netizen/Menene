#!/usr/bin/env python3
"""Fail-closed orchestration for the Mehene Library Builder."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import time
import tempfile
from pathlib import Path
from typing import Any, Iterable

import mehene_library as legacy

BUILDER_VERSION = "2.4.0"
ARTWORK_CONTRACT_VERSION = 1
ARTWORK_ROLES = {"series-card", "series-hero", "episode-card"}
ARTWORK_SHAPES = {"landscape", "poster", "square"}
LOCK_STALE_SECONDS = 6 * 60 * 60


class BuildLock:
    def __init__(self, destination: Path) -> None:
        self.path = destination / ".mehene-build.lock"
        self.fd: int | None = None

    def __enter__(self) -> "BuildLock":
        self.path.parent.mkdir(parents=True, exist_ok=True)
        for attempt in range(2):
            try:
                self.fd = os.open(self.path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                payload = json.dumps({"pid": os.getpid(), "startedAtEpochMs": int(time.time() * 1000)})
                os.write(self.fd, payload.encode("utf-8"))
                os.fsync(self.fd)
                return self
            except FileExistsError:
                if attempt == 0 and self._is_stale():
                    self.path.unlink(missing_ok=True)
                    continue
                raise SystemExit(f"O altă construire Mehene este activă: {self.path}")
        raise SystemExit("Nu s-a putut obține lock-ul Builder")

    def __exit__(self, exc_type: object, exc: object, traceback: object) -> None:
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None
        self.path.unlink(missing_ok=True)

    def _is_stale(self) -> bool:
        try:
            return time.time() - self.path.stat().st_mtime > LOCK_STALE_SECONDS
        except FileNotFoundError:
            return True


def fsync_directory(directory: Path) -> None:
    if os.name == "nt":
        return
    fd = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def atomic_write_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    try:
        with temporary.open("wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        fsync_directory(path.parent)
    finally:
        temporary.unlink(missing_ok=True)


def atomic_write_json(path: Path, payload: Any) -> None:
    atomic_write_bytes(path, json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8"))


def atomic_copy_asset(source: Path, destination: Path) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.resolve() == destination.resolve():
        return destination
    if destination.is_file():
        source_stat = source.stat()
        destination_stat = destination.stat()
        if destination_stat.st_size == source_stat.st_size and destination_stat.st_mtime_ns >= source_stat.st_mtime_ns:
            return destination
    temporary = destination.with_name(f".{destination.name}.{os.getpid()}.copy")
    try:
        with source.open("rb") as input_stream, temporary.open("wb") as output_stream:
            shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)
            output_stream.flush()
            os.fsync(output_stream.fileno())
        shutil.copystat(source, temporary)
        os.replace(temporary, destination)
        fsync_directory(destination.parent)
        return destination
    finally:
        temporary.unlink(missing_ok=True)


def source_fingerprint(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted((item for item in root.rglob("*") if item.is_file()), key=lambda item: item.as_posix().lower()):
        stat = path.stat()
        digest.update(path.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(str(stat.st_size).encode("ascii"))
        digest.update(b"\0")
        digest.update(str(stat.st_mtime_ns).encode("ascii"))
        digest.update(b"\n")
    return digest.hexdigest()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def catalog_revision(catalog: dict[str, Any]) -> str:
    stable = {
        key: value
        for key, value in catalog.items()
        if key not in {"catalogRevision", "generatedAtEpochMs"}
    }
    encoded = json.dumps(stable, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def safe_catalog_path(destination: Path, relative: str) -> Path:
    if not relative or relative == ".":
        raise ValueError("cale catalog goală")
    relative_path = Path(relative.replace("\\", "/"))
    if relative_path.is_absolute() or any(part in {"", ".", ".."} for part in relative_path.parts):
        raise ValueError(f"cale nesigură în catalog: {relative}")
    resolved = (destination / relative_path).resolve()
    try:
        resolved.relative_to(destination.resolve())
    except ValueError as error:
        raise ValueError(f"cale în afara bibliotecii: {relative}") from error
    return resolved
def validate_artwork_metadata(metadata: Any, asset: Path, label: str) -> None:
    if not isinstance(metadata, dict):
        raise ValueError(f"metadata artwork invalidă: {label}")
    role = metadata.get("role")
    if role not in ARTWORK_ROLES:
        raise ValueError(f"rol artwork invalid: {label}")
    shape = metadata.get("shape")
    if shape not in ARTWORK_SHAPES:
        raise ValueError(f"formă artwork invalidă: {label}")
    width = metadata.get("width")
    height = metadata.get("height")
    if (
        isinstance(width, bool)
        or isinstance(height, bool)
        or not isinstance(width, int)
        or not isinstance(height, int)
        or width <= 0
        or height <= 0
    ):
        raise ValueError(f"dimensiuni artwork invalide: {label}")
    expected_shape = legacy.artwork_shape(width, height)
    if shape != expected_shape:
        raise ValueError(f"forma artwork nu corespunde dimensiunilor: {label}")
    digest = metadata.get("sha256")
    if (
        not isinstance(digest, str)
        or len(digest) != 64
        or any(character not in "0123456789abcdef" for character in digest)
    ):
        raise ValueError(f"hash artwork invalid: {label}")
    if not asset.is_file() or asset.stat().st_size <= 0 or file_sha256(asset) != digest:
        raise ValueError(f"hash artwork nu corespunde fișierului: {label}")


def validate_artwork_fields(
    entry: dict[str, Any],
    destination: Path,
    label: str,
    fields: tuple[tuple[str, str, bool], ...],
) -> None:
    raw_metadata = entry.get("artworkMeta", {})
    if raw_metadata is None:
        raw_metadata = {}
    if not isinstance(raw_metadata, dict):
        raise ValueError(f"artworkMeta invalid pentru {label}")
    present_metadata_keys = {
        metadata_key
        for field, metadata_key, _metadata_required in fields
        if entry.get(field) is not None
    }
    for field, metadata_key, metadata_required in fields:
        value = entry.get(field)
        metadata = raw_metadata.get(metadata_key)
        if value is None:
            if metadata is not None and metadata_key not in present_metadata_keys:
                raise ValueError(f"metadata artwork fără fișier pentru {label}.{field}")
            continue
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"cale artwork invalidă pentru {label}.{field}")
        asset = safe_catalog_path(destination, value)
        if not asset.is_file() or asset.stat().st_size <= 0:
            raise ValueError(f"artwork inexistent sau gol: {value}")
        if metadata_required and metadata is None:
            raise ValueError(f"metadata artwork lipsește pentru {label}.{field}")
        if metadata is not None:
            validate_artwork_metadata(metadata, asset, f"{label}.{field}")




def validate_catalog(catalog: dict[str, Any], destination: Path) -> None:
    if catalog.get("schemaVersion") != 1:
        raise ValueError("schemaVersion catalog nesuportat")
    if "artworkContractVersion" in catalog and catalog["artworkContractVersion"] != ARTWORK_CONTRACT_VERSION:
        raise ValueError("versiune contract artwork nesuportată")
    if "catalogRevision" in catalog:
        revision = catalog["catalogRevision"]
        if (
            not isinstance(revision, str)
            or len(revision) != 64
            or any(character not in "0123456789abcdef" for character in revision)
        ):
            raise ValueError("catalogRevision invalid")
        if revision != catalog_revision(catalog):
            raise ValueError("catalogRevision nu corespunde catalogului")
    series_ids: set[str] = set()
    episode_ids: set[str] = set()
    episode_count = 0
    for series in catalog.get("series", []):
        series_id = str(series.get("id", "")).strip()
        if not series_id or series_id in series_ids:
            raise ValueError(f"ID serial invalid sau duplicat: {series_id}")
        series_ids.add(series_id)
        series_path = safe_catalog_path(destination, str(series.get("path", "")))
        if not series_path.is_dir():
            raise ValueError(f"director serial inexistent: {series_path}")
        validate_artwork_fields(
            series,
            destination,
            f"serial {series_id}",
            (("cover", "card", False), ("cardArtwork", "card", True), ("heroArtwork", "hero", True)),
        )
        display_title = series.get("displayTitle")
        if display_title is not None and (not isinstance(display_title, str) or not display_title.strip()):
            raise ValueError(f"displayTitle invalid pentru serial {series_id}")
        season_numbers: set[int] = set()
        for season in series.get("seasons", []):
            season_number = int(season.get("number", 0))
            if season_number <= 0 or season_number in season_numbers:
                raise ValueError(f"sezon invalid sau duplicat: {season_number}")
            season_numbers.add(season_number)
            for episode in season.get("episodes", []):
                episode_id = str(episode.get("id", "")).strip()
                if not episode_id or episode_id in episode_ids:
                    raise ValueError(f"ID episod invalid sau duplicat: {episode_id}")
                episode_ids.add(episode_id)
                media = safe_catalog_path(destination, str(episode.get("media", "")))
                if not media.is_file() or media.stat().st_size <= 0:
                    raise ValueError(f"video inexistent sau gol: {media}")
                if "subtitle" not in episode or episode.get("subtitle") is not None:
                    raise ValueError("subtitrările sunt interzise; câmpul subtitle trebuie să fie null")
                validate_artwork_fields(
                    episode,
                    destination,
                    f"episod {episode_id}",
                    (("artwork", "card", False), ("cardArtwork", "card", True)),
                )
                display_title = episode.get("displayTitle")
                if display_title is not None and (not isinstance(display_title, str) or not display_title.strip()):
                    raise ValueError(f"displayTitle invalid pentru episod {episode_id}")
                episode_count += 1
    if series_ids and episode_count == 0:
        raise ValueError("catalogul nu conține episoade")
def restore_catalog(catalog_path: Path, previous: bytes | None) -> None:
    if previous is None:
        catalog_path.unlink(missing_ok=True)
        fsync_directory(catalog_path.parent)
    else:
        atomic_write_bytes(catalog_path, previous)


def build_library(
    source: Path,
    destination: Path,
    preferred_language: str,
    media_profile: str = "tablet",
    *,
    publish_partial: bool = False,
) -> dict[str, Any]:
    source = source.resolve()
    destination = destination.resolve()
    if media_profile not in {"tablet", "tv"}:
        raise SystemExit(f"Profil media necunoscut: {media_profile}")
    if not source.is_dir():
        raise SystemExit(f"Sursa nu este director: {source}")
    if source == destination or source in destination.parents:
        raise SystemExit("Destinația trebuie să fie în afara folderului sursă")
    destination.mkdir(parents=True, exist_ok=True)
    catalog_path = destination / "catalog.json"
    report_path = destination / "mehene-report.json"
    started = time.monotonic()

    with BuildLock(destination), tempfile.TemporaryDirectory(prefix=".catalog-stage-", dir=destination) as staging:
        legacy.copy_asset = atomic_copy_asset
        fingerprint = source_fingerprint(source)
        candidate_path = Path(staging) / "catalog.json"
        report = legacy.build_library(source, destination, preferred_language, media_profile, catalog_output=candidate_path)
        report.update({
            "builderVersion": BUILDER_VERSION,
            "sourceFingerprint": fingerprint,
            "mediaProfile": media_profile,
            "durationMs": int((time.monotonic() - started) * 1000),
            "catalogPublished": False,
        })

        candidate: dict[str, Any] | None = None
        try:
            if candidate_path.is_file():
                candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
                candidate["builderVersion"] = BUILDER_VERSION
                candidate["sourceFingerprint"] = fingerprint
                candidate["mediaProfile"] = media_profile
                candidate["artworkContractVersion"] = ARTWORK_CONTRACT_VERSION
                candidate["catalogRevision"] = catalog_revision(candidate)
                validate_catalog(candidate, destination)
        except Exception as error:
            candidate = None
            report.setdefault("errors", []).append({"file": str(catalog_path), "error": str(error)})

        errors = report.get("errors", [])
        if candidate is not None and (not errors or publish_partial):
            atomic_write_json(catalog_path, candidate)
            report["catalogPublished"] = True
        else:
            report.setdefault("warnings", []).append(
                "Catalogul anterior a fost păstrat deoarece există erori; folosește --publish-partial numai intenționat"
            )

        atomic_write_json(report_path, report)
        return report


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Construiește în mod fail-closed biblioteca offline Mehene")
    parser.add_argument("source", type=Path, help="Folderul cu fișierele originale")
    parser.add_argument("destination", type=Path, help="Folderul final Mehene")
    parser.add_argument("--audio-language", default="ron", help="Limba audio preferată, implicit ron")
    parser.add_argument("--media-profile", choices=("tablet", "tv"), default="tablet", help="Profilul de ieșire: tablet (implicit) sau tv")
    parser.add_argument(
        "--publish-partial",
        action="store_true",
        help="Publică un catalog parțial chiar dacă unele fișiere au eșuat",
    )
    args = parser.parse_args(argv)
    report = build_library(
        args.source,
        args.destination,
        args.audio_language,
        args.media_profile,
        publish_partial=args.publish_partial,
    )
    print(f"✓ {report['series']} seriale")
    print(f"✓ {report['episodes']} episoade")
    print(f"✓ {report['reused']} reutilizate fără procesare")
    print(f"✓ catalog publicat: {'da' if report['catalogPublished'] else 'nu'}")
    if report.get("warnings"):
        print(f"⚠ {len(report['warnings'])} avertismente")
    if report.get("errors"):
        print(f"✗ {len(report['errors'])} erori; vezi mehene-report.json", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
