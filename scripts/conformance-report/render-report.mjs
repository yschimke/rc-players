#!/usr/bin/env node
/**
 * Turns one or more lanes' `conformance-results.json` into the browsable report tree published on
 * the `reports/conformance` branch.
 *
 * ### Why this exists at all
 *
 * The conformance workflow runs a lane per matrix job, and each job appends its own `scorecard.md`
 * to its own step summary. That makes the one thing the lane exists for — the comparison *between*
 * lanes — the one thing nobody can see without opening two job pages and diffing them by hand.
 * This renders that comparison, and writes it somewhere with a stable URL.
 *
 * ### Why Markdown rather than the HTML the score deserves
 *
 * GitHub renders Markdown in the repository browser and serves HTML as source text. A report you
 * have to clone to read is not browsable, so Markdown wins on the only criterion that matters here.
 *
 * ### What it writes
 *
 *   README.md                    the landing page: latest run, cross-lane split, trend
 *   runs/<run-id>/README.md      that run in full
 *   runs/<run-id>/<lane>.summary.json   per-gold outcomes, trimmed small enough to keep forever
 *   history.csv                  one row per lane per run, appended
 *
 * `history.csv` is the reason the tree is worth keeping rather than regenerating: a score that
 * moved is only interesting against what it was, and the run artifacts expire after 30 days.
 *
 * Usage:
 *   node render-report.mjs --out <dir> --run-id <id> [--commit <sha>] [--ref <name>]
 *                          [--reference <lane> …] <lane>=<results.json> [<lane>=<results.json> …]
 *
 * The **first** lane given is the subject; the rest are scored beside it.
 *
 * By default every other lane is also a *reference* — one the subject's raster failures are
 * cross-checked against. `--reference` narrows that set, and narrowing it matters once a lane is
 * scored that is not a credible reference: the split asks "does an independent implementation fail
 * this too?", and a lane that fails almost everything answers yes to everything, driving the
 * subject's unique-failure count to zero and reporting nothing.
 */
import fs from "node:fs";
import path from "node:path";

function usage(message) {
  if (message) console.error(`error: ${message}`);
  console.error(
    "usage: node render-report.mjs --out <dir> --run-id <id> [--commit <sha>] [--ref <name>] " +
      "<lane>=<results.json> [<lane>=<results.json> …]",
  );
  process.exit(2);
}

// ------------------------------------------------------------------ arguments

function parseArgs(argv) {
  const options = { lanes: [] };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--out") options.out = argv[++i];
    else if (arg === "--run-id") options.runId = argv[++i];
    else if (arg === "--commit") options.commit = argv[++i];
    else if (arg === "--ref") options.ref = argv[++i];
    else if (arg === "--reference") (options.references ??= []).push(argv[++i]);
    else if (arg.startsWith("--")) usage(`unknown flag ${arg}`);
    else {
      const split = arg.indexOf("=");
      if (split <= 0) usage(`expected <lane>=<results.json>, got ${arg}`);
      options.lanes.push({ name: arg.slice(0, split), file: arg.slice(split + 1) });
    }
  }
  if (!options.out) usage("--out is required");
  if (!options.runId) usage("--run-id is required");
  if (options.lanes.length === 0) usage("at least one lane is required");
  return options;
}

// ------------------------------------------------------------------ reading

/**
 * Reads one lane, tolerating a missing file.
 *
 * A lane whose job failed must show up as *absent* rather than as a zero. Those look identical in
 * a table and mean opposite things — one is a player that draws nothing, the other is a
 * measurement nobody took.
 */
function readLane({ name, file }) {
  if (!fs.existsSync(file)) return { name, missing: true };
  const results = JSON.parse(fs.readFileSync(file, "utf8"));
  return { name, missing: false, results };
}

// ------------------------------------------------------------------ analysis

/** Per-profile gold counts, computed here rather than trusted from the scorecard's prose. */
function profiles(results) {
  const counts = { core: [0, 0], extended: [0, 0] };
  for (const result of results.results) {
    if (result.suspicious) continue;
    const bucket = counts[result.profile] ?? (counts[result.profile] = [0, 0]);
    bucket[0] += 1;
    if (result.status === "PASS") bucket[1] += 1;
  }
  return counts;
}

