// The OxO curation category of a mapping set (ADR-0027): operator knowledge tagged on each
// `mapping_registries` entry of the OxO config, not something present in the SSSOM data.
//
// Keep CODES and DEFAULT in sync with
// oxo2-shared/src/main/java/uk/ac/ebi/spot/oxo/model/sssom/MappingSetCategory.java.
class MappingSetCategories {

    static final List<String> CODES = ['ONTOLOGY', 'CURATED']

    /** An untagged registry is curated — the conservative reading, matching MappingSetCategory.DEFAULT. */
    static final String DEFAULT = 'CURATED'

    /**
     * Maps registry id -> category code for every registry that carries a `category` key.
     * Registries without one are absent from the map and fall back to {@link #DEFAULT}.
     *
     * Throws on an unrecognised category rather than defaulting it: a typo in the config would
     * otherwise silently mis-rank a whole corpus, and the value is interpolated into a command line.
     */
    static Map<String, String> byRegistryId(File configFile) {
        def categoryByRegistryId = [:]
        def config = new groovy.json.JsonSlurper().parse(configFile)
        config.mapping_registries.each { registry ->
            if (registry.category == null) {
                return
            }
            categoryByRegistryId[registry.id] = assertValid(registry.category, registry.id, configFile)
        }
        return categoryByRegistryId
    }

    static String assertValid(Object category, String registryId, File configFile) {
        String code = category.toString().toUpperCase()
        if (!CODES.contains(code)) {
            throw new RuntimeException(
                "Registry '${registryId}' in ${configFile} has category '${category}'. " +
                "Expected one of ${CODES}.")
        }
        return code
    }
}
