#!/usr/bin/env nextflow

// Parameters
params.input_dir = "${System.getenv('OXO2_DATA')}/sssom"
params.output_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json"
params.script_dir = params.script_dir ?: "${projectDir}"
params.config_file = "${System.getenv('OXO2_CONFIG') ?: ''}"
// Data release date (ADR-0043): one UTC instant for the whole run, resolved by the orchestrator
// (loadData.lib.sh oxo2_resolve_release_date) and read here rather than minted per task — one JVM per
// TSV means a per-task `date` would stamp every mapping set a few seconds apart, and "the release
// date" would then depend on which set you asked. Empty when the orchestrator supplied none, in which
// case the flag is omitted and the field simply never reaches Solr.
params.release_date = "${System.getenv('OXO2_RELEASE_DATE') ?: ''}"

workflow {
    // Output filenames are derived from each input's path RELATIVE to the sssom root, flattened
    // to a single safe stem (e.g. mapping_commons/mapping-registry/gene/priority.sssom.tsv ->
    // mapping_commons.mapping-registry.gene.priority.sssom). Using the bare basename would
    // collapse distinct sets that share a filename across sub-directories — notably the five
    // biopragmatics SeMRA landscape `priority.sssom.tsv` files under mapping-registry/<landscape>/,
    // which would otherwise all publish as priority.sssom.json (last-wins). Downstream stages
    // treat the stem as an opaque unique key (they match by *.json glob / read content).
    def sssomRoot = file(params.input_dir).toString()

    // Each mapping set's OxO curation category (ADR-0027) comes from its config registry entry, not
    // from the SSSOM data, so it is read here and passed to the JAR per file. Without a readable
    // config every set falls back to CURATED — the conservative default, which claims no ontology
    // endorsement a set may not have.
    def configFile = params.config_file ? new File(params.config_file) : null
    def categoryByRegistryId = [:]
    // Obsolete-terms support (ADR-0041): the ids of registries flagged `obsolete: true`. Every subject of
    // these sets is an obsolete term, so their subject IRIs seed the global obsolete-entity set below.
    def obsoleteRegistryIds = [] as Set
    if (configFile?.exists()) {
        categoryByRegistryId = MappingSetCategories.byRegistryId(configFile)
        obsoleteRegistryIds = ObsoleteRegistries.ids(configFile)
    } else {
        log.warn "OXO2_CONFIG not readable (${params.config_file ?: 'unset'}); " +
                 "treating every mapping set as ${MappingSetCategories.DEFAULT} and non-obsolete."
    }

    tsv_files = channel.fromPath("${params.input_dir}/**.tsv")
        .map { tsvPath ->
            FilenameGuard.assertSafe(tsvPath.name)
            def absolute = tsvPath.toString()
            def relative = absolute.startsWith("${sssomRoot}/")
                ? absolute.substring(sssomRoot.length() + 1)
                : tsvPath.name
            def stem = relative.replaceAll(/\.tsv$/, '').replace('/', '.')
            FilenameGuard.assertSafe(stem)
            // Recover the config registry id from the downloader's on-disk layout: DownloadMappings writes
            // each registry to `<sssom root>/<registry id>` + extension. A multi-file registry (a .tgz such
            // as the OLS export) extracts into a `<registry id>/` subdirectory, so the first path segment
            // names it. A single-file registry (a plain .tsv/.gz — e.g. a repo-relative fixture, ADR-0039)
            // lands flat as `<registry id>.tsv` (the source basename is discarded), so the filename stem
            // names it. Getting this right is what lets a set's `category` (ADR-0027) and `obsolete`
            // (ADR-0041) config flags reach the JAR; treating a flat file as registry-less would silently
            // drop both.
            def registryId = relative.contains('/')
                ? relative.substring(0, relative.indexOf('/'))
                : relative.replaceAll(/\.tsv$/, '')
            def category = categoryByRegistryId.getOrDefault(registryId, MappingSetCategories.DEFAULT)
            def obsolete = obsoleteRegistryIds.contains(registryId)
            tuple(stem, tsvPath, category, obsolete)
        }
    // Stage every external metadata YAML alongside each TSV so that the JAR's
    // readExternalMetadata() pass finds the matching .yml file in the workdir.
    // The YAML files are tiny; broadcasting them to every task is cheap.
    yml_files = channel.fromPath("${params.input_dir}/**.yml")
        .map { f -> FilenameGuard.assertSafe(f.name); f }
        .collect().ifEmpty([])

    // Pass 1 (ADR-0041): from the obsolete-flagged TSVs only, extract the distinct expanded subject IRIs
    // — the global obsolete-entity set. Object-side obsolescence (a live term mapping to a dead one) can
    // only be known globally: the MONDO file cannot tell that an EFO object is obsolete without seeing
    // EFO's obsolete file. Collected to a single value channel and broadcast into every Pass-2 task.
    // Always runs exactly once (even with no obsolete registry: the list is empty and the JAR writes an
    // empty file), so the main pass always has a `-b` input to read.
    obsolete_tsv_list = tsv_files.filter { it[3] }.map { it[1] }.collect().ifEmpty([])
    obsolete_entities = EXTRACT_OBSOLETE_ENTITIES(obsolete_tsv_list, yml_files).first()

    SSSOM2JSON(tsv_files, yml_files, obsolete_entities)
}

// Pass 1 (ADR-0041): union the subject IRIs of every obsolete-flagged TSV into one obsolete-entities.txt.
// Uses the SSSOM2JSON JAR's --extract-obsolete-entities mode so the CURIE->IRI expansion is identical to
// the main pass (no risk of the two disagreeing on an IRI). One task for the whole obsolete corpus.
process EXTRACT_OBSOLETE_ENTITIES {
    input:
    path 'obsolete_inputs/*'
    path yml_files

    output:
    path "obsolete-entities.txt"

    script:
    """
    mkdir -p obsolete_inputs
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar" \
        --extract-obsolete-entities \
        -i obsolete_inputs \
        -o .

    # The JAR always writes the file, but guard against an aborted run so the output contract holds.
    [ -f obsolete-entities.txt ] || : > obsolete-entities.txt
    """
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
    tuple val(stem), path(tsv_file), val(category), val(obsolete)
    path yml_files
    path obsolete_entities

    output:
    path "mappingSet/${stem}.json", optional: true
    path "mapping/${stem}.json", optional: true

    script:
    // ADR-0041: --obsolete marks this set's subjects obsolete; -b supplies the global obsolete-entity set
    // so both endpoints of every mapping (here and in other files) are stamped against it.
    def obsoleteFlag = obsolete ? '--obsolete' : ''
    // ADR-0043: omitted entirely when the orchestrator supplied no timestamp, so the JAR leaves the
    // field unset rather than writing an empty string Solr's date field could not parse.
    def releaseDateFlag = params.release_date ? "--release-date \"${params.release_date}\"" : ''
    """
    java ${System.getenv('JAVA_OPTS') ?: ''} \
        -jar "${params.script_dir}/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar" \
        -f "${tsv_file}" \
        -c "${category}" \
        -b "${obsolete_entities}" \
        ${obsoleteFlag} \
        ${releaseDateFlag} \
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
