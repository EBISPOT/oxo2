#!/usr/bin/env nextflow
// Convert Nemo inference chain files to JSON mappings with explanations (parallel processing).
// Handles both reasoning phases (ADR-0009) via params:
//   Phase 1 (default): --inference_type OWL_INFERENCE
//                      resolves the source mapping_set_id per chain file; one inferred set per source.
//   Phase 2:           --inference_type SSSOM_INFERENCE --cross_set true
//                      a single cross-set chains file -> the single oxo2/inferences set; no per-set
//                      source resolution (the source-set union is recovered per-leaf from mapping_id).
//
// Reads:  ${input_dir}/*-chains.json
//         sssom-as-json/mappingSet/*.json (phase 1 only, to resolve source mapping_set_id)
// Writes: ${output_dir}/mapping/*-explained.json
//         ${output_dir}/mappingSet/*-mappingSet.json

params.input_dir = "${System.getenv('OXO2_INFERENCES')}/inferenceChains"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
params.mapping_set_json_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mappingSet"
params.inference_type = "OWL_INFERENCE"
params.cross_set = false

def isCrossSet() {
    return params.cross_set?.toString()?.toLowerCase() == 'true'
}

// Top-level function (not a closure variable) so DSL2 allows it alongside process/workflow.
def resolveSourceMappingSetId(chainFile) {
    def baseName = chainFile.getBaseName()
    def sourceBaseName = baseName.endsWith('-chains') ? baseName[0..-8] : baseName
    def mappingSetFile = file("${params.mapping_set_json_dir}/${sourceBaseName}.json")
    if (!mappingSetFile.exists()) {
        log.warn "MappingSet JSON not found for chain file ${chainFile.name} (looked at ${mappingSetFile})"
        return null
    }
    def parsed = new groovy.json.JsonSlurper().parse(mappingSetFile)
    def first = (parsed instanceof List) ? parsed[0] : parsed
    return first?.mapping_set_id
}

workflow {
    if (isCrossSet()) {
        // Phase 2: a single cross-set chains file; no per-set source to resolve.
        input_files = channel.fromPath("${params.input_dir}/*-chains.json")
            .filter { file -> file.size() > 0 }  // Skip empty files
            .map { f -> FilenameGuard.assertSafe(f.name); f }
            .map { chainFile -> tuple(chainFile, '') }
    } else {
        // Phase 1: resolve the source mapping_set_id per chain file.
        input_files = channel.fromPath("${params.input_dir}/*-chains.json")
            .filter { file -> file.size() > 0 }  // Skip empty files
            .map { f -> FilenameGuard.assertSafe(f.name); f }
            .map { chainFile ->
                def sourceId = resolveSourceMappingSetId(chainFile)
                tuple(chainFile, sourceId)
            }
            .filter { chainFile, sourceId ->
                if (sourceId == null) {
                    log.warn "Skipping chain file ${chainFile.name}: source mapping_set_id could not be resolved."
                    return false
                }
                return true
            }
    }

    EXPLANATIONS_TO_JSON(input_files)
}

process EXPLANATIONS_TO_JSON {
    tag "Explanations2JSON: ${input_file.baseName}"

    publishDir "${params.output_dir}/mapping", mode: 'copy', overwrite: true,
        pattern: "*-explained.json"
    publishDir "${params.output_dir}/mappingSet", mode: 'copy', overwrite: true,
        pattern: "*-mappingSet.json"

    input:
    tuple path(input_file), val(source_mapping_set_id)

    output:
    path "${input_file.baseName.replace('-chains', '-explained')}.json", optional: true
    path "${input_file.baseName.replace('-chains', '-mappingSet')}.json", optional: true

    script:
    def base = input_file.baseName.replace('-chains', '')
    def output_file = "${base}-explained.json"
    def mapping_set_output_file = "${base}-mappingSet.json"
    def cross_set_flag = isCrossSet() ? 'true' : 'false'
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
    "${effective_script_dir}/explanations2jsonNextflow.sh" "${input_file}" "${output_file}" "${mapping_set_output_file}" "${params.inference_type}" "${cross_set_flag}" "${source_mapping_set_id}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    if [ ! -s "${mapping_set_output_file}" ]; then
        rm -f "${mapping_set_output_file}"
    fi
    """
}
