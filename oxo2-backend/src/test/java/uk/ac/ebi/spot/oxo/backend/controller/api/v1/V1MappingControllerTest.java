package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import org.apache.solr.common.params.SolrParams;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1MappingController.class)
class V1MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static Mapping mapping(String subjectId, String subjectLabel, String predicateId,
                                   String objectId, String objectLabel) {
        return Mapping.builder()
                .mappingSetId("https://example.org/set")
                .subjectId(subjectId)
                .subjectLabel(subjectLabel)
                .predicateId(predicateId)
                .objectId(objectId)
                .objectLabel(objectLabel)
                .inferenceType("ASSERTED")
                .build();
    }

    private static MappingSearchResponse pageOf(List<Mapping> mappings) {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Mapping> page = new PageImpl<>(mappings, pageable, mappings.size());
        return new MappingSearchResponse(page);
    }

    @Test
    void returnsV1HalEnvelopeWithAdaptedMappingFields() throws Exception {
        Mapping mapping = Mapping.builder()
                .mappingSetId("https://w3id.org/commons/ols/mappings/doid.sssom.tsv")
                .mappingSetTitle("OLS extracted DOID mappings")
                .subjectId("DOID:0001816")
                .subjectLabel("angiosarcoma")
                .subjectIRI("http://purl.obolibrary.org/obo/DOID_0001816")
                .predicateId("skos:exactMatch")
                .objectId("EFO:0000400")
                .objectLabel("diabetes mellitus")
                .objectIRI("http://www.ebi.ac.uk/efo/EFO_0000400")
                .inferenceType("ASSERTED")
                .build();
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(mapping)));

        mockMvc.perform(get("/api/mappings").param("fromId", "DOID:0001816"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.mappings.length()").value(1))
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.curie").value("DOID:0001816"))
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.label").value("angiosarcoma"))
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.datasource.prefix").value("DOID"))
                // Term uri is the full IRI (subject_iri / object_iri), matching v1; identifier stays null.
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.uri")
                        .value("http://purl.obolibrary.org/obo/DOID_0001816"))
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.identifier")
                        .value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.curie").value("EFO:0000400"))
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.datasource.prefix").value("EFO"))
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.uri")
                        .value("http://www.ebi.ac.uk/efo/EFO_0000400"))
                .andExpect(jsonPath("$._embedded.mappings[0].scope").value("EXACT"))
                .andExpect(jsonPath("$._embedded.mappings[0].sourcePrefix").value("DOID"))
                // Mapping-level datasource identifies the SSSOM mapping set: id → prefix, title → name.
                .andExpect(jsonPath("$._embedded.mappings[0].datasource.prefix")
                        .value("https://w3id.org/commons/ols/mappings/doid.sssom.tsv"))
                .andExpect(jsonPath("$._embedded.mappings[0].datasource.preferredPrefix")
                        .value("https://w3id.org/commons/ols/mappings/doid.sssom.tsv"))
                .andExpect(jsonPath("$._embedded.mappings[0].datasource.name")
                        .value("OLS extracted DOID mappings"))
                // Identity deferred to the SSSOM API (ADR-0025): the field is present but null.
                .andExpect(jsonPath("$._embedded.mappings[0].mappingId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.mappings[0].sourceType").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$._links.self.href").value("/api/mappings"));
    }

    @Test
    void derivesV1ScopeFromSssomPredicate() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(
                        mapping("A:1", "a", "skos:closeMatch", "B:1", "b"),
                        mapping("A:2", "a", "rdfs:subClassOf", "B:2", "b"),
                        mapping("A:3", "a", "skos:broadMatch", "B:3", "b"),
                        mapping("A:4", "a", "oboInOwl:hasDbXref", "B:4", "b"))));

        mockMvc.perform(get("/api/mappings"))
                .andExpect(status().isOk())
                // closeMatch is a weaker near-equivalence → RELATED, not EXACT (ADR-0025).
                .andExpect(jsonPath("$._embedded.mappings[0].scope").value("RELATED"))
                .andExpect(jsonPath("$._embedded.mappings[1].scope").value("NARROWER"))
                .andExpect(jsonPath("$._embedded.mappings[2].scope").value("BROADER"))
                .andExpect(jsonPath("$._embedded.mappings[3].scope").value("RELATED"));
    }

    @Test
    void rejectsPageSizeAboveMax() throws Exception {
        mockMvc.perform(get("/api/mappings").param("size", "1001"))
                .andExpect(status().isBadRequest());
    }
}
