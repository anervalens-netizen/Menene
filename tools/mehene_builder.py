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
from pathlib import Path
from typing import Any, Iterable

import mehene_library as legacy

BUILDER_VERSION = "2.1.0"
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


def validate_catalog(catalog: dict[str, Any], destination: Path) -> None:
    if catalog.get("schemaVersion") != 1:
        raise ValueError("schemaVersion catalog nesuportat")
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
        cover = series.get("cover")
        if cover and not safe_catalog_path(destination, str(cover)).is_file():
            raise ValueError(f"copertă inexistentă: {cover}")
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
                for key in ("artwork", "subtitle"):
                    value = episode.get(key)
                    if value and not safe_catalog_path(destination, str(value)).is_file():
                        raise ValueError(f"{key} inexistent: {value}")
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
    *,
    publish_partial: bool = False,
) -> dict[str, Any]:
    source = source.resolve()
    destination = destination.resolve()
    if not source.is_dir():
        raise SystemExit(f"Sursa nu este director: {source}")
    if source == destination or source in destination.parents:
        raise SystemExit("Destinația trebuie să fie în afara folderului sursă")
    destination.mkdir(parents=True, exist_ok=True)
    catalog_path = destination / "catalog.json"
    report_path = destination / "mehene-report.json"
    previous_catalog = catalog_path.read_bytes() if catalog_path.is_file() else None
    started = time.monotonic()

    with BuildLock(destination):
        legacy.copy_asset = atomic_copy_asset
        fingerprint = source_fingerprint(source)
        report = legacy.build_library(source, destination, preferred_language)
        report.update({
            "builderVersion": BUILDER_VERSION,
            "sourceFingerprint": fingerprint,
            "durationMs": int((time.monotonic() - started) * 1000),
            "catalogPublished": False,
        })

        candidate: dict[str, Any] | None = None
        try:
            if catalog_path.is_file():
                candidate = json.loads(catalog_path.read_text(encoding="utf-8"))
                candidate["builderVersion"] = BUILDER_VERSION
                candidate["sourceFingerprint"] = fingerprint
                validate_catalog(candidate, destination)
        except Exception as error:
            report.setdefault("errors", []).append({"file": str(catalog_path), "error": str(error)})

        errors = report.get("errors", [])
        if candidate is not None and (not errors or publish_partial):
            atomic_write_json(catalog_path, candidate)
            report["catalogPublished"] = True
        else:
            restore_catalog(catalog_path, previous_catalog)
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