/** The set of `(gold, step)` frames whose raster disagreed, which is what lanes are compared on. */
function rasterFailures(results) {
  const failures = new Set();
  for (const result of results.results) {
    for (const diff of result.diffs ?? []) {
      if (diff.probe === "raster") failures.add(`${result.name}\0${diff.at}`);
    }
  }
  return failures;
}

/**
 * How the two lanes divide the corpus at gold granularity.
 *
 * `referenceOnly` is the only row that is a work list: the reference reproduces what the gold
 * asserts and the subject does not, so the disagreement cannot be blamed on the expectation. It
 * carries each gold's failing probes, because "which gold" without "which probe" is not yet a bug
 * report.
 *
 * Suspicious golds are excluded on both sides -- the corpus itself disputes them, and a lane
 * "passing" one says nothing.
 */
const UNOBSERVED = new Set(["PROBE_NOT_IMPLEMENTED", "STEP_NOT_RUN", "PROBE_ERROR"]);

function goldVerdicts(subjectResults, referenceResults) {
  const reference = new Map(referenceResults.results.map((result) => [result.name, result]));
  const verdicts = { bothPass: 0, subjectOnly: 0, bothFail: 0, referenceOnly: [] };
  for (const result of subjectResults.results) {
    if (result.suspicious) continue;
    const other = reference.get(result.name);
    if (!other || other.suspicious) continue;
    const subjectPassed = result.status === "PASS";
    const referencePassed = other.status === "PASS";
    if (subjectPassed && referencePassed) verdicts.bothPass += 1;
    else if (subjectPassed) verdicts.subjectOnly += 1;
    else if (!referencePassed) verdicts.bothFail += 1;
    else {
      const probes = {};
      for (const diff of result.diffs ?? []) probes[diff.probe] = (probes[diff.probe] ?? 0) + 1;
      // A gold whose every disagreement is this runner admitting it cannot look is *our* gap, not
      // the player's. Both belong in the report and they are opposite findings: one is a bug to
      // fix in the player, the other a probe to implement in the lane. Folding them together is
      // how a work list stops being a work list.
      const unobservable = (result.diffs ?? []).every((diff) => UNOBSERVED.has(diff.property));
      verdicts.referenceOnly.push({ name: result.name, probes, unobservable });
    }
  }
  verdicts.referenceOnly.sort((a, b) => a.name.localeCompare(b.name));
  return verdicts;
}

function subsystems(results) {
  const rows = new Map();
  for (const result of results.results) {
    if (result.suspicious) continue;
    const row = rows.get(result.category) ?? { total: 0, passed: 0 };
    row.total += 1;
    if (result.status === "PASS") row.passed += 1;
    rows.set(result.category, row);
  }
  return [...rows.entries()]
    .map(([name, row]) => ({ name, ...row, outstanding: row.total - row.passed }))
    .sort((a, b) => b.outstanding - a.outstanding || a.name.localeCompare(b.name));
}

/**
 * Diffs per probe, and how many of those are the runner admitting it cannot look.
 *
 * Counted as diffs rather than checks because that is what the results file holds: one `particles`
 * check compares a whole emitter and yields dozens. Calling them checks — as the per-lane scorecard
 * does — overstates the corpus by an order of magnitude on that probe.
 */
function probes(results) {
  const rows = new Map();
  for (const result of results.results) {
    for (const diff of result.diffs ?? []) {
      const row = rows.get(diff.probe) ?? { diffs: 0, unobservable: 0 };
      row.diffs += 1;
      if (diff.property === "PROBE_NOT_IMPLEMENTED") row.unobservable += 1;
      rows.set(diff.probe, row);
    }
  }
  return [...rows.entries()]
    .map(([name, row]) => ({ name, ...row }))
    .sort((a, b) => b.diffs - a.diffs || a.name.localeCompare(b.name));
}

function errors(results) {
  return results.results
    .filter((result) => result.status === "ERROR")
    .map((result) => ({ name: result.name, message: result.error ?? "no message recorded" }));
}

// ------------------------------------------------------------------ formatting

const percent = (passed, total) => (total === 0 ? "—" : `${((passed / total) * 100).toFixed(1)}%`);

function table(headers, aligns, rows) {
  const head = `| ${headers.join(" | ")} |`;
  const rule = `| ${aligns.map((a) => (a === "r" ? "---:" : "---")).join(" | ")} |`;
  return [head, rule, ...rows.map((cells) => `| ${cells.join(" | ")} |`)].join("\n");
}

