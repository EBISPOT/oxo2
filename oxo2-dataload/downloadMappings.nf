#!/usr/bin/env nextflow

// Parameters
params.config_file = "${System.getenv('OXO2_CONFIG')}"
params.download_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.script_dir = params.script_dir ?: "${projectDir}"
// Base directory that a relative local `url` in the config is resolved against: the directory
// holding the config file itself, so a committed test config can reference in-repo fixtures by a
// checkout-independent relative path (e.g. "testcases/worktree/efo.sssom.tsv"). Remote (http/ftp)
// and absolute file:// urls are unaffected. See ADR-0039.
params.base_dir = params.base_dir ?: file(params.config_file).parent.toString()

workflow {
    // Parse config and create a channel with one entry per registry
    def config = new groovy.json.JsonSlurper().parse(file(params.config_file))

    registries = Channel.of(config.mapping_registries.toArray())
        .map { registry ->
            FilenameGuard.assertSafe(registry.id)
            tuple(registry.id, groovy.json.JsonOutput.toJson([mapping_registries: [registry]]))
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
        --download-dir "${params.download_dir}" \
        --base-dir "${params.base_dir}"
    """
}
