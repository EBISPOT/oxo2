package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.EntityLabelResolver;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real EntityLabelResolver is imported rather than mocked, so the shared v1 label precedence is
// exercised here exactly as it is on /api/search.
@WebMvcTest(V1MappingController.class)
@Import(EntityLabelResolver.class)
class V1MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static QueryResponse entityResponse(SolrDocument... docs) {
        SolrDocumentList list = new SolrDocumentList();
        for (SolrDocument document : docs) {
            list.add(document);
        }
        list.setNumFound(docs.length);
        NamedList<Object> root = new NamedList<>();
        root.add("response", list);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    private static SolrDocument entity(String id, String label) {
        SolrDocument document = new SolrDocument();
        document.setField("id", id);
        document.setField("label", label);
        return document;
    }

    @BeforeEach
    void entityLookupReturnsNothingByDefault() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(entityResponse());
    }

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

    /**
     * The observed defect: MESH:D009202 came back as "Cardiomyopathies" on rows from
     * mesh.ols.sssom.tsv and null on rows from mondo.sssom.tsv — in one response. The label is a
     * property of the term, so both rows must report it.
     */
    @Test
    void unlabelledRowsTakeTheirLabelFromTheEntityCollection() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(
                        mapping("MONDO:0004994", null, "skos:exactMatch", "MESH:D009202", null),
                        mapping("HP:0001638", "Cardiomyopathy", "skos:exactMatch",
                                "MESH:D009202", "Cardiomyopathies"))));
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(entityResponse(
                        entity("MESH:D009202", "Cardiomyopathies"),
                        entity("MONDO:0004994", "cardiomyopathy")));

        mockMvc.perform(get("/api/mappings").param("fromId", "MESH:D009202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.label").value("cardiomyopathy"))
                // Was null before: this row carries no object_label of its own.
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.label").value("Cardiomyopathies"))
                .andExpect(jsonPath("$._embedded.mappings[1].toTerm.label").value("Cardiomyopathies"));
    }

    @Test
    void aTermWithNoLabelAnywhereFallsBackToItsCurie() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(
                        mapping("MONDO:0004994", null, "skos:exactMatch", "ICD10:J45.0", null))));

        // v1 never emits a null label, so the CURIE stands in.
        mockMvc.perform(get("/api/mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.label").value("MONDO:0004994"))
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.label").value("ICD10:J45.0"));
    }

    /**
     * A literal subject (e.g. clinvar-xrefs.sssom.tsv's "Idiopathic cardiomyopathy") has a label but
     * no CURIE. Its row label must survive, and with no CURIE there is nothing to fall back TO — the
     * adapter must not invent an identifier the term does not have.
     */
    @Test
    void aLiteralSubjectKeepsItsRowLabelAndGainsNoFabricatedCurie() throws Exception {
        Mapping literalSubject = Mapping.builder()
                .mappingSetId("https://example.org/clinvar-xrefs.sssom.tsv")
                .subjectLabel("Idiopathic cardiomyopathy")
                .predicateId("oboInOwl:hasDbXref")
                .objectId("MESH:D009202")
                .inferenceType("ASSERTED")
                .build();
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(pageOf(List.of(literalSubject)));
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(entityResponse(entity("MESH:D009202", "Cardiomyopathies")));

        mockMvc.perform(get("/api/mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.curie")
                        .value(Matchers.nullValue()))
                .andExpect(jsonPath("$._embedded.mappings[0].fromTerm.label")
                        .value("Idiopathic cardiomyopathy"))
                .andExpect(jsonPath("$._embedded.mappings[0].toTerm.label").value("Cardiomyopathies"));
    }
}