/** A fixed-width bar, so a subsystem's shortfall reads at a glance in plain Markdown. */
function bar(passed, total, width = 20) {
  if (total === 0) return "";
  const filled = Math.round((passed / total) * width);
  return "█".repeat(filled) + "·".repeat(width - filled);
}

/**
 * The lanes a subject's raster failures are cross-checked against.
 *
 * Every non-subject lane by default. `--reference` narrows it, and narrowing matters once a lane is
 * scored that is not a credible reference: the split asks "does an independent implementation fail
 * this too?", and a lane failing almost every frame answers yes to everything — which silently
 * drives the subject's unique-failure count to zero, the one number these reports lead with.
 */
function referenceLanes(options, present) {
  return present
    .slice(1)
    .filter((lane) => !options.references || options.references.includes(lane.name));
}

// ------------------------------------------------------------------ the run page

function renderRun(options, lanes) {
  const present = lanes.filter((lane) => !lane.missing);
  const subject = present[0];
  const out = [];

  out.push(`# Conformance run — ${options.runId}`);
  out.push("");
  out.push(
    "Every player in this repository, scored against the AndroidX RemoteCompose conformance " +
      "corpus. The corpus comes from an unmerged AOSP Gerrit change and is held on " +
      "`vendor/androidx-rc-conformance`; it is deliberately not in the main line.",
  );
  out.push("");
  if (options.commit) {
    const ref = options.ref ? ` on \`${options.ref}\`` : "";
    out.push(`Measured at \`${options.commit}\`${ref}.`);
    out.push("");
  }

  // ---- lanes
  out.push("## Lanes");
  out.push("");
  const rasterOnly = present
    .filter((lane) => {
      const counts = profiles(lane.results);
      const [coreTotal, corePassed] = counts.core ?? [0, 0];
      return coreTotal > 0 && corePassed === 0;
    })
    .map((lane) => `\`${lane.name}\``);
  if (rasterOnly.length > 0) {
    out.push(
      `**A zero here is not a verdict.** ${rasterOnly.join(", ")} ` +
        `${rasterOnly.length === 1 ? "observes" : "observe"} only \`raster\`, and no gold in the ` +
        "corpus asserts raster alone — so such a lane passes none by construction. Its raster " +
        "column is the number it exists for.",
    );
    out.push("");
  }
  out.push(
    table(
      ["lane", "core golds", "pass rate", "extended", "raster disagreements", "errored"],
      ["l", "r", "r", "r", "r", "r"],
      lanes.map((lane) => {
        if (lane.missing) {
          return [`\`${lane.name}\``, "—", "—", "—", "—", "*not measured*"];
        }
        const counts = profiles(lane.results);
        const [coreTotal, corePassed] = counts.core ?? [0, 0];
        const [extTotal, extPassed] = counts.extended ?? [0, 0];
        return [
          `\`${lane.name}\``,
          `${corePassed} / ${coreTotal}`,
          percent(corePassed, coreTotal),
          `${extPassed} / ${extTotal}`,
          String(rasterFailures(lane.results).size),
          String(lane.results.summary.errored),
        ];
      }),
    ),
  );
  out.push("");

  // ---- the cross-lane split, which is the whole point
  //
  // Only declared reference lanes take part. Every non-subject lane qualifies when none is declared,
  // which is the behaviour this had before a lane existed that should not be one.
  const references = referenceLanes(options, present);
  if (subject && references.length > 0) {
    out.push("## Where the subject lane stands alone");
    out.push("");
    const excluded = present
      .slice(1)
      .filter((lane) => !references.includes(lane))
      .map((lane) => `\`${lane.name}\``);
    if (excluded.length > 0) {
      out.push(
        `Scored above but not compared here: ${excluded.join(", ")}. A lane only counts as a ` +
          "reference if disagreeing with it is evidence; one that fails nearly every frame would " +
          "mark every subject failure as shared and leave nothing to act on.",
      );
      out.push("");
    }
    out.push(
      "The corpus was generated *by* AndroidX, from its own player. So a gold both lanes fail is " +
        "most likely the harness failing to observe something, or the reference asserting behaviour " +
        "no independent player would reproduce — while a gold only the subject lane fails is a " +
        "finding about the subject lane.",
    );
    out.push("");

    // Golds first, then frames. A gold is the unit the corpus scores and the unit a reader can act
    // on, and this split is only meaningful once a reference lane can actually pass one -- before
    // the reference observed `tree` it passed none by construction and every row would have read
    // "only the subject passes".
    for (const reference of references) {
      const verdicts = goldVerdicts(subject.results, reference.results);
      if (verdicts.referenceOnly.length + verdicts.bothPass === 0) continue;
      out.push(`### Golds, against \`${reference.name}\``);
      out.push("");
      out.push(
        table(
          ["outcome", "golds", "what it means"],
          ["l", "r", "l"],
          [
            ["both pass", String(verdicts.bothPass), "Settled. Neither lane disagrees."],
            [
              `only \`${reference.name}\` passes`,
              `**${verdicts.referenceOnly.length}**`,
              "**The work list**, split below into the player's gaps and this runner's.",
            ],
            [
              `only \`${subject.name}\` passes`,
              String(verdicts.subjectOnly),
              "The subject is ahead here, or the reference cannot drive the timeline.",
            ],
            [
              "both fail",
              String(verdicts.bothFail),
              "An expectation that survives neither implementation, or a probe neither lane has.",
            ],
          ],
        ),
      );
      out.push("");
      const players = verdicts.referenceOnly.filter((gold) => !gold.unobservable);
      const lane = verdicts.referenceOnly.filter((gold) => gold.unobservable);
      const describe = (gold) =>
        `- \`${gold.name}\` — ` +
        Object.entries(gold.probes)
          .map(([probe, count]) => `${probe} ×${count}`)
          .join(", ");
      if (players.length > 0 && players.length <= 40) {
        out.push(`**${players.length} the player: the reference draws or reports it and this one does not.**`);
        out.push("");
        players.forEach((gold) => out.push(describe(gold)));
        out.push("");
      }
      if (lane.length > 0 && lane.length <= 40) {
        out.push(
          `**${lane.length} this runner: every disagreement is a probe it cannot observe.** Not a ` +
            "player finding — the lane has no seam for these channels and says so rather than " +
            "scoring them as passes.",
        );
        out.push("");
        lane.forEach((gold) => out.push(describe(gold)));
        out.push("");
      }
    }

    out.push("### Frames");
    out.push("");
    const subjectFailures = rasterFailures(subject.results);
    out.push(
      table(
        ["compared against", "shared failures", "unique to " + `\`${subject.name}\``],
        ["l", "r", "r"],
        references.map((reference) => {
          const referenceFailures = rasterFailures(reference.results);
          const shared = [...subjectFailures].filter((key) => referenceFailures.has(key));
          return [
            `\`${reference.name}\``,
            String(shared.length),
            String(subjectFailures.size - shared.length),
          ];
        }),
      ),
    );
    out.push("");

    // Name them. A count of one is only actionable if the reader can see which one.
    const unique = new Set(subjectFailures);
    for (const reference of references) {
      for (const key of rasterFailures(reference.results)) unique.delete(key);
    }
    if (unique.size > 0 && unique.size <= 25) {
      out.push(`Unique to \`${subject.name}\`, in full:`);
      out.push("");
      for (const key of [...unique].sort()) {
        const [gold, step] = key.split("\0");
        out.push(`- \`${gold}\` at \`${step}\``);
      }
      out.push("");
    } else if (unique.size > 25) {
      out.push(`${unique.size} frames are unique to \`${subject.name}\` — see the results JSON.`);
      out.push("");
    }
  }

  // ---- subject detail
  if (subject) {
    out.push(`## \`${subject.name}\` by subsystem`);
    out.push("");
    out.push(
      table(
        ["subsystem", "", "passed", "pass rate"],
        ["l", "l", "r", "r"],
        subsystems(subject.results).map((row) => [
          row.name,
          `\`${bar(row.passed, row.total)}\``,
          `${row.passed} / ${row.total}`,
          percent(row.passed, row.total),
        ]),
      ),
    );
    out.push("");

    out.push(`## \`${subject.name}\` by probe`);
    out.push("");
    out.push(
      "Counted as **diffs**, not checks: one `particles` check compares a whole emitter and can " +
        "produce dozens. The last column is the runner admitting it has no seam for that channel — " +
        "reported as a failure on purpose, so a player cannot score well by declining to look.",
    );
    out.push("");
    out.push(
      table(
        ["probe", "diffs", "of which the lane cannot observe"],
        ["l", "r", "r"],
        probes(subject.results).map((row) => [
          `\`${row.name}\``,
          String(row.diffs),
          row.unobservable === 0 ? "—" : String(row.unobservable),
        ]),
      ),
    );
    out.push("");

    const crashes = errors(subject.results);
    if (crashes.length > 0) {
      out.push("## Crashes");
      out.push("");
      out.push("A crash is not a conformance gap. These are player bugs on their own terms.");
      out.push("");
      for (const crash of crashes) out.push(`- \`${crash.name}\` — ${crash.message}`);
      out.push("");
    }

    const suspicious = subject.results.results.filter((result) => result.suspicious);
    if (suspicious.length > 0) {
      out.push("## Excluded as suspicious");
      out.push("");
      out.push(
        "Tagged by the corpus itself as disputed reference behaviour. Left out of the rate rather " +
          "than counted as failures.",
      );
      out.push("");
      for (const result of suspicious) {
        out.push(`- \`${result.name}\`${result.suspiciousReason ? ` — ${result.suspiciousReason}` : ""}`);
      }
      out.push("");
    }
  }

  return out.join("\n").trimEnd() + "\n";
}

