#!/usr/bin/env nextflow

params.input_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.output_dir = "${System.getenv('OXO2_DATA')}/inferences/inferredMappings"
params.script_dir = file("${projectDir}")
params.rules_definition = file("${projectDir}/chain-rules.rls")

workflow {
    ttl_files = channel.fromPath("${params.input_dir}/*.ttl")
        .filter { file -> file.size() > 0 }  // Skip empty files
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    // Process each TTL file individually
    INFER_MAPPINGS(ttl_files)
}


process INFER_MAPPINGS {
    tag "Infer mappings: ${ttl_file.name}"
    
    // Resource allocation - nmo inference can be memory intensive
    cpus 1
    memory '4 GB'
    time '4h'
    
    // Error handling - retry transient failures
    errorStrategy 'retry'
    maxRetries 2
    
    publishDir "${params.output_dir}", mode: 'copy', overwrite: true
    
    input:
    path ttl_file
    
    output:
    path "${ttl_file.baseName}.ttl", optional: true
    
    script:
    def input_file = "${params.input_dir}/${ttl_file}"
    def output_file = "${ttl_file.baseName}.ttl"

    """
    ${params.script_dir}/nemoInferMappingsNextflow.sh ${params.rules_definition} \
    ${input_file} ${output_file} ./

    # Remove if empty                                                                                                                                                        
    if [ ! -s "${output_file}" ]; then                                                                                                                            
        rm -f "${output_file}"                                                                                                                                    
    fi         

    """
}
