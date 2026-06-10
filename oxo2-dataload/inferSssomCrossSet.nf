#!/usr/bin/env nextflow
// Phase 2 of two-phase reasoning (ADR-0009): SSSOM reasoning applied ACROSS ALL MAPPING SETS.
// Concatenate every set's N-Quads into one corpus, infer with sssom.rls, trace, and produce a
// SINGLE cross-set inference-chains file. The mapping_id graph term carried by each fact
// (ADR-0010) lets the explanation step recover which source set each asserted premise came from.
//
// Reads the per-set N-Quads published by phase 1 (inferAndExplainMappings.nf), so run this after
// that pipeline. Phase 1 must have produced ${asserted_mappings_dir}/*.nq.
//
// Process names are distinct from the per-set pipeline (…_CROSS_SET…) because the two pipelines
// share nextflow.config selectors by process name, and the per-set EXPLAIN/MERGE resource closures
// reference a per-set baseName that does not exist here.

params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.cross_set_dir = "${System.getenv('OXO2_DATA')}/inferences/crossSet"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChainsCrossSet"

params.script_dir = params.script_dir ?: "${projectDir}"
// Phase 2 reasons with the SSSOM ruleset (strong-predicate transitivity + role chains, cross-set).
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/sssom.rls")

// Single logical inferred set; drives the output file names (inferences-chains.json etc.).
params.corpus_basename = "inferences"
// The concatenated corpus is referenced by path (not channel) by the explain step, mirroring how
// the per-set pipeline references its asserted facts.
params.corpus_file = "${params.cross_set_dir}/${params.corpus_basename}.nq"

// Mappings per tracing chunk. Smaller = more parallelism / more per-chunk overhead.
params.trace_chunk_size = 20000

workflow {
    nquads_files = channel.fromPath("${params.asserted_mappings_dir}/*.nq")
        .filter { file -> file.size() > 0 }
        .map { f -> FilenameGuard.assertSafe(f.name); f }
        .collect()

    corpus = CONCAT_CORPUS(nquads_files)
    inferred = INFER_CROSS_SET(corpus)
    trace = DETERMINE_CROSS_SET_TRACE(inferred)
    chunks = SPLIT_CROSS_SET_TRACE(trace).flatten()
    chunk_chains = EXPLAIN_CROSS_SET_CHUNK(chunks)
    MERGE_CROSS_SET_CHAIN(chunk_chains.collect())
}


process CONCAT_CORPUS {
    tag "Concatenate cross-set corpus"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path nquads_files

    output:
    path "${params.corpus_basename}.nq", optional: true

    script:
    """
    cat ${nquads_files} > "${params.corpus_basename}.nq"
    if [ ! -s "${params.corpus_basename}.nq" ]; then
        rm -f "${params.corpus_basename}.nq"
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
    path "inferred/${params.corpus_basename}.ttl", optional: true

    script:
    """
    mkdir -p inferred
    "${params.script_dir}/oxo2-json2inferences/nemoInferMappingsNextflow.sh" "${params.rules_definition}" \
        "${corpus}" "${params.corpus_basename}.ttl" inferred/

    # Remove if empty
    if [ ! -s "inferred/${params.corpus_basename}.ttl" ]; then
        rm -f "inferred/${params.corpus_basename}.ttl"
    fi
    """
}


process DETERMINE_CROSS_SET_TRACE {
    tag "Inferences to trace (cross-set)"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path inferred_ttl

    output:
    path "${params.corpus_basename}.txt", optional: true

    script:
    def output_file = "${params.corpus_basename}.txt"
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

    input:
    path inferences_to_trace_file

    output:
    path "${params.corpus_basename}-chunk*.txt", optional: true

    script:
    """
    "${params.script_dir}/oxo2-json2inferences/splitInferencesToTrace.sh" \
        "${inferences_to_trace_file}" ${params.trace_chunk_size} "${params.corpus_basename}"
    """
}


process EXPLAIN_CROSS_SET_CHUNK {
    tag "Explain chunk (cross-set): ${chunk_file.baseName}"

    input:
    path chunk_file

    output:
    path "${chunk_file.baseName}-chains.json", optional: true

    script:
    def output_file = "${chunk_file.baseName}-chains.json"
    """
    echo "[EXPLAIN_CROSS_SET_CHUNK] chunk=${chunk_file.name} trace_input_bytes=${chunk_file.size()} allocated_memory=${task.memory}"
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${params.corpus_file}" "${params.corpus_basename}-reexport.ttl" "./" "${chunk_file}" "${output_file}"
    """
}


process MERGE_CROSS_SET_CHAIN {
    tag "Merge chain chunks (cross-set)"

    publishDir "${params.inference_chains_dir}", mode: 'move', overwrite: true

    input:
    path chunk_chains_files

    output:
    path "${params.corpus_basename}-chains.json", optional: true

    script:
    def output_file = "${params.corpus_basename}-chains.json"
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-}"
    "${params.script_dir}/oxo2-json2inferences/mergeChainFiles.sh" \
        "${output_file}" ${chunk_chains_files}
    """
}
