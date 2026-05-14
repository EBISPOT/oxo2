# oxo2-integration-tests — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints.

## Purpose

End-to-end integration tests that exercise the full `loadData.nextflow` pipeline against per-rule SSSOM
fixtures and assert against expected output at four layers (Nemo inferred TTL, Nemo explanation
chain JSON, OxO2 explained JSON, and Solr). The same pipeline run populates Solr with the
test mapping sets, so downstream backend / frontend integration tests can read the resulting
state without re-running anything.

v1 scope: 22 per-rule minimal fixtures under `testcases/minimal/rules/`, one per active
`ChainRulesEnum` entry whose corresponding rule in `chain-rules.rls` is uncommented.

## Vocabulary introduced here

- **Rule fixture** — a minimal SSSOM TSV at `testcases/minimal/rules/<RULE>.sssom.tsv` designed
  to trigger one chain rule (and accept whatever cascade Nemo derives — see Q4 in the design
  notes below).
- **Expected output layer** — one of the four assertion targets per fixture (inferred TTL, Nemo
  chain JSON, explained JSON, Solr `numFound`). All mirrored under
  `testcases_expected_output/minimal/rules/`.
- **Capture mode** — running `mvn ... exec:java@captureExpected` instead of `mvn ... verify`.
  Same pipeline run; the harness writes canonicalised actual output to the expected paths
  instead of asserting against them. Used to baseline a new fixture or refresh after an
  intentional behaviour change.

## Depends on

External:
- **JUnit 5 + Failsafe** — integration-test runner (`*IntegrationTest.java`, `mvn verify`).
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

- The integration-test run **destroys** the developer's local `oxo2-mappings` and
  `oxo2-mappingsets` Solr collections — same contract as `loadData.nextflow` today
  (and intentionally so: the test's job is to *be* the dev's Solr fixture).
- Running `mvn -pl oxo2-integration-tests verify` invokes the full pipeline once.
  Use the `-Doxo2.it.rule=T1` filter (with `generateConfig` / `captureExpected` /
  Failsafe) to scope to one fixture during debugging.

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

| Layer | Source path | Comparator |
|---|---|---|
| Inferred TTL | `$OXO2_DATA/inferences/inferredMappings/<set>.ttl` | Expand commas, sort N-Triples lexically, text-equal. |
| Nemo chain JSON | `$OXO2_DATA/inferences/inferenceChains/<set>-chains.json` | Jackson tree, recursive key + array sort, text-equal. |
| OxO2 explained JSON | `$OXO2_DATA/inferences/solr/mapping/<set>-explained.json` | Deserialise via `InferredMapping`, recursively unwrap embedded `asserted_mappings` / `explanation` JSON strings, sort by `(subject, predicate, object)`, deep-equal. |
| Solr | Solr collections `oxo2-mappings` / `oxo2-mappingsets` | `q=mapping_set_id:"<set>"&rows=0` → `numFound` matches `numFound.json`. |

### Known gaps

- **Two unenum'd transitivity rules deferred**: `chain-rules.rls` defines transitivity for
  `oboInOwl:hasDbXref` (line 44) and `skos:relatedMatch` (line 45) without matching
  `ChainRulesEnum` entries. These rules are intentionally **not covered** by the v1
  fixture set — extending coverage requires first deciding whether to add enum entries
  or remove the rules.
