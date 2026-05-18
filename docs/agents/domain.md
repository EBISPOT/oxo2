# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — the canonical entry point. Its Module map names each top-level module.
- **`oxo2-*/CONTEXT.md`** — read the per-module file for whichever module you're working in (`oxo2-backend`, `oxo2-dataload`, `oxo2-frontend`, `oxo2-integration-tests`, `oxo2-shared`).
- **`docs/adr/`** — read ADRs that touch the area you're about to work in.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo with per-module detail files:

```
/
├── CONTEXT.md                          ← canonical entry point (Module map + glossary)
├── docs/adr/                           ← architectural decisions (cross-cutting)
│   ├── 0001-inference-scope-per-mapping-set.md
│   ├── 0002-solr-as-sole-data-store.md
│   └── ...
├── oxo2-backend/CONTEXT.md             ← module detail
├── oxo2-dataload/CONTEXT.md
├── oxo2-frontend/CONTEXT.md
├── oxo2-integration-tests/CONTEXT.md
└── oxo2-shared/CONTEXT.md
```

Start at the root `CONTEXT.md`. Its Module map points to the per-module `CONTEXT.md` files; read those when you're working inside a specific module.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0006 (chain rules limited to those not copying tree) — but worth reopening because…_
