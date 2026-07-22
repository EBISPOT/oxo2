import { InferenceType } from "./InferenceType";

export enum MappingFields {
    authorId = "authorId",
    authorLabel = "authorLabel",
    comment = "comment",
    confidence = "confidence",
    creatorId = "creatorId",
    creatorLabel = "creatorLabel",
    curationRule = "curationRule",
    issueTrackerItem = "issueTrackerItem",
    license = "license",
    mappingCardinality = "mappingCardinality",
    mappingDate = "mappingDate",
    mappingId = "mappingId",
    mappingJustification = "mappingJustification",
    mappingProvider = "mappingProvider",
    mappingSetDescription = "mappingSetDescription",
    mappingSetId = "mappingSetId",
    mappingSetSource = "mappingSetSource",
    mappingSetTitle = "mappingSetTitle",
    mappingSetVersion = "mappingSetVersion",
    mappingSource = "mappingSource",
    mappingTool = "mappingTool",
    mappingToolVersion = "mappingToolVersion",
    matchString = "matchString",
    objectCategory = "objectCategory",
    objectId = "objectId",
    objectLabel = "objectLabel",
    objectMatchField = "objectMatchField",
    objectPreprocessing = "objectPreprocessing",
    objectSource = "objectSource",
    objectSourceVersion = "objectSourceVersion",
    objectType = "objectType",
    other = "other",
    predicateId = "predicateId",
    predicateLabel = "predicateLabel",
    predicateModifier = "predicateModifier",
    publicationDate = "publicationDate",
    reviewerId = "reviewerId",
    reviewerLabel = "reviewerLabel",
    seeAlso = "seeAlso",
    similarityMeasure = "similarityMeasure",
    similarityScore = "similarityScore",
    subjectCategory = "subjectCategory",
    subjectId = "subjectId",
    subjectLabel = "subjectLabel",
    subjectMatchField = "subjectMatchField",
    subjectPreprocessing = "subjectPreprocessing",
    subjectSource = "subjectSource",
    subjectSourceVersion = "subjectSourceVersion",
    subjectType = "subjectType",

    assertedMappings = "assertedMappings",
    explanation = "explanation"

}

export interface InferredMappingResponse {
    mapping_justification?: string;
    mapping_tool?: string;
    mapping_set_id?: string;
    mapping_id?: string;
    object_iri?: string;
    object_id?: string;
    object_label?: string;
    predicate_iri?: string;
    predicate_id?: string;
    subject_iri?: string;
    subject_id?: string;
    subject_label?: string;
    distance?: number;
    chain_rule_applications?: ChainRuleApplicationsResponse;
}

export interface ChainRuleResponse {
    chain_rule_name: string;
    chain_rule_long_form: string;
    chain_rule_abbreviated: string;
}

export interface ChainRuleApplicationsResponse {
    chain_rule: ChainRuleResponse;
    premises?: string[];
}

export interface ChainRule {
    chainRuleName: string;
    chainRuleLongForm: string;
    chainRuleAbbreviated: string;
}

export interface ChainRuleApplications {
    chainRule?: ChainRule;
    premises?:InferredMapping[]
}

export interface InferredMapping {
    mappingJustification?: string;
    mappingTool?: string;
    mappingSetId?: string;
    // The premise's own mapping_id (present in the precomputed asserted_mappings blob). The blob
    // carries no confidence, but this id resolves the asserted mapping's own Solr doc, which does —
    // see fetchConfidenceByMappingIds.
    mappingId?: string;
    objectIri?: string;
    objectId?: string;
    objectLabel?: string;
    predicateIri?: string;
    predicateId?: string;
    subjectIri?: string;
    subjectId?: string;
    subjectLabel?: string;
    distance?: number;
    chainRuleApplications?: ChainRuleApplications;
}

export interface MappingResponse {
    author_id?: string;
    author_label?: string;
    comment?: string;
    confidence?: number;
    creator_id?: string;
    creator_label?: string;
    curation_rule?: string;
    issue_tracker_item?: string;
    license?: string;
    mapping_cardinality?: string;
    mapping_date?: string;
    mapping_id: string;
    mapping_justification?: string;
    mapping_provider?: string;
    mapping_set_description?: string;
    mapping_set_id: string;
    mapping_set_source?: string;
    mapping_set_title?: string;
    mapping_set_version?: string;
    mapping_source?: string;
    mapping_tool?: string;
    mapping_tool_version?: string;
    match_string?: string;
    object_category?: string;
    object_id?: string;
    object_iri?: string;
    object_label?: string;
    object_match_field?: string[];
    object_preprocessing?: string[];
    object_source?: string;
    object_source_version?: string;
    object_type?: string;
    other?: Record<string, string>;
    predicate_id?: string;
    predicate_iri?: string;
    predicate_label?: string;
    predicate_modifier?: string;
    publication_date?: string;
    reviewer_id?: string;
    reviewer_label?: string;
    see_also?: string[];
    similarity_measure?: string;
    similarity_score?: number;
    subject_category?: string;
    subject_id?: string;
    subject_iri?: string;
    subject_label?: string;
    subject_match_field?: string[];
    subject_preprocessing?: string[];
    subject_source?: string;
    subject_source_version?: string;
    subject_type?: string;

    asserted_mappings?: string;
    explanation?: string;
    inference_type?: string;
    // Hop count (SSSOM extension): 1 on an asserted mapping, the chain length on an inference.
    distance?: number;
    // Result-view grouping (ADR-0013): on a grouped query the representative carries its group's
    // members + true size as a JSON string {"total":N,"members":[...]}.
    group_members?: string;
}


export interface Mapping {
    authorId?: string;
    authorLabel?: string;
    comment?: string;
    confidence?: number;
    creatorId?: string;
    creatorLabel?: string;
    curationRule?: string;
    issueTrackerItem?: string;
    license?: string;
    mappingCardinality?: string;
    mappingDate?: string;
    mappingId: string;
    mappingJustification: string;
    mappingProvider?: string;
    mappingSetDescription?: string;
    mappingSetId: string;
    mappingSetSource?: string;
    mappingSetTitle?: string;
    mappingSetVersion?: string;
    mappingSource?: string;
    inferenceType?: InferenceType;
    // Hop count (SSSOM extension): 1 on an asserted mapping, the chain length on an inference.
    distance?: number;
    mappingTool?: string;
    mappingToolVersion?: string;
    matchString?: string;
    objectCategory?: string;
    objectId: string;
    objectIri: string;
    objectLabel: string;
    objectMatchField?: string[];
    objectPreprocessing?: string[];
    objectSource?: string;
    objectSourceVersion?: string;
    objectType?: string;
    other?: Record<string, string>;
    predicateId: string;
    predicateIri: string;
    predicateLabel: string;
    predicateModifier?: string;
    publicationDate?: string;
    reviewerId?: string;
    reviewerLabel?: string;
    seeAlso?: string[];
    similarityMeasure?: string;
    similarityScore?: number;
    subjectCategory?: string;
    subjectId: string;
    subjectIri: string;
    subjectLabel: string;
    subjectMatchField?: string[];
    subjectPreprocessing?: string[];
    subjectSource?: string;
    subjectSourceVersion?: string;
    subjectType?: string;
    assertedMappings?: InferredMapping[];
    explanation?: InferredMapping | undefined
    // Result-view grouping (ADR-0013): the underlying same-SPO mappings (including this
    // representative) and the group's true total size. Absent on ungrouped rows.
    groupMembers?: Mapping[];
    groupSize?: number;
}
