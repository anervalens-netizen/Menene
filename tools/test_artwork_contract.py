#!/usr/bin/env python3
from __future__ import annotations

import copy
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

import mehene_builder as builder
import mehene_library as library


class ArtworkContractTests(unittest.TestCase):
    def test_explicit_sidecars_win_with_deterministic_extension_order(self) -> None:
        with tempfile.TemporaryDirectory(prefix="mehene-artwork-") as directory:
            root = Path(directory)
            series = root / "Bluey"
            series.mkdir()
            (series / "cover.jpg").touch()
            (series / "card.png").touch()
            (series / "card.webp").touch()
            (series / "hero.jpeg").touch()
            video = series / "Episode 01.mp4"
            video.touch()
            (series / "Episode 01.png").touch()
            (series / "Episode 01.webp").touch()
            (series / "Episode 01.card.png").touch()
            (series / "Episode 01.card.webp").touch()

            self.assertEqual(library.choose_cover(series).name, "cover.jpg")
            self.assertEqual(
                library.choose_named_artwork(series, library.CARD_ARTWORK_NAMES).name,
                "card.webp",
            )
            self.assertEqual(
                library.named_sidecar(video, library.CARD_ARTWORK_NAMES).name,
                "Episode 01.card.webp",
            )
            self.assertEqual(library.sidecar(video, library.IMAGE_EXTENSIONS).name, "Episode 01.webp")

    def test_display_title_sidecars_are_normalized(self) -> None:
        with tempfile.TemporaryDirectory(prefix="mehene-title-") as directory:
            root = Path(directory)
            series = root / "Bluey"
            series.mkdir()
            (series / "display-title.txt").write_text("  Bluey  " + chr(10) + "  și familia  ", encoding="utf-8")
            video = series / "Episode 01.mp4"
            video.touch()
            (series / "Episode 01.display.txt").write_text("  Prima aventură\n specială  ", encoding="utf-8")

            self.assertEqual(
                library.read_text_sidecar(series, library.SERIES_TITLE_SIDECARS),
                "Bluey și familia",
            )
            self.assertEqual(
                library.read_episode_display_title(video),
                "Prima aventură specială",
            )

    def test_shape_metadata_is_deterministic(self) -> None:
        self.assertEqual(library.artwork_shape(1920, 1080), "landscape")
        self.assertEqual(library.artwork_shape(435, 640), "poster")
        self.assertEqual(library.artwork_shape(1000, 1000), "square")

    def test_catalog_revision_ignores_timestamp_but_tracks_contract_changes(self) -> None:
        catalog = {
            "schemaVersion": 1,
            "generatedAtEpochMs": 1,
            "series": [{"id": "bluey", "displayTitle": "Bluey"}],
        }
        later = copy.deepcopy(catalog)
        later["generatedAtEpochMs"] = 2
        changed = copy.deepcopy(catalog)
        changed["series"][0]["displayTitle"] = "Bluey și familia"

        self.assertEqual(builder.catalog_revision(catalog), builder.catalog_revision(later))
        self.assertNotEqual(builder.catalog_revision(catalog), builder.catalog_revision(changed))

    def test_validator_accepts_legacy_and_validates_new_metadata_hashes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="mehene-validator-") as directory:
            destination = Path(directory)
            series_path = destination / "Bluey"
            series_path.mkdir()
            media = series_path / "Episode.mp4"
            card = series_path / "card.webp"
            hero = series_path / "hero.webp"
            episode_card = series_path / "Episode.webp"
            for path in (media, card, hero, episode_card):
                path.write_bytes(path.name.encode("utf-8"))

            def metadata(path: Path, role: str, width: int, height: int) -> dict:
                return {
                    "role": role,
                    "shape": library.artwork_shape(width, height),
                    "width": width,
                    "height": height,
                    "sha256": builder.file_sha256(path),
                }

            catalog = {
                "schemaVersion": 1,
                "generatedAtEpochMs": 1,
                "artworkContractVersion": 1,
                "series": [{
                    "id": "bluey",
                    "title": "Bluey",
                    "displayTitle": "Bluey și familia",
                    "path": "Bluey",
                    "cover": "Bluey/card.webp",
                    "cardArtwork": "Bluey/card.webp",
                    "heroArtwork": "Bluey/hero.webp",
                    "artworkMeta": {
                        "card": metadata(card, "series-card", 640, 360),
                        "hero": metadata(hero, "series-hero", 1600, 600),
                    },
                    "seasons": [{
                        "number": 1,
                        "title": "Sezonul 1",
                        "episodes": [{
                            "id": "bluey-1",
                            "number": 1,
                            "sortOrder": 1,
                            "title": "Episode",
                            "displayTitle": "Un titlu complet",
                            "media": "Bluey/Episode.mp4",
                            "artwork": "Bluey/Episode.webp",
                            "cardArtwork": "Bluey/Episode.webp",
                            "artworkMeta": {
                                "card": metadata(episode_card, "episode-card", 960, 540),
                            },
                            "subtitle": None,
                            "durationMs": 1000,
                        }],
                    }],
                }],
            }
            catalog["catalogRevision"] = builder.catalog_revision(catalog)
            builder.validate_catalog(catalog, destination)

            legacy = copy.deepcopy(catalog)
            legacy.pop("artworkContractVersion")
            legacy.pop("catalogRevision")
            legacy["series"][0]["cardArtwork"] = None
            legacy["series"][0]["heroArtwork"] = None
            legacy["series"][0]["artworkMeta"] = {}
            episode = legacy["series"][0]["seasons"][0]["episodes"][0]
            episode["cardArtwork"] = None
            episode["artworkMeta"] = {}
            builder.validate_catalog(legacy, destination)

            broken = copy.deepcopy(catalog)
            broken["series"][0]["artworkMeta"]["hero"]["sha256"] = "0" * 64
            broken["catalogRevision"] = builder.catalog_revision(broken)
            with self.assertRaisesRegex(ValueError, "hash artwork"):
                builder.validate_catalog(broken, destination)


if __name__ == "__main__":
    unittest.main()