// ------------------------------------------------------------------ archiving

/**
 * A per-gold summary small enough to keep forever.
 *
 * The full results file is 3 MB a lane, most of it the `observed` trees and the `expected`/`actual`
 * payloads on every diff, and a daily run of two lanes would put gigabytes on this branch within a
 * year. The workflow keeps the full file as a 30-day artifact for anyone who needs to re-open a
 * specific disagreement; what is kept here is what a *later* question needs — which golds passed,
 * which frames disagreed, and how badly — so the cross-lane comparison can still be recomputed
 * against a run nobody can re-measure any more.
 */
function summarise(results) {
  return {
    player: results.player,
    corpus: { path: results.corpus?.path, gold_count: results.corpus?.gold_count },
    summary: results.summary,
    results: results.results.map((result) => ({
      name: result.name,
      category: result.category,
      profile: result.profile,
      status: result.status,
      suspicious: result.suspicious,
      checks_total: result.checks_total,
      checks_failed: result.checks_failed,
      error: result.error,
      // Probe totals rather than the diffs themselves, plus the raster steps by name: between them
      // they answer "what moved" and "is this failure shared with another lane".
      diffs_by_probe: Object.fromEntries(
        Object.entries(
          (result.diffs ?? []).reduce((counts, diff) => {
            counts[diff.probe] = (counts[diff.probe] ?? 0) + 1;
            return counts;
          }, {}),
        ).sort(),
      ),
      raster_failures: [
        ...new Set((result.diffs ?? []).filter((d) => d.probe === "raster").map((d) => d.at)),
      ].sort(),
    })),
  };
}

