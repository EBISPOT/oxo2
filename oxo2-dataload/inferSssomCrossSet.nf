#!/usr/bin/env nextflow
// SSSOM reasoning applied ACROSS ALL MAPPING SETS (ADR-0016). This is OxO2's only inference pass:
// convert each set's JSON to N-Quads, concatenate every set's N-Quads into one corpus, infer with
// sssom.rls, trace, and produce a SINGLE inference-chains file. The mapping_id graph term carried by
// each fact (ADR-0010) lets the explanation step recover which source set each asserted premise came
// from.
//
// Process names keep the …_CROSS_SET suffix: they reason over the concatenated all-sets corpus
// rather than a single set, and select their own resource tiers by name in nextflow.config.

params.json_input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.cross_set_dir = "${System.getenv('OXO2_DATA')}/inferences/crossSet"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChainsCrossSet"
// Durable per-chunk artifacts. SPLIT_CROSS_SET_TRACE and EXPLAIN_CROSS_SET_CHUNK publish their
// outputs here so a resumed run (loadData.slurm START_STAGE=explain|merge) can re-enter the DAG
// from a stable path under $OXO2_DATA, independent of Nextflow's transient work dir. See the
// resume entry points below and oxo2-dataload/CONTEXT.md § Resumable dataload.
params.chunks_dir = "${params.cross_set_dir}/chunks"
params.chunk_chains_dir = "${params.cross_set_dir}/chunkChains"

params.script_dir = params.script_dir ?: "${projectDir}"
// Reason with the SSSOM ruleset (strong-predicate transitivity + role chains, across all sets).
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/sssom.rls")

// The single logical inferred set; drives the inferred-set output names (inferences.ttl,
// inferences-chains.json, and downstream inferences-explained.json / inferences-mappingSet.json).
// This is the reasoning OUTPUT, not the corpus it is derived from.
params.inferred_set_basename = "inferences"

// The concatenated asserted-mappings corpus (every set's N-Quads), the reasoning INPUT. Named
// distinctly from the inferred set because it holds asserted facts, not inferences. Referenced by
// path (not channel) by the explain step.
params.asserted_corpus_basename = "assertedCorpus"
params.corpus_file = "${params.cross_set_dir}/${params.asserted_corpus_basename}.nq"

// The inferred mappings selected for tracing (nmo --trace-input-file). An intermediate selection
// list split into per-chunk files, not the inferred set itself.
params.inferences_to_trace_basename = "inferencesToTrace"

// Mappings per tracing chunk. Smaller = more parallelism / more per-chunk overhead.
params.trace_chunk_size = 10000

// Default entry point: the full pipeline from each set's JSON. Unchanged behaviour — this is what
// loadData.nextflow (local/integration) and a from-scratch HPC run (loadData.slurm START_STAGE in
// download|sssom2json|nquads) invoke.
workflow {
    json_files = channel.fromPath("${params.json_input_dir}/*.json")
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    nquads_files = JSON2NQUADS(json_files)
        .filter { nquads -> nquads.size() > 0 }
        .collect()

    corpus = CONCAT_CORPUS(nquads_files)
    inferThroughMerge(corpus)
}

// ---------------------------------------------------------------------------
// Resume entry points. Each re-enters the linear DAG at a substep, reading the previous substep's
// PUBLISHED artifact (under $OXO2_DATA) rather than Nextflow's transient work dir — so a resumed
// run is independent of any cached NXF_WORK and of the container digest. loadData.slurm selects one
// via `-entry` from its START_STAGE parameter. See oxo2-dataload/CONTEXT.md § Resumable dataload.
// ---------------------------------------------------------------------------

// Resume from the concatenated all-sets corpus (JSON2NQUADS + CONCAT_CORPUS already done).
workflow from_infer {
    corpus = channel.fromPath(params.corpus_file, checkIfExists: true)
    inferThroughMerge(corpus)
}

// Resume from the inferred TTL (INFER_CROSS_SET already done).
workflow from_trace {
    inferred = channel.fromPath(
        "${params.cross_set_dir}/${params.inferred_set_basename}.ttl", checkIfExists: true)
    traceThroughMerge(inferred)
}

// Resume from the per-chunk trace inputs (DETERMINE_CROSS_SET_TRACE + SPLIT_CROSS_SET_TRACE done).
workflow from_explain {
    chunks = channel.fromPath(
        "${params.chunks_dir}/${params.inferences_to_trace_basename}-chunk*.txt", checkIfExists: true)
    explainThroughMerge(chunks)
}

// Resume from the per-chunk explanation chains (EXPLAIN_CROSS_SET_CHUNK already done). This is the
// formalised "merge-only" recovery — re-run just the merge over the expensive chunk chains, e.g.
// after rebuilding the image to fix the merge step. Replaces the old manual NXF_WORK spelunking.
workflow from_merge {
    chunk_chains = channel.fromPath(
        "${params.chunk_chains_dir}/*-chains.json", checkIfExists: true).collect()
    MERGE_CROSS_SET_CHAIN(chunk_chains)
}

