#!/usr/bin/env nextflow
// Build BARE inferred-mapping JSON from the single cross-set inferred-mappings TTL (ADR-0020).
// No explanations: explanations are deferred to a future on-demand service, so each inferred
// mapping is emitted with its subject/predicate/object + CURIE/label + inference_type only — no
// explanation chain, distance, explanation_length, asserted evidence, or set-source union.
//
// Reads:  ${inferences_ttl}                       (crossSet/inferences.ttl from INFER_CROSS_SET)
// Writes: ${output_dir}/mapping/inferences-explained.json
//         ${output_dir}/mappingSet/inferences-mappingSet.json
//
// The asserted mappings must already be in Solr: the indexer resolves each inferred entity's
// CURIE/label from the asserted index (DataloadSolr), so this runs after index-asserted.

params.inferences_ttl = "${System.getenv('OXO2_INFERENCES')}/crossSet/inferences.ttl"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
params.inference_type = "SSSOM_INFERENCE"

workflow {
    inferences = channel.fromPath(params.inferences_ttl, checkIfExists: true)
        .filter { file -> file.size() > 0 }  // Skip an empty TTL (no inferences)
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    INFERENCES_TO_JSON(inferences)
}

process INFERENCES_TO_JSON {
    tag "Inferences2JSON (bare): ${inferences_file.name}"

    publishDir "${params.output_dir}/mapping", mode: 'copy', overwrite: true,
        pattern: "inferences-explained.json"
    publishDir "${params.output_dir}/mappingSet", mode: 'copy', overwrite: true,
        pattern: "inferences-mappingSet.json"

    input:
    path inferences_file

    output:
    path "inferences-explained.json", optional: true
    path "inferences-mappingSet.json", optional: true

    script:
    def output_file = "inferences-explained.json"
    def mapping_set_output_file = "inferences-mappingSet.json"
    def solr_url = params.solr_url ?: 'http://localhost:8983/solr'
    def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"
    // Size the JVM heap from the task allocation. Without -Xmx, OpenJDK falls back to ~25% of host
    // RAM, far below the configured task.memory on large SLURM nodes. 80% leaves headroom for
    // metaspace, direct buffers, and native code.
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export SOLR_URL="${solr_url}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    "${effective_script_dir}/inferences2jsonNextflow.sh" "${inferences_file}" "${output_file}" "${mapping_set_output_file}" "${params.inference_type}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    if [ ! -s "${mapping_set_output_file}" ]; then
        rm -f "${mapping_set_output_file}"
    fi
    """
}
