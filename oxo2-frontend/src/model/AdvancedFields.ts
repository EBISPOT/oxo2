export type AdvancedFieldGroup =
    | "subject"
    | "object"
    | "predicate"
    | "mapping"
    | "authors_reviewers"
    | "provenance"
    | "other";

export type AdvancedFieldType = "string" | "text_general";

/**
 * Which typeahead a field gets (ADR-0034). Chosen by CARDINALITY, not by taste — a suggester over
 * millions of entity labels and a suggester over the five values of `predicate_modifier` are not the
 * same problem, and a typeahead over free prose is noise.
 *
 *  - `entity` — millions of distinct values. Served by the oxo2-entities collection: prefix-matched,
 *    ranked, filtered server-side.
 *  - `vocab`  — tens to a few hundred distinct values. The whole list is fetched once, cached
 *    forever, and filtered client-side — exactly what OntologySelector already does for prefixes.
 *  - `none`   — free prose (`comment`, `other`, …), which has no vocabulary to complete.
 */
export type SuggestKind = "entity" | "vocab" | "none";

export interface AdvancedFieldDef {
    field: string;       // canonical Solr field name; matches backend MappingEnum
    label: string;       // user-facing label
    group: AdvancedFieldGroup;
    type: AdvancedFieldType;
    multiValued?: boolean;
    /** Required, so adding a field forces a decision about how it completes rather than defaulting. */
    suggest: SuggestKind;
    /** suggest === 'entity': which attribute of the picked entity fills the box. */
    entityAttribute?: "id" | "iri" | "label";
    /** suggest === 'entity': which side of a mapping the entity must appear on. */
    entitySide?: "subject" | "object";
}

export const GROUP_LABELS: Record<AdvancedFieldGroup, string> = {
    subject: "Subject",
    object: "Object",
    predicate: "Predicate",
    mapping: "Mapping",
    authors_reviewers: "Authors / Reviewers",
    provenance: "Provenance",
    other: "Other",
};

export const GROUP_ORDER: AdvancedFieldGroup[] = [
    "subject",
    "object",
    "predicate",
    "mapping",
    "authors_reviewers",
    "provenance",
    "other",
];

