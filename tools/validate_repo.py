#!/usr/bin/env python3
"""Fast offline validation for the Mehene repository."""
from __future__ import annotations

import argparse
import importlib.util
import json
import py_compile
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

    with tempfile.TemporaryDirectory(prefix="mehene-validation-") as directory:
        root_path = Path(directory)
        source = root_path / "source"
        destination = root_path / "destination"
        source.mkdir()
        series = destination / "Serial"
        series.mkdir(parents=True)
        media = series / "Episod.mp4"
        media.write_bytes(b"validation")
        catalog = minimal_catalog()
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

        def failing_build(_source: Path, output: Path, _language: str) -> dict:
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


def validate_version(root: Path) -> None:
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'versionName = "2.3.0"' not in gradle or "versionCode = 7" not in gradle:
        fail("Versiunea Android nu este 2.3.0 / 7")


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
    validate_version(root)
    if args.android:
        run_android(root)

    print(f"✓ {xml_count} fișiere XML valide")
    print("✓ manifest offline și Application corecte")
    print("✓ politica fără securitate prezentă")
    print("✓ Builder: sintaxă, catalog, scriere atomică, lock și fail-closed")
    print("✓ versiune Android 2.3.0")
    if args.android:
        print("✓ teste, lint și APK debug/release")
    else:
        print("ℹ buildul Android nu a fost rulat; folosește --android pe PC/server")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
