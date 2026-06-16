# oxo2-integration-tests — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints.

## Purpose

End-to-end integration tests that exercise the full `loadData.nextflow` pipeline against minimal SSSOM
fixtures and assert against expected output at four layers (Nemo inferred TTL, Nemo explanation chain
JSON, OxO2 explained JSON, and Solr counts). Under the single-pass SSSOM reasoning model ([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)) each fixture is run **in isolation** — its own `loadData.nextflow` pass over only its set(s) — so the single cross-set `oxo2/inferences` output belongs to exactly that fixture and can be asserted per-rule.
The final pass leaves Solr populated for downstream backend / frontend work.

Scope: one minimal single-set fixture per active `ChainRulesEnum` rule under `testcases/minimal/rules/` (the SSSOM rules in `sssom.rls`), plus cross-set fixtures under `testcases/minimal/crossset/<name>/` whose mappings only chain when reasoned over together.

## Vocabulary introduced here

- **Rule fixture** — a minimal single-set SSSOM TSV at `testcases/minimal/rules/<RULE>.sssom.tsv`
  designed to trigger one chain rule (and accept whatever cascade Nemo derives).
- **Cross-set fixture** — a directory `testcases/minimal/crossset/<name>/` of two or more SSSOM TSVs
  whose mappings only chain when reasoned over together, proving cross-set inference with
  per-leaf `mapping_id` provenance (the inferred set's `mapping_set_source` is the union of the
  contributing sets).
- **Expected output layer** — an assertion target for a fixture: the cross-set inferred TTL / chain JSON / explained JSON / mappingSet JSON (the single `inferences-*` files) and the per-`inference_type` Solr `numFound`. All mirrored per
  fixture under `testcases_expected_output/minimal/<fixture>/`. `ArtifactPaths.artifactsFor(fixture)`
  is the single source of truth for the path list.
- **Capture mode** — running `mvn ... exec:java@captureExpected` instead of `mvn ... verify`. Runs the
  same per-fixture isolated passes but writes canonicalised actual output to the expected paths
  instead of asserting. Used to baseline a new fixture or refresh after an intentional change.

## Depends on

External:
- **JUnit 6 + Failsafe** — integration-test runner (`*IntegrationTest.java`, `mvn verify`).
- **Nextflow + Nemo + Solr** — invoked as the production pipeline; the test does not mock them.

Internal:
- `oxo2-shared` — uses `InferredMapping`, `ChainRuleApplications`, `ChainRulesEnum` for the
  semantic comparator on the explained-JSON layer.

## Exposes

- **`ChainRulesIntegrationTest`** — Failsafe IT, one dynamic test per rule fixture.
- **`mvn ... exec:java@generateConfig`** — writes an `oxo-config-minimal-rules.generated.json`
  to `$OXO2_DATA/`. Can be invoked standalone to set up an `OXO2_CONFIG` for a manual pipeline
  run against the test fixtures.
- **`mvn ... exec:java@captureExpected`** — runs the pipeline, then writes canonicalised
  actual output to `testcases_expected_output/minimal/rules/`. Both honour
  `-Doxo2.it.rule=<RULE>` to scope to a single fixture.

This module exposes no Java API to other OxO2 modules.

## Module notes

### Environment contract

Reuses the same env vars as `loadData.nextflow`. The harness fails fast if any is missing:

- `OXO2_DATA` — workspace for pipeline intermediates and outputs.
- `NEXTFLOW_DIR` — Nextflow workdir.
- `SOLR_SCRIPT` — Solr `bin/` directory.
- `SOLR_HOME` — Solr data directory.
- `OXO2_SOLR_HOST` — Solr base URL (typically `http://localhost:8983/solr`).

### Operational consequences

- The integration-test run **destroys** the developer's local `oxo2-mappings` and `oxo2-mappingsets`
  Solr collections, repeatedly: each fixture is a fresh isolated `loadData.nextflow` pass that wipes
  `$OXO2_DATA` and the collections. The final fixture's data is what remains in Solr afterwards.
- Because every fixture is its own pipeline pass, a full `mvn -pl oxo2-integration-tests verify` runs
  the pipeline once per fixture (tens of minutes). Use `-Doxo2.it.rule=<name>` (with `generateConfig`
  / `captureExpected` / Failsafe) to scope to one fixture during debugging.

