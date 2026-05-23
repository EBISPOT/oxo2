#!/usr/bin/env nextflow
// Combined 4-stage inference pipeline: JSON→TTL → Infer → Trace → Explain
// Replaces three separate Nextflow runs with a single pipelined workflow.
// Each file flows through all stages independently, enabling per-file pipelining.

params.json_input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.inferred_mappings_dir = "${System.getenv('OXO2_DATA')}/inferences/inferredMappings"
params.inferences_to_trace_dir = "${System.getenv('OXO2_DATA')}/inferences/inferencesToTrace"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChains"

params.script_dir = params.script_dir ?: "${projectDir}"
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/chain-rules.rls")

// Mappings per tracing chunk. Smaller = more parallelism / more per-chunk overhead.
params.trace_chunk_size = 100000

workflow {
    json_files = channel.fromPath("${params.json_input_dir}/*.json")
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    asserted_ttl = JSON2TTL(json_files)
    infer_result = INFER_MAPPINGS(asserted_ttl)
    trace_result = DETERMINE_INFERENCES_TO_TRACE(infer_result)
    chunks = SPLIT_INFERENCES_TO_TRACE(trace_result).transpose()
    chunk_chains = EXPLAIN_INFERENCES_TO_TRACE_CHUNK(chunks)
    MERGE_CHAIN_JSON(chunk_chains.groupTuple())
}


process JSON2TTL {
    tag "JSON2Turtle: ${json_file.name}"

    publishDir "${params.asserted_mappings_dir}", mode: 'copy', overwrite: true

    input:
    path json_file

    output:
    path "${json_file.baseName}.ttl", optional: true

    script:
    def output_file = "${json_file.baseName}.ttl"
    """
    "${params.script_dir}/oxo2-json2inferences/json2ttlNextflow.sh" "${json_file}" "${output_file}"
    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process INFER_MAPPINGS {
    tag "Infer mappings: ${asserted_ttl.name}"

    cpus 1

//     errorStrategy 'retry'
//     maxRetries 2

    publishDir "${params.inferred_mappings_dir}", pattern: "inferred/*.ttl", mode: 'copy', overwrite: true, saveAs: { filename -> file(filename).name }

    input:
    path asserted_ttl

    output:
    tuple val("${asserted_ttl.baseName}"), path("inferred/${asserted_ttl.baseName}.ttl"), optional: true

    script:
    def baseName = asserted_ttl.baseName
    """
    mkdir -p inferred
    "${params.script_dir}/oxo2-json2inferences/nemoInferMappingsNextflow.sh" "${params.rules_definition}" \
        "${params.asserted_mappings_dir}/${asserted_ttl}" "${baseName}.ttl" inferred/

    # Remove if empty
    if [ ! -s "inferred/${baseName}.ttl" ]; then
        rm -f "inferred/${baseName}.ttl"
    fi
    """
}


process DETERMINE_INFERENCES_TO_TRACE {
    tag "Inferences to trace: ${baseName}"

    publishDir "${params.inferences_to_trace_dir}", mode: 'copy', overwrite: true

    input:
    tuple val(baseName), path(inferred_ttl)

    output:
    tuple val(baseName), path("${inferred_ttl.baseName}.txt"), optional: true

    script:
    def output_file = "${inferred_ttl.baseName}.txt"
    """
    "${params.script_dir}/oxo2-json2inferences/inferences2trace.sh" "${inferred_ttl}" "${output_file}"

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
    echo "[EXPLAIN_INFERENCES_TO_TRACE_CHUNK] baseName=${baseName} chunk=${chunk_file.name} trace_input_bytes=${chunk_file.size()} allocated_memory=${task.memory}"
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${asserted_file}" "${inferred_file}" "./" "${chunk_file}" "${output_file}"
    """
}


process MERGE_CHAIN_JSON {
    tag "Merge chain chunks: ${baseName}"

    publishDir "${params.inference_chains_dir}", mode: 'move', overwrite: true

    input:
    tuple val(baseName), path(chunk_chains_files)

    output:
    path "${baseName}-chains.json", optional: true

    script:
    def output_file = "${baseName}-chains.json"
    // Cap the JVM heap at 80 % of the SLURM allocation. Without -Xmx the JVM
    // uses ~25 % of host RAM; on large nodes that exceeds the cgroup limit and
    // SLURM's OOM killer fires before any data is loaded (exit 137).
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-}"
    "${params.script_dir}/oxo2-json2inferences/mergeChainFiles.sh" \
        "${output_file}" ${chunk_chains_files}
    """
}
