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

                                All endpoints are rooted at `/api/v2` and return SSSOM/OxO2-shaped \
                                JSON. The service is a thin query layer over Apache Solr: there is \
                                no write API — mappings and mapping sets are populated by the \
                                offline dataload pipeline.

                                Results carry a soft inference-type ranking (asserted mappings rank \
                                above SSSOM-inferred ones; shorter inference chains rank higher).""")
                        .contact(new Contact()
                                .name("EBI SPOT")
                                .url("https://github.com/EBISPOT/oxo2"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
