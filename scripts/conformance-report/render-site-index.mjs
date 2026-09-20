#!/usr/bin/env node
/**
 * The landing page for the published conformance site.
 *
 * ### What the site is, and what this file is not
 *
 * The corpus ships its own player-agnostic report generators — `generate-audit-report.mjs` produces
 * a visual audit and a single-test inspector from one player's `conformance-results.json`, and
 * `generate-gold-overview.mjs` describes the golds themselves. Those are the site. This writes only
 * the index that points at them, because nothing upstream knows how many players this repository
 * has.
 *
 * ### Why the site is deployed rather than committed
 *
 * The generated audit is 3–4 MB a player and the inspector another 2 MB, all of it embedded
 * reference images and documents. Committing that on every scheduled run would put gigabytes into
 * git history within a year for a page nobody reads twice. So the site is a Pages *artifact*, built
 * fresh each run and never versioned; the durable record is the Markdown on `reports/conformance`,
 * which is small enough to keep forever.
 *
 * Usage:
 *   node render-site-index.mjs --out <dir> [--run-id <id>] [--commit <sha>]
 *                              <lane>=<results.json> [<lane>=<results.json> …]
 */
import fs from "node:fs";
import path from "node:path";

function usage(message) {
  if (message) console.error(`error: ${message}`);
  console.error(
    "usage: node render-site-index.mjs --out <dir> [--run-id <id>] [--commit <sha>] " +
      "<lane>=<results.json> [<lane>=<results.json> …]",
  );
  process.exit(2);
}

const options = { lanes: [] };
for (let i = 2; i < process.argv.length; i++) {
  const arg = process.argv[i];
  if (arg === "--out") options.out = process.argv[++i];
  else if (arg === "--run-id") options.runId = process.argv[++i];
  else if (arg === "--commit") options.commit = process.argv[++i];
  else if (arg.startsWith("--")) usage(`unknown flag ${arg}`);
  else {
    const split = arg.indexOf("=");
    if (split <= 0) usage(`expected <lane>=<results.json>, got ${arg}`);
    options.lanes.push({ name: arg.slice(0, split), file: arg.slice(split + 1) });
  }
}
if (!options.out) usage("--out is required");
if (options.lanes.length === 0) usage("at least one lane is required");

/** What each lane observes, which is the difference between a score and a reference number. */
const CHANNELS = {
  cmp: "every probe",
  "androidx-jvm": "tree + raster",
  "native-appkit": "tree + scalar values + raster",
  typescript: "reported by upstream",
};

const escape = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (character) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character],
  );

function measure(file) {
  if (!fs.existsSync(file)) return null;
  const results = JSON.parse(fs.readFileSync(file, "utf8"));
  let coreTotal = 0;
  let corePassed = 0;
  const raster = new Set();
  for (const result of results.results) {
    if (!result.suspicious && result.profile === "core") {
      coreTotal += 1;
      if (result.status === "PASS") corePassed += 1;
    }
    for (const diff of result.diffs ?? []) {
      if (diff.probe === "raster") raster.add(`${result.name} ${diff.at}`);
    }
  }
  return {
    version: results.player?.version,
    coreTotal,
    corePassed,
    raster: raster.size,
    errored: results.summary?.errored ?? 0,
    // The corpus marks every raster check advisory: reported, never binding. Showing the two
    // together is what let this repository quote a score nobody else could reproduce.
    advisory: results.summary?.advisory_failed ?? null,
  };
}

const lanes = options.lanes.map((lane) => ({ ...lane, stats: measure(lane.file) }));

// A lane whose run produced nothing is rendered as absent. It must never read as a zero: those look
// identical on a card and mean opposite things — one is a player that draws nothing, the other is a
// measurement nobody took.
const cards = lanes
  .map((lane) => {
    const channels = CHANNELS[lane.name] ?? "unknown channels";
    if (!lane.stats) {
      return `      <article class="card absent">
        <h2>${escape(lane.name)}</h2>
        <p class="channels">${escape(channels)}</p>
        <p class="score">not measured</p>
        <p class="detail">This run produced no results for the lane.</p>
      </article>`;
    }
    const { coreTotal, corePassed, raster, errored, version, advisory } = lane.stats;
    const rate = coreTotal === 0 ? "—" : `${((corePassed / coreTotal) * 100).toFixed(1)}%`;
    const rasterOnly = coreTotal > 0 && corePassed === 0;
    return `      <article class="card">
        <h2>${escape(lane.name)}</h2>
        <p class="channels">${escape(channels)}${version ? ` · ${escape(version)}` : ""}</p>
        <p class="score"><b>${corePassed}</b><span class="of"> / ${coreTotal}</span></p>
        <p class="detail">${
          rasterOnly
            ? "No gold asserts raster alone, so a raster-only lane passes none by construction."
            : `${escape(rate)} of the core profile.`
        }</p>
        <dl>
          <dt>Raster disagreements</dt><dd>${raster}</dd>
          <dt>…advisory, not binding</dt><dd>${advisory ?? "—"}</dd>
          <dt>Errored</dt><dd>${errored}</dd>
        </dl>
        <nav>
          <a href="${escape(lane.name)}/conformance-audit.html">Visual audit</a>
          <a href="${escape(lane.name)}/test-inspector.html">Test inspector</a>
        </nav>
      </article>`;
  })
  .join("\n");

