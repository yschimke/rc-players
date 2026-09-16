#!/usr/bin/env python3
"""Build the lane manifest for a directory of catalog `.rc` documents.

The manifest is built from each document's OWN header, not from the catalog's rendered image sizes.
They usually agree, but not always: the three `widgetcontainer-*` stickers are framed by the catalog
and published 42px wider than the document they contain. A lane has to draw the document at the size
the document states, or every lane draws a different picture and the score measures the manifest
instead of the players.
"""

import collections
import json
import pathlib
import struct
import sys

WIDTH, HEIGHT, DENSITY_AT_GENERATION = 5, 6, 1031


def header(document: bytes) -> dict[int, bytes]:
    count = struct.unpack(">I", document[13:17])[0]
    offset, properties = 17, {}
    for _ in range(count):
        key, length = struct.unpack(">HH", document[offset : offset + 4])
        offset += 4
        properties[key] = document[offset : offset + length]
        offset += length
    return properties


def main(output: pathlib.Path, generation: str) -> int:
    entries, dropped, unreadable = [], [], []
    for path in sorted(output.glob("*.rc")):
        if path.stat().st_size == 0:
            path.unlink()
            dropped.append(path.stem)
            continue
        try:
            properties = header(path.read_bytes())
            width = struct.unpack(">i", properties[WIDTH])[0]
            height = struct.unpack(">i", properties[HEIGHT])[0]
        except Exception as error:  # noqa: BLE001 - the reason belongs in the report
            path.unlink()
            unreadable.append(f"{path.stem} ({error})")
            continue
        entries.append(
            {
                "id": path.stem,
                "width": width,
                "height": height,
                # A document that declares no generation density still came off a 2.0 Wear capture;
                # every sticker on this sheet did. Saying so keeps the lanes on one density rather
                # than letting three documents resolve dp at 1.0 in one lane and 2.0 in another.
                "density": (
                    struct.unpack(">f", properties[DENSITY_AT_GENERATION])[0]
                    if DENSITY_AT_GENERATION in properties
                    else 2.0
                ),
                # Every sticker here is an Android capture, so the Android density contract is the
                # honest mode to score it in. Native-default is a different question and manifest.
                "androidCompatibility": True,
            }
        )

    output.joinpath("manifest.json").write_text(json.dumps(entries, indent=1) + "\n")
    output.joinpath("PIN").write_text(f"{generation}\n")
    if dropped:
        print(f"no document published for {len(dropped)}: {', '.join(dropped[:4])}")
    if unreadable:
        print(f"unreadable header for {len(unreadable)}: {', '.join(unreadable[:4])}")
    print(f"corpus: {len(entries)} documents in {output}")

    # A refresh that fetched nothing usable is reported with what the server actually said, rather
    # than as a bare count, so the next failure names its own cause instead of being guessed at.
    if not entries:
        statuses: collections.Counter[str] = collections.Counter()
        status_log = output / "http-status.txt"
        if status_log.is_file():
            for line in status_log.read_text().splitlines():
                code = line.split(" ", 1)[0].strip()
                if code:
                    statuses[code] += 1
        summary = ", ".join(f"{code}x{count}" for code, count in sorted(statuses.items()))
        print(f"no usable documents; HTTP responses: {summary or 'none recorded'}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(pathlib.Path(sys.argv[1]), sys.argv[2]))
