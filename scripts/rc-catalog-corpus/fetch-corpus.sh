#!/usr/bin/env bash
# Materialise the wear-m3-catalog Remote Compose sticker sheet as the shared comparison contract:
# a directory of `<id>.rc` documents plus the `manifest.json` every lane harness in this repository
# already reads.
#
# The corpus is NOT committed here. It is ~700 documents of someone else's generated output, it is
# regenerated upstream on every catalog change, and a stale copy would silently score the wrong
# thing. What is committed is the pin — one commit on the catalog's delivery branch — so a lane
# score always names the sheet it was measured against.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
pin_file="$repo_root/scripts/rc-catalog-corpus/pin"
output="${1:-$repo_root/build/rc-catalog-corpus}"
catalog_repo="https://github.com/yschimke/wear-m3-catalog"
# Documents are served by preview.coo.ee rather than carried on the delivery branch; see
# docs/design/RC_CATALOG_CORPUS_LANE.md under "The one weak link".
serve_root="${RC_CATALOG_SERVE:-https://preview.coo.ee/remote-m3/render}"

pin="$(tr -d '[:space:]' < "$pin_file")"
[ -n "$pin" ] || { echo "error: $pin_file is empty" >&2; exit 1; }

work="$(mktemp -d "${TMPDIR:-/tmp}/rc-catalog-corpus.XXXXXX")"
trap 'rm -rf "$work"' EXIT
rm -rf "$output"
mkdir -p "$output"

# The catalog inventory names every sticker. Reading it rather than scraping the compare page keeps
# this a data dependency on a pinned commit.
git -C "$work" init --quiet
git -C "$work" fetch --quiet --depth 1 "$catalog_repo" "$pin"
git -C "$work" show "FETCH_HEAD:catalog.json" > "$work/catalog.json"

python3 - "$work/catalog.json" "$output" "$serve_root" "$pin" <<'PY'
import json, pathlib, sys

catalog = json.loads(pathlib.Path(sys.argv[1]).read_text())
output, serve, pin = pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4]
identifiers = sorted(
    # images/<component-slug>/<variant>__<state>__<size>.png
    f"{image['path'].split('/')[1]}__{image['path'].split('/')[2][: -len('.png')]}"
    for component in catalog["components"]
    for image in component.get("images", [])
)
output.joinpath("urls.txt").write_text(
    "".join(
        f'url = "{serve}/{name}.rc?gen={pin}"\noutput = "{output / name}.rc"\n'
        for name in identifiers
    )
)
print(f"catalog {pin[:12]}: {len(identifiers)} stickers")
PY

# No --fail-early: a sticker the sheet renders but does not publish as a document should drop out of
# the manifest below rather than abort the corpus.
curl --silent --show-error --parallel --parallel-max 6 --retry 2 --config "$output/urls.txt"
rm -f "$output/urls.txt"

# The manifest is built from each document's OWN header, not from the catalog's rendered image
# sizes. They usually agree, but not always: the three `widgetcontainer-*` stickers are framed by
# the catalog and published 42px wider than the document they contain. A lane has to draw the
# document at the size the document states, or every lane draws a different picture and the score
# measures the manifest instead of the players.
python3 - "$output" "$pin" <<'PY'
import json, pathlib, struct, sys

WIDTH, HEIGHT, DENSITY_AT_GENERATION = 5, 6, 1031

output = pathlib.Path(sys.argv[1])


def header(document: bytes) -> dict[int, bytes]:
    count = struct.unpack(">I", document[13:17])[0]
    offset, properties = 17, {}
    for _ in range(count):
        key, length = struct.unpack(">HH", document[offset : offset + 4])
        offset += 4
        properties[key] = document[offset : offset + length]
        offset += length
    return properties


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
    except Exception as error:  # noqa: BLE001 - the reason belongs in the report, not a traceback
        path.unlink()
        unreadable.append(f"{path.stem} ({error})")
        continue
    entries.append(
        {
            "id": path.stem,
            "width": width,
            "height": height,
            # A document that declares no generation density still came off a 2.0 Wear capture;
            # every sticker on this sheet did. Saying so keeps the two lanes on one density rather
            # than letting three documents quietly resolve dp at 1.0 in one lane and 2.0 in another.
            "density": (
                struct.unpack(">f", properties[DENSITY_AT_GENERATION])[0]
                if DENSITY_AT_GENERATION in properties
                else 2.0
            ),
            # Every sticker here is an Android capture, so the Android density contract is the
            # honest mode to score it in. Native-default is a different question and a different
            # manifest.
            "androidCompatibility": True,
        }
    )

output.joinpath("manifest.json").write_text(json.dumps(entries, indent=1) + "\n")
output.joinpath("PIN").write_text(f"{sys.argv[2]}\n")
if dropped:
    print(f"no document published for {len(dropped)}: {', '.join(dropped[:4])}")
if unreadable:
    print(f"unreadable header for {len(unreadable)}: {', '.join(unreadable[:4])}")
print(f"corpus: {len(entries)} documents in {output}")
PY

node "$repo_root/scripts/rc-operation-conformance/validate-results.mjs" inputs "$output"
