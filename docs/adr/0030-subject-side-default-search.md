# ADR-0030: The default mapping search matches the subject side only

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

The default (classified-by-shape) search path — `POST /api/v2/mappings/search` with a free-text
`queries` list, no `queryFields` and no advanced field queries — used to fan each term out across
**both** ends and the predicate of a mapping. A term classified as an IRI matched
`subject_iri OR object_iri OR predicate_iri`; a CURIE matched the three `*_id` fields; a label matched
the three `*_label*` fields (the field chosen by the `labelMatch` mode, ADR-0026).

But a mapping is a directed statement *subject → predicate → object*, and a user searching for a term's
mappings is asking it from that term's point of view: "what does **this** thing map to?" Fanning out to
the object and predicate columns answers a different, noisier question — it returns rows where the term
is the *target* of someone else's mapping, or (via the `*_id` / `*_iri` fan-out) rows where the term
merely happens to be the predicate. That dilutes the result the user actually wanted and inflates the
same-SPO group count.

The subject-side classification already existed and was trusted: `subjectSideClause` is what cross-set
batch mapping (ADR-0024) and the v1 `/api/search` adapter use to match an input term to the subject
column. The default search was the only path still fanning out.

## Decision

The default search matches the **subject side only**. Each query term becomes a `subjectSideClause`:

- an IRI → `subject_iri`,
- a CURIE → `subject_id` (normalised to its stored prefix casing via `EntityReference`, so a
  lower-cased prefix still matches),
- anything else → the subject label field the `labelMatch` mode selects (`subject_label` /
  `subject_label_ci` / `subject_label_str`, ADR-0026).

Terms OR together, exactly as before. `constructClassifiedQuery` now delegates per term to the same
`subjectSideClause` the batch and v1 paths use, so there is a single subject-side classifier. The
`labelMatch` mode keeps its meaning (partial / case-insensitive-exact / case-sensitive-exact) but now
picks among the **subject** label fields only.

The other query paths are unchanged: the **Advanced** tab still targets whatever fields the user names
(including object and predicate), `queryFields` still overrides field selection, and column filters
still filter any column. A caller who genuinely wants to match on the object or predicate uses those.

## Consequences

- **Directed results.** A search for a term returns the mappings where it is the subject — its own
  outgoing mappings — not rows where it is some other subject's object or a predicate. This is the
  question the Search tab poses ("which term do you want to map?").
- **Reverse mappings via strong predicates are still found.** The inference closure
  ([ADR-0016](0016-single-pass-sssom-reasoning.md)) materialises the symmetric/inverse row for every
  strong predicate — the four equivalence predicates (`SYM-*`), `skos:exactMatch`, the broad/narrow
  inverses (RI1/RI2) and the crossSpecies inverses (RI3/RI4/RI5). So if `X skos:exactMatch term` is
  asserted, `term skos:exactMatch X` exists as an inferred row whose subject is `term`, and the
  subject-side search finds it. No reachability is lost for the predicates that carry identity or
  hierarchy meaning.
- **Weak-predicate mappings are directional.** The weak/noisy predicates (`skos:closeMatch`,
  `skos:relatedMatch`, `oboInOwl:hasDbXref`, `rdfs:seeAlso`, `rdf:type`) are **not** closed, so a
  mapping where the term appears only as the *object* of a weak predicate is no longer surfaced by a
  plain search. This is a deliberate narrowing: those rows answered "who points weakly at this term",
  not "what does this term map to". They remain reachable through the Advanced tab (an explicit
  `object_*` query) and, for `oboInOwl:hasDbXref`, through the v1 `/api/mappings` listing whose
  `fromId` filter is undirected by design (ADR-0025).
- **Inferred reverse rows respect the inference-type filter.** A materialised reverse row carries
  `inference_type = SSSOM_INFERENCE`. A user who restricts results to `ASSERTED` therefore sees only
  the direction that was literally asserted — consistent with every other inferred mapping, not a new
  rule.
- **Weak-predicate default hiding is now unconditional for plain search.** Because a main-box term can
  never land on a predicate field, a plain search can no longer accidentally count as an "explicit
  predicate filter", so the default hiding of `rdfs:subClassOf` / `oboInOwl:hasDbXref`
  ([search default](../../oxo2-backend/CONTEXT.md)) always applies to it. Only a column or advanced
  predicate filter lifts it, which was always the intent.
- **One classifier.** The default search, batch mapping and the v1 adapter now share
  `subjectSideClause`, so subject-side matching (prefix normalisation included) can only be defined
  once.
- **No schema or reindex.** Pure query-construction change; the fields it targets already exist.
