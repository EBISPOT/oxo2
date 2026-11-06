import { InferenceType, asInferenceType } from "./InferenceType";
import { MappingSetCategory, asMappingSetCategory } from "./MappingSetCategory";

// Explicit display fields keep infrastructure metadata (e.g. curie_map) out of the page.
const METADATA_LABELS = {
    mapping_set_id: 'Mapping set ID',
    mapping_set_version: 'Mapping set version',
    mapping_set_confidence: 'Mapping set confidence',
    license: 'License',
    publisher: 'Publisher',
    mapping_provider: 'Mapping provider',
    creator_label: 'Creator names',
    creator_id: 'Creator IDs',
    mapping_date: 'Mapping date',
    publication_date: 'Publication date',
    data_release_date: 'OxO data release',
    issue_tracker: 'Issue tracker',
    mailing_list: 'Mailing list',
    mapping_tool: 'Mapping tool',
    mapping_tool_version: 'Mapping tool version',
    ontology: 'Ontology',
    ontology_iri: 'Ontology IRI',
    prefix: 'Ontology prefix',
    subject_source: 'Subject source',
    subject_source_version: 'Subject source version',
    subject_type: 'Subject type',
    subject_match_field: 'Subject match fields',
    subject_preprocessing: 'Subject preprocessing',
    object_source: 'Object source',
    object_source_version: 'Object source version',
    object_type: 'Object type',
    object_match_field: 'Object match fields',
    object_preprocessing: 'Object preprocessing',
    mapping_set_source: 'Source mapping sets',
    see_also: 'See also',
    comment: 'Comment',
} as const;

type MetadataField = keyof typeof METADATA_LABELS;
type MetadataValue = string | number | string[] | null;

export interface MappingSetMetadataEntry {
    field: MetadataField;
    label: string;
    values: string[];
}

export interface MappingSetResponse extends Partial<Record<MetadataField, MetadataValue>> {
    mapping_set_id: string;
    mapping_set_title?: string;
    mapping_set_description?: string;
    creator_label?: string[];
    mapping_provider?: string;
    issue_tracker?: string;
    mailing_list?: string;
    inference_type?: string;
    mapping_set_source?: string[];
    mapping_set_category?: string;
    prefix?: string;
    ontology?: string;
    obsolete?: boolean;
}

export interface MappingSet {
    metadata?: MappingSetMetadataEntry[];
    mappingSetId: string;
    mappingSetTitle: string;
    mappingSetDescription: string;
    creatorLabel: string[];
    mappingProvider: string;
    issueTracker?: string;
    mailingList?: string;
    inferenceType: InferenceType;
    mappingSetSource: string[];
    // OxO curation category (ADR-0027); undefined on the synthetic inferences set.
    mappingSetCategory?: MappingSetCategory;
    // Ontology CURIE prefix and display name (ADR-0038), populated only on ONTOLOGY sets; '' otherwise.
    prefix: string;
    ontology: string;
    // True if every subject of this set is an obsolete term (ADR-0041). Hidden from the picker unless
    // the "obsolete terms" switch is on; labelled by the Obsolete column when shown.
    obsolete: boolean;
}

export function fromMappingSetResponse(r: MappingSetResponse): MappingSet {
    return {
        metadata: (Object.keys(METADATA_LABELS) as MetadataField[]).flatMap(field => {
            const value = r[field];
            const values = (Array.isArray(value) ? value : value == null ? [] : [value])
                .map(String).filter(text => text.trim() !== '');
            return values.length ? [{field, label: METADATA_LABELS[field], values}] : [];
        }),
        mappingSetId: r.mapping_set_id,
        mappingSetTitle: r.mapping_set_title ?? '',
        mappingSetDescription: r.mapping_set_description ?? '',
        creatorLabel: r.creator_label ?? [],
        mappingProvider: r.mapping_provider ?? '',
        issueTracker: r.issue_tracker,
        mailingList: r.mailing_list,
        inferenceType: asInferenceType(r.inference_type),
        mappingSetSource: r.mapping_set_source ?? [],
        mappingSetCategory: asMappingSetCategory(r.mapping_set_category),
        prefix: r.prefix ?? '',
        ontology: r.ontology ?? '',
        obsolete: r.obsolete ?? false,
    };
}
