#!/usr/bin/env nextflow

// Parameters
params.input_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.output_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json"
params.script_dir = file("${projectDir}")

workflow {

     // Find all TSV files recursively and create tuples with original path
    tsv_files = channel.fromPath("${params.input_dir}/**.tsv")
        .filter { file -> file.size() > 0 }  // Skip empty files
        .map { file -> tuple(file, file.toString()) }  // Create tuple: (file, original_path)    
    
    // Process each TSV file individually in parallel
    SSSOM2JSON(tsv_files)
}

process SSSOM2JSON {
    tag "TSV to JSON: ${original_path}"
    
    // Publish output files to final directories
    publishDir "${params.output_dir}", mode: 'copy', overwrite: true, pattern: "**/*.json"
    
    input:
    tuple path(tsv_file), val(original_path)
    
    output:
    path "**/*.json", optional: true
    
    script:
    """
    
    # Process the TSV file using the original path
    "${params.script_dir}/sssom2jsonNextflow.sh" "${original_path}" .
    
    """
}