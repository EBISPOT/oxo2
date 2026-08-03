# ADR-0046: Spring Boot 4 forces Jackson 3, so the whole repo moves to `tools.jackson`

- **Status**: Accepted
- **Date**: 2026-07-31

## Context

Two Dependabot PRs arrived that look independent and are not: #83 bumping `spring.boot.version`
3.5.14 → 4.1.0, and #85 bumping `springdoc-openapi-starter-webmvc-ui` 2.8.17 → 3.0.3. springdoc
3.0.3's own parent is `spring-boot-starter-parent:4.0.5`, and the 2.8.x line targets Spring Boot
3.4/3.5. Applying either alone leaves the build on a springdoc line that does not match the
framework, so they are one change.

The consequential part is not the version numbers. `spring-boot-starter-web:4.1.0` pulls
`spring-boot-starter-jackson` → `spring-boot-jackson`, which depends on `tools.jackson.core:jackson-databind`
non-optionally. **Jackson 3 is the auto-configured HTTP mapper under Spring Boot 4**, and it is not
optional. Spring Boot 4's BOM still manages Jackson 2 (`jackson-2-bom.version` 2.21.4) alongside
Jackson 3 (`jackson-bom.version` 3.1.4), so both lines coexist happily on the classpath — which is
precisely what makes the failure mode quiet.

Jackson 3 keeps `jackson-annotations` at `com.fasterxml.jackson.core:jackson-annotations`, so the
*core* annotations — `@JsonProperty`, `@JsonValue`, `@JsonCreator`, `@JsonInclude`, `@JsonIgnore`,
`@JsonFormat` — are shared between both lines and needed no change. Only `jackson-core` and
`jackson-databind` moved to `tools.jackson.*`. That distinction is what made the damage survivable,
and it is also what made it invisible: everything in `com.fasterxml.jackson.databind.annotation.*`
moved, and a Jackson 3 mapper simply does not see those annotations.

`SSSOMDataType` is annotated `@JsonSerialize(using = SSSOMDataType.Serializer.class)` — a
databind-level annotation. Nearly every field of `Mapping` is an `SSSOMDataType` subclass
(`EntityReference`, `Uri`, `Date`, `Double`, `CurieMap`, `KeyValuePairsAsString`), and `Mapping` is a
REST response payload on the v1, v2 and SSSOM endpoints. Under Jackson 3 the annotation was ignored
and bean introspection took over, turning

```json
"subject_id": "MONDO:0005148"
```

into

```json
"subject_id": {"curiePrefix":"MONDO","dataAsString":"MONDO:0005148","dataRepresentation":"MONDO:0005148"}
```

on every mapping-bearing endpoint. No compile error, no exception — the internal representation
simply leaked into the public contract.

Keeping Jackson 2 as the MVC converter was considered. Spring Framework 7.0.8 still ships
`MappingJackson2HttpMessageConverter`, so excluding `JacksonAutoConfiguration` and forcing the old
converter would have worked and touched roughly twenty lines. It was rejected: that converter is
deprecated on arrival, so it buys one release and has to be undone at the next major anyway.

`oxo2-shared`'s annotations are also read by four dataload modules driving their own
`ObjectMapper`s over the same classes, so `oxo2-shared` cannot move alone.

## Decision

Take both bumps together and migrate the entire repository from Jackson 2 to Jackson 3
(`tools.jackson`) — `oxo2-shared`, all four dataload modules, `oxo2-backend` and
`oxo2-integration-tests` — rather than pinning the backend to the deprecated Jackson 2 converter.
The Jackson version is managed once, by importing `tools.jackson:jackson-bom` in the root pom.

## Consequences

**The `com.fasterxml.jackson.annotation` package stays.** Core annotations on DTOs are untouched and
still correct; only databind-level constructs moved. Do not "tidy" those imports to `tools.jackson` —
there is no such package for them.

**Jackson 3 changed two defaults, and both were load-bearing here.**

