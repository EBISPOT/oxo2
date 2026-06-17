export type AdvancedFieldGroup =
    | "subject"
    | "object"
    | "predicate"
    | "mapping"
    | "authors_reviewers"
    | "provenance"
    | "asserted_mappings"
    | "other";

export type AdvancedFieldType = "string" | "text_general";

export interface AdvancedFieldDef {
    field: string;       // canonical Solr field name; matches backend MappingEnum
    label: string;       // user-facing label
    group: AdvancedFieldGroup;
    type: AdvancedFieldType;
    multiValued?: boolean;
}

export const GROUP_LABELS: Record<AdvancedFieldGroup, string> = {
    subject: "Subject",
    object: "Object",
    predicate: "Predicate",
    mapping: "Mapping",
    authors_reviewers: "Authors / Reviewers",
    provenance: "Provenance",
    asserted_mappings: "Asserted mappings",
    other: "Other",
};

export const GROUP_ORDER: AdvancedFieldGroup[] = [
    "subject",
    "object",
    "predicate",
    "mapping",
    "authors_reviewers",
    "provenance",
    "asserted_mappings",
    "other",
];

// Numeric / date fields are intentionally excluded — they need range/format-aware UI
// (phase 2): mapping_date, publication_date, confidence, similarity_score, distance,
// explanation_length. `explanation` is excluded because it's indexed=false in the
// Solr schema. Internal fields excluded: mapping_id (uuid), chain_rule,
// chain_rule_applications, premises (computed structured).
export const ADVANCED_FIELDS: AdvancedFieldDef[] = [
    // Subject
    { field: "subject_id", label: "Subject ID", group: "subject", type: "string" },
    { field: "subject_iri", label: "Subject IRI", group: "subject", type: "string" },
    { field: "subject_label", label: "Subject label", group: "subject", type: "text_general" },
    { field: "subject_category", label: "Subject category", group: "subject", type: "text_general" },
    { field: "subject_source", label: "Subject source", group: "subject", type: "string" },
    { field: "subject_source_version", label: "Subject source version", group: "subject", type: "string" },
    { field: "subject_type", label: "Subject type", group: "subject", type: "string" },
    { field: "subject_match_field", label: "Subject match field", group: "subject", type: "string", multiValued: true },
    { field: "subject_preprocessing", label: "Subject preprocessing", group: "subject", type: "string", multiValued: true },

    // Object
    { field: "object_id", label: "Object ID", group: "object", type: "string" },
    { field: "object_iri", label: "Object IRI", group: "object", type: "string" },
    { field: "object_label", label: "Object label", group: "object", type: "text_general" },
    { field: "object_category", label: "Object category", group: "object", type: "text_general" },
    { field: "object_source", label: "Object source", group: "object", type: "string" },
    { field: "object_source_version", label: "Object source version", group: "object", type: "string" },
    { field: "object_type", label: "Object type", group: "object", type: "string" },
    { field: "object_match_field", label: "Object match field", group: "object", type: "string", multiValued: true },
    { field: "object_preprocessing", label: "Object preprocessing", group: "object", type: "string", multiValued: true },

    // Predicate
    { field: "predicate_id", label: "Predicate ID", group: "predicate", type: "string" },
    { field: "predicate_iri", label: "Predicate IRI", group: "predicate", type: "string" },
    { field: "predicate_label", label: "Predicate label", group: "predicate", type: "text_general" },
    { field: "predicate_modifier", label: "Predicate modifier", group: "predicate", type: "string" },

    // Mapping
    { field: "mapping_justification", label: "Mapping justification", group: "mapping", type: "string" },
    { field: "mapping_cardinality", label: "Mapping cardinality", group: "mapping", type: "string" },
    { field: "mapping_source", label: "Mapping source", group: "mapping", type: "string" },
    { field: "mapping_tool", label: "Mapping tool", group: "mapping", type: "text_general" },
    { field: "mapping_tool_version", label: "Mapping tool version", group: "mapping", type: "string" },
    { field: "match_string", label: "Match string", group: "mapping", type: "text_general", multiValued: true },
    { field: "similarity_measure", label: "Similarity measure", group: "mapping", type: "string" },
    { field: "curation_rule", label: "Curation rule", group: "mapping", type: "string", multiValued: true },

    // Authors / Reviewers
    { field: "author_id", label: "Author ID", group: "authors_reviewers", type: "string", multiValued: true },
    { field: "author_label", label: "Author label", group: "authors_reviewers", type: "text_general", multiValued: true },
    { field: "creator_id", label: "Creator ID", group: "authors_reviewers", type: "string", multiValued: true },
    { field: "creator_label", label: "Creator label", group: "authors_reviewers", type: "text_general", multiValued: true },
    { field: "reviewer_id", label: "Reviewer ID", group: "authors_reviewers", type: "string", multiValued: true },
    { field: "reviewer_label", label: "Reviewer label", group: "authors_reviewers", type: "text_general", multiValued: true },

    // Provenance
    { field: "mapping_set_id", label: "Mapping set ID", group: "provenance", type: "string" },
    { field: "mapping_set_title", label: "Mapping set title", group: "provenance", type: "text_general" },
    { field: "mapping_set_description", label: "Mapping set description", group: "provenance", type: "text_general" },
    { field: "mapping_set_version", label: "Mapping set version", group: "provenance", type: "string" },
    { field: "mapping_set_source", label: "Mapping set source", group: "provenance", type: "string", multiValued: true },
    { field: "mapping_provider", label: "Mapping provider", group: "provenance", type: "string" },
    { field: "license", label: "License", group: "provenance", type: "string" },
    { field: "issue_tracker_item", label: "Issue tracker item", group: "provenance", type: "string" },

    // Asserted mappings
    { field: "asserted_mappings", label: "Asserted mappings (free text, CURIE, or IRI)", group: "asserted_mappings", type: "text_general", multiValued: true },

    // Other
    { field: "comment", label: "Comment", group: "other", type: "text_general" },
    { field: "see_also", label: "See also", group: "other", type: "text_general", multiValued: true },
    { field: "other", label: "Other", group: "other", type: "text_general" },
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
