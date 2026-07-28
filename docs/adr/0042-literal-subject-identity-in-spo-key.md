# ADR-0042: A literal subject is identified by its text in `spo_key`

- **Status**: Accepted
- **Date**: 2026-07-28
- **Amends**: the `spo_key` design of [ADR-0013](0013-group-same-spo-mappings-in-result-views.md)
  (its "IDs only" rule now reads "the subject's identity, which is its id when it has one")

## Context

[ADR-0013](0013-group-same-spo-mappings-in-result-views.md) keys the same-SPO collapse on
`subject_id + predicate_id + predicate_modifier + object_id`, deliberately **IDs only**, "so the same
entity collapses despite per-set label drift". `Mapping.spoKey()` implements that by appending each
slot when present — an absent slot contributes nothing.

Some mappings have no `subject_id`. A **literal mapping** (`subject_type: rdfs literal`) maps a string
of free text that has not been assigned a CURIE yet onto a term that has one: *this text means that
term*. SSSOM carries the text in `subject_label` and leaves `subject_id` empty. Every such mapping
therefore hashed to the same key as every other literal mapping sharing its predicate and object.

This is not truncation of a duplicated triple; it is conflation of distinct mappings. Measured on the
dev index (`oxo2-mappings`, 27,106,094 documents, 2026-07-28):

| | |
|---|---|
| Mappings with no `subject_id` | 174,411 — exactly the `subject_type: rdfs literal` set, all `ASSERTED`, all from the 13 `mapping-commons/ebi-text-mappings` sets |
| `spo_key` groups they form | 60,595, against 135,853 distinct subject labels |
| Groups exceeding the 21-member display limit | 492 — **all** of them literal-subject groups; no group of identified subjects comes close |
| Mappings past that limit, unreachable from the UI | 57,139 |
| Largest group (`a3172fba-…`) | 7,539 members, **7,539 distinct labels**, one set, one predicate, one object |

That largest group is every GWAS trait string mapped `skos:closeMatch` to `EFO:0004747` "protein
measurement". The UI renders it as a single row labelled with one arbitrary trait, claiming to stand
for 7,539 mappings, and the overflow is the plain text "+7,518 more not shown" — the deep link that
once reached them was removed by [ADR-0040](0040-remove-advanced-search.md).

The premise behind "IDs only" does not hold here. Labels are excluded because the *same entity* is
labelled differently in different sets; but a literal subject has no entity behind it to drift from.
The text **is** the identity, and it is what the interface displays.

## Decision

In `spo_key`, a subject with no `subject_id` contributes its `subject_label`, prefixed with a record
separator (`U+001E`) so a literal can never share a key with a CURIE spelled the same way. A subject
that has an id is keyed on the id exactly as before, so every existing key is byte-identical.

- **Subject side only.** A literal *object* is not a case OxO2 has: no document in the corpus lacks
  an `object_id`, and the "text awaiting a CURIE" relation runs one way — free text is the thing being
  mapped, the ontology term is what it is mapped to. The object slot is unchanged.
- **The label, not the mapping.** Two sets asserting the same text against the same term still
  collapse, which is what the collapse exists for: `"lung" skos:closeMatch UBERON:0002048` is asserted
  in five of the `ebi-text-mappings` sets and is one triple, not five.
- **No schema change.** `spo_key` is a derived accessor, not stored model state.

## Consequences

- **A full reindex is required** before the fix is visible on a loaded corpus, as for any change to a
  derived field. Keys for identified subjects are unchanged, so only literal mappings move.
- The oversized-group problem disappears with it: every one of the 492 over-limit groups was a
  literal-subject group, so nothing is left near the 21-member cap and the "+*n* more not shown" dead
  end stops being reachable in practice. The display limit itself is untouched.
- Group counts rise for the literal sets — 60,595 groups become roughly one per distinct
  text/predicate/object — so a subject-less search scoped to an ontology returns more, smaller rows.
- Literal subjects remain absent from `oxo2-entities`, so the typeahead still cannot suggest one
  ([ADR-0034](0034-entity-collection-for-typeahead.md) keys on identified endpoints), and they remain
  outside the inference corpus, because `JSON2NQuads` requires a resolvable subject IRI. This ADR
  changes how they are grouped for display, nothing else.
- The marker character means `spo_key` is no longer derivable from the four ID columns alone; anything
  recomputing it must go through `Mapping.spoKey()`.

## Considered options

- **Refuse to collapse an id-less subject** (give each its own key) — rejected: it splits the genuine
  cross-set duplicates, and `"lung"` asserted in five sets would render as five identical rows.
- **Fall back to the subject IRI** — rejected: a no-op. No literal mapping in the corpus carries a
  `subject_iri`, by construction — the text has no IRI, which is why it has no CURIE.
- **Leave it and document the quirk** — rejected: a row that says "7,539 mappings" under one trait's
  name misreports the data, and 57,139 mappings stay unreachable.
