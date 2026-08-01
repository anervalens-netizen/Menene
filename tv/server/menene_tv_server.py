#!/usr/bin/env python3
"""Read-only LAN server for the Menene TV catalog and media library."""

from __future__ import annotations

import argparse
import ipaddress
import json
import mimetypes
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path, PurePosixPath
from typing import BinaryIO
from urllib.parse import unquote, urlsplit


SERVER_VERSION = "1.0.0"
CATALOG_NAME = "catalog.json"
CHUNK_SIZE = 1024 * 1024
RANGE_RE = re.compile(r"^bytes=(\d*)-(\d*)$")


@dataclass(frozen=True)
class ByteRange:
    start: int
    end: int

    @property
    def length(self) -> int:
        return self.end - self.start + 1


def parse_byte_range(value: str | None, size: int) -> ByteRange | None:
    """Parse one RFC 7233 byte range; reject malformed or multi-ranges."""
    if value is None:
        return None
    match = RANGE_RE.fullmatch(value.strip())
    if not match or size <= 0:
        raise ValueError("invalid byte range")

    first, last = match.groups()
    if not first and not last:
        raise ValueError("empty byte range")

    if not first:
        suffix_length = int(last)
        if suffix_length <= 0:
            raise ValueError("invalid suffix length")
        start = max(0, size - suffix_length)
        return ByteRange(start=start, end=size - 1)

    start = int(first)
    end = size - 1 if not last else int(last)
    if start >= size or end < start:
        raise ValueError("unsatisfiable byte range")
    return ByteRange(start=start, end=min(end, size - 1))


def resolve_library_path(library_root: Path, encoded_relative_path: str) -> Path:
    """Resolve a URL path inside the library and reject traversal."""
    relative = unquote(encoded_relative_path)
    if "\x00" in relative:
        raise ValueError("null byte in path")
    candidate = (library_root / relative).resolve()
    try:
        candidate.relative_to(library_root)
    except ValueError as exc:
        raise ValueError("path escapes library") from exc
    return candidate


def normalize_catalog_path(value: object, field: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value:
        raise ValueError(f"catalog {field} path is invalid")
    path = PurePosixPath(value)
    normalized = path.as_posix()
    if path.is_absolute() or ".." in path.parts or normalized != value:
        raise ValueError(f"catalog {field} path is invalid")
    return normalized


def load_catalog(
    catalog_path: Path,
) -> tuple[bytes, dict[str, int], frozenset[str]]:
    raw = catalog_path.read_bytes()
    document = json.loads(raw)
    series = document.get("series")
    if document.get("schemaVersion") != 1 or not isinstance(series, list):
        raise ValueError("catalog.json has an unsupported schema")

    episode_count = 0
    subtitle_count = 0
    allowed_paths: set[str] = set()
    for serial in series:
        if serial.get("cover") is not None:
            allowed_paths.add(normalize_catalog_path(serial["cover"], "cover"))
        for season in serial.get("seasons", []):
            for episode in season.get("episodes", []):
                episode_count += 1
                if "subtitle" not in episode or episode["subtitle"] is not None:
                    subtitle_count += 1
                allowed_paths.add(normalize_catalog_path(episode.get("media"), "media"))
                if episode.get("artwork") is not None:
                    allowed_paths.add(
                        normalize_catalog_path(episode["artwork"], "artwork")
                    )

    if subtitle_count:
        raise ValueError("catalog.json violates the permanent no-subtitle rule")

    return (
        raw,
        {
            "series": len(series),
            "episodes": episode_count,
            "subtitles": subtitle_count,
        },
        frozenset(allowed_paths),
    )


def content_type(path: Path) -> str:
    if path.suffix.lower() == ".webp":
        return "image/webp"
    guessed, _ = mimetypes.guess_type(path.name)
    return guessed or "application/octet-stream"


class MeneneTvServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        address: tuple[str, int],
        library_root: Path,
        allowed_networks: tuple[ipaddress._BaseNetwork, ...],
    ) -> None:
        self.library_root = library_root.resolve()
        self.catalog_bytes, self.catalog_stats, self.allowed_paths = load_catalog(
            self.library_root / CATALOG_NAME
        )
        self.allowed_networks = allowed_networks
        super().__init__(address, MeneneRequestHandler)


