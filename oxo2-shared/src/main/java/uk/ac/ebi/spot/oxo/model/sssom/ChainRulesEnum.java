package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ChainRulesEnum {
    ASSERTED("Asserted", "Asserted","Asserted", false),
    RCE1_1("RCE1-1", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, ?p, ?c)",
            "(?a, ?p, ?c) <- (?a, <OWL:equivalentClass>, ?b), (?b, ?p, ?c)",
            false),
    RCE1_2("RCE1-2", "(?a, ?p, ?c) <- (?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b), (?b, ?p, ?c)",
            "(?a, ?p, ?c) <- (?a, <SKOS:exactMatch>, ?b), (?b, ?p, ?c)",
            false),
    RCE2_1("RCE2-1", "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <http://www.w3.org/2002/07/owl#equivalentClass>, ?c)",
            "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <OWL:equivalentClass>, ?c)",
            false),
    RCE2_2("RCE2-2", "(?a, ?p, ?c) <- (?a, ?p, ?b), (?b, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?c)",
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
    T4("T4",
            "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)",
            "(?a, <RDFS:subClassOf>, ?c) <- (?a, <RDFS:subClassOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)"),
    T5("T5", "(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c)",
            "(?a, <RDFS:subPropertyOf>, ?c) <- (?a, <RDFS:subPropertyOf>, ?b), (?b, <RDFS:subPropertyOf>, ?c)"),
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
            "(?b, <SEMAPV:crossSpeciesNarrowMatch>, ?a) <- (?a, <SEMAPV:crossSpeciesBroadMatch>, ?b)"),
    RG1("RG1", "(?a, <http://www.w3.org/2004/02/skos/core#exactMatch>, ?b) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b)",
            "(?a, <SKOS:exactMatch>, ?b) <- (?a, <OWL:equivalentClass>, ?b)"),
    RG2("RG2", "(?a, <http://www.w3.org/2004/02/skos/core#broadMatch>, ?b) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b)",
            "(?a, <SKOS:broadMatch>, ?b) <- (?a, <RDFS:subClassOf>, ?b)"),
    RCE_N1("RCE-N1", "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)",
            "(?a, <RDFS:subClassOf>, ?c) <- (?a, <OWL:equivalentClass>, ?b), (?b, <RDFS:subClassOf>, ?c)"),
    RCE_N2("RCE-N2",
            "(?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subClassOf>, ?c)",
            "(?a, <RDFS:subClassOf>, ?c) <- (?a, <RDFS:subClassOf>, ?b), (?b, <RDFS:subClassOf>, ?c)"),
    RCE_N3("RCE-N3",
            "(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?b), (?b, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c)",
            "(?a, <RDFS:subPropertyOf>, ?c) <- (?a, <OWL:equivalentProperty>, ?b), (?b, <RDFS:subPropertyOf>, ?c)"),
    RCE_N4("RCE-N4",
            "mapping(?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, ?c) <- (?a, <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>, b), (?a, <http://www.w3.org/2002/07/owl#equivalentProperty>, ?c)",
            "mapping(?a, <RDFS:subPropertyOf>, ?c) <- (?a, <RDFS:subPropertyOf>, b), (?a, <OWL:equivalentProperty>, ?c)");


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
