package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import org.springframework.data.domain.Page;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.List;

public class MappingSearchResponse {
    private final MappingPage mappings;

    public MappingSearchResponse(Page<Mapping> mappings) {
        this.mappings = MappingPage.of(mappings);
    }

    public MappingPage getMappings() {
        return mappings;
    }

    /**
     * The paged envelope as the API actually promises it: content plus the four paging counters,
     * and nothing else.
     *
     * <p>This used to be a raw Spring Data {@link Page}. Serializing one directly leaked the whole
     * of {@code PageImpl} — {@code pageable}, {@code sort}, {@code first}, {@code last},
     * {@code empty}, {@code numberOfElements} — into the public response, none of which any client
     * reads. It also broke outright under Jackson 3: bean introspection walks into
     * {@code Pageable}, and an unpaged query yields {@code Unpaged}, whose {@code getOffset()}
     * throws {@link UnsupportedOperationException} by design. Naming the five fields fixes both.
     */
    public record MappingPage(
            List<Mapping> content,
            long totalElements,
            int totalPages,
            int number,
            int size) {

        static MappingPage of(Page<Mapping> page) {
            if (page == null) {
                return new MappingPage(List.of(), 0L, 0, 0, 0);
            }
            return new MappingPage(
                    page.getContent(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.getNumber(),
                    page.getSize());
        }
    }

    @Override
    public String toString() {
        StringBuilder mappingsStr = new StringBuilder();
        if (mappings != null && mappings.content() != null) {
            mappingsStr.append("[");
            for (Mapping mapping : mappings.content()) {
                mappingsStr.append(mapping.toString()).append(", ");
            }
            if (!mappings.content().isEmpty()) {
                mappingsStr.setLength(mappingsStr.length() - 2); // remove last comma and space
            }
            mappingsStr.append("]");
        } else {
            mappingsStr.append("null");
        }
        return "MappingSearchResponse{" +
                "mappings=" + mappingsStr +
                '}';
    }
}
