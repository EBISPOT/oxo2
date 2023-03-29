export default class Mapping {
  properties: any;

  constructor(properties: any) {
    this.properties = properties;
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
  getObjectId(): string {
    return this.properties["object_id"];
  }
  getObjectCurie(): string {
    return this.properties["object_id_curie"];
  }
  getJustification(): string {
    return this.properties["mapping_justification"];
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
  getObjectType(): string {
    return this.properties["object_type"];
  }
  getObjectSource(): string {
    return this.properties["object_source"];
  }
  getProvider(): string {
    return this.properties["mapping_provider"];
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
  getObjectMatchFields(): string[] {
    return this.properties["object_match_field"];
  }
  getMatchStrings(): string[] {
    return this.properties["match_string"];
  }
}
