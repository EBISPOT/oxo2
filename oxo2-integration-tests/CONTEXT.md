# oxo2-integration-tests — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints.

## Purpose

End-to-end integration tests that exercise the full `loadData.nextflow` pipeline against minimal SSSOM
fixtures and assert against expected output at three layers (Nemo inferred TTL, the OxO2
inferred-mapping JSON with its explanation chains, and Solr counts). Under the
single-pass SSSOM reasoning model ([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)) each fixture is run **in isolation** — its own `loadData.nextflow` pass over only its set(s) — so the single cross-set `oxo2/inferences` output belongs to exactly that fixture and can be asserted per-rule.
The final pass leaves Solr populated for downstream backend / frontend work.

Scope: one minimal single-set fixture per active `ChainRulesEnum` rule under `testcases/minimal/rules/` (the SSSOM rules in `sssom.rls`), plus cross-set fixtures under `testcases/minimal/crossset/<name>/` whose mappings only chain when reasoned over together.

## Vocabulary introduced here

- **Rule fixture** — a minimal single-set SSSOM TSV at `testcases/minimal/rules/<RULE>.sssom.tsv`
  designed to trigger one chain rule (and accept whatever cascade Nemo derives).
- **Cross-set fixture** — a directory `testcases/minimal/crossset/<name>/` of two or more SSSOM TSVs
  whose mappings only chain when reasoned over together, proving cross-set inference. The inferred set's
  `mapping_set_source` union is populated again, recovered from the per-leaf `mapping_id` provenance in
  each explanation chain ([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md)).
  The shape also carries anything that needs two sets to show at all: `literal-subject` uses it to prove
  that the same free-text subject asserted in both sets collapses into one row while different texts do
  not ([ADR-0042](../docs/adr/0042-literal-subject-identity-in-spo-key.md)), and `prefix-case-drift` to
  prove that one triple spelled `doid:` in one set and `DOID:` in the other is still one row
  ([ADR-0048](../docs/adr/0048-spo-key-uses-the-normalised-id.md)). And anything that needs
  a per-**registry** config flag, which a SSSOM TSV cannot express: a set whose base name ends
  `-obsolete` is declared `"obsolete": true` in the generated config
  (`ConfigGenerator.OBSOLETE_SET_SUFFIX`), which is how `obsolete-endpoint` gets an obsolete term to
  point at ([ADR-0045](../docs/adr/0045-live-buckets-for-obsolete-endpoints.md)).
- **Expected output layer** — an assertion target for a fixture: the cross-set inferred TTL / the
  explained mapping JSON / the mappingSet JSON / the derived entity documents, and the Solr `numFound`
  counts. All mirrored per fixture under `testcases_expected_output/minimal/<fixture>/`.
  `numFound.json` also pins `oxo2-mappings-spo-groups`, the distinct `spo_key` count — how many rows the
  collapsed views render. It is not a collection count, and it is the only golden that moves when a
  grouping key starts conflating unrelated mappings
  ([ADR-0042](../docs/adr/0042-literal-subject-identity-in-spo-key.md)) — or stops collapsing ones it
  should ([ADR-0048](../docs/adr/0048-spo-key-uses-the-normalised-id.md)) — since document counts stay
  put either way.
  `ArtifactPaths.artifactsFor(fixture)` is the single source of truth for the path list.
  The explained-mapping layer is a **set** of files — `inferences-explained-NNNNN.json`, one per
  explanation bundle ([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md)) — so its
  actual side is a lazily-resolved glob (`artifactsFor` runs before the fixture's pipeline pass, so an
  eager glob would always be empty) and the comparator merges the bundles before canonicalising. The
  golden stays one file: `Canonicalisers` sorts arrays, so bundling never reaches it.
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

The tests run against an **isolated** test workspace + Solr, kept separate from the production
`OXO2_DATA` / `SOLR_HOME` / `OXO2_SOLR_HOST` so a run never wipes a developer's real data or Solr.
The harness reads the `*_TEST` vars below and injects them into the `loadData.nextflow` subprocess as
the plain `OXO2_DATA` / `SOLR_HOME` / `SOLR_URL` that script expects. It **fails fast** (`Env.requireAll`)
if any is missing or blank:

- `OXO2_DATA_TEST` — workspace for pipeline intermediates and outputs (becomes `OXO2_DATA` downstream).
- `SOLR_HOME_TEST` — test Solr data directory (becomes `SOLR_HOME` downstream).
- `OXO2_SOLR_HOST_TEST` — test Solr base URL **with an explicit, non-production port**, e.g.
  `http://localhost:8984/solr`. The harness parses the port from it to start/stop Solr, and uses it
  as the query/index URL, so the test Solr never collides with a production Solr on 8983.
