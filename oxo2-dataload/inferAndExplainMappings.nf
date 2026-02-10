#!/usr/bin/env nextflow
// Combined 4-stage inference pipeline: JSON→TTL → Infer → Trace → Explain
// Replaces three separate Nextflow runs with a single pipelined workflow.
// Each file flows through all stages independently, enabling per-file pipelining.

params.json_input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.inferred_mappings_dir = "${System.getenv('OXO2_DATA')}/inferences/inferredMappings"
params.inferences_to_trace_dir = "${System.getenv('OXO2_DATA')}/inferences/inferencesToTrace"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChains"

params.script_dir = "${System.getenv('SCRIPT_DIR')}"
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/chain-rules.rls")


workflow {
    json_files = channel.fromPath("${params.json_input_dir}/*.json")

    asserted_ttl = JSON2TTL(json_files)
    infer_result = INFER_MAPPINGS(asserted_ttl)
    trace_result = DETERMINE_INFERENCES_TO_TRACE(infer_result)
    EXPLAIN_INFERENCES_TO_TRACE(trace_result)
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
    memory '4 GB'
    time '4h'

    errorStrategy 'retry'
    maxRetries 2

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


process EXPLAIN_INFERENCES_TO_TRACE {
    tag "Explain mappings: ${baseName}"

    publishDir "${params.inference_chains_dir}", mode: 'move', overwrite: true

    input:
    tuple val(baseName), path(inferences_to_trace_file)

    output:
    path "${inferences_to_trace_file.baseName}-chains.json", optional: true

    script:
    def output_file = "${inferences_to_trace_file.baseName}-chains.json"
    def asserted_file = "${params.asserted_mappings_dir}/${baseName}.ttl"
    def inferred_file = "${params.inferred_mappings_dir}/${baseName}.ttl"

    """
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${asserted_file}" "${inferred_file}" "./" "${inferences_to_trace_file}" "${output_file}"
    """
}
