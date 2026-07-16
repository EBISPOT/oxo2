#!/usr/bin/env nextflow
// Derive the oxo2-entities collection — one document per DISTINCT entity — from the already-indexed
// oxo2-mappings collection (ADR-0034).
//
// Reads:  the oxo2-mappings Solr collection (NOT the JSON on disk — the index is the only place the
//         asserted AND inferred mappings are both present, and this stage must see both)
// Writes: ${output_dir}/<PREFIX>.json   (one Solr-update array per CURIE prefix shard)
//
// Runs AFTER index-inferred. An inferred mapping's subject may appear only as an OBJECT in the
// asserted data, so deriving before the inferred mappings are indexed would drop it from the
// subject-side suggest (ADR-0030) — the entity would be un-completable in the main search box.
//
// Sharded by CURIE prefix because the fold needs the entity map in heap and the whole corpus will
// not fit. Every mapping touching an entity of prefix P is matched by `subject_prefix:P OR
// object_prefix:P`, and the JAR folds only the sides whose own prefix is P — so each shard's counts
// are exact and its heap is bounded by ONE ontology's entity count. Same idiom as sssom2json.nf's
// per-file and explanations2json.nf's per-bundle fan-out.

// Script-level defaults. Config (the slurm profile pins script_dir to the in-container /opt/oxo path)
// and CLI --params both take precedence, so these are only the local-run fallbacks.
params.output_dir = "${System.getenv('OXO2_DATA')}/entities"
params.script_dir = params.script_dir ?: "${projectDir}"
params.solr_url = 'http://localhost:8983/solr'

workflow {
    prefixes_file = LIST_PREFIXES()

    // Each line is `<shardName>\t<prefix>`: the safe filename stem, then the true prefix that keys
    // the Solr query. They are decoupled because a prefix can be a valid facet value yet an unsafe
    // filename — a slash-bearing malformed-IRI-as-CURIE like SRAO_/SRAO (ADR-0031). FilenameGuard
    // now asserts the DERIVED name, which ShardName has already made safe.
    prefixes = prefixes_file
        .splitText()
        .map { line -> line.trim() }
        .filter { line -> line }
        .map { line ->
            def (shard_name, prefix) = line.split('\t', 2)
            tuple(FilenameGuard.assertSafe(shard_name), prefix)
        }

    ENTITIES_FOR_PREFIX(prefixes)
}

// The distinct CURIE prefixes on either side of any mapping, plus the __none__ sentinel for entities
// whose CURIE never resolved to a prefix (a bare IRI, ADR-0024) — those belong to no prefix shard and
// would otherwise be silently absent from the typeahead.
process LIST_PREFIXES {
    tag "List CURIE prefixes"

    output:
    path "prefixes.txt"

    script:
    def solr_host = params.solr_url.replaceAll('https?://', '').replaceAll('/.*', '').replaceAll(':.*', '')
    // 0.7 for the same reason as ENTITIES_FOR_PREFIX below — --mem caps total RSS, not heap. This
    // stage only issues one rows=0 facet so it was never close to its cap, but the ratio is the
    // tighter of the two (0.8 of 2 GB left just 400 MB for the JVM's non-heap footprint).
    def heap_mb = (task.memory.toMega() * 0.7) as long
    """
    export SOLR_URL="${params.solr_url}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_host}"
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_host}"
    "${params.script_dir}/oxo2-mappings2entities/mappings2entitiesNextflow.sh" list-prefixes "prefixes.txt"
    """
}

// One JVM per prefix, so a shard's heap is bounded by one ontology's entity count. An empty shard
// (possible for __none__ when every entity resolved to a CURIE) publishes nothing rather than an
// empty array: json2solr.sh globs *.json, and posting an empty array is a pointless commit.
process ENTITIES_FOR_PREFIX {
    tag "Entities: ${prefix}"

    publishDir "${params.output_dir}", mode: 'copy', overwrite: true, pattern: "*.json"

    input:
    tuple val(shard_name), val(prefix)

    output:
    path "${shard_name}.json", optional: true

    script:
    def output_file = "${shard_name}.json"
    def solr_host = params.solr_url.replaceAll('https?://', '').replaceAll('/.*', '').replaceAll(':.*', '')
    // Size the JVM heap from the task allocation. Without -Xmx, OpenJDK falls back to ~25% of host
    // RAM, far below the configured task.memory on large SLURM nodes.
    //
    // 0.7, not 0.8: on SLURM --mem is a hard cgroup cap on TOTAL RSS, and a JVM's RSS is its heap PLUS
    // metaspace, code cache, G1's own native structures (~8% of -Xmx), thread stacks and the Solr
    // client's direct buffers. 0.8 of a 4 GB cap left ~800 MB for all of that, and the kernel won the
    // race: NCBITAXON died SIGKILL/OUT_OF_MEMORY at MaxRSS 3.9995 GiB (job 4695837, 2026-07-16) having
    // never reported a thing. Leaving 30% keeps the JVM the first to notice — it hits -Xmx, and
    // ExitOnOutOfMemoryError (exported by loadData.slurm) prints a readable OutOfMemoryError instead of
    // an opaque 137. Diagnosability, not headroom, is the point: the memory itself comes from
    // nextflow.config's attempt scaling.
    def heap_mb = (task.memory.toMega() * 0.7) as long
    """
    export SOLR_URL="${params.solr_url}"
    export no_proxy="localhost,127.0.0.1,\$(hostname),${solr_host}"
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-} -Dhttp.nonProxyHosts=localhost|127.0.0.1|${solr_host}"
    "${params.script_dir}/oxo2-mappings2entities/mappings2entitiesNextflow.sh" entities "${output_file}" "${prefix}"

    # An empty array ([]) is 2 bytes; drop it rather than post a no-op update.
    if [ ! -s "${output_file}" ] || [ "\$(cat "${output_file}")" = "[]" ]; then
        rm -f "${output_file}"
    fi
    """
}
