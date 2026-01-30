#!/usr/bin/env nextflow
// Determine inferences to trace from inferred mappings and generate explanation chains (nmo trace).
// Reads: assertedMappings/*.ttl, inferences/inferredMappings/*.ttl
// Writes: inferences/inferencesToTrace/*.txt, inferences/inferenceChains/*-chains.json

params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.inferred_mappings_dir = "${System.getenv('OXO2_DATA')}/inferences/inferredMappings"
params.inferences_to_trace_dir = "${System.getenv('OXO2_DATA')}/inferences/inferencesToTrace"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChains"
params.script_dir = file("${projectDir}")
params.rules_definition = file("${projectDir}/oxo2-json2inferences/chain-rules.rls")



workflow {
    inferred_mappings = channel.fromPath("${params.inferred_mappings_dir}/*.ttl")
        .filter { file -> file.size() > 0 }  // Skip empty files  
    
    inferences_to_trace = DETERMINE_INFERENCES_TO_TRACE(inferred_mappings)
    EXPLAIN_INFERENCES_TO_TRACE(inferences_to_trace)
}

process DETERMINE_INFERENCES_TO_TRACE {
    tag "Inferences to trace: ${inferred_ttl_file.baseName}"
    
    publishDir "${params.inferences_to_trace_dir}", mode: 'copy', overwrite: true

    input:
    path inferred_ttl_file
    
    output:
    path "${inferred_ttl_file.baseName}.txt", optional: true
    
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




process EXPLAIN_INFERENCES_TO_TRACE {
    tag "Explain mappings: ${inferences_to_trace_file.baseName}"

    publishDir "${params.inference_chains_dir}", mode: 'copy', overwrite: true
    
    input:
    path inferences_to_trace_file

    output:
    path "${inferences_to_trace_file.baseName}-chains.json", optional: true

    script:
    def output_file = "${inferences_to_trace_file.baseName}-chains.json"
    def asserted_file = "${params.asserted_mappings_dir}/${inferences_to_trace_file.baseName}.ttl"
    def inferred_file = "${params.inferred_mappings_dir}/${inferences_to_trace_file.baseName}.ttl"

    """
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
    "${asserted_file}" "${inferred_file}" "./" "${inferences_to_trace_file}" "${output_file}"
    """
}