// Numeric / date fields are intentionally excluded — they need range/format-aware UI
// (phase 2): mapping_date, publication_date, confidence, similarity_score, distance,
// explanation_length. `explanation` is excluded because it's indexed=false in the
// Solr schema. Internal fields excluded: mapping_id (uuid), chain_rule,
// chain_rule_applications, premises (computed structured).
//
// `issue_tracker_item` and `asserted_mappings` were removed here (ADR-0034). Both are
// indexed="false" in the mappings schema (asserted_mappings deliberately, ADR-0028), so filling
// them in returned nothing, silently — they were never searchable, and offering a box that cannot
// match is worse than not offering it. The field-classification pass for the typeahead is what
// surfaced them.
export const ADVANCED_FIELDS: AdvancedFieldDef[] = [
    // Subject. id / iri / label are the entity itself — millions of values, so they get the entity
    // typeahead; picking a suggestion fills the named attribute of the entity that was chosen.
    { field: "subject_id", label: "Subject ID", group: "subject", type: "string", suggest: "entity", entityAttribute: "id", entitySide: "subject" },
    { field: "subject_iri", label: "Subject IRI", group: "subject", type: "string", suggest: "entity", entityAttribute: "iri", entitySide: "subject" },
    { field: "subject_label", label: "Subject label", group: "subject", type: "text_general", suggest: "entity", entityAttribute: "label", entitySide: "subject" },
    { field: "subject_category", label: "Subject category", group: "subject", type: "text_general", suggest: "vocab" },
    { field: "subject_source", label: "Subject source", group: "subject", type: "string", suggest: "vocab" },
    { field: "subject_source_version", label: "Subject source version", group: "subject", type: "string", suggest: "vocab" },
    { field: "subject_type", label: "Subject type", group: "subject", type: "string", suggest: "vocab" },
    { field: "subject_match_field", label: "Subject match field", group: "subject", type: "string", multiValued: true, suggest: "vocab" },
    { field: "subject_preprocessing", label: "Subject preprocessing", group: "subject", type: "string", multiValued: true, suggest: "vocab" },

    // Object
    { field: "object_id", label: "Object ID", group: "object", type: "string", suggest: "entity", entityAttribute: "id", entitySide: "object" },
    { field: "object_iri", label: "Object IRI", group: "object", type: "string", suggest: "entity", entityAttribute: "iri", entitySide: "object" },
    { field: "object_label", label: "Object label", group: "object", type: "text_general", suggest: "entity", entityAttribute: "label", entitySide: "object" },
    { field: "object_category", label: "Object category", group: "object", type: "text_general", suggest: "vocab" },
    { field: "object_source", label: "Object source", group: "object", type: "string", suggest: "vocab" },
    { field: "object_source_version", label: "Object source version", group: "object", type: "string", suggest: "vocab" },
    { field: "object_type", label: "Object type", group: "object", type: "string", suggest: "vocab" },
    { field: "object_match_field", label: "Object match field", group: "object", type: "string", multiValued: true, suggest: "vocab" },
    { field: "object_preprocessing", label: "Object preprocessing", group: "object", type: "string", multiValued: true, suggest: "vocab" },

    // Predicate. A handful of distinct values across the whole corpus — a vocabulary, not an entity.
    { field: "predicate_id", label: "Predicate ID", group: "predicate", type: "string", suggest: "vocab" },
    { field: "predicate_iri", label: "Predicate IRI", group: "predicate", type: "string", suggest: "vocab" },
    { field: "predicate_label", label: "Predicate label", group: "predicate", type: "text_general", suggest: "vocab" },
    { field: "predicate_modifier", label: "Predicate modifier", group: "predicate", type: "string", suggest: "vocab" },

    // Mapping
    { field: "mapping_justification", label: "Mapping justification", group: "mapping", type: "string", suggest: "vocab" },
    { field: "mapping_cardinality", label: "Mapping cardinality", group: "mapping", type: "string", suggest: "vocab" },
    { field: "mapping_source", label: "Mapping source", group: "mapping", type: "string", suggest: "vocab" },
    { field: "mapping_tool", label: "Mapping tool", group: "mapping", type: "text_general", suggest: "vocab" },
    { field: "mapping_tool_version", label: "Mapping tool version", group: "mapping", type: "string", suggest: "vocab" },
    // Free text the matcher happened to compare — not a vocabulary.
    { field: "match_string", label: "Match string", group: "mapping", type: "text_general", multiValued: true, suggest: "none" },
    { field: "similarity_measure", label: "Similarity measure", group: "mapping", type: "string", suggest: "vocab" },
    { field: "curation_rule", label: "Curation rule", group: "mapping", type: "string", multiValued: true, suggest: "vocab" },

    // Authors / Reviewers. A small, closed set of people per corpus.
    { field: "author_id", label: "Author ID", group: "authors_reviewers", type: "string", multiValued: true, suggest: "vocab" },
    { field: "author_label", label: "Author label", group: "authors_reviewers", type: "text_general", multiValued: true, suggest: "vocab" },
    { field: "creator_id", label: "Creator ID", group: "authors_reviewers", type: "string", multiValued: true, suggest: "vocab" },
    { field: "creator_label", label: "Creator label", group: "authors_reviewers", type: "text_general", multiValued: true, suggest: "vocab" },
    { field: "reviewer_id", label: "Reviewer ID", group: "authors_reviewers", type: "string", multiValued: true, suggest: "vocab" },
    { field: "reviewer_label", label: "Reviewer label", group: "authors_reviewers", type: "text_general", multiValued: true, suggest: "vocab" },

    // Provenance
    { field: "mapping_set_id", label: "Mapping set ID", group: "provenance", type: "string", suggest: "vocab" },
    { field: "mapping_set_title", label: "Mapping set title", group: "provenance", type: "text_general", suggest: "vocab" },
    // Prose: a sentence about the set, not a value anyone completes.
    { field: "mapping_set_description", label: "Mapping set description", group: "provenance", type: "text_general", suggest: "none" },
    { field: "mapping_set_version", label: "Mapping set version", group: "provenance", type: "string", suggest: "vocab" },
    { field: "mapping_set_source", label: "Mapping set source", group: "provenance", type: "string", multiValued: true, suggest: "vocab" },
    { field: "mapping_provider", label: "Mapping provider", group: "provenance", type: "string", suggest: "vocab" },
    { field: "license", label: "License", group: "provenance", type: "string", suggest: "vocab" },

    // Other — all free prose.
    { field: "comment", label: "Comment", group: "other", type: "text_general", suggest: "none" },
    { field: "see_also", label: "See also", group: "other", type: "text_general", multiValued: true, suggest: "none" },
    { field: "other", label: "Other", group: "other", type: "text_general", suggest: "none" },
];

export function groupFields(): Record<AdvancedFieldGroup, AdvancedFieldDef[]> {
    const result = GROUP_ORDER.reduce((acc, g) => {
        acc[g] = [];
        return acc;
    }, {} as Record<AdvancedFieldGroup, AdvancedFieldDef[]>);
    for (const fd of ADVANCED_FIELDS) {
        result[fd.group].push(fd);
    }
    return result;
}

export const ADVANCED_FIELD_NAMES: Set<string> = new Set(ADVANCED_FIELDS.map((f) => f.field));
