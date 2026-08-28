# ADR-0013: Group same-SPO mappings into one row in result views

- **Status**: Accepted — grouping *mechanism* superseded by
  [ADR-0023](0023-collapse-for-same-spo.md) (the same-SPO collapse behaviour and `spo_key` design
  below still stand; only the Solr result-grouping implementation is replaced); the **IDs only** rule
  amended by [ADR-0042](0042-literal-subject-identity-in-spo-key.md) for subjects that have no id
- **Date**: 2026-06-08

## Context

The Search results table and the Inferences page render **one Solr document per row**. The same logical
triple — a subject related by a predicate to an object — appears as **several rows**, because a mapping's
`id` UUID is hashed over the mapping set, the full subject/predicate/object slots, the predicate modifier,
and the justification (`Mapping.generateMappingUuid()`). So the same triple yields distinct documents when
it is asserted in more than one set, asserted under different justifications, or — since two-phase reasoning
([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)) — asserted in a source set **and**
re-derived as `SSSOM_INFERENCE` in the single cross-set inference set. The cross-set SSSOM set itself holds
multiple same-SPO mappings. The net effect is that one relationship is scattered across rows, and the result
count and paging are expressed in documents rather than in the relationships users actually care about.

## Decision

Collapse mappings that share the same **subject_id, predicate_id, predicate_modifier, and object_id** into a
single **mapping group**, rendered as one expandable row whose members are reachable by expanding it.

- **Group key — a denormalised `spo_key` field.** Add a single-valued, indexed, `docValues` string field
  `spo_key` to `oxo2-mappings`, populated **once at dataload** as a deterministic hash of the four key
  components. **IDs only** — labels and IRIs are excluded so the same entity collapses despite per-set label
  drift (amended by [ADR-0042](0042-literal-subject-identity-in-spo-key.md): a subject with *no* id is keyed
  on its text, which is its identity rather than a drifting label for one; and by
  [ADR-0048](0048-spo-key-uses-the-normalised-id.md): an id contributes the **normalised** form Solr
  indexes, not the source spelling, since a CURIE prefix drifts in case too);
  **`predicate_modifier` is included** so a relation and its negation (`predicate_modifier = Not`)
  never collapse into one row. Mapping set, justification, and `inference_type` are deliberately *not* in the
  key — they are exactly the axes being collapsed.
- **Solr result grouping**, not Collapse+Expand. `group=true&group.field=spo_key&group.ngroups=true`
  &`group.limit≈20`, so a single query returns each group's members **and** its true size.
- **Grouping is presentation-layer, applied on top of every existing filter**, including the inference-type
  filter ([ADR-0011](0011-inference-type-replaces-is-inferred.md)). A group's members are only the documents
  passing the active filter; counts and badges reflect only what passed. A triple that is *only* OWL-inferred
  does not appear while OWL is hidden.
- **Representative = highest inference tier.** `group.sort=score desc` reuses the ADR-0011 boost
  (`ASSERTED` > `SSSOM_INFERENCE` > `OWL_INFERENCE`, shorter chains first), so the parent row shows the
  asserted member's subject/predicate/object when one exists. Groups themselves are ordered by the user's
  column sort, with **`spo_key` appended as a final tiebreaker** so the order is total and paging is stable.
- **Parent row** shows the representative's S/P/O, **stacked distinct inference-type badges** for the types
  present, a member count, and **"Multiple"** where justification / provider / mapping set differ across
  members. The expansion lists the members (up to `group.limit`); the count uses the group's true
  `numFound`, and a **"+N more"** link deep-links to a flat Advanced view filtered to the triple when the cap
  is exceeded.
- **The expand affordance appears only on a group of more than one.** A singleton group has nothing
  behind it — expanding it only restated the row — so it carries no chevron at all rather than a
  disabled one, and the header's expand-all control hides when no row on the page can expand. Where
  the affordance *is* present, clicking anywhere on the row toggles it, so the target is the whole
  row and not just the chevron; clicks that land on a link or button belong to that control, and a
  click that merely ended a text selection does not toggle.
- **A page is N groups.** `rows`/`start` page over groups and `totalElements = getNGroups()`. Members are
  transported on the representative as a `group_members` JSON string, mirroring the existing
  `asserted_mappings` / `explanation` fields, so `MappingSearchResponse` / `Page<Mapping>` keep their shape.
- **Scope.** Grouping is intrinsic to the `NormalResultsTable`, which backs both the **Search** results and
  the **Inferences** page. The **Advanced** tab stays flat (one document per row) as the per-document escape
  hatch.

## Consequences

- Requires a new schema field and a **full reindex** to populate `spo_key`.
- `group.ngroups` adds a modest query cost (exact distinct-group counting); acceptable for CURIE-scoped
  searches, revisit only if a broad query is slow.
- A mapping group is **identity-by-meaning**: positive and negated assertions of a triple remain distinct
  rows because `predicate_modifier` is in the key.
- The member list is capped at `group.limit`; the rare over-cap group shows an accurate total and a "+N more"
  link rather than inlining every member.
- Facet counts stay document-based, but the normal/inferences tables render no facets, so no `group.facet` is
  needed.

## Considered options

- **Pure SPO key (no modifier)** — rejected: would collapse a relation with its negation into one row,
  misrepresenting the data (see § predicate_modifier in `/CONTEXT.md`).
- **Client-side per-page grouping** — rejected: same-SPO documents straddle page boundaries, and the total /
  pagination counts would still be document-based.
- **Collapsing QParser + ExpandComponent** — rejected here on the assumption that result grouping's
  single-response members-and-count was cheap; **reversed by [ADR-0023](0023-collapse-for-same-spo.md)**
  after `group.ngroups` measured at ~19s on a high-frequency term. This is now the chosen mechanism.
- **Lazy-load members on expand** — deferred: inline-capped members keep the single-query `Page` shape;
  revisit if groups turn out to be routinely huge.
