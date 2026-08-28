#!/usr/bin/env nextflow
// Turn the per-shard Nemo derivations into inferred-mapping JSON carrying explanations (ADR-0028).
//
// Reads:  crossSet/shardChains/shardNNNNN-chains.json   (from explainSssomCrossSet.nf)
// Writes: ${output_dir}/mapping/inferences-explained-NNNNN.json     (one per bundle)
//         ${output_dir}/mappingSet/inferences-mappingSet.json       (one, source-union merged)
//
// Shards are processed in BUNDLES: chain interpretation is cheap next to JVM startup and the Solr
// connection, so one JVM handles many shards. Bundling is sound because each shard's trace is
// self-contained (a proof never leaves its component), so a bundle needs no cross-file dedup.
//
// The asserted mappings must already be in Solr: each inferred entity's CURIE/label and each
// asserted premise are resolved from the asserted index (DataloadSolr), so this runs after
// index-asserted.
//
// Each bundle's inferred MappingSet carries only ITS shards' contributing sources, so
// MERGE_INFERRED_MAPPING_SETS unions them into the one cross-set MappingSet. Posting the partials
// unmerged would collapse to whichever bundle Solr indexed last.

params.cross_set_dir = "${System.getenv('OXO2_INFERENCES')}/crossSet"
params.shard_chains_dir = "${params.cross_set_dir}/shardChains"
// The shard corpora (.nq) matching the chain files. Chain files are staged into the task work dir,
// so the Java side cannot find the shards by relative layout; it reads OXO2_SHARDS_DIR instead
// (ADR-0049: an asserted premise expands to every corpus quad sharing its s/p/o).
params.shards_dir = "${params.cross_set_dir}/shards"
params.output_dir = "${System.getenv('OXO2_INFERENCES')}/solr"
params.inference_type = "SSSOM_INFERENCE"

// Shards per explanations2json JVM. 100 keeps JVM+Solr-connection startup under ~1% of the stage
// while still giving Nextflow thousands of tasks' worth of parallelism to schedule.
params.explain_bundle_size = 100

workflow {
    chains = channel.fromPath("${params.shard_chains_dir}/*-chains.json", checkIfExists: true)
        .filter { file -> file.size() > 0 }
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    // Sort before collating so bundle membership (and therefore output file names) is deterministic
    // across runs — the integration goldens depend on it.
    bundles = chains.toSortedList { left, right -> left.name <=> right.name }
        .flatMap { files ->
            files.collate(params.explain_bundle_size).indexed()
                 .collect { index, group -> tuple(String.format('%05d', index), group) }
        }

    explained = EXPLANATIONS_TO_JSON(bundles)
    MERGE_INFERRED_MAPPING_SETS(explained.mapping_set.collect())
}

process EXPLANATIONS_TO_JSON {
    tag "Explanations2JSON: bundle ${bundle_id} (${trace_files.size()} shards)"

    // 'move', not 'copy': the explained JSON is this run's largest artifact (tens of GB) and is
    // terminal — index-inferred reads it from the published dir, no Nextflow process stages it — so
    // moving it out of the work dir instead of copying halves its on-disk peak. The per-bundle
    // mappingSet output is excluded by the pattern, so it still reaches MERGE via the channel.
    publishDir "${params.output_dir}/mapping", mode: 'move', overwrite: true,
        pattern: "inferences-explained-*.json"

    input:
    tuple val(bundle_id), path(trace_files)

    output:
    path "inferences-explained-${bundle_id}.json", optional: true
    path "inferences-mappingSet-${bundle_id}.json", optional: true, emit: mapping_set

    script:
    def output_file = "inferences-explained-${bundle_id}.json"
    def mapping_set_output_file = "inferences-mappingSet-${bundle_id}.json"
    def solr_url = params.solr_url ?: 'http://localhost:8983/solr'
    def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"
    // Size the JVM heap from the task allocation. Without -Xmx, OpenJDK falls back to ~25% of host
    // RAM, far below the configured task.memory on large SLURM nodes. 80% leaves headroom for
    // metaspace, direct buffers, and native code.
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export SOLR_URL="${solr_url}"
    export OXO2_SHARDS_DIR="${params.shards_dir}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_url.replaceAll('https?://','').replaceAll('/.*','').replaceAll(':.*','')}"
    "${effective_script_dir}/explanations2jsonNextflow.sh" "${output_file}" "${mapping_set_output_file}" "${params.inference_type}" ${trace_files}

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    if [ ! -s "${mapping_set_output_file}" ]; then
        rm -f "${mapping_set_output_file}"
    fi
    """
}

process MERGE_INFERRED_MAPPING_SETS {
    tag "Merge inferred MappingSet source union"

    // 'move' for the same reason: the merged mappingSet is terminal (index-inferred reads it from
    // the published dir). Tiny, so the disk win is negligible — kept consistent with the mapping side.
    publishDir "${params.output_dir}/mappingSet", mode: 'move', overwrite: true,
        pattern: "inferences-mappingSet.json"

    input:
    path mapping_set_files

    output:
    path "inferences-mappingSet.json", optional: true

    script:
    def effective_script_dir = params.script_dir ? "${params.script_dir}/oxo2-json2inferences" : "${projectDir}"
    """
    "${effective_script_dir}/mergeInferredMappingSetsNextflow.sh" "inferences-mappingSet.json" ${mapping_set_files}

    # Remove if empty
    if [ ! -s "inferences-mappingSet.json" ]; then
        rm -f "inferences-mappingSet.json"
    fi
    """
}
