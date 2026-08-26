package uk.ac.ebi.spot.oxo.model.sssom;

public class MappingConstants {
    public static final String AUTHOR_ID = "author_id";
    public static final String AUTHOR_LABEL = "author_label";
    public static final String COMMENT = "comment";
    public static final String CONFIDENCE = "confidence";
    public static final String CREATOR_ID = "creator_id";
    public static final String CREATOR_LABEL = "creator_label";
    public static final String CURATION_RULE = "curation_rule";
    public static final String CURIE_MAP = "curie_map";
    public static final String EXTENSION_DEFINITIONS = "extension_definitions";
    public static final String ISSUE_TRACKER = "issue_tracker";
    public static final String ISSUE_TRACKER_ITEM = "issue_tracker_item";
    public static final String LICENSE = "license";
    public static final String MAPPING_CARDINALITY = "mapping_cardinality";
    public static final String MAPPING_DATE = "mapping_date";
    public static final String MAPPING_JUSTIFICATION = "mapping_justification";
    public static final String MAPPING_PROVIDER = "mapping_provider";
    public static final String MAPPING_SET_DESCRIPTION = "mapping_set_description";
    public static final String MAPPING_SET_ID = "mapping_set_id";
    public static final String MAPPING_SET_SOURCE = "mapping_set_source";
    public static final String MAPPING_SET_TITLE = "mapping_set_title";
    public static final String MAPPING_SET_VERSION = "mapping_set_version";
    public static final String MAPPING_SOURCE = "mapping_source";
    public static final String MAPPING_TOOL = "mapping_tool";
    public static final String MAPPING_TOOL_VERSION = "mapping_tool_version";
    public static final String MATCH_STRING = "match_string";
    public static final String OBJECT_CATEGORY = "object_category";
    public static final String OBJECT_ID = "object_id";
    public static final String OBJECT_LABEL = "object_label";
    public static final String OBJECT_MATCH_FIELD = "object_match_field";
    public static final String OBJECT_PREPROCESSING = "object_preprocessing";
    public static final String OBJECT_SOURCE = "object_source";
    public static final String OBJECT_SOURCE_VERSION = "object_source_version";
    public static final String OBJECT_TYPE = "object_type";
    public static final String OTHER = "other";
    public static final String PREDICATE = "predicate";
    public static final String PREDICATE_ID = "predicate_id";
    public static final String PREDICATE_LABEL = "predicate_label";
    public static final String PREDICATE_MODIFIER = "predicate_modifier";
    public static final String PREPROCESSING = "preprocessing";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String REVIEWER_ID = "reviewer_id";
    public static final String REVIEWER_LABEL = "reviewer_label";
    public static final String SEE_ALSO = "see_also";
    public static final String SIMILARITY_MEASURE = "similarity_measure";
    public static final String SIMILARITY_SCORE = "similarity_score";
    public static final String SUBJECT_CATEGORY = "subject_category";
    public static final String SUBJECT_ID = "subject_id";
    public static final String SUBJECT_LABEL = "subject_label";
    public static final String SUBJECT_MATCH_FIELD = "subject_match_field";
    public static final String SUBJECT_PREPROCESSING = "subject_preprocessing";
    public static final String SUBJECT_SOURCE = "subject_source";
    public static final String SUBJECT_SOURCE_VERSION = "subject_source_version";
    public static final String SUBJECT_TYPE = "subject_type";

    // Extensions
    public static final String ASSERTED_MAPPINGS = "asserted_mappings";
    // Result-view grouping (ADR-0013): spo_key is the same-SPO group key (derived, not deserialised);
    // group_members carries a representative row's underlying members as {"total":N,"members":[...]}.
    public static final String SPO_KEY = "spo_key";
    public static final String GROUP_MEMBERS = "group_members";
    // Cross-ontology mapping (ADR-0024): the CURIE prefix of subject_id / object_id (the "ontology"
    // a term belongs to). Derived at serialization time, not deserialised.
    public static final String SUBJECT_PREFIX = "subject_prefix";
    public static final String OBJECT_PREFIX = "object_prefix";
    // Endpoint obsolescence (ADR-0041): true iff the subject / object of this mapping is an obsolete term
    // (its IRI is a subject of an obsolete-flagged registry). Stamped by the dataload from the global
    // obsolete-entity set, not an SSSOM property. Drives the default hide of obsolete terms in search.
    // OBSOLETE is the set-level / entity-level twin (oxo2-mappingsets and oxo2-entities cores).
    public static final String SUBJECT_OBSOLETE = "subject_obsolete";
    public static final String OBJECT_OBSOLETE = "object_obsolete";
    public static final String OBSOLETE = "obsolete";
    // Data release date (ADR-0043, oxo2-mappingsets core only): the UTC instant of the dataload run
    // that indexed this mapping set, stamped by the SSSOM-to-JSON stage from one run-level timestamp.
    // Not an SSSOM property. The newest value across the collection is the corpus's current release
    // date; it is absent on any set indexed before this field existed.
    public static final String DATA_RELEASE_DATE = "data_release_date";
    public static final String CHAIN_RULE = "chain_rule";
    public static final String CHAIN_RULE_APPLICATIONS = "chain_rule_applications";
    public static final String DISTANCE = "distance";
    public static final String EXPLANATION_LENGTH = "explanation_length";
    public static final String EXPLANATION = "explanation";
    public static final String INFERENCE_TYPE = "inference_type";
    // OxO curation category (ADR-0027): "ontology" (OLS-derived) vs "curated" (EVORA / Mapping Commons /
    // curated GitHub repos), carried on each OxO config mapping_registries entry, not an SSSOM property.
    public static final String MAPPING_SET_CATEGORY = "mapping_set_category";
    // Ontology mapping-set fields (oxo2-mappingsets core only): the CURIE prefix and human-readable name
    // of the ontology an OLS-derived set belongs to. OLS SSSOM extracts carry these inside the `other`
    // extension block; the dataload promotes them to discrete fields (derived from `other` at
    // serialization time, not deserialised) so the frontend can list ontologies with their own columns.
    public static final String PREFIX = "prefix";
    public static final String ONTOLOGY = "ontology";
    // The IRI of the ontology itself (e.g. http://purl.obolibrary.org/obo/sepio.owl), promoted out of
    // the same `other` block. Distinct from the ontology's NAMESPACE, which is the IRI stem its terms
    // expand against (http://purl.obolibrary.org/obo/SEPIO_) and is not derivable from this value —
    // see EntityConstants.NAMESPACE. ADR-0047.
    public static final String ONTOLOGY_IRI = "ontology_iri";
    public static final String MAPPING_ID = "mapping_id";
    public static final String OBJECT_IRI = "object_iri";
    public static final String PREDICATE_IRI = "predicate_iri";
    public static final String PREMISES = "premises";
    public static final String SUBJECT_IRI = "subject_iri";


    // Rule
    public static final String CHAIN_RULE_NAME = "chain_rule_name";
    public static final String CHAIN_RULE_LONG_FORM = "chain_rule_long_form";
    public static final String CHAIN_RULE_ABBREVIATED = "chain_rule_abbreviated";
}
