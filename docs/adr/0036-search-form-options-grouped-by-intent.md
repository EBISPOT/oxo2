# ADR-0036: Search-form options grouped by intent; result order moves to the results table

- **Status**: Accepted
- **Date**: 2026-07-16
- **Amends**: [ADR-0027](0027-config-driven-mapping-set-category.md) (the sort control's
  placement; its corpus control and ranking semantics are unchanged)

## Context

The search form had accumulated one control per feature ADR: the corpus selector and sort dropdown
(ADR-0027) on the surface, the weak-predicate checkboxes (ADR-0035) beside them, and label-match
mode (ADR-0026) plus the restrict-to-mapping-sets table behind a "More options" disclosure. Moving
the surface controls behind the same disclosure only relocated the problem: opening it revealed a
grab-bag — a segmented control, a dropdown, checkboxes, a differently-styled mini-dropdown, and a
table of hundreds of rows — with no grouping logic and no way to tell which options mattered.

Two of those options also duplicated the results surface. The sort dropdown wrote the same `?sort`
param the table's per-column sort popovers write; the weak-predicate checkboxes also exist in the
table's Predicate column header. But the duplication was asymmetric: the popovers only cover
subject/predicate/object fields, so deleting the sort dropdown outright would have removed the
only UI for confidence- and recency-ordering, while the checkboxes gate what the search box
*suggests* (ADR-0035's promise that every suggestion returns rows), which is a search-time concern
no results-page control can replace.

## Decision

The search form keeps only the term input, the from/to ontology selectors, and the buttons on its
surface. Behind one "More options" disclosure sit exactly three intent-sized groups: **where
mappings should come from** (the corpus selector, with the mapping-sets table nested behind its
own "Choose specific mapping sets…" disclosure), **"Also show"** (the weak-predicate checkboxes),
and **"Label matching"**. The disclosure's collapsed summary names every non-default choice inside
it.

The search form has no result-order control. The Best match / Highest confidence / Most recent
choice moves to the results table's toolbar, where picking a mode replaces the whole `?sort`
state.

## Consequences

- Ordering is chosen where its effect is visible, and "Best match" in the toolbar becomes the
  one-click way back to relevance from any per-column sort — previously there was no single
  control that could clear all sorts. A column sort the trio cannot represent shows as a disabled
  "Column sort" entry rather than reading back as "Best match".
- The `?sort` URL contract is unchanged: `SortMode` still maps the trio to the same tokens, and
  old URLs keep working. `SearchInput` no longer carries `sortBy`; submitting a search never
  writes `?sort`.
- Because meaning-changing filters (corpus, weak predicates, label match, mapping sets) are now
  all invisible until the disclosure opens, the summary-line naming of non-default choices is a
  load-bearing promise, not a nicety — a control added behind More options must add its hint.
- Any new search option must join one of the three intent groups (or justify a fourth); the
  grab-bag layout must not regrow one control at a time.
