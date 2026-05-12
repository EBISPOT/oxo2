# Architecture Decision Records

This directory records architectural decisions made on OxO2. An ADR captures a single decision, why it was made, and what follows from it. New ADRs are added by copying `0000-template.md`.

## Reading order

ADRs are independent. Read whichever decisions are relevant to your task. `/CONTEXT.md` § Cross-cutting constraints links to each ADR from where it bites.

## Adding a new ADR

1. Copy `0000-template.md` to `NNNN-short-kebab-title.md`, using the next free `NNNN`.
2. Fill in **Title**, **Status**, **Date**, **Context**, **Decision**, **Consequences**.
3. Status starts at `Accepted` for decisions already in force. Use `Proposed` if the decision is still under discussion, and `Superseded by ADR-NNNN` when replaced.
4. If the new ADR supersedes an existing one, update the old ADR's status to `Superseded by ADR-NNNN` in the same PR.
5. Add a one-line bullet to `/CONTEXT.md` § Cross-cutting constraints linking to the ADR.

## When to write an ADR vs. a CONTEXT.md note

- **ADR**: cross-cutting decision that constrains how multiple modules behave (e.g. the data store, the dataload execution path).
- **CONTEXT.md cross-cutting note**: a fact that follows from existing decisions or is too tactical to need its own decision record (e.g. a chunk size, a default timeout).

If unsure, prefer a CONTEXT.md note first — promote to an ADR when a second team member needs to ask why.

## Template

See [`0000-template.md`](0000-template.md).