// ------------------------------------------------------------------ history

function readHistory(file) {
  if (!fs.existsSync(file)) return [];
  const [, ...lines] = fs.readFileSync(file, "utf8").trim().split("\n");
  return lines.filter(Boolean).map((line) => {
    const [runId, lane, coreGolds, corePassed, rasterFails, errored, commit] = line.split(",");
    return { runId, lane, coreGolds, corePassed, rasterFails, errored, commit };
  });
}

function writeHistory(file, rows) {
  const header = "run_id,lane,core_golds,core_passed,raster_failures,errored,commit";
  const body = rows.map((row) =>
    [row.runId, row.lane, row.coreGolds, row.corePassed, row.rasterFails, row.errored, row.commit].join(
      ",",
    ),
  );
  fs.writeFileSync(file, [header, ...body].join("\n") + "\n");
}

// ------------------------------------------------------------------ landing page

function renderLanding(options, lanes, history) {
  const present = lanes.filter((lane) => !lane.missing);
  const subject = present[0];
  const out = [];

  out.push("# Conformance reports");
  out.push("");
  out.push(
    "Published by the scheduled `Conformance` workflow. This branch carries **only** reports — no " +
      "source, no corpus — so a score can be linked to and compared over time without the run " +
      "artifacts, which expire.",
  );
  out.push("");
  out.push(`**Latest run: [\`${options.runId}\`](runs/${options.runId}/README.md)**`);
  out.push("");

  if (subject) {
    const counts = profiles(subject.results);
    const [coreTotal, corePassed] = counts.core ?? [0, 0];
    const subjectFailures = rasterFailures(subject.results);
    const unique = new Set(subjectFailures);
    // The same reference set the run page uses. Computing it differently here would let the landing
    // page and the run it links to disagree about the one number both lead with.
    for (const reference of referenceLanes(options, present)) {
      for (const key of rasterFailures(reference.results)) unique.delete(key);
    }
    out.push(
      table(
        ["", `\`${subject.name}\``],
        ["l", "r"],
        [
          ["Core golds passed", `**${corePassed} / ${coreTotal}** (${percent(corePassed, coreTotal)})`],
          ["Raster disagreements", String(subjectFailures.size)],
          [
            "…not shared with a reference lane",
            present.length > 1 ? `**${unique.size}**` : "*no reference lane in this run*",
          ],
        ],
      ),
    );
    out.push("");
    if (present.length > 1) {
      out.push(
        `Of ${subjectFailures.size} frames where \`${subject.name}\` disagrees with the reference ` +
          `image, ${subjectFailures.size - unique.size} are frames a reference lane fails too. ` +
          `${unique.size} ${unique.size === 1 ? "is" : "are"} the subject lane's alone — and that ` +
          "is the number worth acting on.",
      );
      out.push("");
    }
  }

  out.push("## Trend");
  out.push("");
  const byRun = new Map();
  for (const row of history) {
    const runs = byRun.get(row.runId) ?? new Map();
    runs.set(row.lane, row);
    byRun.set(row.runId, runs);
  }
  const laneNames = [...new Set(history.map((row) => row.lane))];
  const runIds = [...byRun.keys()].sort().reverse().slice(0, 30);
  out.push(
    table(
      ["run", ...laneNames.map((lane) => `\`${lane}\` golds`), "commit"],
      ["l", ...laneNames.map(() => "r"), "l"],
      runIds.map((runId) => {
        const runs = byRun.get(runId);
        const any = [...runs.values()][0];
        return [
          `[\`${runId}\`](runs/${runId}/README.md)`,
          ...laneNames.map((lane) => {
            const row = runs.get(lane);
            return row ? `${row.corePassed} / ${row.coreGolds}` : "—";
          }),
          any?.commit ? `\`${any.commit.slice(0, 7)}\`` : "—",
        ];
      }),
    ),
  );
  out.push("");
  out.push("`history.csv` holds every row, including runs older than the table above.");
  out.push("");
  out.push("## How to read a score");
  out.push("");
  out.push(
    "The pass rate is the weakest number in these reports. The corpus was generated by AndroidX " +
      "from its own player, so agreeing with it is partly a measure of resembling that player. " +
      "Read the cross-lane split first: it separates *this player disagrees* from *every player " +
      "disagrees*, and only the first is a bug report.",
  );
  out.push("");
  out.push("See `docs/design/RC_CONFORMANCE.md` on `main` for what the lane does and does not measure.");

  return out.join("\n").trimEnd() + "\n";
}