- `NEXTFLOW_DIR` — Nextflow workdir (shared; wiped each run regardless).
- `SOLR_SCRIPT` — Solr `bin/` directory.

### Solr lifecycle

`SolrLifecycle` owns one isolated test Solr for the whole run, **not** per fixture (the old per-fixture
stop / on-disk core wipe / start / stop / restart churn dominated the run time):

- `@BeforeAll` (Failsafe IT) / start of `captureExpected`: stop any prior test Solr on the test port →
  `copySolrConfig.sh` once (fresh empty cores in `SOLR_HOME_TEST`, safe because Solr is down) →
  `solr start` on the test port → wait for all three collections.
- Before each fixture's pipeline pass: empty all three collections with a Solr `delete *:*` + hard
  commit. `oxo2-entities` must be cleared too, or one fixture's entities would be counted into the
  next fixture's assertions.
  Solr stays up, so this replaces `copySolrConfig.sh`'s on-disk wipe (which would need Solr down). The
  schema never changes between fixtures, so re-laying config is unnecessary.
- `loadData.nextflow` runs with `OXO2_SOLR_UNMANAGED=true`, so each pass indexes into the
  already-running, already-cleared collections and skips its own `copySolrConfig` / `solr start` /
  `solr stop` (see `oxo2-dataload/CONTEXT.md` § Solr lifecycle).
