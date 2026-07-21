package uk.ac.ebi.spot.oxo.inferences.nemo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.ebi.spot.oxo.inferences.ApplicablePredicatesEnum;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

/**
 * Converts SSSOM-as-JSON mappings to N-Quads facts for Nemo (ADR-0010). Each applicable
 * mapping becomes a quad {@code <s> <p> <o> <urn:uuid:mapping_id> .} whose graph term carries
 * the mapping's {@code mapping_id}. Nemo imports N-Quads in graph-first order, so the
 * {@code mapping_id} is the first term of every fact — that is how the rulesets and the trace
 * walker recover the source-mapping provenance for cross-set inference.
 *
 * <p>Replaces the prior triple ({@code .ttl}) emitter: triples carried no provenance, so the
 * source mapping set of an asserted premise could not be recovered after chaining across sets.
 *
 * <p><b>Confidence gate (ADR-0037).</b> When {@code --minConfidence} is set above 0, a mapping whose
 * SSSOM {@code confidence} is <em>present and strictly below</em> the threshold is not emitted as a
 * quad, so it never seeds cross-set inference. The gate is deliberately narrow: a mapping with no
 * confidence value (absent, blank, or unparseable) always passes — only an explicit low confidence
 * drops an edge. Dropped edges are still indexed and served as asserted mappings (the Solr index is
 * built independently of the N-Quad corpus); this only removes them from the inference corpus. Every
 * drop is recorded in a sidecar {@code <output>.dropped-low-confidence.tsv} — never silent. A
 * threshold of 0 (the default) disables the gate entirely and reproduces the prior output byte for
 * byte.
 */
public class JSON2NQuads {

    private static final Logger logger = LoggerFactory.getLogger(JSON2NQuads.class);

