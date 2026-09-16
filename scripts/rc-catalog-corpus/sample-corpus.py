#!/usr/bin/env python3
"""Reduce the catalog corpus to one document per component.

Two reasons, and the second is the one that matters.

COST. The CMP JVM lane renders all 701 documents in 43 seconds; the native UIKit lane ran 55
minutes without finishing and took a 90-minute macOS job down with it. Sixty documents is the
same measurement at a fraction of a CI job.

BALANCE. The sheet is not evenly spread: `pageindicator-vertical` alone is 100 of the 701
documents, `circularprogressindicator` 65, `edgebutton` 64, while 26 components have exactly one.
A score over the whole sheet is therefore mostly a score of four components — "14% of documents
failed" can mean one component is broken or fourteen are. One document per component answers
"which components can this player draw", which is the question the backlog is scoped to.

The pick is deterministic so two runs are comparable: the `ideal__default` variant when the
component publishes one, otherwise any `ideal` variant, otherwise the first by name. Deliberately
not random — a sample that moves between runs turns every score change into an investigation.
"""

import json
import pathlib
import shutil
import sys


def rank(identifier: str) -> tuple[int, str]:
    rest = identifier.split("__", 1)[1] if "__" in identifier else ""
    if rest.startswith("ideal__default"):
        return (0, identifier)
    if rest.startswith("ideal__"):
        return (1, identifier)
    return (2, identifier)


def main(source: pathlib.Path, destination: pathlib.Path) -> int:
    manifest = json.loads(source.joinpath("manifest.json").read_text())
    components: dict[str, list[dict]] = {}
    for entry in manifest:
        components.setdefault(entry["id"].split("__")[0], []).append(entry)

    chosen = [
        min(entries, key=lambda entry: rank(entry["id"]))
        for _, entries in sorted(components.items())
    ]

    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    for entry in chosen:
        shutil.copy2(source / f"{entry['id']}.rc", destination / f"{entry['id']}.rc")
    destination.joinpath("manifest.json").write_text(json.dumps(chosen, indent=1) + "\n")
    shutil.copy2(source / "PIN", destination / "PIN")

    print(
        f"sample: {len(chosen)} documents, one per component, from {len(manifest)} in the sheet"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])))
