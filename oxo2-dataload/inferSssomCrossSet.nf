#!/usr/bin/env nextflow
// SSSOM reasoning applied ACROSS ALL MAPPING SETS (ADR-0016). This is OxO2's only inference pass:
// convert each set's JSON to N-Quads, concatenate every set's N-Quads into one corpus, and infer
// with sssom.rls, producing a SINGLE inferred-mappings TTL (inferences.ttl). The mapping_id graph
// term carried by each fact (ADR-0010) is what a later on-demand explanation would use to recover
// premises; this pipeline no longer traces or explains (ADR-0020) — explanations are deferred to an
// on-demand service, and the inferred mappings are indexed bare from inferences.ttl.
//
// Process names keep the …_CROSS_SET suffix: they reason over the concatenated all-sets corpus
// rather than a single set, and select their own resource tiers by name in nextflow.config.

params.json_input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.cross_set_dir = "${System.getenv('OXO2_DATA')}/inferences/crossSet"

params.script_dir = params.script_dir ?: "${projectDir}"
// Reason with the SSSOM ruleset (strong-predicate transitivity + role chains, across all sets).
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/sssom.rls")

// Confidence gate (ADR-0037). A mapping whose SSSOM `confidence` is present and strictly below this
// threshold is kept out of the N-Quad inference corpus (it is still indexed/served as asserted).
// Mappings without a confidence value are unaffected. The threshold is the global
// `min_inference_confidence` key in the OxO config ($OXO2_CONFIG); absent -> 0, which disables the gate
// and makes the corpus byte-identical to a run without it.
params.config_file = "${System.getenv('OXO2_CONFIG') ?: ''}"
params.min_inference_confidence = InferenceConfidenceThreshold.fromConfigPath(params.config_file)

// The single logical inferred set; drives the inferred-set output name (inferences.ttl). This is
// the reasoning OUTPUT, not the corpus it is derived from.
params.inferred_set_basename = "inferences"

// The concatenated asserted-mappings corpus (every set's N-Quads), the reasoning INPUT. Named
// distinctly from the inferred set because it holds asserted facts, not inferences. Referenced by
// path (not channel) for the from_infer resume entry; also the corpus a future on-demand
// explanation service reasons over (ADR-0020).
params.asserted_corpus_basename = "assertedCorpus"
params.corpus_file = "${params.cross_set_dir}/${params.asserted_corpus_basename}.nq"

// Default entry point: the full pipeline from each set's JSON. Unchanged behaviour — this is what
// loadData.nextflow (local/integration) and a from-scratch HPC run (loadData.slurm START_STAGE in
// download|sssom2json|nquads) invoke.
workflow {
    json_files = channel.fromPath("${params.json_input_dir}/*.json")
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    if (params.min_inference_confidence.toDouble() > 0) {
        log.info "Confidence gate active (ADR-0037): dropping inference edges with confidence < " +
                 "${params.min_inference_confidence} (min_inference_confidence from ${params.config_file})."
    }

    json2nquads_out = JSON2NQUADS(json_files)
    nquads_files = json2nquads_out.nquads
        .filter { nquads -> nquads.size() > 0 }
        .collect()

    corpus = CONCAT_CORPUS(nquads_files)
    INFER_CROSS_SET(corpus)
}

// ---------------------------------------------------------------------------
// Resume entry point. Re-enters the DAG at INFER, reading the previously PUBLISHED
// assertedCorpus.nq under $OXO2_DATA (not Nextflow's transient work dir) — so a resumed run is
// independent of any cached NXF_WORK and of the container digest. loadData.slurm selects it via
// `-entry from_infer` from START_STAGE=infer. See oxo2-dataload/CONTEXT.md § Resumable dataload.
// ---------------------------------------------------------------------------

// Resume from the concatenated all-sets corpus (JSON2NQUADS + CONCAT_CORPUS already done).
workflow from_infer {
    corpus = channel.fromPath(params.corpus_file, checkIfExists: true)
    INFER_CROSS_SET(corpus)
}


process JSON2NQUADS {
    tag "JSON2NQuads: ${json_file.name}"

    publishDir "${params.asserted_mappings_dir}", mode: 'copy', overwrite: true

    input:
    path json_file

    output:
    path "${json_file.baseName}.nq", optional: true, emit: nquads
    // Confidence-gate drop report (ADR-0037), only emitted when the gate drops at least one edge.
    path "${json_file.baseName}.dropped-low-confidence.tsv", optional: true, emit: dropped

    script:
    def output_file = "${json_file.baseName}.nq"
    """
    "${params.script_dir}/oxo2-json2inferences/json2nquadsNextflow.sh" "${json_file}" "${output_file}" "${params.min_inference_confidence}"
    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process CONCAT_CORPUS {
    tag "Concatenate cross-set corpus"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path nquads_files

    output:
    path "${params.asserted_corpus_basename}.nq", optional: true

    script:
    """
    cat ${nquads_files} > "${params.asserted_corpus_basename}.nq"
    if [ ! -s "${params.asserted_corpus_basename}.nq" ]; then
        rm -f "${params.asserted_corpus_basename}.nq"
    fi
    """
}


process INFER_CROSS_SET {
    tag "Infer mappings (SSSOM, cross-set)"

    cpus 1

    publishDir "${params.cross_set_dir}", pattern: "inferred/*.ttl", mode: 'copy', overwrite: true, saveAs: { filename -> file(filename).name }

    input:
    path corpus

    output:
    path "inferred/${params.inferred_set_basename}.ttl", optional: true

    script:
    """
    mkdir -p inferred
    "${params.script_dir}/oxo2-json2inferences/nemoInferMappingsNextflow.sh" "${params.rules_definition}" \
        "${corpus}" "${params.inferred_set_basename}.ttl" inferred/

    # Remove if empty
    if [ ! -s "inferred/${params.inferred_set_basename}.ttl" ]; then
        rm -f "inferred/${params.inferred_set_basename}.ttl"
    fi
    """
}
