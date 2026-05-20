#!/usr/bin/env nextflow
// Determine inferences to trace from inferred mappings and generate explanation chains (nmo trace).
// Reads: assertedMappings/*.ttl, inferences/inferredMappings/*.ttl
// Writes: inferences/inferencesToTrace/*.txt, inferences/inferenceChains/*-chains.json
//
// Tracing is parallelised within each mapping set: each per-set facts-to-trace file is split
// into chunks of params.trace_chunk_size mappings, nmo runs per chunk concurrently, and the
// per-chunk chain JSONs are merged back into one per-set chain file.

params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.inferred_mappings_dir = "${System.getenv('OXO2_DATA')}/inferences/inferredMappings"
params.inferences_to_trace_dir = "${System.getenv('OXO2_DATA')}/inferences/inferencesToTrace"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChains"
params.script_dir = file("${projectDir}")
params.rules_definition = file("${projectDir}/oxo2-json2inferences/chain-rules.rls")

// Mappings per tracing chunk. Smaller = more parallelism / more per-chunk overhead.
params.trace_chunk_size = 100000

workflow {
    inferred_mappings = channel.fromPath("${params.inferred_mappings_dir}/*.ttl")
        .filter { file -> file.size() > 0 }  // Skip empty files
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    inferences_to_trace = DETERMINE_INFERENCES_TO_TRACE(inferred_mappings)
    chunks = SPLIT_INFERENCES_TO_TRACE(inferences_to_trace).transpose()
    chunk_chains = EXPLAIN_INFERENCES_TO_TRACE_CHUNK(chunks)
    MERGE_CHAIN_JSON(chunk_chains.groupTuple())
}


process DETERMINE_INFERENCES_TO_TRACE {
    tag "Inferences to trace: ${inferred_ttl_file.baseName}"

    publishDir "${params.inferences_to_trace_dir}", mode: 'copy', overwrite: true

    input:
    path inferred_ttl_file

    output:
    tuple val("${inferred_ttl_file.baseName}"), path("${inferred_ttl_file.baseName}.txt"), optional: true

    script:
    def output_file = "${inferred_ttl_file.baseName}.txt"
    """
    "${params.script_dir}/oxo2-json2inferences/inferences2trace.sh" "${inferred_ttl_file}" "${output_file}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process SPLIT_INFERENCES_TO_TRACE {
    tag "Split trace input: ${baseName}"

    input:
    tuple val(baseName), path(inferences_to_trace_file)

    output:
    tuple val(baseName), path("${baseName}-chunk*.txt"), optional: true

    script:
    """
    "${params.script_dir}/oxo2-json2inferences/splitInferencesToTrace.sh" \
        "${inferences_to_trace_file}" ${params.trace_chunk_size} "${baseName}"
    """
}


process EXPLAIN_INFERENCES_TO_TRACE_CHUNK {
    tag "Explain chunk: ${chunk_file.baseName}"

    input:
    tuple val(baseName), path(chunk_file)

    output:
    tuple val(baseName), path("${chunk_file.baseName}-chains.json"), optional: true

    script:
    def output_file = "${chunk_file.baseName}-chains.json"
    def asserted_file = "${params.asserted_mappings_dir}/${baseName}.ttl"
    def inferred_file = "${params.inferred_mappings_dir}/${baseName}.ttl"

    """
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${asserted_file}" "${inferred_file}" "./" "${chunk_file}" "${output_file}"
    """
}


process MERGE_CHAIN_JSON {
    tag "Merge chain chunks: ${baseName}"

    publishDir "${params.inference_chains_dir}", mode: 'copy', overwrite: true

    input:
    tuple val(baseName), path(chunk_chains_files)

    output:
    path "${baseName}-chains.json", optional: true

    script:
    def output_file = "${baseName}-chains.json"
    """
    "${params.script_dir}/oxo2-json2inferences/mergeChainFiles.sh" \
        "${output_file}" ${chunk_chains_files}
    """
}