class MeneneRequestHandler(BaseHTTPRequestHandler):
    server: MeneneTvServer
    protocol_version = "HTTP/1.1"

    def do_GET(self) -> None:
        self._serve(include_body=True)

    def do_HEAD(self) -> None:
        self._serve(include_body=False)

    def do_POST(self) -> None:
        self._json_error(HTTPStatus.METHOD_NOT_ALLOWED, "read-only server")

    def do_PUT(self) -> None:
        self._json_error(HTTPStatus.METHOD_NOT_ALLOWED, "read-only server")

    def do_DELETE(self) -> None:
        self._json_error(HTTPStatus.METHOD_NOT_ALLOWED, "read-only server")

    def do_OPTIONS(self) -> None:
        self.send_response(HTTPStatus.NO_CONTENT)
        self._common_headers()
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Range, Content-Type")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _serve(self, include_body: bool) -> None:
        if not self._client_allowed():
            self._json_error(HTTPStatus.FORBIDDEN, "client outside allowed network")
            return

        path = urlsplit(self.path).path
        if path == "/api/v1/health":
            self._serve_health(include_body)
            return
        if path == "/api/v1/catalog":
            self._serve_catalog(include_body)
            return
        if path.startswith("/media/"):
            self._serve_media(path[len("/media/") :], include_body)
            return
        self._json_error(HTTPStatus.NOT_FOUND, "route not found")

    def _client_allowed(self) -> bool:
        if not self.server.allowed_networks:
            return True
        try:
            client = ipaddress.ip_address(self.client_address[0])
        except ValueError:
            return False
        return any(client in network for network in self.server.allowed_networks)

    def _serve_health(self, include_body: bool) -> None:
        body = json.dumps(
            {
                "ok": True,
                "service": "menene-tv",
                "version": SERVER_VERSION,
                "catalog": self.server.catalog_stats,
            },
            separators=(",", ":"),
        ).encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self._common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if include_body:
            self.wfile.write(body)

    def _serve_catalog(self, include_body: bool) -> None:
        body = self.server.catalog_bytes
        self.send_response(HTTPStatus.OK)
        self._common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if include_body:
            self.wfile.write(body)

    def _serve_media(self, encoded_path: str, include_body: bool) -> None:
        try:
            path = resolve_library_path(self.server.library_root, encoded_path)
        except ValueError:
            self._json_error(HTTPStatus.BAD_REQUEST, "invalid media path")
            return
        relative_path = path.relative_to(self.server.library_root).as_posix()
        if relative_path not in self.server.allowed_paths or not path.is_file():
            self._json_error(HTTPStatus.NOT_FOUND, "media not found")
            return

        size = path.stat().st_size
        try:
            requested = parse_byte_range(self.headers.get("Range"), size)
        except ValueError:
            self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            self._common_headers()
            self.send_header("Content-Range", f"bytes */{size}")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        selected = requested or (ByteRange(0, size - 1) if size else None)
        status = HTTPStatus.PARTIAL_CONTENT if requested else HTTPStatus.OK
        self.send_response(status)
        self._common_headers()
        self.send_header("Content-Type", content_type(path))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(selected.length if selected else 0))
        self.send_header("Cache-Control", "public, max-age=86400")
        self.send_header("Last-Modified", self.date_time_string(path.stat().st_mtime))
        if requested:
            self.send_header(
                "Content-Range", f"bytes {selected.start}-{selected.end}/{size}"
            )
        self.end_headers()

        if include_body and selected is not None and selected.length:
            with path.open("rb") as source:
                source.seek(selected.start)
                self._copy_exact(source, selected.length)

    def _copy_exact(self, source: BinaryIO, remaining: int) -> None:
        while remaining:
            chunk = source.read(min(CHUNK_SIZE, remaining))
            if not chunk:
                break
            try:
                self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError):
                break
            remaining -= len(chunk)

    def _json_error(self, status: HTTPStatus, message: str) -> None:
        body = json.dumps({"error": message}, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self._common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _common_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Server-Timing", "menene;dur=0")

    def version_string(self) -> str:
        return f"MeneneTV/{SERVER_VERSION}"

    def log_message(self, fmt: str, *args: object) -> None:
        now = datetime.now(timezone.utc).isoformat(timespec="seconds")
        sys.stderr.write(f"{now} {self.client_address[0]} {fmt % args}\n")


def parse_networks(values: list[str]) -> tuple[ipaddress._BaseNetwork, ...]:
    return tuple(ipaddress.ip_network(value, strict=False) for value in values)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", type=Path, required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument(
        "--allow-subnet",
        action="append",
        default=[],
        help="CIDR allowed to read the service; repeat for multiple networks",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    library = args.library.resolve()
    if not library.is_dir():
        print(f"library directory does not exist: {library}", file=sys.stderr)
        return 2
    if not (library / CATALOG_NAME).is_file():
        print(f"catalog is missing: {library / CATALOG_NAME}", file=sys.stderr)
        return 2

    try:
        server = MeneneTvServer(
            (args.bind, args.port), library, parse_networks(args.allow_subnet)
        )
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"cannot start Menene TV server: {exc}", file=sys.stderr)
        return 2

    print(
        f"Menene TV server {SERVER_VERSION} listening on "
        f"http://{args.bind}:{args.port} with {server.catalog_stats['episodes']} episodes",
        file=sys.stderr,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
