import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from tv.server.menene_tv_server import (
    ByteRange,
    MeneneTvServer,
    load_catalog,
    parse_byte_range,
    resolve_library_path,
)


class RangeTests(unittest.TestCase):
    def test_full_open_ended_and_suffix_ranges(self) -> None:
        self.assertIsNone(parse_byte_range(None, 100))
        self.assertEqual(parse_byte_range("bytes=10-19", 100), ByteRange(10, 19))
        self.assertEqual(parse_byte_range("bytes=90-", 100), ByteRange(90, 99))
        self.assertEqual(parse_byte_range("bytes=-10", 100), ByteRange(90, 99))
        self.assertEqual(parse_byte_range("bytes=-200", 100), ByteRange(0, 99))

    def test_range_is_clamped_and_invalid_values_fail(self) -> None:
        self.assertEqual(parse_byte_range("bytes=90-200", 100), ByteRange(90, 99))
        for value in ("bytes=100-101", "bytes=20-10", "items=0-1", "bytes=0-1,3-4"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                parse_byte_range(value, 100)


class CatalogTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_catalog(self, subtitle=None, include_subtitle=True) -> Path:
        episode = {
            "id": "episode-1",
            "media": "Series/Season 01/E01.mp4",
        }
        if include_subtitle:
            episode["subtitle"] = subtitle
        catalog = {
            "schemaVersion": 1,
            "series": [
                {
                    "id": "series-1",
                    "cover": "Series/cover.webp",
                    "cardArtwork": "Series/card.webp",
                    "heroArtwork": "Series/hero.webp",
                    "seasons": [
                        {
                            "number": 1,
                            "episodes": [episode],
                        }
                    ],
                }
            ],
        }
        path = self.root / "catalog.json"
        path.write_text(json.dumps(catalog), encoding="utf-8")
        return path

    def test_catalog_counts_and_enforces_no_subtitles(self) -> None:
        _, stats, allowed = load_catalog(self.write_catalog())
        self.assertEqual(stats, {"series": 1, "episodes": 1, "subtitles": 0})
        self.assertEqual(
            allowed,
            {
                "Series/Season 01/E01.mp4",
                "Series/cover.webp",
                "Series/card.webp",
                "Series/hero.webp",
            },
        )
        with self.assertRaisesRegex(ValueError, "no-subtitle"):
            load_catalog(self.write_catalog("Episode.srt"))
        with self.assertRaisesRegex(ValueError, "no-subtitle"):
            load_catalog(self.write_catalog(include_subtitle=False))

    def test_library_path_cannot_escape_root(self) -> None:
        expected = self.root / "Series" / "Episode 01.mp4"
        self.assertEqual(
            resolve_library_path(self.root, "Series/Episode%2001.mp4"), expected
        )
        with self.assertRaisesRegex(ValueError, "escapes"):
            resolve_library_path(self.root, "../secret")


class HttpTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        (self.root / "Series").mkdir()
        (self.root / "Series" / "Episode.mp4").write_bytes(b"0123456789")
        (self.root / "Series" / "Empty.mp4").write_bytes(b"")
        (self.root / "Series" / "card.webp").write_bytes(b"card")
        (self.root / "Series" / "hero.webp").write_bytes(b"hero")
        (self.root / "Series" / "episode.webp").write_bytes(b"episode")
        (self.root / "Series" / "Unreferenced.txt").write_text("private")
        (self.root / "catalog.json").write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "series": [
                        {
                            "id": "series-1",
                            "cardArtwork": "Series/card.webp",
                            "heroArtwork": "Series/hero.webp",
                            "seasons": [
                                {
                                    "episodes": [
                                        {
                                            "id": "episode-1",
                                            "media": "Series/Episode.mp4",
                                            "artwork": "Series/Empty.mp4",
                                            "subtitle": None,
                                            "cardArtwork": "Series/episode.webp",
                                        }
                                    ]
                                }
                            ],
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.server = MeneneTvServer(("127.0.0.1", 0), self.root, ())
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    def request(self, path: str, headers=None, method="GET"):
        return urllib.request.urlopen(
            urllib.request.Request(self.base + path, headers=headers or {}, method=method),
            timeout=3,
        )

    def test_health_catalog_and_range_streaming(self) -> None:
        with self.request("/api/v1/health") as response:
            body = json.load(response)
            self.assertTrue(body["ok"])
            self.assertEqual(body["catalog"]["episodes"], 1)

        with self.request("/api/v1/catalog") as response:
            self.assertEqual(json.load(response)["schemaVersion"], 1)

        with self.request(
            "/media/Series/Episode.mp4", {"Range": "bytes=2-5"}
        ) as response:
            self.assertEqual(response.status, 206)
            self.assertEqual(response.headers["Content-Range"], "bytes 2-5/10")
            self.assertEqual(response.read(), b"2345")

        for artwork in ("card.webp", "hero.webp", "episode.webp"):
            with self.subTest(artwork=artwork), self.request(
                f"/media/Series/{artwork}"
            ) as response:
                self.assertEqual(response.status, 200)

    def test_head_traversal_and_read_only_contract(self) -> None:
        with self.request("/media/Series/Episode.mp4", method="HEAD") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.headers["Accept-Ranges"], "bytes")
            self.assertEqual(response.read(), b"")

        with self.request(
            "/media/Series/Episode.mp4", {"Range": "bytes=2-5"}, method="HEAD"
        ) as response:
            self.assertEqual(response.status, 206)
            self.assertEqual(response.headers["Content-Range"], "bytes 2-5/10")
            self.assertEqual(response.headers["Content-Length"], "4")

        with self.request("/media/Series/Empty.mp4") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.headers["Content-Length"], "0")
            self.assertEqual(response.read(), b"")

        with self.assertRaises(urllib.error.HTTPError) as traversal:
            self.request("/media/%2e%2e/secret")
        self.assertEqual(traversal.exception.code, 400)

        with self.assertRaises(urllib.error.HTTPError) as unreferenced:
            self.request("/media/Series/Unreferenced.txt")
        self.assertEqual(unreferenced.exception.code, 404)

        with self.assertRaises(urllib.error.HTTPError) as unsatisfiable:
            self.request("/media/Series/Episode.mp4", {"Range": "bytes=20-30"})
        self.assertEqual(unsatisfiable.exception.code, 416)
        self.assertEqual(unsatisfiable.exception.headers["Content-Range"], "bytes */10")

        with self.assertRaises(urllib.error.HTTPError) as post:
            self.request("/api/v1/catalog", method="POST")
        self.assertEqual(post.exception.code, 405)


if __name__ == "__main__":
    unittest.main()
