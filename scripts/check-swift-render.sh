#!/usr/bin/env bash
# Prove an `rc-swift-render` binary draws, both ways the preview server drives it:
#
#   * one shot, at density 2, and the PNG is exactly the requested pixel size;
#   * as a pooled worker over the `rc-render-jvm` frame protocol: the hello names protocol 2, a
#     document renders, an SVG request and an undrawable document each fail *that request* while
#     the worker keeps serving, and closing stdin ends it cleanly.
#
# Usage: scripts/check-swift-render.sh <path to rc-swift-render>
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
binary="${1:?usage: scripts/check-swift-render.sh <path to rc-swift-render>}"
fixture="$repo_root/Tests/RcNativePlayerCoreTests/Fixtures/TitleCardRemote-640x480.rc"
work="$(mktemp -d "${TMPDIR:-/tmp}/rc-swift-render-check.XXXXXX")"
trap 'rm -rf "$work"' EXIT

"$binary" --input "$fixture" --output "$work/one-shot.png" --width 640 --height 480 \
  --density 2 --theme dark

python3 - "$binary" "$fixture" "$work/one-shot.png" <<'PY'
import struct, subprocess, sys

binary, fixture, one_shot = sys.argv[1:4]
HELLO, REQUEST, RESPONSE = 0x52435731, 0x52435131, 0x52435231


def png_size(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    return struct.unpack(">II", data[16:24])


size = png_size(open(one_shot, "rb").read())
assert size == (640, 480), f"one-shot PNG is {size}, expected (640, 480)"

document = open(fixture, "rb").read()
worker = subprocess.Popen([binary, "--worker"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)


def read_exactly(n):
    data = worker.stdout.read(n)
    assert data is not None and len(data) == n, "worker closed its stream early"
    return data


assert struct.unpack(">ii", read_exactly(8)) == (HELLO, 2), "unexpected worker hello"


def render(request_id, doc, width=320, height=240, density=1.5, fmt=0, theme=0, seeds=b""):
    frame = struct.pack(">iiiiiii", REQUEST, request_id, width, height,
                        struct.unpack(">i", struct.pack(">f", density))[0], fmt, theme)
    frame += struct.pack(">i", len(seeds)) + seeds + struct.pack(">i", len(doc)) + doc
    worker.stdin.write(frame)
    worker.stdin.flush()
    magic, answered, status, length = struct.unpack(">iiii", read_exactly(16))
    assert magic == RESPONSE and answered == request_id, "response does not answer the request"
    return status, read_exactly(length)


status, payload = render(1, document)
assert status == 0, f"worker failed the fixture: {payload[:300]!r}"
assert png_size(payload) == (320, 240), f"worker PNG is {png_size(payload)}"

status, payload = render(2, document, fmt=1)
assert status == 1, "an SVG request must fail, not answer with a PNG"

status, payload = render(3, b"not a remote compose document")
assert status == 1, "an undrawable document must fail its request"

# A seed the document does not declare is ignored rather than fatal.
seeds = b"str " + __import__("base64").b64encode(b"missing") + b" " + \
    __import__("base64").b64encode(b"value")
status, payload = render(4, document, theme=1, seeds=seeds)
assert status == 0, f"worker stopped serving after failures: {payload[:300]!r}"

worker.stdin.close()
assert worker.wait(timeout=30) == 0, "worker did not exit cleanly on end of input"
print("rc-swift-render: one-shot and worker ok")
PY
