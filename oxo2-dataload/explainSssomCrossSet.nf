#!/usr/bin/env nextflow
// Component-sharded chase+trace: produce a Nemo derivation for every inferred mapping (ADR-0028).
//
// SHARD_CONCLUSIONS partitions assertedCorpus.nq into connected components over the STRONG
// predicates of sssom.rls and assigns every conclusion in inferences.ttl to its component's shard.
// EXPLAIN_SHARD then chases each shard's tiny corpus and traces all of that shard's conclusions on
// the warm engine, in parallel across shards.
//
// This replaces the pre-ADR-0020 fan-out, which re-imported the WHOLE corpus and re-ran the FULL
// reasoning once per trace chunk (~48 h). Per-trace cost scales with the size of the store being
// traced against, not with the size of the proof — so shrinking the store from 55.9M facts to a few
// thousand is what makes precompute affordable.
//
// Reads:  crossSet/assertedCorpus.nq, crossSet/inferences.ttl
// Writes: crossSet/shards/shardNNNNN.nq + shardNNNNN-targets.txt + shards-manifest.json
//         crossSet/shardChains/shardNNNNN-chains.json
//
// No Solr needed: this stage is pure reasoning. The Solr-dependent step is explanations2json.

params.cross_set_dir = "${System.getenv('OXO2_DATA')}/inferences/crossSet"
params.script_dir = params.script_dir ?: "${projectDir}"
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/sssom.rls")

params.asserted_corpus_basename = "assertedCorpus"
params.inferred_set_basename = "inferences"
params.corpus_file = "${params.cross_set_dir}/${params.asserted_corpus_basename}.nq"
params.inferences_ttl = "${params.cross_set_dir}/${params.inferred_set_basename}.ttl"

params.shards_dir = "${params.cross_set_dir}/shards"
params.shard_chains_dir = "${params.cross_set_dir}/shardChains"

// Cap on a shard's entity (dictionary) count — the knob that bounds per-trace cost. Measured:
// ms_per_trace ~= 0.95 + 3.47e-4 * entities, so capping by entities rather than by conclusion count
// nearly halves CPU per conclusion. Components are never split, so a component bigger than the cap
// becomes its own shard.
params.max_shard_entities = 1200

workflow {
    corpus = channel.fromPath(params.corpus_file, checkIfExists: true)
    inferences = channel.fromPath(params.inferences_ttl, checkIfExists: true)
        .filter { file -> file.size() > 0 }  // no inferences -> nothing to explain

    shard_files = SHARD_CONCLUSIONS(corpus, inferences).flatten()
    EXPLAIN_SHARD(pairShardWithTargets(shard_files))
}

// ---------------------------------------------------------------------------
// Resume entry point. Re-enters at EXPLAIN_SHARD, reading the PUBLISHED shard files under
// $OXO2_DATA (not Nextflow's transient work dir) — so a resumed run is independent of any cached
// NXF_WORK and of the container digest. See oxo2-dataload/CONTEXT.md § Resumable dataload.
// ---------------------------------------------------------------------------
workflow from_explain_shard {
    shard_files = channel.fromPath("${params.shards_dir}/shard*", checkIfExists: true)
        .map { f -> FilenameGuard.assertSafe(f.name); f }
    EXPLAIN_SHARD(pairShardWithTargets(shard_files))
}

/**
 * A shard is the pair (corpus .nq, targets .txt) sharing a basename. Both arrive on one channel —
 * from the process's own work dir on a full run, or from the published dir on a resume — so pair
 * them by key rather than reconstructing a path, which would race publishDir's async copy.
 *
 * The '-targets.txt' suffix is inlined rather than hoisted to a script-level constant: a script-level
 * `def` is a local of the script's run method and is NOT in scope inside a function, so referencing
 * one here throws MissingPropertyException at channel-evaluation time — which Nextflow reports as an
 * operator error while still exiting 0.
 */
def pairShardWithTargets(shard_files) {
    def corpora = shard_files
        .filter { f -> f.name.endsWith('.nq') }
        .map { nq -> tuple(nq.baseName, nq) }
    def targets = shard_files
        .filter { f -> f.name.endsWith('-targets.txt') }
        .map { t -> tuple(t.name - '-targets.txt', t) }
    return corpora.join(targets, failOnMismatch: true, failOnDuplicate: true)
}

process SHARD_CONCLUSIONS {
    tag "Shard conclusions by strong-predicate component"

    cpus 1

    publishDir "${params.shards_dir}", mode: 'copy', overwrite: true,
        pattern: "shards/*", saveAs: { filename -> file(filename).name }

    input:
    path corpus
    path inferences

    output:
    path "shards/*"

    script:
    // Size the JVM heap from the task allocation: the union-find interns every strong-predicate IRI
    // (4.35M on the dev corpus, ~4.7 GiB RSS). Without -Xmx, OpenJDK takes ~25% of host RAM.
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    mkdir -p shards
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-}"
    "${params.script_dir}/oxo2-json2inferences/shardConclusionsNextflow.sh" \
        "${corpus}" "${inferences}" shards "${params.max_shard_entities}"
    """
}

process EXPLAIN_SHARD {
    tag "Explain shard: ${shard_id}"

    cpus 1

    // 'move' rather than 'copy': the chain files are the bulk of this stage's output (~22 GB on the
    // dev corpus) and nothing downstream in THIS workflow consumes them — explanations2json.nf is a
    // separate invocation reading the published dir. Copying would transiently double the footprint.
    publishDir "${params.shard_chains_dir}", mode: 'move', overwrite: true,
        pattern: "*-chains.json"

    input:
    tuple val(shard_id), path(shard_nq), path(targets)

    output:
    path "${shard_id}-chains.json", optional: true

    script:
    """
    "${params.script_dir}/oxo2-json2inferences/nemoExplainShardNextflow.sh" \
        "${params.rules_definition}" "${shard_nq}" "${targets}" "${shard_id}-chains.json"

    # Remove if empty
    if [ ! -s "${shard_id}-chains.json" ]; then
        rm -f "${shard_id}-chains.json"
    fi
    """
}
