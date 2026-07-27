// Obsolete-terms support (ADR-0041): a `mapping_registries` entry may carry an `obsolete: true` flag
// meaning every subject of that registry is an obsolete term. Like `category` (ADR-0027) this is
// operator knowledge, not present in the SSSOM data, so it is read here from the OxO config and
// threaded into the SSSOM-to-JSON stage rather than derived.
//
// Mirrors MappingSetCategories.groovy.
class ObsoleteRegistries {

    /**
     * The ids of every registry whose `obsolete` key is true. A registry without the key, or with
     * `false`, is absent from the set (its subjects are live terms).
     *
     * Throws on a non-boolean value rather than defaulting it: a typo would otherwise silently ship an
     * obsolete corpus as live, and the flag is interpolated into a command line.
     */
    static Set<String> ids(File configFile) {
        def obsoleteIds = new HashSet<String>()
        def config = new groovy.json.JsonSlurper().parse(configFile)
        config.mapping_registries.each { registry ->
            if (isObsolete(registry.obsolete, registry.id, configFile)) {
                obsoleteIds.add(registry.id)
            }
        }
        return obsoleteIds
    }

    static boolean isObsolete(Object value, String registryId, File configFile) {
        if (value == null) {
            return false
        }
        if (value instanceof Boolean) {
            return value
        }
        String normalised = value.toString().toLowerCase()
        if (normalised == 'true') {
            return true
        }
        if (normalised == 'false') {
            return false
        }
        throw new RuntimeException(
            "Registry '${registryId}' in ${configFile} has obsolete '${value}'. Expected true or false.")
    }
}
