#!/usr/bin/env python3
"""Run every SwiftPM test in its own process, one at a time, and report each result.

`swift test` runs a whole target in one process, so one test's leftover state — a registered font,
a cached layout, a static that a previous test mutated — can make another pass or fail depending on
order, and one crash takes every later test in the process down with it. This builds the tests once,
lists them, and runs each with an exact `--filter`, so every result stands on its own.

    scripts/run-swift-tests-isolated.py                          # every test except the fuzz suite
    scripts/run-swift-tests-isolated.py --filter NativeLayout    # only ids matching a regex
    scripts/run-swift-tests-isolated.py --skip ''                # include the fuzz suite too

Writes `results.tsv`, `summary.md` and one log per test under `--out`, appends the summary to
`$GITHUB_STEP_SUMMARY` when set, and exits non-zero if any test failed, timed out or matched nothing.
"""
import argparse
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--filter", default="", help="only run test ids matching this regex")
    parser.add_argument(
        "--skip",
        default="NativeSwiftFuzzTests",
        help="skip test ids matching this regex (default: the fuzz suite, which CI runs optimized)",
    )
    parser.add_argument("--timeout", type=int, default=600, help="seconds per test (default 600)")
    parser.add_argument("--out", default=str(REPO / "build" / "swift-tests-isolated"))
    return parser.parse_args()


def run(command, log_path=None, timeout=None):
    """Runs `command` in its own process group, so a timeout also kills the test runner it spawns."""
    output = open(log_path, "w") if log_path else subprocess.PIPE
    started = time.monotonic()
    process = subprocess.Popen(
        command, cwd=REPO, stdout=output, stderr=subprocess.STDOUT, text=True,
        start_new_session=True)
    try:
        stdout, _ = process.communicate(timeout=timeout)
        timed_out = False
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        stdout, _ = process.communicate()
        timed_out = True
    finally:
        if log_path:
            output.close()
    return process.returncode, stdout, timed_out, time.monotonic() - started


def list_tests():
    code, stdout, _, _ = run(["swift", "test", "list", "--skip-build"])
    if code != 0:
        sys.exit(f"swift test list failed:\n{stdout}")
    # Test ids are `Module.Suite/test()`; anything else is toolchain chatter.
    return [line.strip() for line in stdout.splitlines() if re.fullmatch(r"\S+\.\S+/\S+", line.strip())]


def exact_filter(test):
    """A `--filter` that selects exactly `test`.

    swift-testing matches the filter against the full test ID, which carries the source location
    after the name (`Module.Suite/name()/File.swift:12:3`) even though `swift test list` prints it
    without one, so a plain `$` anchor matches nothing. The optional `/…` tail allows the location;
    the name itself still has to match whole, so `test()` never selects `testMore()`.
    """
    return f"^{re.escape(test)}(/.*)?$"


def classify(code, timed_out, log):
    if timed_out:
        return "timeout"
    if code != 0:
        return "fail"
    # An exact filter that matched nothing still exits 0; that is a runner problem, not a pass.
    ran_swift_testing = re.search(r"Test run with [1-9]\d* tests?", log)
    ran_xctest = re.search(r"Executed [1-9]\d* tests?", log)
    return "pass" if ran_swift_testing or ran_xctest else "empty"


def main():
    args = parse_args()
    os.environ.setdefault("RC_COMPOSE_PLAYER_NATIVE_ONLY", "1")
    out = Path(args.out)
    logs = out / "logs"
    logs.mkdir(parents=True, exist_ok=True)

    print("Building tests once…", flush=True)
    code, _, _, elapsed = run(["swift", "build", "--build-tests"], log_path=out / "build.log")
    if code != 0:
        sys.exit(f"swift build --build-tests failed after {elapsed:.0f}s; see {out / 'build.log'}")

    tests = [
        test for test in list_tests()
        if re.search(args.filter, test) and not (args.skip and re.search(args.skip, test))
    ]
    if not tests:
        sys.exit("no tests matched")
    print(f"Running {len(tests)} tests, each in its own process", flush=True)

    results = []
    for index, test in enumerate(tests, 1):
        log_path = logs / (re.sub(r"[^A-Za-z0-9_.-]+", "_", test).strip("_") + ".log")
        code, _, timed_out, elapsed = run(
            ["swift", "test", "--skip-build", "--filter", exact_filter(test)],
            log_path=log_path, timeout=args.timeout)
        status = classify(code, timed_out, log_path.read_text(errors="replace"))
        results.append((test, status, elapsed, log_path))
        print(f"[{index}/{len(tests)}] {status:7} {elapsed:6.1f}s  {test}", flush=True)

    with open(out / "results.tsv", "w") as tsv:
        tsv.write("test\tstatus\tseconds\tlog\n")
        for test, status, elapsed, log_path in results:
            tsv.write(f"{test}\t{status}\t{elapsed:.2f}\t{log_path.relative_to(out)}\n")

    counts = {s: sum(1 for r in results if r[1] == s) for s in ("pass", "fail", "timeout", "empty")}
    problems = [r for r in results if r[1] != "pass"]
    lines = [
        "## Swift tests, one process each",
        "",
        " · ".join(f"{count} {status}" for status, count in counts.items()),
        "",
    ]
    if problems:
        lines += ["| status | test | seconds | log |", "| --- | --- | ---: | --- |"]
        lines += [
            f"| {status} | `{test}` | {elapsed:.1f} | `{log_path.relative_to(out)}` |"
            for test, status, elapsed, log_path in problems
        ]
        lines.append("")
    lines += ["Slowest ten:", "", "| test | seconds |", "| --- | ---: |"]
    lines += [
        f"| `{test}` | {elapsed:.1f} |"
        for test, _, elapsed, _ in sorted(results, key=lambda r: -r[2])[:10]
    ]
    summary = "\n".join(lines) + "\n"
    (out / "summary.md").write_text(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as step_summary:
            step_summary.write(summary)
    print(summary)
    # The first few problem logs inline, so the job log shows why without downloading the artifact.
    for test, status, _, log_path in problems[:3]:
        tail = log_path.read_text(errors="replace").splitlines()[-40:]
        print(f"--- {status}: {test} (last {len(tail)} lines of {log_path.name})")
        print("\n".join(tail))
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
