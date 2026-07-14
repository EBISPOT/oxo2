// Weak predicates (ADR-0035): mapping predicates hidden from results unless the user asks for them.
// The codes match the backend WeakPredicate enum and travel as-is in the API and the `wp` URL param.
//
// Neither predicate says two entities are the same thing, and on an OLS-derived corpus they are the
// overwhelming majority of mappings — so showing them by default buries the equivalences people
// actually search for. They are hidden, not absent: each has its own checkbox, because ontology
// hierarchy and loose cross-references are different questions and wanting one is no reason to be
// shown the other.
//
// The same selection drives the typeahead. A suggestion is a promise that searching for that entity
// returns something, so the suggest is filtered by exactly these checkboxes — tick nothing and an
// entity whose every mapping is an xref is not offered at all, because picking it would land you on
// an empty table.

export type WeakPredicate = 'subClassOf' | 'hasDbXref';

export const WEAK_PREDICATE_ORDER: WeakPredicate[] = ['subClassOf', 'hasDbXref'];

export const WEAK_PREDICATE_LABELS: Record<WeakPredicate, string> = {
    subClassOf: 'Subclass (rdfs:subClassOf)',
    hasDbXref: 'Cross-reference (oboInOwl:hasDbXref)',
};

export const WEAK_PREDICATE_HINTS: Record<WeakPredicate, string> = {
    subClassOf: 'Ontology hierarchy, not equivalence.',
    hasDbXref: 'A loose link between two identifiers, with no stated meaning.',
};

// Hidden by default: both boxes start unchecked.
export const DEFAULT_WEAK_PREDICATES: WeakPredicate[] = [];

export function asWeakPredicate(value?: string | null): WeakPredicate | null {
    return value != null && value in WEAK_PREDICATE_LABELS ? (value as WeakPredicate) : null;
}