const stamp = [
  options.runId ? `Run ${escape(options.runId)}` : null,
  options.commit ? `<code>${escape(options.commit.slice(0, 7))}</code>` : null,
].filter(Boolean);

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RemoteCompose conformance</title>
<style>
  :root {
    --ground: #f4f6f8; --surface: #fff; --ink: #16202e; --muted: #5e6c80;
    --faint: #8494a6; --rule: #d9dfe8; --accent: #2b5fc9;
    --sans: "Helvetica Neue", Helvetica, Arial, sans-serif;
    --mono: ui-monospace, "SF Mono", Menlo, monospace;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --ground: #111721; --surface: #182029; --ink: #e6ebf2; --muted: #97a5b8;
      --faint: #6d7d91; --rule: #29333f; --accent: #7fa9f0;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 0 20px 80px; background: var(--ground); color: var(--ink);
    font-family: var(--sans); line-height: 1.6; -webkit-font-smoothing: antialiased;
  }
  .wrap { max-width: 900px; margin: 0 auto; }
  header { padding: 56px 0 24px; border-bottom: 2px solid var(--ink); margin-bottom: 40px; }
  .eyebrow {
    font-family: var(--mono); font-size: 11px; font-weight: 600; letter-spacing: .14em;
    text-transform: uppercase; color: var(--accent); margin: 0 0 12px;
  }
  h1 { font-size: clamp(30px, 5vw, 44px); line-height: 1.05; letter-spacing: -.02em; margin: 0 0 14px; }
  .standfirst { color: var(--muted); font-size: 18px; margin: 0; max-width: 60ch; }
  .stamp { font-family: var(--mono); font-size: 12px; color: var(--faint); margin: 18px 0 0; }
  h2 { font-size: 20px; margin: 0 0 4px; font-family: var(--mono); }
  .grid {
    display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 18px; margin: 0 0 44px;
  }
  .card { background: var(--surface); border: 1px solid var(--rule); padding: 22px 20px; }
  .card.absent { opacity: .72; }
  .channels {
    font-family: var(--mono); font-size: 11px; letter-spacing: .06em; text-transform: uppercase;
    color: var(--faint); margin: 0 0 16px;
  }
  .score { font-size: 15px; color: var(--faint); margin: 0 0 8px; font-variant-numeric: tabular-nums; }
  .score b { font-size: 40px; color: var(--ink); letter-spacing: -.03em; }
  .card.absent .score { font-size: 19px; color: var(--muted); }
  .detail { font-size: 13.5px; color: var(--muted); margin: 0 0 16px; }
  dl {
    display: grid; grid-template-columns: 1fr max-content; gap: 4px 12px;
    margin: 0 0 18px; font-size: 13px; font-family: var(--mono);
  }
  dt { color: var(--muted); }
  dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
  nav { display: flex; flex-wrap: wrap; gap: 8px 16px; font-size: 13.5px; }
  a { color: var(--accent); }
  a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
  .elsewhere { border-top: 1px solid var(--rule); padding-top: 26px; }
  .elsewhere ul { list-style: none; padding: 0; margin: 0; }
  .elsewhere li { padding: 9px 0; border-bottom: 1px solid var(--rule); font-size: 14.5px; }
  .elsewhere li:last-child { border-bottom: none; }
  .elsewhere span { display: block; color: var(--muted); font-size: 13px; }
  code { font-family: var(--mono); font-size: .88em; }
</style>
</head>
<body>
  <div class="wrap">
    <header>
      <p class="eyebrow">Conformance</p>
      <h1>RemoteCompose conformance</h1>
      <p class="standfirst">
        Every player in this repository, scored against the AndroidX RemoteCompose conformance
        corpus — 252 golds, 1,515 checks — and rendered by the corpus's own report generators.
      </p>
      ${stamp.length > 0 ? `<p class="stamp">${stamp.join(" · ")}</p>` : ""}
    </header>

    <div class="grid">
${cards}
    </div>

    <section class="elsewhere">
      <h2>Also here</h2>
      <ul>
        <li><a href="gold-overview.html">Gold overview</a>
          <span>What the corpus asserts, by subsystem. About the golds, not about a player.</span></li>
        <li><a href="https://github.com/yschimke/rc-players/blob/reports/conformance/README.md">Report branch</a>
          <span>The durable record: the cross-lane split and the trend, in Markdown, kept per run.</span></li>
        <li><a href="https://github.com/yschimke/rc-players/blob/main/docs/design/RC_CONFORMANCE.md">What the score measures</a>
          <span>And what it does not. Read before quoting a pass rate.</span></li>
      </ul>
    </section>
  </div>
</body>
</html>
`;

fs.mkdirSync(options.out, { recursive: true });
fs.writeFileSync(path.join(options.out, "index.html"), html);
console.log(`wrote ${path.join(options.out, "index.html")}`);
