#!/usr/bin/env nextflow
// Convert Nemo inference chain files to JSON mappings with explanations (parallel processing).
// A single cross-set chains file (ADR-0016) -> the single oxo2/inferences set (SSSOM_INFERENCE);
// the source-set union is recovered per-leaf from the mapping_id provenance, so there is no per-set
// source resolution.
//
// Reads:  ${input_dir}/*-chains.json
// Writes: ${output_dir}/mapping/*-explained.json
//         ${output_dir}/mappingSet/*-mappingSet.json

params.input_dir = "${System.getenv('OXO2_INFERENCES')}/inferenceChainsCrossSet"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
params.inference_type = "SSSOM_INFERENCE"

workflow {
    // A single cross-set chains file; no per-set source to resolve (the source-set union is
    // recovered per-leaf from mapping_id).
    input_files = channel.fromPath("${params.input_dir}/*-chains.json")
        .filter { file -> file.size() > 0 }  // Skip empty files
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    EXPLANATIONS_TO_JSON(input_files)
}

process EXPLANATIONS_TO_JSON {
    tag "Explanations2JSON: ${input_file.baseName}"

    publishDir "${params.output_dir}/mapping", mode: 'copy', overwrite: true,
        pattern: "*-explained.json"
    publishDir "${params.output_dir}/mappingSet", mode: 'copy', overwrite: true,
        pattern: "*-mappingSet.json"

    input:
    path input_file

    output:
    path "${input_file.baseName.replace('-chains', '-explained')}.json", optional: true
    path "${input_file.baseName.replace('-chains', '-mappingSet')}.json", optional: true

    script:
    def base = input_file.baseName.replace('-chains', '')
    def output_file = "${base}-explained.json"
    def mapping_set_output_file = "${base}-mappingSet.json"
    def solr_url = params.solr_url ?: 'http://localhost:8983/solr'
    def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"
    // Size the JVM heap from the task allocation. Without -Xmx, OpenJDK falls
    // back to ~25% of host RAM, which on large SLURM nodes is far below the
    // configured task.memory and OOMs on big inputs (e.g. snomed at 64 GB
    // allocated, ~31 GB default heap). 80% of allocation leaves headroom for
    // metaspace, direct buffers, and native code.
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export SOLR_URL="${solr_url}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    "${effective_script_dir}/explanations2jsonNextflow.sh" "${input_file}" "${output_file}" "${mapping_set_output_file}" "${params.inference_type}" "true" ""

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    if [ ! -s "${mapping_set_output_file}" ]; then
        rm -f "${mapping_set_output_file}"
    fi
    """
}
