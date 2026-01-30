#!/usr/bin/env nextflow

params.input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.output_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"

params.script_dir = "${System.getenv('SCRIPT_DIR')}"

workflow {
    // Create a channel of JSON files from the input directory
    json_files = channel.fromPath("${params.input_dir}/*.json")
    
    // Process each JSON file individually
    JSON2TTL(json_files)
}

process JSON2TTL {
    tag "JSON2Turtle: ${json_file.name}"
    
    publishDir "${params.output_dir}", mode: 'copy', overwrite: true
    
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