#!/usr/bin/env nextflow

// Parameters
params.input_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.output_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json"
params.script_dir = params.script_dir ?: "${projectDir}"

workflow {
    tsv_files = channel.fromPath("${params.input_dir}/**.tsv")
        .map { f -> FilenameGuard.assertSafe(f.name); f }
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
// output to the input TSV's basename inside the task workdir to guarantee
// publish-time uniqueness; downstream consumers (inferAndExplainMappings.nf, json2solr.sh)
// match files by *.json glob, not by exact name.
process SSSOM2JSON {
    tag "${tsv_file.baseName}"

    publishDir "${params.output_dir}", mode: 'copy', pattern: "{mappingSet,mapping}/*.json"

    input:
    path tsv_file
    path yml_files

    output:
    path "mappingSet/${tsv_file.baseName}.json", optional: true
    path "mapping/${tsv_file.baseName}.json", optional: true

    script:
    """
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar" \
        -f "${tsv_file}" \
        -o .

    # Rename the JAR's mappingSetId-derived output filenames to the input
    # basename so concurrent tasks cannot overwrite each other at publish time.
    for d in mappingSet mapping; do
      [ -d "\$d" ] || continue
      for f in "\$d"/*.json; do
        [ -f "\$f" ] || continue
        target="\$d/${tsv_file.baseName}.json"
        if [ "\$f" != "\$target" ]; then
          mv -- "\$f" "\$target"
        fi
        break
      done
    done
    """
}
