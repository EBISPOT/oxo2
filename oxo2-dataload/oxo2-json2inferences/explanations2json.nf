#!/usr/bin/env nextflow
// Convert Nemo inference chain files to JSON mappings with explanations (parallel processing).
// Reads:  inferences/inferenceChains/*-chains.json
//         sssom-as-json/mappingSet/*.json (to resolve source mapping_set_id per chain file)
// Writes: inferences/solr/mapping/*.json   (one explained-mapping JSON per source set)
//         inferences/solr/mappingSet/*.json (one inferred MappingSet JSON per source set)

import groovy.json.JsonSlurper

// Parameters (use OXO2_INFERENCES if set, else $OXO2_DATA/inferences)
params.input_dir = "${System.getenv('OXO2_INFERENCES')}/inferenceChains"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
params.mapping_set_json_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mappingSet"
def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"

def resolveSourceMappingSetId = { chainFile ->
    def baseName = chainFile.getBaseName()
    def sourceBaseName = baseName.endsWith('-chains') ? baseName[0..-8] : baseName
    def mappingSetFile = file("${params.mapping_set_json_dir}/${sourceBaseName}.json")
    if (!mappingSetFile.exists()) {
        log.warn "MappingSet JSON not found for chain file ${chainFile.name} (looked at ${mappingSetFile})"
        return null
    }
    def parsed = new JsonSlurper().parse(mappingSetFile)
    def first = (parsed instanceof List) ? parsed[0] : parsed
    return first?.mapping_set_id
}

workflow {
    input_files = channel.fromPath("${params.input_dir}/*-chains.json")
        .filter { file -> file.size() > 0 }  // Skip empty files
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
    def solr_url = params.solr_url ?: 'http://localhost:8983/solr'
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
    "${effective_script_dir}/explanations2jsonNextflow.sh" "${input_file}" "${output_file}" "${mapping_set_output_file}" "${source_mapping_set_id}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    if [ ! -s "${mapping_set_output_file}" ]; then
        rm -f "${mapping_set_output_file}"
    fi
    """
}
