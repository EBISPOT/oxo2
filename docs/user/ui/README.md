# User documentation — the web interface

`oxo2-user-interface.html` is a standalone, self-contained guide to the OxO2 web interface: what
each control does, how results are ranked, and where the interface will surprise you. It is written
for someone using OxO, not for someone changing it.

- **`oxo2-user-interface.html`** — the source of truth. One file, no external assets: the CSS is
  inline, the figures are inline SVG, and the OxO logo is an embedded data URI. Open it from disk,
  email it, or serve it from anywhere. The only outbound links are two ordinary hyperlinks (the
  SSSOM specification and the issue tracker), so it reads fine offline.
- **`oxo2-user-interface.pdf`** — a build artifact, regenerated from the HTML.
- **`build.sh`** — regenerates the PDF. Needs Chrome or Chromium on `PATH`.

```bash
./docs/user/ui/build.sh
```

## Keeping it honest

The document describes behaviour, and behaviour drifts. When you change the interface, this file is
as much a consumer of that change as the code is. In particular:

- The **ranking tiers** in § 6 restate the constants in `SolrQueryBuilder` (`ONTOLOGY_BOOST`,
  `PREDICATE_STRICT_IDENTITY`, `CURATION_MANUAL`, `CONFIDENCE_WEIGHT`, `DISTANCE_DECAY`). Change a
  constant and the worked arithmetic in § 6 becomes wrong. Nothing tests this — the numbers are
  prose.
- § 11, *Things that will surprise you*, records known rough edges. Fixing one means deleting its
  entry, not leaving a stale warning behind.
- The header carries the date and commit it was written against, so a reader can tell how stale it
  is. Update the stamp when you make a substantive revision.

Architectural rationale belongs in [`docs/adr/`](../../adr/), not here. This document says *what the
interface does*; the ADRs say *why*. Where a section rests on a decision, it names the ADR.

## Why standalone HTML rather than a docs site

There is no documentation site to publish to — the in-app `/docs` route is a placeholder — and a
user guide that needs a build pipeline to read is a user guide that stops being read. One file that
opens in any browser, and a PDF for the people who want to print it, is the whole requirement.
