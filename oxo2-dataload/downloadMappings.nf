#!/usr/bin/env nextflow

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// Parameters
params.config_file = "${System.getenv('OXO2_CONFIG')}"
params.download_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.script_dir = file("${projectDir}")

workflow {
    // Parse config and create a channel with one entry per registry
    def config = new JsonSlurper().parse(file(params.config_file))

    registries = Channel.of(config.mapping_registries.toArray())
        .map { registry ->
            tuple(registry.id, JsonOutput.toJson([mapping_registries: [registry]]))
        }

    // Download each registry in parallel
    DOWNLOAD_REGISTRY(registries)
}

process DOWNLOAD_REGISTRY {
    tag "Download: ${registry_id}"

    input:
    tuple val(registry_id), val(config_json)

    output:
    val registry_id

    script:
    """
    printf '%s' '${config_json}' > registry_config.json

    mkdir -p "${params.download_dir}"
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-downloader/target/oxo2-downloader-1.0.0-SNAPSHOT.jar" \
        --config registry_config.json \
        --download-dir "${params.download_dir}"
    """
}