// ---- Composable tails shared by the default and resume entry points ----

workflow inferThroughMerge {
    take:
        corpus
    main:
        inferred = INFER_CROSS_SET(corpus)
        traceThroughMerge(inferred)
}

workflow traceThroughMerge {
    take:
        inferred_ttl
    main:
        trace = DETERMINE_CROSS_SET_TRACE(inferred_ttl)
        chunks = SPLIT_CROSS_SET_TRACE(trace).flatten()
        explainThroughMerge(chunks)
}

workflow explainThroughMerge {
    take:
        chunks
    main:
        chunk_chains = EXPLAIN_CROSS_SET_CHUNK(chunks)
        MERGE_CROSS_SET_CHAIN(chunk_chains.collect())
}


process JSON2NQUADS {
    tag "JSON2NQuads: ${json_file.name}"

    publishDir "${params.asserted_mappings_dir}", mode: 'copy', overwrite: true

    input:
    path json_file

    output:
    path "${json_file.baseName}.nq", optional: true

    script:
    def output_file = "${json_file.baseName}.nq"
    """
    "${params.script_dir}/oxo2-json2inferences/json2nquadsNextflow.sh" "${json_file}" "${output_file}"
    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process CONCAT_CORPUS {
    tag "Concatenate cross-set corpus"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path nquads_files

    output:
    path "${params.asserted_corpus_basename}.nq", optional: true

    script:
    """
    cat ${nquads_files} > "${params.asserted_corpus_basename}.nq"
    if [ ! -s "${params.asserted_corpus_basename}.nq" ]; then
        rm -f "${params.asserted_corpus_basename}.nq"
    fi
    """
}


process INFER_CROSS_SET {
    tag "Infer mappings (SSSOM, cross-set)"

    cpus 1

    publishDir "${params.cross_set_dir}", pattern: "inferred/*.ttl", mode: 'copy', overwrite: true, saveAs: { filename -> file(filename).name }

    input:
    path corpus

    output:
    path "inferred/${params.inferred_set_basename}.ttl", optional: true

    script:
    """
    mkdir -p inferred
    "${params.script_dir}/oxo2-json2inferences/nemoInferMappingsNextflow.sh" "${params.rules_definition}" \
        "${corpus}" "${params.inferred_set_basename}.ttl" inferred/

    # Remove if empty
    if [ ! -s "inferred/${params.inferred_set_basename}.ttl" ]; then
        rm -f "inferred/${params.inferred_set_basename}.ttl"
    fi
    """
}


process DETERMINE_CROSS_SET_TRACE {
    tag "Inferences to trace (cross-set)"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path inferred_ttl

    output:
    path "${params.inferences_to_trace_basename}.txt", optional: true

    script:
    def output_file = "${params.inferences_to_trace_basename}.txt"
    """
    "${params.script_dir}/oxo2-json2inferences/inferences2trace.sh" "${inferred_ttl}" "${output_file}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process SPLIT_CROSS_SET_TRACE {
    tag "Split trace input (cross-set)"

    // Published so START_STAGE=explain can re-feed the chunks without re-running INFER/SPLIT.
    publishDir "${params.chunks_dir}", mode: 'copy', overwrite: true

    input:
    path inferences_to_trace_file

    output:
    path "${params.inferences_to_trace_basename}-chunk*.txt", optional: true

    script:
    """
    "${params.script_dir}/oxo2-json2inferences/splitInferencesToTrace.sh" \
        "${inferences_to_trace_file}" ${params.trace_chunk_size} "${params.inferences_to_trace_basename}"
    """
}


process EXPLAIN_CROSS_SET_CHUNK {
    tag "Explain chunk (cross-set): ${chunk_file.baseName}"

    // Published so START_STAGE=merge can re-merge the (expensive) per-chunk chains without
    // re-running the trace. This is the durable replacement for the old NXF_WORK spelunking.
    publishDir "${params.chunk_chains_dir}", mode: 'copy', overwrite: true

    input:
    path chunk_file

    output:
    path "${chunk_file.baseName}-chains.json", optional: true

    script:
    def output_file = "${chunk_file.baseName}-chains.json"
    """
    echo "[EXPLAIN_CROSS_SET_CHUNK] chunk=${chunk_file.name} trace_input_bytes=${chunk_file.size()} allocated_memory=${task.memory}"
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${params.corpus_file}" "${params.inferred_set_basename}-reexport.ttl" "./" "${chunk_file}" "${output_file}"
    """
}


process MERGE_CROSS_SET_CHAIN {
    tag "Merge chain chunks (cross-set)"

    publishDir "${params.inference_chains_dir}", mode: 'move', overwrite: true

    input:
    path chunk_chains_files

    output:
    path "${params.inferred_set_basename}-chains.json", optional: true

    script:
    def output_file = "${params.inferred_set_basename}-chains.json"
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-}"
    "${params.script_dir}/oxo2-json2inferences/mergeChainFiles.sh" \
        "${output_file}" ${chunk_chains_files}
    """
}
