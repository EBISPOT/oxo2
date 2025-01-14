export default class MappingSet {
  properties: any;

  constructor(properties: any) {
    this.properties = properties;
  }

  getId(): string {
    return this.properties["uuid"];
  }
  getMappingProvider(): string {
    return this.properties["mapping_provider"];
  }
  getDescription(): string {
    return this.properties["mapping_set_description"];
  }
  getComment(): string {
    return this.properties["comment"];
  }
  getLicense(): string {
    return this.properties["license"];
  }
  getObjectMatchFields(): string[] {
    return this.properties["object_match_field"];
  }
  getSources(): string[] {
    return this.properties["mapping_set_source"];
  }
  getCreatorLabels(): string[] {
    return this.properties["creator_label"];
  }
  getSubjectMatchFields(): string[] {
    return this.properties["subject_match_field"];
  }
  getCreatorIds(): string[] {
    return this.properties["creator_id"];
  }
  getMappingsLink(): string {
    return this.properties["mappings"]["href"];
  }
}
