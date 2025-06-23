package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.ExplainInferredMappings;
import uk.ac.ebi.spot.oxo.model.sssom.*;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoChainRulesEnum;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.util.*;

public class NemoHelper {
    private static final Logger logger = LoggerFactory.getLogger(NemoHelper.class);

    /**
     * @Todo: The same conclusion can appear multiple times. Currently we will loose different explanations for the same
     * conclusion. See: https://github.com/knowsys/nemo/issues/677.
     *
     * @param nemoInferences
     * @return
     */
    public static Set<InferredMapping> fromNemoInferencesToInferredMappings(
            NemoInferences nemoInferences,
            Map<ExplainInferredMappings.MinimalMapping, List<Mapping>> assertedMappings,
            Map<String, ExplainInferredMappings.EntityDetails> iriToEntityDetails) {

        long timeBegin = System.currentTimeMillis();
        Set<InferredMapping> inferredMappings = new HashSet<>();
        Set<NemoInferences.NemoInference> conclusionsWithPremisesAndRules = nemoInferences.getInferencesSet();
        Map<String, InferredMapping> premiseToInferredMapping = new HashMap<>();
        Map<String, InferredMapping> conclusionToInferredMapping = new HashMap<>();
        for (NemoInferences.NemoInference nemoInference  : conclusionsWithPremisesAndRules) {
            InferredMapping inferredMapping;
            if (premiseToInferredMapping.containsKey(nemoInference.getConclusion())) {
                inferredMapping = premiseToInferredMapping.get(nemoInference.getConclusion());
            } else {
                inferredMapping = createInferredMapping(nemoInference.getConclusion(), assertedMappings, iriToEntityDetails);
            }
            if (inferredMapping.isMappingToSelf()) {
                continue;
            }

            Optional<NemoChainRulesEnum> nemoChainRulesEnum =
                    NemoChainRulesEnum.getChainRuleFromNemoRuleName(nemoInference.getRule());
            Optional<ChainRulesEnum> chainRulesEnum = nemoChainRulesEnum.map(
                    nemoRule -> ChainRulesEnum.valueOf(nemoRule.name()));

            InferredMapping.ChainRuleApplications chainRuleApplication =
                    new InferredMapping.ChainRuleApplications(chainRulesEnum);

            List<InferredMapping> premises = new ArrayList<>();
            for (String premise : nemoInference.getPremises()) {
                InferredMapping inferredMappingForPremise;
                if (conclusionToInferredMapping.containsKey(premise)) {
                    inferredMappingForPremise = conclusionToInferredMapping.get(premise);
                } else {
                    inferredMappingForPremise = createInferredMapping(premise, assertedMappings, iriToEntityDetails);
                }

                premises.add(inferredMappingForPremise);
                if (premiseToInferredMapping.containsKey(premise)) {
                    InferredMapping existingPremise = premiseToInferredMapping.get(premise);
                    if (!existingPremise.equals(inferredMappingForPremise)) {
                    logger.error("Premise {} already exists in premiseToInferredMapping. Existing related inferred mapping is {}" +
                            " and the new one is {}. ", premise, existingPremise, inferredMappingForPremise);
                    }
                }
                premiseToInferredMapping.put(premise, inferredMappingForPremise);
            }
            chainRuleApplication.setPremises(premises);

            inferredMapping.setChainRuleApplications(Optional.of(chainRuleApplication));
            conclusionToInferredMapping.put(nemoInference.getConclusion(), inferredMapping);
            inferredMappings.add(inferredMapping);
        }

        long timeEnd = System.currentTimeMillis();
        logger.info("Time taken: {} s", (timeEnd - timeBegin)/1000);
        return inferredMappings;
    }

    private static boolean isValidMappingString(String mappingString) {
        if (mappingString == null || mappingString.isEmpty()) {
            return false;
        }
        String mappingRegex = "^mapping\\(\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*\\)$";
        String assertedMapping = "^assertedMapping\\(\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*,\\s*<[^>]+>\\s*\\)$";
        boolean result = mappingString.matches(mappingRegex) || mappingString.matches(assertedMapping);
        logger.trace("Result of isValidMappingString for {} is {} ", mappingString, result);
        return result;
    }

    private static InferredMapping createInferredMapping(
            String mapping,
            Map<ExplainInferredMappings.MinimalMapping, List<Mapping>> assertedMappings,
            Map<String, ExplainInferredMappings.EntityDetails> iriToEntityDetails) {

        InferredMapping inferredMapping = new InferredMapping();
        if (!isValidMappingString(mapping)) {
            throw new IllegalArgumentException("Invalid mapping string: " + mapping);
        }

        String[] parts = mapping.substring(mapping.indexOf('<') + 1, mapping.lastIndexOf('>')).split(">\\s*,\\s*<");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Conclusion string must contain exactly three URIs: " + mapping);
        }

        inferredMapping.setSubjectIRI(new Uri(parts[0]));
        inferredMapping.setPredicateIRI(new Uri(parts[1]));
        inferredMapping.setObjectIRI(new Uri(parts[2]));

        ExplainInferredMappings.MinimalMapping minimalMapping = new ExplainInferredMappings.MinimalMapping(
                parts[0], parts[1], parts[2]);
        List<Mapping> assertedMappingsList = assertedMappings.get(minimalMapping);

        if (assertedMappingsList != null) {
            Mapping firstAssertedMapping = assertedMappingsList.get(0);
            inferredMapping = inferredMapping.populateFromMapping(firstAssertedMapping);
        }

        inferredMapping = updateSubject(inferredMapping, iriToEntityDetails);
        inferredMapping = updatePredicate(inferredMapping, iriToEntityDetails);
        inferredMapping = updateObject(inferredMapping, iriToEntityDetails);

        return inferredMapping;
    }

    private static InferredMapping updateSubject(InferredMapping inferredMapping,
                                          Map<String, ExplainInferredMappings.EntityDetails> iriToEntityDetails) {

        ExplainInferredMappings.EntityDetails details =  iriToEntityDetails.get(inferredMapping.getSubjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setSubjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setSubjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updatePredicate(InferredMapping inferredMapping,
                                          Map<String, ExplainInferredMappings.EntityDetails> iriToEntityDetails) {

        ExplainInferredMappings.EntityDetails details =  iriToEntityDetails.get(inferredMapping.getPredicateIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setPredicateId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setPredicateLabel(details.getLabel());
        }

        return inferredMapping;
    }

    private static InferredMapping updateObject(InferredMapping inferredMapping,
                                         Map<String, ExplainInferredMappings.EntityDetails> iriToEntityDetails) {

        ExplainInferredMappings.EntityDetails details =  iriToEntityDetails.get(inferredMapping.getObjectIRI().asStringIRI());
        if (details != null) {
            if (details.isCuriePresent())
                inferredMapping.setObjectId(details.getCurie());
            if (details.isLabelPresent())
                inferredMapping.setObjectLabel(details.getLabel());
        }

        return inferredMapping;
    }

}