- `FAIL_ON_UNKNOWN_PROPERTIES` now defaults to *false*. `ConfigurationReader` relied on the Jackson 2
  default to reject a typo'd key; without it, a mistyped `min_confidence_typo` would be silently
  ignored and the confidence gate (ADR-0037) would quietly not apply. It is now enabled explicitly
  there, in `InferredMapping`, and in `ExplainInferredMappings`' nmo-trace reader. **Any new mapper
  that deserializes into a POJO must ask for this explicitly.**
- `FAIL_ON_TRAILING_TOKENS` now defaults to *true*, which breaks `mapper.readTree(parser)` inside a
  loop over a streamed array — it rejects everything after the first element. Streaming reads use
  `parser.readValueAsTree()` instead.

**`Page` is no longer serialized directly.** `MappingSearchResponse` exposed a Spring Data
`Page<Mapping>`; Jackson 3 bean-introspects `PageImpl`, walks into `Pageable`, and an unpaged query
yields `Unpaged`, whose `getOffset()` throws `UnsupportedOperationException` by design. It now
exposes a `MappingPage` record naming exactly the five fields the API promises — `content`,
`totalElements`, `totalPages`, `number`, `size` — which is what the frontend already reads. This also
stops leaking `pageable`, `sort`, `first`, `last`, `empty` and `numberOfElements` into the public
response. The SSSOM endpoints were already insulated by `SssomPage`.

**The shaded fat jar needed a second and third resource transformer.** Spring Boot 4 split
autoconfigure into many small modules, which multiplied the jars shipping the same metadata file.
`META-INF/spring.factories` is now shipped by **fifteen** jars; the shade plugin kept one, silently
discarding spring-boot's own `PropertySourceLoader` and `ConfigDataLocationResolver` registrations,
so the fat jar booted without ever reading `application.properties` and died on an unresolved
`${connectionTimeoutMillis}`. Appending is not sufficient for that file — several keys are defined by
multiple jars and duplicate keys in one properties file resolve to the last value — so it uses
Spring Boot's `PropertiesMergingResourceTransformer`, which merges per key. This class of bug will
recur: **whenever a Spring metadata resource starts being shipped by more than one jar, the shade
config needs a matching transformer, and the symptom is silent.**

**Two datatype modules disappeared.** Jackson 3 folds `jackson-datatype-jdk8` (Optional) and
`jackson-datatype-jsr310` (java.time) into databind, so those dependencies and every
`registerModule` call for them are gone. `WRITE_DATES_AS_TIMESTAMPS` moved from
`SerializationFeature` to `DateTimeFeature`.

**Mappers are immutable now.** All configuration moves onto `JsonMapper.builder()`, and a configured
mapper is thread-safe, so the several classes that built an identical mapper per call now hold one
shared static instance.

**Jackson 2 is still on the classpath, and that is expected.** No OxO2 code references it, but
`solr-solrj:9.8.0` drags in `jackson-databind` 2.21.4 for its own use, and `jackson-annotations` 2.21
is the single annotations jar both lines share. Seeing Jackson 2 in `dependency:tree` does not mean
the migration is incomplete — check whether anything under `uk.ac.ebi.spot` imports it (nothing does)
before concluding otherwise.

**Three orphan poms gained a parent.** `oxo2-shared`, `oxo2-sssom2json` and `oxo2-mappings2entities`
were listed as modules but declared no parent, so the root `dependencyManagement` could not reach
them. They now inherit, which is what lets the Jackson version live in one place.

**The swagger-annotations pin survives unchanged.** springdoc 3.0.3 ships the same swagger-core
2.2.47 as 2.8.17 did, and SolrJ still drags in the older 2.2.22, so the `2.2.52` pin in
`oxo2-backend/pom.xml` keeps its original rationale. `OpenApiDocsTest` still covers it.

**Verification.** 243/243 backend tests, 150 unit tests across shared and the dataload modules, and
184/184 integration tests — all matching the pre-upgrade baseline — plus a fat-jar boot serving
`/v3/api-docs`. The integration goldens were unaffected: `Canonicalisers` sorts object keys, so the
field-order change Jackson 3 introduces is canonicalised away.
