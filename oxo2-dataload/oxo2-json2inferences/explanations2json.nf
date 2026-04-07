#!/usr/bin/env nextflow
// Convert Nemo inference chain files to JSON mappings with explanations (parallel processing).
// Reads: inferences/inferenceChains/*-chains.json
// Writes: inferences/solr/*.json

// Parameters (use OXO2_INFERENCES if set, else $OXO2_DATA/inferences)
params.input_dir = "${System.getenv('OXO2_INFERENCES')}/inferenceChains"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"

workflow {
    input_files = channel.fromPath("${params.input_dir}/*-chains.json")
        .filter { file -> file.size() > 0 }  // Skip empty files

    EXPLANATIONS_TO_JSON(input_files)
}

process EXPLANATIONS_TO_JSON {
    tag "Explanations2JSON: ${input_file.baseName}"

    publishDir "${params.output_dir}", mode: 'copy', overwrite: true

    input:
    path input_file

    output:
    path "${input_file.baseName.replace('-chains', '-explained')}.json", optional: true

    script:
    def output_file = "${input_file.baseName.replace('-chains', '-explained')}.json"
    def solr_url = params.solr_url ?: 'http://localhost:8983/solr'
    """
    export SOLR_URL="${solr_url}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    export JAVA_OPTS="\${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    "${effective_script_dir}/explanations2jsonNextflow.sh" "${input_file}" "${output_file}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}
