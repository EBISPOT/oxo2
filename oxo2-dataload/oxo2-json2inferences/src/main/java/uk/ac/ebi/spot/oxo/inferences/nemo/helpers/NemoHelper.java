package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.dataload.solr.DataloadSolr;
import uk.ac.ebi.spot.oxo.dataload.solr.EntityDetails;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.*;
import uk.ac.ebi.spot.oxo.model.sssom.*;

import java.util.*;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    public static Set<InferredMapping> fromNemoInferencesToInferredMappings(
            NemoInferences nemoInferences, DataloadSolr solrClient) {

        Set<InferredMapping> inferredMappings = new HashSet<>();

        nemoInferences.getFinalConclusion().forEach(finalConclusion -> {
            InferredMapping inferredMapping = determineInferencesLeadingToConclusion(nemoInferences,
                    finalConclusion, solrClient);
            inferredMappings.add(inferredMapping);
        });

        return inferredMappings;
    }

    private static InferredMapping determineInferencesLeadingToConclusion(
            NemoInferences nemoInferences,
            String conclusion, DataloadSolr solrClient) {

        InferredMapping inferredMapping = createInferredMapping(conclusion, solrClient);

        Optional<NemoInferences.NemoInference> optionalNemoInference =
                nemoInferences.findNemoInferenceForConclusion(conclusion);
        if (optionalNemoInference.isPresent()) {
            NemoInferences.NemoInference nemoInference = optionalNemoInference.get();
            Optional<NemoChainRulesEnum> nemoChainRulesEnum = NemoChainRulesEnum.getChainRuleFromNemoRuleName(
                    nemoInference.getRule());
            Optional<ChainRulesEnum> chainRulesEnum = nemoChainRulesEnum.map(
                    nemoRule -> ChainRulesEnum.valueOf(nemoRule.name()));

            if (chainRulesEnum.isEmpty()) {
                InferredMapping.ChainRuleApplications chainRuleApplication =
                        new InferredMapping.ChainRuleApplications(Optional.of(ChainRulesEnum.ASSERTED));
                chainRuleApplication.setPremises(new ArrayList<>());
                inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
                return inferredMapping;
            }

            InferredMapping.ChainRuleApplications chainRuleApplication =
                    new InferredMapping.ChainRuleApplications(chainRulesEnum);

            inferredMapping.setMappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL);
            inferredMapping.setMappingJustification(new EntityReference(
                    OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION));
            inferredMapping.setMappingSetId(OXOInferenceConstants.OXO_MAPPING_SET_ID);

            List<InferredMapping> premises = new ArrayList<>();

            nemoInference.getPremises().forEach(premise -> {
                InferredMapping premiseAsInferredMapping = determineInferencesLeadingToConclusion(
                        nemoInferences, premise, solrClient
                );
                premises.add(premiseAsInferredMapping);
            });

            chainRuleApplication.setPremises(premises);
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
        }
        return inferredMapping;
    }

    private static boolean isValidMappingString(String mappingString) {
        if (mappingString == null || mappingString.isEmpty()) {
            return false;
        }
        String mappingRegex = "^mapping\\(\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*\\)$";
        boolean result = mappingString.matches(mappingRegex);
        logger.trace("Result of isValidMappingString for {} is {} ", mappingString, result);
        return result;
    }


    private static InferredMapping createInferredMapping(
            String mapping, DataloadSolr solrClient) {

        InferredMapping inferredMapping = new InferredMapping();
        if (!isValidMappingString(mapping)) {
            throw new IllegalArgumentException("Invalid mapping string: " + mapping);
        }

        String[] parts = mapping.substring(mapping.indexOf('<') + 1, mapping.lastIndexOf('>')).split(">\\s*,\\s*<");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Conclusion string must contain exactly three URIs: " + mapping);
        }

        String subjectIRI = parts[0];
        String predicateIRI = parts[1];
        String objectIRI = parts[2];
        inferredMapping.setSubjectIRI(new Uri(subjectIRI));
        inferredMapping.setPredicateIRI(new Uri(predicateIRI));
        inferredMapping.setObjectIRI(new Uri(objectIRI));

        List<Mapping> assertedMappingsList =
                solrClient.querySubjectPredicateObjectIRI(subjectIRI, predicateIRI, objectIRI);

        if (assertedMappingsList != null && assertedMappingsList.size() > 0) {
            Mapping firstAssertedMapping = assertedMappingsList.get(0);
            inferredMapping = inferredMapping.populateFromMapping(firstAssertedMapping);
            InferredMapping.ChainRuleApplications chainRuleApplications = new InferredMapping.ChainRuleApplications(
                    Optional.of(ChainRulesEnum.ASSERTED));
            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplications));
        }

        inferredMapping = updateSubject(inferredMapping, solrClient);
        inferredMapping = updatePredicate(inferredMapping, solrClient);
        inferredMapping = updateObject(inferredMapping, solrClient);

        return inferredMapping;
    }


    private static InferredMapping updateSubject(InferredMapping inferredMapping,
                                          Map<String, EntityDetails> iriToEntityDetails) {

        EntityDetails details =  iriToEntityDetails.get(inferredMapping.getSubjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setSubjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateSubject(InferredMapping inferredMapping, DataloadSolr solrClient) {

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                SUBJECT_IRI,
                inferredMapping.getSubjectIRI().asStringIRI(),
                SUBJECT_ID,
                SUBJECT_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setSubjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updatePredicate(InferredMapping inferredMapping,
                                          DataloadSolr solrClient) {

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                PREDICATE_IRI,
                inferredMapping.getPredicateIRI().asStringIRI(),
                PREDICATE_ID,
                PREDICATE_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setPredicateId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setPredicateLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateObject(InferredMapping inferredMapping,
                                                DataloadSolr solrClient) {

        EntityDetails details = solrClient.queryEntityDetailsForIRI(
                OBJECT_IRI,
                inferredMapping.getObjectIRI().asStringIRI(),
                OBJECT_ID,
                OBJECT_LABEL);
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setObjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setObjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

}

