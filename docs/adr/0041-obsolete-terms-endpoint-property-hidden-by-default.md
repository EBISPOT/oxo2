# ADR-0041: Obsolete terms are an endpoint property, hidden by default, honoured across every surface

- **Status**: Accepted
- **Date**: 2026-07-27

## Context

Ontologies deprecate terms. A deprecated (obsolete) term keeps its stable IRI so old data still
resolves, but it should no longer be used to annotate anything new — it has typically been replaced
by one or more current terms. OxO2 ingests obsolete terms today with no way to tell them from live
ones: they sit in search results, in the mapping-set picker, and in the typeahead as if current.

Two distinct user needs pull in opposite directions, and both are legitimate:

- A user *holding* an obsolete IRI wants to find its replacement. For them the obsolete term and its
  mappings must stay queryable — indeed, inference *through* an obsolete term is valuable, because a
  chain `obsolete → X → Z` surfaces more replacement candidates, possibly in an ontology the user
  prefers over the one the obsolete term was directly mapped to.
- A user doing an ordinary lookup does not want live terms cluttered with mappings that point at dead
  terms. A live term whose only visible target is obsolete is a dead end dressed up as an answer.

Obsolescence is **not carried in SSSOM rows**. Like the ontology/curated distinction (ADR-0027) it is
operator knowledge, not a property of any mapping cell. An OLS SSSOM export can, however, be split
into a live-subjects file and an obsolete-subjects file, and the operator can tag the obsolete one —
exactly the shape `category` already uses.

The hard part is that obsolescence is a property of the **term**, and a term appears on *both sides*
of mappings across *different files*. An obsolete EFO term is the subject of its own set's rows and
simultaneously the object of `MONDO → EFO` rows living in the MONDO file. Hiding "a live term that
maps to an obsolete term" therefore requires the *object* side to be flagged, which no single file
can determine on its own — the MONDO file cannot know which EFO objects are obsolete without seeing
EFO's obsolete file. This is what forces a global step; the per-file `-c category` thread of ADR-0027
is not enough.

## Decision

**1. An optional `"obsolete": true` flag on a `mapping_registries` entry** (default `false`), meaning
*every subject of this registry is an obsolete term*. It mirrors `category` (ADR-0027): registered on
`OxoConfiguration` so the strict deserializer accepts the key, but actually read by a small Groovy
helper (`ObsoleteRegistries.groovy`, a mirror of `MappingSetCategories.groovy`).

**2. Obsolescence is modelled as an endpoint property of a term, computed globally.** The dataload
runs two passes:

- *Pass 1* builds one global **obsolete-entity IRI set** — the union of subject IRIs across all
  `obsolete:true` registries, keyed on the **expanded IRI**, not the CURIE (CURIE casing varies by
  source, the same reason the weak-predicate filter matches `predicate_iri` — ADR-0035).
- *Pass 2* stamps, by lookup against that set: `subject_obsolete` and `object_obsolete` on **every
  mapping document — asserted and inferred alike**, `obsolete` on every `oxo2-entities` document, and
  `obsolete` on every `oxo2-mappingsets` document. All are booleans defaulting to `false`.

**3. Inference is unchanged; obsolescence is a display/filter concern only.** Obsolete terms
participate fully in the cross-set SSSOM closure, and a bridge through an obsolete term still produces
inferred mappings. An inferred mapping's flags reflect *its own endpoints*, not the terms it bridged:
`obsolete → Z` is subject-obsolete (and hidden by default — this is the replacement-discovery case),
whereas a live `MONDO → NCIt` that merely *bridged through* an obsolete EFO term has neither endpoint
obsolete and is shown. A live↔live result is never tainted by an obsolete intermediate.

**4. Hidden by default, revealed by one control that governs every surface.**
`MappingSearchRequest.includeObsolete` (default `false`) adds an `fq` excluding
`subject_obsolete:true OR object_obsolete:true`. The frontend surfaces a **single "Show obsolete
terms" checkbox** in the search form's More options section — the same pattern as the weak-predicate
control (ADR-0035, ADR-0036). Its URL param drives all three surfaces from one place:

- the **search** `fq` (hide rows with either endpoint obsolete);
- the **mapping-set picker** (`MappingSetSelector`) — obsolete ontology sets are hidden until the box
  is ticked, a pure client-side filter over rows that now carry `obsolete`;
- the **typeahead** — obsolete entities are excluded from suggestions until ticked.

The Ontologies table additionally gains an **`Obsolete` column** so that, once revealed, obsolete sets
are labelled and independently filterable (Material React Table gives the column filter for free).

**5. The typeahead honours it, by the rule of ADR-0035** — a suggestion must be a promise the search
returns rows. `oxo2-entities` carries `obsolete`; with the box unticked the suggest hides obsolete
entities, so it never offers a term that the default search would then hide.

## Consequences

- **The dataload gains a global pre-pass.** Object-side flagging cannot be local to a file, so a stage
  that extracts the obsolete-entity IRI set must run before stamping and feed both the mapping stamper
  and the entity/inference stages. This is genuinely new structure next to the per-file `-c category`
  thread — it was the one part of the design that grew beyond a per-set stamp.
- **Requires a full reindex, and ships dark until one runs** (exactly as ADR-0027's
  `mapping_set_category` did). Absent flags read `false`, so nothing hides and behaviour is unchanged
  on an un-reloaded index. A `loadData.nextflow` run that recreates the collections from
  `managed-schema.xml` populates the fields.
- **Obsolescence is defined as "subject of ≥1 `obsolete:true` registry."** A term that is a subject in
  both a live and an obsolete set is treated as obsolete — the obsolete tag wins. The match is on
  expanded IRI; a CURIE that expands to different IRIs under two curie maps is the known
  prefix-divergence caveat, not introduced here.
- **One ontology can now appear as two mapping sets** (e.g. a live EFO set and an obsolete EFO set,
  two rows in the picker distinguished by the `Obsolete` column). This is deliberate: a per-row
  obsolete column *inside* a single set is not expressible from a per-registry config flag, and the
  OLS export gives us the live/obsolete split for free.
- **Orthogonal to `category` and `inference_type`.** A mapping carries its category, its inference
  type, *and* its two obsolete flags; none replaces another. Unlike `category` (a pure per-set stamp),
  obsolescence is per-endpoint and needs the global set — the two are threaded differently on purpose.
- **One control, not two.** Weak predicates get two checkboxes because subclass hierarchy and loose
  cross-references are different questions (ADR-0035). Obsolescence is a single question — "do you want
  dead terms?" — and `subject_obsolete`/`object_obsolete` are two faces of it, not independently useful,
  so one checkbox governs both flags across all three surfaces.

## Alternatives considered

- **A per-set display flag only** — stamp only the obsolete set and hide only rows *from* it. Rejected:
  it leaves the object side unflagged, so a live MONDO term still shows its mapping to a dead EFO term —
  precisely the case we are asked to hide.
- **Exclude obsolete terms from the inference corpus**, like the confidence gate (ADR-0037). Rejected:
  `obsolete → X → Z` inferences *are* the replacement-discovery feature; suppressing them removes the
  reason to keep obsolete mappings queryable at all.
- **Encode obsolescence per row in the SSSOM file.** Rejected: SSSOM has no such column and the signal
  is not in the data — it is operator knowledge, exactly like `category` (ADR-0027).
- **A per-side control like weak predicates.** Rejected: obsolescence is one question, so two checkboxes
  would only let a user contradict themselves (show subject-obsolete but not object-obsolete) with no
  use case behind it.