- `@AfterAll` / end of `captureExpected`: `solr stop`, **unless** `-Doxo2.it.keepSolr=true`, which
  leaves the test Solr running (with the last fixture's data) for inspection while debugging.

### Operational consequences

- The test run no longer touches the developer's production Solr or `OXO2_DATA`: it only wipes
  `OXO2_DATA_TEST` and the `oxo2-mappings` / `oxo2-mappingsets` / `oxo2-entities` collections inside
  the test Solr (`SOLR_HOME_TEST`, test port). The final fixture's data remains in the test Solr only if
  `-Doxo2.it.keepSolr=true`; otherwise the run stops it.
- Caveat: the test Solr is a separate process but is still a local Solr. Running the IT while a
  production Solr is up on a *different* port is fine; only two Solrs on the *same* port would clash —
  hence the explicit non-8983 `OXO2_SOLR_HOST_TEST`.
- Because every fixture is its own pipeline pass, a full `mvn -pl oxo2-integration-tests verify` still
  runs the pipeline once per fixture, but with Solr started/stopped **once** for the whole suite. Use
  `-Doxo2.it.rule=<name>` (with `generateConfig` / `captureExpected` / Failsafe) to run a single
  fixture — Solr is started and stopped around just that one fixture — when chasing a specific bug.

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
# One-time, and ALWAYS after an oxo2-shared change: rebuild and install the dataload jars.
# Use `clean`: the oxo2-sssom2json / oxo2-json2inferences shaded jars bundle oxo2-shared, and a
# non-clean `install` can re-shade around a stale bundled Mapping.class, so the pipeline silently
# runs old code (e.g. emitting fields the source no longer defines). `clean` forces a fresh shade.
mvn clean install -DskipTests

# Bootstrap or refresh expected outputs after intentional change.
mvn -pl oxo2-integration-tests exec:java@captureExpected
mvn -pl oxo2-integration-tests exec:java@captureExpected -Doxo2.it.rule=T1

# Generate OXO2_CONFIG only (no pipeline run).
mvn -pl oxo2-integration-tests exec:java@generateConfig

# Full regression. `verify` is a standard lifecycle phase so `-am` is safe here.
# Solr is started once before the suite and stopped once after.
mvn -pl oxo2-integration-tests -am verify

# Single rule (start + stop Solr around just this fixture — use while chasing a specific bug).
mvn -pl oxo2-integration-tests -am verify -Doxo2.it.rule=T1

# Leave the test Solr running afterwards (with the last fixture's data) for inspection.
mvn -pl oxo2-integration-tests -am verify -Doxo2.it.rule=T1 -Doxo2.it.keepSolr=true
```

### Layer comparison strategy

`ArtifactPaths.artifactsFor(fixture)` enumerates the layer artifacts; each fixture's single cross-set pass populates the cross-set paths. A layer absent on both the actual and expected side passes silently. Paths below are relative to `$OXO2_DATA/inferences/` (actual)
and `testcases_expected_output/minimal/<fixture>/` (expected).

| Layer | Path (cross-set) | Comparator |
|---|---|---|
| Inferred TTL | `crossSet/inferences.ttl` | Expand commas, sort N-Triples lexically, text-equal. |
| OxO2 inferred JSON (bare) | `solr/mapping/inferences-explained.json` | Recursive key + array sort, text-equal. (Bare docs carry no embedded `asserted_mappings` / `explanation` strings to unwrap — ADR-0020.) |
| MappingSet JSON | `solr/mappingSet/inferences-mappingSet.json` | Jackson tree, recursive key + array sort, text-equal. |
| Entity documents | `entities/entities.json` | Merge the per-prefix shards, recursive key + array sort, text-equal. |
| Solr | `oxo2-mappings` / `oxo2-mappingsets` / `oxo2-entities` | per-`inference_type` `numFound` (ASSERTED / SSSOM_INFERENCE) for the two mapping collections, plus the `oxo2-entities` total, matches `numFound.json`. |

The **entity layer is the one whose actual side is rooted at `$OXO2_DATA/entities`, not
`$OXO2_DATA/inferences/`** — it is folded from the Solr index by `mappings2entities`
([ADR-0034](../docs/adr/0034-entity-collection-for-typeahead.md)) after `index-inferred`, so it is not
reasoner output at all. Its golden pins the documents, not just the count: that is what catches a
wrong label/IRI pick, a miscounted degree, and an entity reachable only as the *object* of an inferred
mapping being dropped from the subject-side suggest. The `oxo2-entities` total in `numFound.json` is
the **distinct-entity count** (the collection carries no `inference_type`), so a fold that stopped
deduping shows up there as the mapping count.

Since [ADR-0035](../docs/adr/0035-weak-predicates-as-a-user-visible-control.md) those documents also
carry the per-side, per-predicate count buckets
(`{subject,object}_count_{strong,subclassof,hasdbxref}`), and pinning them is load-bearing rather than
incidental: those buckets are what the typeahead filters and ranks on, so a bucket credited to the
wrong predicate or the wrong side silently changes which entities are suggestable — the class of bug
that shipped once already.

Two guards, because the goldens alone are not enough. The **`HIDDEN_PREDICATES` fixture** is the only
one that asserts a `rdfs:subClassOf` or `oboInOwl:hasDbXref` mapping at all, so it is the only golden
in which a weak bucket is ever non-zero — without it, a fold that credited every weak sighting to the
*strong* bucket would leave all nineteen other goldens byte-identical and pass. Its `ex:C` is the whole
bug in miniature: the subject of one `subClassOf` and one `hasDbXref` and nothing else, so it has
`subject_count` 2 and `subject_count_strong` **0**, and must be present in the collection yet not
suggestable by default. And the **"entity buckets sum to totals"** assertion holds for every entity of
every fixture (`strong + subclassof + hasdbxref == subject_count`, and likewise object-side), catching
a double-count or a dropped sighting even where the golden has nothing to say.

The **`obsolete-endpoint` fixture** closes the identical hole one dimension over, for ADR-0045's `_live`
count buckets. Without an obsolete registry anywhere in the fixture set, every `_live` bucket in every
golden equals its base bucket, so a fold that wrongly credited an obsolete-endpoint sighting to the live
twin would leave all 22 other goldens byte-identical and pass. It is a cross-set fixture because the flag
lives on the *registry*, not in the TSV: `ConfigGenerator` writes `"obsolete": true` for any set whose base
name ends `-obsolete` (`ConfigGenerator.OBSOLETE_SET_SUFFIX`), which is the only way a fixture can ask for
operator knowledge it cannot express in SSSOM. Its `ex:A` is the reported bug in miniature — a **live**
entity whose one mapping points at the obsolete `ex:DEAD`, so `subject_count_strong` is 1 while
`subject_count_strong_live` is **0**, and its `set_scope` carries `setLive|S|strong` with no `strong_live`
twin: present in the collection, correctly *not* suggestable by default. `ex:P`/`ex:Q` are the live control
at 1 and 1, `ex:DEAD` shows an obsolete term with every live bucket zero (which is why the entity-level
`obsolete` filter is now redundant, though kept), and `ex:R` covers the weak×obsolete cross —
`object_count_hasdbxref` 1, its live twin 0.

The **`UNSAFE_PREFIX_SHARD` fixture** guards the shard-naming layer, which no unit test can reach: its
`ex/x:1` subject has the CURIE prefix `EX/X`, a valid Solr facet value but an unsafe *filename*. A
malformed-IRI-as-CURIE like this (`SRAO_/SRAO`, `CPONT_/VOCAB/CPONT`) is real — the OLS corpus carries
several — and `mappings2entities` names each prefix shard's JSON file. `ShardName` decouples the two,
encoding an unsafe prefix as `enc-<hex>`; before it existed, `FilenameGuard` refused the slash and the
whole `mappings2entities` stage aborted mid-load. The fixture drives that path end to end, which a
`ShardNameTest` (correct as it is) cannot: the bug was the Nextflow wiring using the raw prefix as the
filename, not the encoding itself.

The **`prefix-case-drift` fixture** is the only one that exercises the same-SPO collapse rather
than merely pinning its stability, and the only fixture whose `oxo2-mappings-spo-groups` (4) is
strictly **below** its document count (5). Every other fixture has the two equal, so before it
existed a regression that keyed `spo_key` on the raw source spelling again — the ADR-0048 defect —
left all 23 goldens byte-identical and kept the whole suite green, with only `MappingSpoKeyTest`
to catch it. It is a cross-set fixture because the drift is *per-set* by nature: `setA` writes the
triple `doid:0050043 skos:exactMatch mondo:0000616` and `setB` writes the same triple as
`DOID:0050043 SKOS:exactMatch MONDO:0000616`. Solr indexes both rows under identical ids, so they
are one mapping group, and the count parts into 5-and-4 only because the key normalises.

The fixture's duplicate pair also pins **evidence expansion**
([ADR-0049](../docs/adr/0049-asserted-evidence-expands-to-corpus-identical-premises.md)): its two
rows produce corpus quads identical in (s, p, o), so Nemo's trace carried an arbitrary one and the
inferred mapping's `asserted_mappings` / `mapping_set_source` flipped between `setA` and `setB`
across runs — the goldens pinned a coin flip. Under ADR-0049 the leaf canonicalises to the lowest
`mapping_id`, `asserted_mappings` lists **both** rows, and the inferred set's source union is
`[setA, setB]`, deterministically.

The `omim:PS100070` / `OMIM:ps100070` pair carries the **negative** half, and is what stops the
fixture passing for the wrong reason: those two rows differ only in the case of the CURIE's *local
part*, which identifies the term and must stay case-sensitive, so they hold **two** groups. Without
them a "fix" that lower-cased the whole CURIE would also drive groups below documents and look
correct. They use `oboInOwl:hasDbXref` deliberately — a weak predicate does not chain (ADR-0009),
so the control adds no inferences and the fixture's single `SSSOM_INFERENCE` stays the symmetric
`exactMatch` edge of the pair under test.

> **"Weak" means two different things here.** `RCE_WEAK_NOCHAIN` means *inference*-weak — `skos:closeMatch`,
> which the RCE role chains do not propagate. ADR-0035's weak predicates are *visibility*-weak — hidden
> from search unless the user ticks them. `skos:closeMatch` is inference-weak but perfectly visible, so
> it belongs in the **strong** entity bucket. Only `rdfs:subClassOf` and `oboInOwl:hasDbXref` are
> visibility-weak; `WeakPredicate` in `oxo2-shared` is the definitive list.

### Known gaps

- **Per-fixture isolation is still a pipeline pass each**: each fixture is a full `loadData.nextflow`
  pass, so a complete `verify` runs the pipeline once per fixture. Solr is no longer bounced per
  fixture (one start/stop for the whole suite; collections cleared with a `delete *:*` between
  fixtures), which removes the dominant overhead, but the per-fixture Nextflow passes remain the cost
  of asserting cross-set rules per-fixture. Scope with `-Doxo2.it.rule=<name>` while iterating.
- **Weak predicates are negative-tested only for `closeMatch`**: `oboInOwl:hasDbXref`,
  `skos:relatedMatch`, `skos:closeMatch`, `rdfs:seeAlso`, and `rdf:type` are deliberately excluded
  from chaining (ADR-0009). `RCE_WEAK_NOCHAIN` is an explicit guard fixture: its
  `equivalentClass`+`closeMatch` chain matches the RCE1 role-chain pattern but the `strongPredicate`
  guard suppresses it: the weak `closeMatch` is never propagated to a chained inference. Its only
  inference is the symmetric edge `B owl:equivalentClass A` of the strong asserted
  `A owl:equivalentClass B` (`SSSOM_INFERENCE` numFound 1) — no longer a zero-inference fixture. The other weak predicates have no dedicated
  fixture and are covered only by every fixture's exact-count `numFound` assertion.
- **`distance`/`explanation_length` are derived and asserted in the goldens**: explanations are
  precomputed again (ADR-0028), so each inferred doc carries a real `explanation_length` and a
  `distance` — the ontology span (ADR-0031). The single-prefix fixtures are all one ontology, hence
  `distance` 1; the `DISTANCE_MULTI_HOP` fixture spans three synthetic ontologies (`ex`/`ey`/`ez`) and
  captures `distance` 2 for its three-ontology conclusions, exercising the span end-to-end.