// ------------------------------------------------------------------ main

function main(argv) {
  const options = parseArgs(argv);
  const lanes = options.lanes.map(readLane);
  const runDir = path.join(options.out, "runs", options.runId);
  fs.mkdirSync(runDir, { recursive: true });

  for (const lane of lanes) {
    if (lane.missing) continue;
    fs.writeFileSync(
      path.join(runDir, `${lane.name}.summary.json`),
      JSON.stringify(summarise(lane.results), null, 1) + "\n",
    );
  }
  fs.writeFileSync(path.join(runDir, "README.md"), renderRun(options, lanes));

  const historyFile = path.join(options.out, "history.csv");
  const history = readHistory(historyFile).filter((row) => row.runId !== options.runId);
  for (const lane of lanes) {
    if (lane.missing) continue;
    const counts = profiles(lane.results);
    const [coreTotal, corePassed] = counts.core ?? [0, 0];
    history.push({
      runId: options.runId,
      lane: lane.name,
      coreGolds: String(coreTotal),
      corePassed: String(corePassed),
      rasterFails: String(rasterFailures(lane.results).size),
      errored: String(lane.results.summary.errored),
      commit: options.commit ?? "",
    });
  }
  history.sort((a, b) => a.runId.localeCompare(b.runId) || a.lane.localeCompare(b.lane));
  writeHistory(historyFile, history);

  fs.writeFileSync(path.join(options.out, "README.md"), renderLanding(options, lanes, history));
  console.log(`wrote ${path.join(options.out, "README.md")} and runs/${options.runId}/`);
}

main(process.argv.slice(2));
