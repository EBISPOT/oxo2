#!/usr/bin/env nextflow
// SSSOM reasoning applied ACROSS ALL MAPPING SETS (ADR-0016). This is OxO2's only inference pass:
// convert each set's JSON to N-Quads, concatenate every set's N-Quads into one corpus, infer with
// sssom.rls, trace, and produce a SINGLE inference-chains file. The mapping_id graph term carried by
// each fact (ADR-0010) lets the explanation step recover which source set each asserted premise came
// from.
//
// Process names keep the …_CROSS_SET suffix: they reason over the concatenated all-sets corpus
// rather than a single set, and select their own resource tiers by name in nextflow.config.

params.json_input_dir = "${System.getenv('OXO2_DATA')}/sssom-as-json/mapping"
params.asserted_mappings_dir = "${System.getenv('OXO2_DATA')}/assertedMappings"
params.cross_set_dir = "${System.getenv('OXO2_DATA')}/inferences/crossSet"
params.inference_chains_dir = "${System.getenv('OXO2_DATA')}/inferences/inferenceChainsCrossSet"

params.script_dir = params.script_dir ?: "${projectDir}"
// Reason with the SSSOM ruleset (strong-predicate transitivity + role chains, across all sets).
params.rules_definition = file("${params.script_dir}/oxo2-json2inferences/sssom.rls")

// The single logical inferred set; drives the inferred-set output names (inferences.ttl,
// inferences-chains.json, and downstream inferences-explained.json / inferences-mappingSet.json).
// This is the reasoning OUTPUT, not the corpus it is derived from.
params.inferred_set_basename = "inferences"

// The concatenated asserted-mappings corpus (every set's N-Quads), the reasoning INPUT. Named
// distinctly from the inferred set because it holds asserted facts, not inferences. Referenced by
// path (not channel) by the explain step.
params.asserted_corpus_basename = "assertedCorpus"
params.corpus_file = "${params.cross_set_dir}/${params.asserted_corpus_basename}.nq"

// The inferred mappings selected for tracing (nmo --trace-input-file). An intermediate selection
// list split into per-chunk files, not the inferred set itself.
params.inferences_to_trace_basename = "inferencesToTrace"

// Mappings per tracing chunk. Smaller = more parallelism / more per-chunk overhead.
params.trace_chunk_size = 20000

workflow {
    json_files = channel.fromPath("${params.json_input_dir}/*.json")
        .map { f -> FilenameGuard.assertSafe(f.name); f }

    nquads_files = JSON2NQUADS(json_files)
        .filter { nquads -> nquads.size() > 0 }
        .collect()

    corpus = CONCAT_CORPUS(nquads_files)
    inferred = INFER_CROSS_SET(corpus)
    trace = DETERMINE_CROSS_SET_TRACE(inferred)
    chunks = SPLIT_CROSS_SET_TRACE(trace).flatten()
    chunk_chains = EXPLAIN_CROSS_SET_CHUNK(chunks)
    MERGE_CROSS_SET_CHAIN(chunk_chains.collect())
}


process JSON2NQUADS {
    tag "JSON2NQuads: ${json_file.name}"

    publishDir "${params.asserted_mappings_dir}", mode: 'copy', overwrite: true

    input:
    path json_file

    output:
    path "${json_file.baseName}.nq", optional: true

    script:
    def output_file = "${json_file.baseName}.nq"
    """
    "${params.script_dir}/oxo2-json2inferences/json2nquadsNextflow.sh" "${json_file}" "${output_file}"
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


process DETERMINE_CROSS_SET_TRACE {
    tag "Inferences to trace (cross-set)"

    publishDir "${params.cross_set_dir}", mode: 'copy', overwrite: true

    input:
    path inferred_ttl

    output:
    path "${params.inferences_to_trace_basename}.txt", optional: true

    script:
    def output_file = "${params.inferences_to_trace_basename}.txt"
    """
    "${params.script_dir}/oxo2-json2inferences/inferences2trace.sh" "${inferred_ttl}" "${output_file}"

    # Remove if empty
    if [ ! -s "${output_file}" ]; then
        rm -f "${output_file}"
    fi
    """
}


process SPLIT_CROSS_SET_TRACE {
    tag "Split trace input (cross-set)"

    input:
    path inferences_to_trace_file

    output:
    path "${params.inferences_to_trace_basename}-chunk*.txt", optional: true

    script:
    """
    "${params.script_dir}/oxo2-json2inferences/splitInferencesToTrace.sh" \
        "${inferences_to_trace_file}" ${params.trace_chunk_size} "${params.inferences_to_trace_basename}"
    """
}


process EXPLAIN_CROSS_SET_CHUNK {
    tag "Explain chunk (cross-set): ${chunk_file.baseName}"

    input:
    path chunk_file

    output:
    path "${chunk_file.baseName}-chains.json", optional: true

    script:
    def output_file = "${chunk_file.baseName}-chains.json"
    """
    echo "[EXPLAIN_CROSS_SET_CHUNK] chunk=${chunk_file.name} trace_input_bytes=${chunk_file.size()} allocated_memory=${task.memory}"
    "${params.script_dir}/oxo2-json2inferences/nemoExplainMappingsNextflow.sh" "${params.rules_definition}" \
        "${params.corpus_file}" "${params.inferred_set_basename}-reexport.ttl" "./" "${chunk_file}" "${output_file}"
    """
}


process MERGE_CROSS_SET_CHAIN {
    tag "Merge chain chunks (cross-set)"

    publishDir "${params.inference_chains_dir}", mode: 'move', overwrite: true

    input:
    path chunk_chains_files

    output:
    path "${params.inferred_set_basename}-chains.json", optional: true

    script:
    def output_file = "${params.inferred_set_basename}-chains.json"
    def heap_mb = (task.memory.toMega() * 0.8) as long
    """
    export JAVA_OPTS="-Xmx${heap_mb}m \${JAVA_OPTS:-}"
    "${params.script_dir}/oxo2-json2inferences/mergeChainFiles.sh" \
        "${output_file}" ${chunk_chains_files}
    """
}
