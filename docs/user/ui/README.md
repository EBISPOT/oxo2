# User documentation — the web interface

`oxo2-user-interface.html` is a standalone, self-contained guide to the OxO2 web interface: what
each control does, how results are ranked, and where the interface will surprise you. It is written
for someone using OxO, not for someone changing it.

- **`oxo2-user-interface.html`** — the source of truth. One file, no external assets: the CSS is
  inline, the figures are inline SVG, and the OxO logo is an embedded data URI. Open it from disk,
  email it, or serve it from anywhere. The only outbound links are two ordinary hyperlinks (the
  SSSOM specification and the issue tracker), so it reads fine offline.
- **`oxo2-user-interface.pdf`** — a build artifact, regenerated from the HTML.
- **`build.sh`** — regenerates the PDF *and* refreshes the copies the app serves. Needs Chrome or
  Chromium on `PATH`.

```bash
./docs/user/ui/build.sh
```

Run it after every substantive edit and commit what it changes — the HTML, the PDF, and the two
copies under `oxo2-frontend/public/`.

## Keeping it honest

The document describes behaviour, and behaviour drifts. When you change the interface, this file is
as much a consumer of that change as the code is. In particular:

- The **ranking tiers** in § 5 restate the constants in `SolrQueryBuilder` (`ONTOLOGY_BOOST`,
  `PREDICATE_STRICT_IDENTITY`, `CURATION_MANUAL`, `CONFIDENCE_WEIGHT`, `DISTANCE_DECAY`, and the
  whole tier-2 predicate ladder). Change a constant and the worked arithmetic in § 5 becomes wrong.
  `SolrQueryBuilderTest#rankingTiersAreLexicographic` pins the *invariant* that tier 1 dominates
  tiers 2–4, so the table's punchline cannot silently rot — but the individual numbers are prose and
  nothing checks them.
- § 10, *Things that will surprise you*, records known rough edges. Fixing one means deleting its
  entry, not leaving a stale warning behind.
- **No deployment-specific URLs.** The same file is served by every instance, so a hostname or port
  written into it is wrong for all the others and goes stale as deployments are added. Describe
  paths relative to the instance (`/swagger-ui.html`), and send the reader to the app's
  Documentation tab for a link they can click — that page resolves the base URL from the container
  configuration, which this document cannot.
- The header carries the date it was last updated, so a reader can tell how stale it is. Update the
  stamp when you make a substantive revision. Deliberately a date and not a commit: the audience is
  people using OxO, and "how old is this?" is a question a date answers and a SHA does not.

Architectural rationale belongs in [`docs/adr/`](../../adr/), not here. This document says *what the
interface does*; the ADRs say *why*. Where a section rests on a decision, it names the ADR.

## Why standalone HTML rather than a docs site

A user guide that needs a build pipeline to read is a user guide that stops being read. One file
that opens in any browser, and a PDF for the people who want to print it, is the whole requirement.
Being self-contained is what lets the same file be emailed, opened from disk, *and* served by the
app without a second rendering path.

## How it reaches the app

The in-app **Documentation** tab (`/docs`) shows this file in an iframe, with links to open it in
its own tab or download the PDF. An iframe rather than inlined markup because the guide styles bare
`body`, `h2`, `table` and `a` selectors, which would collide with the app's Tailwind styles.

The frontend image builds with context `./oxo2-frontend`, so it cannot reach this directory at image
build time. The served copies therefore have to live inside the frontend, at
`oxo2-frontend/public/`, and be committed. `build.sh` refreshes them, so regenerating the PDF and
updating what `/docs` serves are one action — but the copies are still copies. If you edit the HTML
and skip `build.sh`, the app keeps serving the old guide.
