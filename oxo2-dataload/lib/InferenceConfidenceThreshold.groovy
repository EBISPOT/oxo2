// The confidence-gate threshold (ADR-0037): a mapping whose SSSOM `confidence` is present and
// strictly below this value is kept out of the cross-set inference corpus (it is still indexed and
// served as an asserted mapping). Unlike `category`, this is a GLOBAL knob — a single
// `min_inference_confidence` at the top level of the OxO config, not a per-`mapping_registries` field.
// Read here so the `nquads` stage can pass it to JSON2NQuads. Absent -> 0 (gate disabled).
class InferenceConfidenceThreshold {

    /** No threshold configured: the gate drops nothing and the corpus is byte-identical to before. */
    static final String DISABLED = '0.0'

    /**
     * The `min_inference_confidence` value from the config file at {@code configPath} as a string for
     * the command line, or {@link #DISABLED} when the path is empty, missing, or the key is absent.
     */
    static String fromConfigPath(String configPath) {
        if (!configPath) {
            return DISABLED
        }
        def configFile = new File(configPath)
        return configFile.exists() ? fromConfig(configFile) : DISABLED
    }

    /**
     * The `min_inference_confidence` from an existing config file, or {@link #DISABLED} when absent.
     *
     * Throws on a non-numeric or negative value rather than defaulting it: a typo would otherwise
     * silently disable the gate the operator asked for, and the value is interpolated into a command
     * line.
     */
    static String fromConfig(File configFile) {
        def config = new groovy.json.JsonSlurper().parse(configFile)
        def value = config.min_inference_confidence
        return value == null ? DISABLED : assertValid(value, configFile)
    }

    static String assertValid(Object value, File configFile) {
        double threshold
        try {
            threshold = value.toString().toDouble()
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                "min_inference_confidence '${value}' in ${configFile} is not a number.")
        }
        if (threshold < 0) {
            throw new RuntimeException(
                "min_inference_confidence '${value}' in ${configFile} must be >= 0.")
        }
        return value.toString()
    }
}
