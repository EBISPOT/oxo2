# ADR-0048: `spo_key` is keyed on the normalised id, not the source spelling

- **Status**: Accepted
- **Date**: 2026-08-27
- **Amends**: the `spo_key` design of [ADR-0013](0013-group-same-spo-mappings-in-result-views.md)
  (its "IDs only" rule now reads "the ids **as indexed**")

## Context

[ADR-0013](0013-group-same-spo-mappings-in-result-views.md) keys the same-SPO collapse on
`subject_id + predicate_id + predicate_modifier + object_id`, deliberately **IDs only**, "so the
same entity collapses despite per-set drift". The point of excluding labels and IRIs is that they
are written differently by different sets for one entity; the ids are supposed to be the part that
does not drift.

But a CURIE does drift, in its prefix. `EntityReference.parseData()` upper-cases the prefix of a
CURIE on construction (`doid:0050043` → `DOID:0050043`), and it is that normalised value —
`getDataRepresentation()` — that the `SSSOMDataType` serializer writes and Solr stores in the
`subject_id` / `predicate_id` / `object_id` fields. `Mapping.spoKey()` did not use it. It hashed
`getDataAsString()`, the raw string exactly as the source TSV wrote it.

So two mappings could carry **identical** indexed ids and still receive **different** `spo_key`
values. The collapse then renders one triple as two rows — the precise failure ADR-0013 exists to
prevent, hidden behind ids that look the same everywhere a developer would go to check.

Prefix-case drift is real in the corpora OxO2 loads. In the OLS export at
`testcases/worktree/mondo.ols.sssom.tsv` (1,128 rows) the term `DOID:0050043` is written both
`DOID:0050043` and `doid:0050043` — within a single file from a single source. (In that file the two
spellings sit on rows with different predicates, so no group of its own splits; it establishes that
the drift occurs, not that this file demonstrates a split.)

The drift is not confined to what a source writes, though: OxO2 **introduces** it. An asserted
mapping keys its predicate as the TSV spelled it, while the OxO2-inferred mapping over the same
triple mints its predicate CURIE from the prefix map, lowercase, and takes its subject and object
from the `oxo2-entities` doc — which stores the *normalised* CURIE. The `T1` rule fixture shows all
of it at once: the fixture asserts `ex:A OWL:equivalentClass ex:B`, and the inferred documents it
produces hash their key over `EX:B`, `owl:equivalentClass`, `EX:A` — subject and object normalised,
predicate raw, and every case opposite to the asserted row's. A mapping asserted in a source set and
re-derived as `SSSOM_INFERENCE` therefore could not collapse with itself, which ADR-0013 names as
the first reason the collapse exists.

Three slots of one key were being read two different ways. That is the defect.

## Decision

In `spo_key`, each of `subject_id`, `predicate_id` and `object_id` contributes its **normalised**
representation — `getDataRepresentation()`, the value Solr indexes — falling back to the raw string
only where parsing produced nothing (a blank id, which stays blank).

- **The literal-subject branch is untouched.** A subject with no id still contributes its
  `subject_label` verbatim behind the `U+001E` marker
  ([ADR-0042](0042-literal-subject-identity-in-spo-key.md)). Free text is its own identity and text
  identity is case-sensitive on purpose: `"Lung"` is not `"lung"`. Only the CURIE slots normalise.
- **Only the prefix folds**, because that is all `EntityReference` folds. The local part of a CURIE
  identifies the term and stays case-sensitive: `OMIM:PS100070` and `OMIM:ps100070` remain distinct
  keys.
- **No schema change.** `spo_key` is a derived accessor, not stored model state.

## Consequences

- **A full reindex is required** before the fix is visible on a loaded corpus, as for any change to
  a derived field. Unlike [ADR-0042](0042-literal-subject-identity-in-spo-key.md), which left
  identified subjects byte-identical, **every `spo_key` in the index changes** wherever any of the
  three ids was written with a lowercase prefix — which, for `skos:exactMatch`, is nearly every
  mapping in the corpus.
- **Group counts fall** where the drift was splitting a triple: two sets spelling a prefix
  differently now collapse into one row, and an asserted mapping collapses with its
  `SSSOM_INFERENCE` re-derivation instead of rendering beside it.
- The integration goldens under `testcases_expected_output/minimal/*/solr/mapping/` were
  re-baselined, since they pin `spo_key` values.
- **A fixture was added to exercise the repair, because none of the existing ones could.** Every
  fixture predating this ADR has `oxo2-mappings-spo-groups` equal to its total document count for
  want of a case-drift pair, so all of their group counts survived the fix unchanged and a
  regression back to the raw-string key would have kept the whole suite green. The new cross-set
  fixture `testcases/minimal/crossset/prefix-case-drift/` asserts one triple in two sets under
  differently-cased prefixes, making it the first fixture whose group count is strictly below its
  document count *for this reason* — so the golden moves the moment the key stops normalising. See
  [`oxo2-integration-tests/CONTEXT.md`](../../oxo2-integration-tests/CONTEXT.md).
- `spo_key` remains underivable from the four ID *columns of a source TSV* — it was already so after
  ADR-0042's marker character, and normalisation adds a second reason. Anything recomputing it must
  go through `Mapping.spoKey()`. It **is** derivable from the four indexed Solr fields for a mapping
  with a `subject_id`, which is what makes the goldens checkable offline.
- `ExplainInferredMappings.foldedSpoKey()` — the cycle guard inside a single explanation trace — is
  a different key and is left alone. Its terms are minted from one source within one trace, so they
  do not drift relative to each other.

## Considered options

- **Normalise on the way in instead**, so `getDataAsString()` returns the folded CURIE — rejected:
  the raw string is deliberately retained across `SSSOMDataType` as the "what the source actually
  said" half of the pair, and several outputs depend on round-tripping it.
- **Fold the whole CURIE, prefix and local part** — rejected: it would merge genuinely distinct
  terms. `EntityReference` folds only the prefix, and `spo_key` must agree with what is indexed
  rather than invent a second normalisation.
- **Leave it and normalise prefixes at ingest for the affected sets** — rejected: it treats each
  offending source one at a time, and does nothing about the asserted-vs-inferred divergence OxO2
  creates itself.
