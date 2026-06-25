package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.cli.*;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.dataload.solr.DataloadSolr;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Build BARE inferred-mapping JSON from the single cross-set inferred-mappings TTL
 * (INFER_CROSS_SET's {@code inferences.ttl}), with no explanations (ADR-0020).
 *
 * <p>The TTL is already exactly the set of inferred mappings — {@code sssom.rls} exports
 * {@code inferredMapping(?s,?p,?o) :- mapping(?id,?s,?p,?o), ~assertedTriple(?s,?p,?o)}, so asserted
 * echoes are already excluded and no chain is needed for the <em>what</em>. Each triple becomes one
 * inferred mapping doc carrying its subject/predicate/object + CURIE/label + {@code inference_type};
 * the explanation chain, {@code distance}, {@code explanation_length}, asserted evidence, and the
 * inferred set's source union are all dropped (deferred to an on-demand explanation service).
 *
 * <p>Two streaming passes over the TTL (constant memory regardless of TTL size, like
 * {@link Inferences2Trace}): pass 1 collects the distinct subject/object IRIs and seeds the
 * {@link DataloadSolr} entity cache from the asserted index; pass 2 builds and streams one bare
 * {@link Mapping} per triple, sharing entity-resolution with the explanation path via
 * {@link ExplainInferredMappings#baseInferredMappingBuilder}.
 */
public class BareInferredMappings {

    private static final Logger logger = LoggerFactory.getLogger(BareInferredMappings.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("BareInferredMappings", options);
            System.exit(1);
            return;
        }

        InferenceType inferenceType;
        try {
            inferenceType = InferenceType.fromCode(cmd.getOptionValue("inferenceType"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid --inferenceType: {}", cmd.getOptionValue("inferenceType"), e);
            formatter.printHelp("BareInferredMappings", options);
            System.exit(1);
            return;
        }
        if (inferenceType == null || inferenceType == InferenceType.ASSERTED) {
            logger.error("--inferenceType is required and must be SSSOM_INFERENCE.");
            formatter.printHelp("BareInferredMappings", options);
            System.exit(1);
            return;
        }

        String inferencesTtl = cmd.getOptionValue("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");
        String mappingSetOutputFile = cmd.getOptionValue("mappingSetOutputFile");
        if (inferencesTtl == null || outputFile == null || mappingSetOutputFile == null) {
            logger.error("Provide -i <inferences.ttl> -f <outputFile> -m <mappingSetOutputFile> -t <inferenceType>.");
            formatter.printHelp("BareInferredMappings", options);
            System.exit(1);
            return;
        }

        File inputFile = new File(inferencesTtl);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Inferred-mappings TTL does not exist or is not a file: {}", inferencesTtl);
            System.exit(1);
            return;
        }

        long startTime = System.currentTimeMillis();
        String inferredMappingSetId = OXOInferenceConstants.CROSS_SET_INFERENCES_SET_ID;
        DataloadSolr solrClient = null;
        try {
            solrClient = new DataloadSolr();

            // Pass 1 — collect the distinct subject/object IRIs and seed the entity cache from the
            // asserted Solr index, so pass 2's queryEntityDetailsForIRI lookups are cache hits.
            logger.info("Collecting inferred endpoint IRIs from {}", inferencesTtl);
            IriCollector iriCollector = new IriCollector();
            RDFParser.source(inferencesTtl).parse(iriCollector);
            logger.info("Collected {} distinct endpoint IRIs from {} inferred triple(s)",
                    iriCollector.iris.size(), iriCollector.tripleCount);
            solrClient.prefetchEntityDetailsByIris(iriCollector.iris);

            // Pass 2 — build and stream one bare inferred mapping per triple.
            File outFile = new File(outputFile);
            ObjectMapper objectMapper = newObjectMapper();
            MappingWriter mappingWriter = new MappingWriter(
                    solrClient, inferredMappingSetId, inferenceType, objectMapper, outFile);
            logger.info("Creating bare inferred mappings and streaming to {}", outputFile);
            RDFParser.source(inferencesTtl).parse(mappingWriter);
            mappingWriter.closeAndCheck();
            logger.info("Wrote {} bare inferred mapping(s)", mappingWriter.written);

            if (mappingWriter.written > 0) {
                writeInferredMappingSet(inferredMappingSetId, inferenceType, mappingSetOutputFile, objectMapper);
            } else {
                logger.warn("No inferred mappings written; skipping MappingSet emission for {}",
                        inferredMappingSetId);
            }

            solrClient.close();
        } catch (Exception e) {
            if (solrClient != null) {
                try {
                    solrClient.close();
                } catch (Throwable t) {
                    logger.error("Error closing Solr", t);
                }
            }
            logger.error("Error building bare inferred mappings from {}", inferencesTtl, e);
            System.exit(1);
        }

        logger.info("Bare inferred-mapping build took {} s", (System.currentTimeMillis() - startTime) / 1000);
    }

    /** StreamRDF callback collecting distinct subject/object IRIs, skipping self-mappings. */
    private static final class IriCollector extends StreamRDFBase {
        private final Set<String> iris = new HashSet<>();
        private long tripleCount = 0;

        @Override
        public void triple(Triple triple) {
            Node subject = triple.getSubject();
            Node object = triple.getObject();
            if (!subject.isURI() || !object.isURI()) {
                return;
            }
            String subjectIri = subject.getURI();
            String objectIri = object.getURI();
            if (subjectIri.equals(objectIri)) {
                return;
            }
            iris.add(subjectIri);
            iris.add(objectIri);
            tripleCount++;
        }
    }

    /**
     * StreamRDF callback building one bare {@link Mapping} per inferred triple (skipping
     * self-mappings) and streaming it to a Jackson {@link SequenceWriter}. The output file is opened
     * lazily on the first emitted mapping, so a TTL that yields nothing leaves no file (matching the
     * downstream {@code [ ! -s file ]} cleanup in inferences2json.nf).
     */
    private static final class MappingWriter extends StreamRDFBase {
        private final DataloadSolr solrClient;
        private final String inferredMappingSetId;
        private final InferenceType inferenceType;
        private final ObjectMapper objectMapper;
        private final File outputFile;
        private SequenceWriter sequenceWriter;
        private long written = 0;
        private RuntimeException error = null;

        MappingWriter(DataloadSolr solrClient, String inferredMappingSetId, InferenceType inferenceType,
                      ObjectMapper objectMapper, File outputFile) {
            this.solrClient = solrClient;
            this.inferredMappingSetId = inferredMappingSetId;
            this.inferenceType = inferenceType;
            this.objectMapper = objectMapper;
            this.outputFile = outputFile;
        }

        @Override
        public void triple(Triple triple) {
            if (error != null) {
                return;
            }
            Node subject = triple.getSubject();
            Node predicate = triple.getPredicate();
            Node object = triple.getObject();
            if (!subject.isURI() || !predicate.isURI() || !object.isURI()) {
                return;
            }
            String subjectIri = subject.getURI();
            String objectIri = object.getURI();
            if (subjectIri.equals(objectIri)) {
                return;
            }
            try {
                Mapping mapping = ExplainInferredMappings.baseInferredMappingBuilder(
                        solrClient, subjectIri, predicate.getURI(), objectIri,
                        inferredMappingSetId, inferenceType).build();
                if (sequenceWriter == null) {
                    sequenceWriter = objectMapper.writer().writeValuesAsArray(outputFile);
                }
                sequenceWriter.write(mapping);
                written++;
                if (written % 1000 == 0) {
                    logger.info("Wrote {} bare inferred mappings", written);
                }
            } catch (IOException e) {
                error = new RuntimeException("Failed writing bare inferred mapping", e);
            }
        }

        void closeAndCheck() {
            if (error != null) {
                throw error;
            }
            if (sequenceWriter != null) {
                try {
                    sequenceWriter.close();
                } catch (IOException e) {
                    throw new RuntimeException("Failed closing bare inferred mappings output", e);
                }
            }
        }
    }

    /**
     * Write the single cross-set inferred {@link MappingSet} with an EMPTY source union — the
     * per-set source attribution needs the trace, which is no longer run (ADR-0020).
     */
    private static void writeInferredMappingSet(String inferredMappingSetId, InferenceType inferenceType,
                                                String outputFilePath, ObjectMapper objectMapper) throws IOException {
        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId(inferredMappingSetId)
                .mappingSetTitle("OxO2 SSSOM cross-set inferences")
                .mappingSetDescription("Inferred mappings derived via SEMAPV:MappingChaining across all "
                        + "mapping sets (SSSOM reasoning). Explanations are computed on demand.")
                .mappingSetSource(new TreeSet<Uri>())
                .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
                .inferenceType(inferenceType)
                .build();

        File file = new File(outputFilePath);
        objectMapper.writeValue(file, List.of(mappingSet));
        logger.info("Inferred MappingSet written to {} ({} bytes)", outputFilePath, file.length());
    }

    private static ObjectMapper newObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFile = new Option("i", "inputFile", true,
                "Inferred-mappings TTL (INFER_CROSS_SET's inferences.ttl).");
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option outputFile = new Option("f", "outputFile", true,
                "Output file for the bare inferred-mapping JSON array.");
        outputFile.setRequired(false);
        options.addOption(outputFile);

        Option mappingSetOutputFile = new Option("m", "mappingSetOutputFile", true,
                "Output file for the inferred MappingSet JSON metadata.");
        mappingSetOutputFile.setRequired(false);
        options.addOption(mappingSetOutputFile);

        Option inferenceType = new Option("t", "inferenceType", true,
                "Inference type stamped on the inferred mappings/set: SSSOM_INFERENCE. Required.");
        inferenceType.setRequired(false);
        options.addOption(inferenceType);

        return options;
    }
}
