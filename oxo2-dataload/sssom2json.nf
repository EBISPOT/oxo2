#!/usr/bin/env nextflow

// Parameters
params.input_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.output_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json"
params.script_dir = params.script_dir ?: "${projectDir}"

workflow {
    // Output filenames are derived from each input's path RELATIVE to the sssom root, flattened
    // to a single safe stem (e.g. mapping_commons/mapping-registry/gene/priority.sssom.tsv ->
    // mapping_commons.mapping-registry.gene.priority.sssom). Using the bare basename would
    // collapse distinct sets that share a filename across sub-directories — notably the five
    // biopragmatics SeMRA landscape `priority.sssom.tsv` files under mapping-registry/<landscape>/,
    // which would otherwise all publish as priority.sssom.json (last-wins). Downstream stages
    // treat the stem as an opaque unique key (they match by *.json glob / read content).
    def sssomRoot = file(params.input_dir).toString()
    tsv_files = channel.fromPath("${params.input_dir}/**.tsv")
        .map { tsvPath ->
            FilenameGuard.assertSafe(tsvPath.name)
            def absolute = tsvPath.toString()
            def relative = absolute.startsWith("${sssomRoot}/")
                ? absolute.substring(sssomRoot.length() + 1)
                : tsvPath.name
            def stem = relative.replaceAll(/\.tsv$/, '').replace('/', '.')
            FilenameGuard.assertSafe(stem)
            tuple(stem, tsvPath)
        }
    // Stage every external metadata YAML alongside each TSV so that the JAR's
    // readExternalMetadata() pass finds the matching .yml file in the workdir.
    // The YAML files are tiny; broadcasting them to every task is cheap.
    yml_files = channel.fromPath("${params.input_dir}/**.yml")
        .map { f -> FilenameGuard.assertSafe(f.name); f }
        .collect().ifEmpty([])

    SSSOM2JSON(tsv_files, yml_files)
}

// One JVM per TSV — gives parallel CPU usage and resets the EntityReference/Uri
// caches between files for free. The JAR's getUniqueFilename() derives output
// filenames from mappingSetId, so two TSVs declaring the same id (or simply
// running concurrently) would collide at publish time. We rename the JAR's
// output to the input's relative-path stem (computed in the workflow) inside the
// task workdir to guarantee publish-time uniqueness; downstream consumers
// (inferSssomCrossSet.nf, json2solr.sh) match files by *.json glob, not by exact name.
process SSSOM2JSON {
    tag "${stem}"

    publishDir "${params.output_dir}", mode: 'copy', pattern: "{mappingSet,mapping}/*.json"

    input:
    tuple val(stem), path(tsv_file)
    path yml_files

    output:
    path "mappingSet/${stem}.json", optional: true
    path "mapping/${stem}.json", optional: true

    script:
    """
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar" \
        -f "${tsv_file}" \
        -o .

    # Rename the JAR's mappingSetId-derived output filenames to the input's
    # relative-path stem so concurrent tasks (and same-basename inputs from different
    # sub-directories) cannot overwrite each other at publish time.
    for d in mappingSet mapping; do
      [ -d "\$d" ] || continue
      for f in "\$d"/*.json; do
        [ -f "\$f" ] || continue
        target="\$d/${stem}.json"
        if [ "\$f" != "\$target" ]; then
          mv -- "\$f" "\$target"
        fi
        break
      done
    done
    """
}
