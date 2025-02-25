
export enum MappingFields {
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

export interface Mapping {
    mappingSetId: string;
    subjectId: string;
    subjectLabel: string;
    subjectIdPrefix: string;
    predicateId: string;
    predicateLabel: string;
    predicateModifier: string;
    objectId: string;
    objectLabel: string;
    objectIdPrefix: string;
    mappingJustification: string;
}
