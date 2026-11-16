package uk.ac.ebi.spot.oxo.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata for the OxO2 REST API.
 *
 * <p>springdoc auto-generates the operation/schema parts of the document by introspecting the
 * controllers and DTOs; this bean only supplies the top-level {@code info} block (title,
 * description, version, contact, licence). The generated spec is served at {@code /v3/api-docs}
 * and rendered by the bundled Swagger UI at {@code /swagger-ui.html} (see
 * {@code application.properties} and {@code oxo2-backend/CONTEXT.md} § Exposes).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI oxo2OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OxO2 API")
                        .version("v2")
                        .description("""
                                REST API for OxO2, an SSSOM-compliant ontology mapping service.

                                The main API is rooted at `/api/v2`, with SSSOM endpoints under \
                                `/api/sssom` and legacy OxO compatibility endpoints under `/api`. \
                                Responses use the corresponding SSSOM/OxO2 or legacy shapes. The service is a thin query layer over Apache Solr: there is \
                                no write API — mappings and mapping sets are populated by the \
                                offline dataload pipeline.

                                Default search order is distinct direct asserted source count descending, \
                                then relevance score descending. Three source sets rank above two, two \
                                above one, and one above zero, regardless of provenance or query score. \
                                A column sort replaces this default order.

                                Sources are distinct asserted mapping sets for the same subject, predicate, \
                                modifier and object across the dataset, unaffected by search filters. \
                                Repeated records and extra justification methods do not add source sets. \
                                Missing counts are treated as zero. This is not a confidence estimate \
                                or independent-evidence count.

                                At equal source count, query relevance is multiplied by provenance, \
                                predicate strength, manual curation and confidence. Provenance favours \
                                ontology assertions, then curated assertions, then inferences spanning \
                                fewer ontologies.

                                `GET /api/v2/mappings/{mappingId}/support` lists every record in that \
                                group, including the selected record and those hidden by search filters, \
                                with group-wide justification-method and asserted-source counts.""")
                        .contact(new Contact()
                                .name("EBI SPOT")
                                .url("https://github.com/EBISPOT/oxo2"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