    /** Suffix of the per-set sidecar listing edges the confidence gate kept out of the corpus. */
    private static final String DROPPED_REPORT_SUFFIX = ".dropped-low-confidence.tsv";

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("JSON2NQuads", options);
            System.exit(1);
            return;
        }

        String inputDirectory = cmd.getOptionValue("inputDir");
        String outputDir = cmd.getOptionValue("outputDir");
        String inputFile = cmd.getOptionValue("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");
        double minConfidence = parseMinConfidence(cmd.getOptionValue("minConfidence"));

        // Validate that either directory mode or file mode is used, but not both or neither
        boolean hasDirMode = (inputDirectory != null && outputDir != null);
        boolean hasFileMode = (inputFile != null && outputFile != null);

        if (!hasDirMode && !hasFileMode) {
            logger.error("Either inputDir/outputDir OR inputFile/outputFile must be provided");
            formatter.printHelp("JSON2NQuads", options);
            System.exit(1);
            return;
        }

        if (hasDirMode && hasFileMode) {
            logger.error("Cannot use both directory mode (inputDir/outputDir) and file mode (inputFile/outputFile) simultaneously");
            formatter.printHelp("JSON2NQuads", options);
            System.exit(1);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            if (minConfidence > 0) {
                logger.info("Confidence gate active: dropping inference edges with confidence < {} "
                        + "(mappings without a confidence value are unaffected).", minConfidence);
            }
            if (hasDirMode) {
                logger.info("Input Directory: {}", inputDirectory);
                logger.info("Output Directory: {}", outputDir);
                processMappings(inputDirectory, outputDir, minConfidence);
            } else {
                logger.info("Input File: {}", inputFile);
                logger.info("Output File: {}", outputFile);
                generateNQuadsFromJSON(Paths.get(inputFile), Paths.get(outputFile), minConfidence);
            }
        } catch (Exception e) {
            logger.error("Error processing mappings", e);
            System.exit(0);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Processing took {} s", (endTime - startTime) / 1000);
    }

    /**
     * Converts a single JSON file to an N-Quads file. Reads mappings from the JSON file and
     * writes one quad {@code <s> <p> <o> <urn:uuid:mapping_id> .} per applicable mapping.
     *
     * @param jsonFile      Path to the input JSON file
     * @param outputFile    Path to the output N-Quads file
     * @param minConfidence Confidence gate (ADR-0037): edges whose {@code confidence} is present and
     *                      strictly below this are dropped from the corpus and recorded in a sidecar.
     *                      {@code 0} disables the gate; a mapping with no confidence always passes.
     * @throws IOException if an I/O error occurs
     */
    // Package-private for JSON2NQuadsConfidenceGateTest.
    static void generateNQuadsFromJSON(Path jsonFile, Path outputFile, double minConfidence)
            throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        boolean gateActive = minConfidence > 0;
        // Each row: {subjectIRI, predicateIRI, objectIRI, confidence, mappingId} for the sidecar.
        List<String[]> droppedByConfidence = new ArrayList<>();

        logger.info("Processing file: {}", jsonFile);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
             JsonParser parser = objectMapper.getFactory().createParser(jsonFile.toFile())) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_ARRAY) {
                return;
            }
            long quadsWritten = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode mappingNode = objectMapper.readTree(parser);
                String predicateIRI = asTextOrEmpty(mappingNode, MappingEnum.PREDICATE_IRI.getField());
                if (!isApplicablePredicate(predicateIRI)) {
                    continue;
                }
                String subjectIRI = asTextOrEmpty(mappingNode, MappingEnum.SUBJECT_IRI.getField());
                String objectIRI = asTextOrEmpty(mappingNode, MappingEnum.OBJECT_IRI.getField());
                JsonNode mappingIdNode = mappingNode.get(MappingEnum.MAPPING_ID.getField());
                String mappingId = mappingIdNode == null ? null : mappingIdNode.asText();

                if (mappingId == null || mappingId.isBlank()) {
                    logger.warn("Skipping mapping with no mapping_id: <{}> <{}> <{}>", subjectIRI, predicateIRI, objectIRI);
                    continue;
                }

                if (!isSkipOnPredicateModifier(mappingNode) && areURIsValid(subjectIRI, predicateIRI, objectIRI)) {
                    // Confidence gate (ADR-0037). Applied only to inference-eligible edges (applicable
                    // predicate, no predicate modifier, valid IRIs) so the sidecar reflects edges that
                    // would otherwise have entered the corpus. A mapping without a parseable confidence
                    // is never dropped — the gate acts only on an explicit low value.
                    if (gateActive) {
                        double confidence = parseConfidence(mappingNode);
                        if (!java.lang.Double.isNaN(confidence) && confidence < minConfidence) {
                            droppedByConfidence.add(new String[] {
                                    subjectIRI, predicateIRI, objectIRI,
                                    java.lang.Double.toString(confidence), mappingId });
                            continue;
                        }
                    }
                    writer.write(String.format("<%s> <%s> <%s> <%s%s> .\n",
                            subjectIRI, predicateIRI, objectIRI, OXOInferenceConstants.URN_UUID_PREFIX, mappingId));
                    quadsWritten++;
                }
            }
            if (!droppedByConfidence.isEmpty()) {
                Path reportFile = droppedReportPath(outputFile);
                writeDroppedReport(reportFile, minConfidence, droppedByConfidence);
                logger.warn("Confidence gate (min={}) kept {} inference-eligible mapping(s) out of the "
                        + "corpus for {}; they remain asserted. Dropped edges listed in {}.",
                        minConfidence, droppedByConfidence.size(), jsonFile, reportFile);
            }
            if (quadsWritten == 0) {
                // Legitimate for sets whose mappings all use non-inference predicates (e.g. the
                // ebi-text-mappings sets are skos:closeMatch) or that lack a valid subject/object
                // IRI: they are still indexed as asserted mappings, they just do not enter the
                // N-Quad inference corpus. Logged (rather than silently dropped) so the absence
                // of a <set>.nq is explained.
                logger.warn("No N-Quads generated for {}: every mapping was skipped (non-applicable "
                        + "predicate, predicate modifier, or missing/invalid subject/predicate/object "
                        + "IRI). The set is still indexed as asserted but will not participate in "
                        + "inference.", jsonFile);
            }
        }
    }

    private static String asTextOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText();
    }

    /**
     * There is no indication from SSSOM that there is an intent to reason on for example negation.
     * Moreover, SSSOM gives no guidance as to the impact of negation on chain_rules. For this reason we exclude it from
     * all reasoning tasks.
     */
    private static boolean isSkipOnPredicateModifier(JsonNode jsonNode) {
        JsonNode predicateModifier = jsonNode.get(MappingEnum.PREDICATE_MODIFIER.getField());
        return predicateModifier != null;
    }

    private static void processMappings(String inputDirectory, String outputDirectory, double minConfidence)
            throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            List<Path> jsonFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path jsonFile : jsonFiles) {
                String outputFile = outputDirectory + File.separator
                        + jsonFile.getFileName().toString().replace(".json", ".nq");
                try {
                    generateNQuadsFromJSON(jsonFile, Paths.get(outputFile), minConfidence);
                } catch (Exception e) {
                    logger.error("Error processing file: {}", jsonFile, e);
                }
            }
        } catch (Throwable t) {
            logger.error("Error processing directory: {}", inputDirectory, t);
        }
    }

    /**
     * Reads a mapping's SSSOM {@code confidence} as a double, or {@link java.lang.Double#NaN} when it
     * is absent, blank, or not a number. NaN is the "no confidence" signal the gate treats as passing.
     */
    private static double parseConfidence(JsonNode mappingNode) {
        JsonNode confidenceNode = mappingNode.get(MappingEnum.CONFIDENCE.getField());
        if (confidenceNode == null || confidenceNode.isNull()) {
            return java.lang.Double.NaN;
        }
        String confidenceText = confidenceNode.asText();
        if (confidenceText == null || confidenceText.isBlank()) {
            return java.lang.Double.NaN;
        }
        try {
            return java.lang.Double.parseDouble(confidenceText);
        } catch (NumberFormatException e) {
            return java.lang.Double.NaN;
        }
    }

    /** Sidecar path for an output {@code <name>.nq}: {@code <name>.dropped-low-confidence.tsv}. */
    private static Path droppedReportPath(Path outputFile) {
        String outputName = outputFile.getFileName().toString();
        String baseName = outputName.endsWith(".nq")
                ? outputName.substring(0, outputName.length() - ".nq".length())
                : outputName;
        String reportName = baseName + DROPPED_REPORT_SUFFIX;
        Path parent = outputFile.getParent();
        return parent == null ? Paths.get(reportName) : parent.resolve(reportName);
    }

    /** Writes the confidence-gate drop report (ADR-0037) — one dropped edge per row, TSV. */
    private static void writeDroppedReport(Path reportFile, double minConfidence, List<String[]> droppedRows)
            throws IOException {
        try (BufferedWriter reportWriter = Files.newBufferedWriter(reportFile)) {
            reportWriter.write("# Mappings kept out of the inference corpus by the confidence gate "
                    + "(min_inference_confidence=" + minConfidence + ", ADR-0037). They are still "
                    + "indexed and served as asserted mappings.\n");
            reportWriter.write("subject_iri\tpredicate_iri\tobject_iri\tconfidence\tmapping_id\n");
            for (String[] row : droppedRows) {
                reportWriter.write(String.join("\t", row));
                reportWriter.write("\n");
            }
        }
    }

    /**
     * Parses the {@code --minConfidence} option. Absent → 0 (gate disabled). A negative value is
     * clamped to 0 with a warning. A non-numeric value is a hard configuration error (exit 1) rather
     * than a silent disable, so a mistyped threshold never quietly lets low-confidence edges through.
     */
    private static double parseMinConfidence(String optionValue) {
        if (optionValue == null || optionValue.isBlank()) {
            return 0.0;
        }
        double threshold;
        try {
            threshold = java.lang.Double.parseDouble(optionValue.trim());
        } catch (NumberFormatException e) {
            logger.error("Invalid --minConfidence value '{}': must be a number (e.g. 0.5).", optionValue);
            System.exit(1);
            return 0.0;
        }
        if (threshold < 0) {
            logger.warn("Negative --minConfidence '{}' clamped to 0 (gate disabled).", optionValue);
            return 0.0;
        }
        if (threshold > 1) {
            logger.warn("--minConfidence '{}' exceeds 1.0; every mapping that reports a confidence "
                    + "will be dropped from inference.", optionValue);
        }
        return threshold;
    }

    private static boolean areURIsValid(String subjectIRI, String predicateIRI, String objectIRI) {
        return StringUtils.isURIValid(subjectIRI) && StringUtils.isURIValid(predicateIRI)
                && StringUtils.isURIValid(objectIRI);
    }

    private static boolean isApplicablePredicate(String predicateIRI) {
        for (ApplicablePredicatesEnum predicate : ApplicablePredicatesEnum.values()) {
            if (predicate.getIri().equals(predicateIRI)) {
                return true;
            }
        }
        return false;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputDirectory = new Option("i", "inputDir", true, "Input directory containing JSON files");
        inputDirectory.setRequired(false);
        options.addOption(inputDirectory);

        Option outputDirectory = new Option("o", "outputDir", true, "Output directory for N-Quads files");
        outputDirectory.setRequired(false);
        options.addOption(outputDirectory);

        Option inputFile = new Option("f", "inputFile", true, "Input JSON file");
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option outputFile = new Option("p", "outputFile", true, "Output N-Quads file");
        outputFile.setRequired(false);
        options.addOption(outputFile);

        Option minConfidence = new Option("c", "minConfidence", true,
                "Confidence gate (ADR-0037): drop edges whose confidence is present and below this "
                        + "value from the inference corpus. 0 (default) disables the gate; mappings "
                        + "without a confidence value always pass.");
        minConfidence.setRequired(false);
        options.addOption(minConfidence);

        return options;
    }
}
