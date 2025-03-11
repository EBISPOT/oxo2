
export enum MappingFields {
    mappingId = "mapping_id",
    mappingSetId = "mapping_set_id",
    subjectId = "subject_id",
    subjectLabel = "subject_label",
    subjectIdPrefix = "subject_id_prefix",
    predicateId = "predicate_id",
    predicateLabel = "predicate_label",
    predicateModifier = "predicate_modifier",
    objectId = "object_id",
    objectLabel = "object_label",
    objectIdPrefix = "object_id_prefix",
    mappingJustification = "mapping_justification"
}


export interface MappingResponse {
    mapping_id: string;
    mapping_justification?: string;
    mapping_set_id: string;
    object_id?: string;
    predicate_id?: string;
    subject_id?: string;
}

export interface Mapping {
    mappingId: string;
    mappingJustification: string;
    mappingSetId: string;
    objectId: string;
    predicateId: string;
    subjectId: string;
}