### Pipeline resource overrides

`nextflow-test.config` defines a `test` profile sized for fixtures with 1–2 mappings
each (256 MB–512 MB per process, no `maxForks` cap). `Pipeline.java` exports
`NF_PROFILE=test` and `NF_EXTRA_CONFIG=<abs path to nextflow-test.config>` before
invoking `loadData.nextflow`; the dataload scripts append `-c $NF_EXTRA_CONFIG` to
every `nextflow run` invocation so the test profile is available alongside the
production `standard` / `slurm` profiles. The production `standard` profile is far
too large for fixtures this small and deadlocks the local executor on 22 fixtures.

### Maven goals

`mvn -am` would propagate `exec:java@<id>` to upstream modules (oxo2-shared) where the
execution id is undefined, so the workflow is two steps:

```bash
# One-time (or after upstream-module changes): populate the local Maven cache.
mvn install -DskipTests

# Bootstrap or refresh expected outputs after intentional change.
mvn -pl oxo2-integration-tests exec:java@captureExpected
mvn -pl oxo2-integration-tests exec:java@captureExpected -Doxo2.it.rule=T1

# Generate OXO2_CONFIG only (no pipeline run).
mvn -pl oxo2-integration-tests exec:java@generateConfig

# Full regression. `verify` is a standard lifecycle phase so `-am` is safe here.
mvn -pl oxo2-integration-tests -am verify

# Single rule.
mvn -pl oxo2-integration-tests -am verify -Doxo2.it.rule=T1
```

### Layer comparison strategy

`ArtifactPaths.artifactsFor(fixture)` enumerates the layer artifacts; each fixture's single cross-set pass populates the cross-set paths. A layer absent on both the actual and expected side passes silently. Paths below are relative to `$OXO2_DATA/inferences/` (actual)
and `testcases_expected_output/minimal/<fixture>/` (expected).

| Layer | Path (cross-set) | Comparator |
|---|---|---|
| Inferred TTL | `crossSet/inferences.ttl` | Expand commas, sort N-Triples lexically, text-equal. |
| Nemo chain JSON | `inferenceChainsCrossSet/inferences-chains.json` | Jackson tree, recursive key + array sort, text-equal. |
| OxO2 explained JSON | `solr/mapping/inferences-explained.json` | Recursively unwrap embedded `asserted_mappings` / `explanation` JSON strings, then recursive key + array sort, text-equal. |
| MappingSet JSON | `solr/mappingSet/inferences-mappingSet.json` | Jackson tree, recursive key + array sort, text-equal. |
| Solr | `oxo2-mappings` / `oxo2-mappingsets` | per-`inference_type` `numFound` (ASSERTED / SSSOM_INFERENCE) matches `numFound.json`. |

### Known gaps

- **Per-fixture isolation is slow**: each fixture is a full `loadData.nextflow` pass, so a complete
  `verify` runs the pipeline once per fixture (tens of minutes). This is the cost of asserting cross-set rules per-fixture; scope with `-Doxo2.it.rule=<name>` while iterating.
- **Weak predicates are negative-tested only for `closeMatch`**: `oboInOwl:hasDbXref`,
  `skos:relatedMatch`, `skos:closeMatch`, `rdfs:seeAlso`, and `rdf:type` are deliberately excluded
  from chaining (ADR-0009). `RCE_WEAK_NOCHAIN` is an explicit guard fixture: its
  `equivalentClass`+`closeMatch` chain matches the RCE1 role-chain pattern but the `strongPredicate`
  guard suppresses it: the weak `closeMatch` is never propagated to a chained inference. Its only
  inference is the symmetric edge `B owl:equivalentClass A` of the strong asserted
  `A owl:equivalentClass B` (`SSSOM_INFERENCE` numFound 1) — no longer a zero-inference fixture. The other weak predicates have no dedicated
  fixture and are covered only by every fixture's exact-count `numFound` assertion.
- **Synthetic-IRI distance is degenerate**: `distance` is derived from OBO-style `PREFIX_NUMBER` IRIs,
  which the `ex:A`-style test IRIs don't match, so fixtures record a constant placeholder distance.
  This is deterministic (captured in the golden) and never affects real OBO data.
