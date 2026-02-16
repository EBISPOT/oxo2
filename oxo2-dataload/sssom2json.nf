#!/usr/bin/env nextflow

// Parameters
params.input_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.output_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json"
params.script_dir = file("${projectDir}")

workflow {
    SSSOM2JSON()
}

// Process all TSV files in directory mode (-i flag) to ensure getUniqueFilename()
// handles output filename collisions correctly. Per-file parallelism is not safe
// here because multiple TSV files across subdirectories can produce the same
// output filename (derived from mappingSetId), causing silent overwrites.
process SSSOM2JSON {
    tag "SSSOM to JSON"

    publishDir "${params.output_dir}", mode: 'copy', pattern: "{mappingSet,mapping}/*.json"

    output:
    path "mappingSet/*.json", optional: true
    path "mapping/*.json", optional: true

    script:
    """
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar" \
        -i "${params.input_dir}" \
        -o .
    """
}