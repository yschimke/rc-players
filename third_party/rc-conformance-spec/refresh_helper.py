"""Gerrit JSON helpers for refresh.sh. Not a standalone tool."""

import base64
import json
import os
import sys
import urllib.parse
import urllib.request

BINARY_SUFFIXES = (".ttf", ".otf", ".png", ".jpg", ".webp")


def _latest(detail):
    return max(detail["revisions"].items(), key=lambda kv: kv[1]["_number"])


def revision(detail_path):
    pinned = os.environ.get("REVISION") or ""
    detail = json.load(open(detail_path))
    if pinned:
        if pinned not in detail["revisions"]:
            sys.exit(f"revision {pinned} is not a patch set of this change")
        return pinned
    return _latest(detail)[0]


def describe(detail_path):
    detail = json.load(open(detail_path))
    sha, rev = _latest(detail)
    print(f"    subject:  {detail['subject']}")
    print(f"    status:   {detail['status']}")
    print(f"    patchset: {rev['_number']} (latest)")


def binaries(detail_path, sha, root, host, change):
    detail = json.load(open(detail_path))
    files = detail["revisions"][sha]["files"]
    for path in sorted(files):
        if not path.lower().endswith(BINARY_SUFFIXES):
            continue
        quoted = urllib.parse.quote(path, safe="")
        with urllib.request.urlopen(f"{host}/changes/{change}/revisions/{sha}/files/{quoted}/content") as response:
            blob = base64.b64decode(response.read())
        dest = os.path.join(root, path)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as out:
            out.write(blob)
        print(f"    {path}  ({len(blob)} bytes)")


if __name__ == "__main__":
    command, args = sys.argv[1], sys.argv[2:]
    result = {"revision": revision, "describe": describe, "binaries": binaries}[command](*args)
    if result is not None:
        print(result)
