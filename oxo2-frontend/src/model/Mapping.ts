export default class Mapping {
  properties: any;

  constructor(properties: any) {
    this.properties = properties;
  }

  getMappingId(): string {
    return this.properties["uuid"];
  }
  getSubjectId(): string {
    return this.properties["subject_id"];
  }
  getSubjectCurie(): string {
    return this.properties["subject_id_curie"];
  }
  getPredicateId(): string {
    return this.properties["predicate_id"];
  }
  getPredicateCurie(): string {
    return this.properties["predicate_id_curie"];
  }
  getObjectId(): string {
    return this.properties["object_id"];
  }
  getObjectCurie(): string {
    return this.properties["object_id_curie"];
  }
  getJustificationId(): string {
    return this.properties["mapping_justification"];
  }
  getJustificationCurie(): string {
    return this.properties["mapping_justification_curie"];
  }
  getSubjectLabel(): string {
    return this.properties["subject_label"];
  }
  getSubjectCategory(): string {
    return this.properties["subject_category"];
  }
  getPredicateLabel(): string {
    return this.properties["predicate_label"];
  }
  getPredicateModifier(): string {
    return this.properties["predicate_modifier"];
  }
  getObjectLabel(): string {
    return this.properties["object_label"];
  }
  getObjectCategory(): string {
    return this.properties["object_category"];
  }
  getAuthorIds(): string[] {
    return this.properties["author_id"];
  }
  getAuthorLabels(): string[] {
    return this.properties["author_label"];
  }
  getReviewerIds(): string[] {
    return this.properties["reviewer_id"];
  }
  getReviewerLabels(): string[] {
    return this.properties["reviewer_label"];
  }
  getCreatorIds(): string[] {
    return this.properties["creator_id"];
  }
  getCreatorLabels(): string[] {
    return this.properties["creator_label"];
  }
  getSubjectType(): string {
    return this.properties["subject_type"];
  }
  getSubjectSource(): string {
    return this.properties["subject_source"];
  }
  getSubjectSourceVersion(): string {
    return this.properties["subject_source_version"];
  }
  getObjectType(): string {
    return this.properties["object_type"];
  }
  getObjectSource(): string {
    return this.properties["object_source"];
  }
  getObjectSourceVersion(): string {
    return this.properties["object_source_version"];
  }
  getProvider(): string {
    return this.properties["mapping_provider"];
  }
  getCardinality(): string {
    return this.properties["mapping_cardinality"];
  }
  getMappingDate(): string {
    return this.properties["mapping_date"];
  }
  getConfidence(): number {
    return this.properties["confidence"];
  }
  getSubjectMatchFields(): string[] {
    return this.properties["subject_match_field"];
  }
  getSubjectPre(): string[] {
    return this.properties["subject_preprocessing"];
  }
  getObjectMatchFields(): string[] {
    return this.properties["object_match_field"];
  }
  getObjectPre(): string[] {
    return this.properties["object_preprocessing"];
  }
  getMatchStrings(): string[] {
    return this.properties["match_string"];
  }
  getOtherRefs(): string[] {
    return this.properties["see_also"];
  }
  getOther(): string {
    return this.properties["other"];
  }
  getComment(): string {
    return this.properties["comment"];
  }
  getLicense(): string {
    return this.properties["license"];
  }
  getTool(): string {
    return this.properties["mapping_tool"];
  }
  getToolVersion(): string {
    return this.properties["mapping_tool_version"];
  }
  getSimilarityScore(): string {
    return this.properties["semantic_similarity_score"];
  }
  getSimilarityMeasure(): string {
    return this.properties["semantic_similarity_measure"];
  }
}
