export default abstract class Entity {
  properties: any;

  constructor(properties: any) {
    this.properties = properties;
  }

  abstract getId(): string;
  abstract getLabel(): string;
  abstract getCategory(): string;
  abstract getType(): string;
  abstract getSource(): string;
  abstract getMatchField(): string[];
}
