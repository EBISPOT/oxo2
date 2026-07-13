package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  SSSOM chain rules are defined here: https://mapping-commons.github.io/sssom/dev/chaining-rules/
 *
 */

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ChainRulesEnum {
    ASSERTED("Asserted", "Asserted","Asserted", false),
    RCE1_1("RCE1-1", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, ?p, ?c)",
            "(?a, ?p, ?c) <- (?a, <OWL:equivalentClass>, ?b), (?b, ?p, ?c)",
            false),
    // Weak (skos:exactMatch) bridge in the leading position, split by predicate strength (ADR-0016):
    // a strict-identity ?p caps the conclusion at skos:exactMatch (RCE1-2a); a hierarchy/crossSpecies
    // ?p propagates unchanged (RCE1-2b). One entry per RCE1-2* rule in sssom.rls — the trace names
    // them, so these names must stay in sync with those rule names exactly.
    RCE1_2A("RCE1-2a", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, ?p, ?c)",
            "(?a, <SKOS:exactMatch>, ?c) <- (?a, <SKOS:exactMatch>, ?b), (?b, ?p, ?c)",
            false),
    RCE1_2B("RCE1-2b", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, ?p, ?c)",
            "(?a, ?p, ?c) <- (?a, <SKOS:exactMatch>, ?b), (?b, ?p, ?c)",
            false),
    RCE2_1("RCE2-1", "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c)",
            "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <OWL:equivalentClass>, ?c)",
            false),
    // Weak (skos:exactMatch) bridge in the trailing position, split by predicate strength as above.
    RCE2_2A("RCE2-2a", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) <- (?a, ?p, ?b), (?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c)",
            "(?a, <SKOS:exactMatch>, ?c) <- (?a, ?p, ?b), (?b, <SKOS:exactMatch>, ?c)",
            false),
    RCE2_2B("RCE2-2b", "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c)",
            "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <SKOS:exactMatch>, ?c)",
            false),
    T1("T1",
            "(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c)",
            "(?a, <OWL:equivalentClass>, ?c) <- (?a, <OWL:equivalentClass>, ?b), (?b, <OWL:equivalentClass>, ?c)"),
    T2("T2",
            "(?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c)",
            "(?a, <OWL:equivalentProperty>, ?c) <- (?a, <OWL:equivalentProperty>, ?b), (?b, <OWL:equivalentProperty>, ?c)"),
    T3("T3",
            "(?a, <http://www.w3.org/2002/07/owl#sameAs>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#sameAs>, ?b), (?b, <http://www.w3.org/2002/07/owl#sameAs>, ?c)",
            "(?a, <OWL:sameAs>, ?c) <- (?a, <OWL:sameAs>, ?b), (?b, <OWL:sameAs>, ?c)"),
    T6("T6",
            "(?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?c)",
            "(?a, <SEMAPV:crossSpeciesBroadMatch>, ?c) <- (?a, <SEMAPV:crossSpeciesBroadMatch>, ?b), (?b, <SEMAPV:crossSpeciesBroadMatch>, ?c)"),
    T7("T7",
            "(?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?c)",
            "(?a, <SEMAPV:crossSpeciesExactMatch>, ?c) <- (?a, <SEMAPV:crossSpeciesExactMatch>, ?b), (?b, <SEMAPV:crossSpeciesExactMatch>, ?c)"),
    T8("T8",
            "(?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b), (?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?c)",
            "(?a, <SEMAPV:crossSpeciesNarrowMatch>, ?c) <- (?a, <SEMAPV:crossSpeciesNarrowMatch>, ?b), (?b, <SEMAPV:crossSpeciesNarrowMatch>, ?c)"),
    T9("T9", "(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?c)",
            "(?a, <SKOS:broadMatch>, ?c) <- (?a, <SKOS:broadMatch>, ?b), (?b, <SKOS:broadMatch>, ?c)"),
    T10("T10", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c)",
            "(?a, <SKOS:exactMatch>, ?c) <- (?a, <SKOS:exactMatch>, ?b), (?b, <SKOS:exactMatch>, ?c)"),
    T11("T11", "(?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b), (?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?c)",
            "(?a, <SKOS:narrowMatch>, ?c) <- (?a, <SKOS:narrowMatch>, ?b), (?b, <SKOS:narrowMatch>, ?c)"),
    // Symmetry of the equivalence predicates. One entry per SYM-* rule in sssom.rls; the trace
    // names them so this set must stay in sync with those rule names, exactly like the RI* rules.
    SYM_EQUIVALENT_CLASS("SYM-equivalentClass",
            "(?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?a) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b)",
            "(?b, <OWL:equivalentClass>, ?a) <- (?a, <OWL:equivalentClass>, ?b)"),
    SYM_EQUIVALENT_PROPERTY("SYM-equivalentProperty",
            "(?b, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?a) <- (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b)",
            "(?b, <OWL:equivalentProperty>, ?a) <- (?a, <OWL:equivalentProperty>, ?b)"),
    SYM_SAME_AS("SYM-sameAs",
            "(?b, <http://www.w3.org/2002/07/owl#sameAs>, ?a) <- (?a, <http://www.w3.org/2002/07/owl#sameAs>, ?b)",
            "(?b, <OWL:sameAs>, ?a) <- (?a, <OWL:sameAs>, ?b)"),
    SYM_EXACT_MATCH("SYM-exactMatch",
            "(?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?a) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b)",
            "(?b, <SKOS:exactMatch>, ?a) <- (?a, <SKOS:exactMatch>, ?b)"),
    RI1("RI1",
            "(?b, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?a) <- (?a, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?b)",
            "(?b, <SKOS:broadMatch>, ?a) <- (?a, <SKOS:narrowMatch>, ?b)"),
    RI2("RI2",
            "(?b, <http://www.w3.org/2004/02/skos/core#narrowMatch>, ?a) <- (?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b)",
            "(?b, <SKOS:narrowMatch>, ?a) <- (?a, <SKOS:broadMatch>, ?b)"),
    RI3("RI3",
            "(?b, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesExactMatch>, ?b)",
            "(?b, <SEMAPV:crossSpeciesExactMatch>, ?a) <- (?a, <SEMAPV:crossSpeciesExactMatch>, ?b)"),
    RI4("RI4", "(?b, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?b)",
            "(?b, <SEMAPV:crossSpeciesBroadMatch>, ?a) <- (?a, <SEMAPV:crossSpeciesNarrowMatch>, ?b)"),
    RI5("RI5", "(?b, <https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch>, ?a) <- (?a, <https://w3id.org/semapv/vocab/crossSpeciesBroadMatch>, ?b)",
            "(?b, <SEMAPV:crossSpeciesNarrowMatch>, ?a) <- (?a, <SEMAPV:crossSpeciesBroadMatch>, ?b)");


    @JsonProperty(CHAIN_RULE_NAME)
    private final String name;
    @JsonProperty(CHAIN_RULE_LONG_FORM)
    private final String longFormRule;
    @JsonProperty(CHAIN_RULE_ABBREVIATED)
    private final String abbreviatedRule;
    private final boolean increasesDistance;

    private static final Logger logger = LoggerFactory.getLogger(ChainRulesEnum.class);

    ChainRulesEnum(String name, String longFormRule, String abbreviatedRule, boolean increasesDistance) {
        this.name = name;
        this.longFormRule = longFormRule;
        this.abbreviatedRule = abbreviatedRule;
        this.increasesDistance = increasesDistance;
    }

    ChainRulesEnum(String name, String longFormRule, String abbreviatedRule) {
        this.name = name;
        this.longFormRule = longFormRule;
        this.abbreviatedRule = abbreviatedRule;
        this.increasesDistance = true;
    }

    public String getName() {
        return name;
    }

    public String getLongFormRule() {
        return longFormRule;
    }

//    @JsonValue
    public String getAbbreviatedRule() {
        return abbreviatedRule;
    }

    public boolean increasesDistance() {
        return increasesDistance;
    }

    @JsonCreator
    public static ChainRulesEnum fromJson(@JsonProperty(CHAIN_RULE_NAME) String name) {
        logger.debug("CHAIN_RULE_NAME = {}", name);
        for (ChainRulesEnum rule : ChainRulesEnum.values()) {
            if (rule.getName().equals(name)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("Unknown ChainRulesEnum: " + name);
    }
}
