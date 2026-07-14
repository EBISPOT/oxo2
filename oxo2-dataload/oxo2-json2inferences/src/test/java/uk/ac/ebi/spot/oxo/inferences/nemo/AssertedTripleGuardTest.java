package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the ADR-0033 invariant on {@code sssom.rls}: <b>every derivation rule guards its own head
 * with {@code ~assertedTriple(s, p, o)}</b>, so the chase never derives a nil-UUID copy of a triple
 * that some set already asserts.
 *
 * <p>Without the guard, an asserted triple and its derived copy are distinct atoms (the mapping_id
 * differs), so an involution — {@code SYM-*} twice, or {@code RI4} then {@code RI5} — can re-derive an
 * asserted fact. nmo's trace stays acyclic over its 4-ary atoms, but {@link ExplainInferredMappings}
 * projects the mapping_id away, the two atoms fold into one triple, and the shipped proof shows the
 * conclusion inside its own derivation. That is how
 * {@code UPHENO:0049904 —crossSpeciesNarrowMatch→ HP:0001939} came to be explained by itself.
 *
 * <p>This is the cheap seam: a new rule added without a guard fails here in milliseconds, rather than
 * surviving to a full-pipeline run (where {@code ChainRulesIntegrationTest}'s "explanation
 * well-founded" assertion is the backstop) or, worse, to a re-baselined golden.
 */
class AssertedTripleGuardTest {

    /** {@code sssom.rls} lives at the module root; Surefire runs with the module dir as cwd. */
    private static final Path SSSOM_RLS = Path.of("sssom.rls");

    /** The graph term every rule-derived conclusion carries (ADR-0010). */
    private static final String NIL_UUID = "<urn:uuid:00000000-0000-0000-0000-000000000000>";

    /** A derivation rule: a nil-UUID {@code mapping/4} head, then its body. */
    private static final Pattern DERIVATION_RULE = Pattern.compile(
            "mapping\\(\\s*" + Pattern.quote(NIL_UUID) + "\\s*,(?<head>.*?)\\)\\s*:-(?<body>.*)",
            Pattern.DOTALL);

    private static final Pattern RULE_NAME = Pattern.compile("#\\[name\\(\"([^\"]+)\"\\)]");

    /**
     * The ruleset's statements, comments stripped. Comments are dropped first because their prose
     * contains sentence-ending periods, which would otherwise split a statement in half; within code,
     * a period at end-of-line is always a statement terminator (an IRI's dots are mid-token).
     */
    private static List<String> statements() {
        String ruleset;
        try {
            ruleset = Files.readString(SSSOM_RLS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SSSOM_RLS.toAbsolutePath(), e);
        }
        String code = ruleset.lines()
                .filter(line -> !line.trim().startsWith("%"))
                .collect(Collectors.joining("\n"));
        return List.of(code.split("\\.\\s*\\n"));
    }

    /** Rule name from a statement's {@code #[name(...)]} annotation, or a readable fallback. */
    private static String nameOf(String statement) {
        Matcher matcher = RULE_NAME.matcher(statement);
        return matcher.find() ? matcher.group(1) : "<unnamed> " + statement.strip();
    }

    private static String withoutWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    @Test
    void everyDerivationRuleGuardsItsHeadWithAssertedTriple() {
        List<String> derivationRules = statements().stream()
                .filter(statement -> DERIVATION_RULE.matcher(statement).find())
                .toList();

        assertTrue(derivationRules.size() >= 20,
                "Expected to parse the sssom.rls derivation rules but found only "
                        + derivationRules.size());

        List<String> unguarded = new ArrayList<>();
        for (String rule : derivationRules) {
            Matcher matcher = DERIVATION_RULE.matcher(rule);
            if (!matcher.find()) {
                continue;
            }
            // The guard must repeat the head's own subject/predicate/object, not some other triple:
            // ~assertedTriple over anything else would not prevent the redundant copy.
            String expectedGuard =
                    withoutWhitespace("~assertedTriple(" + matcher.group("head") + ")");
            if (!withoutWhitespace(matcher.group("body")).contains(expectedGuard)) {
                unguarded.add(nameOf(rule) + " — body lacks " + expectedGuard);
            }
        }

        assertEquals(List.of(), unguarded,
                "Derivation rules in sssom.rls whose head is not guarded by ~assertedTriple over its "
                        + "own s/p/o (ADR-0033). Each can re-derive an asserted triple as a nil-UUID "
                        + "copy, which folds into a circular explanation: " + unguarded);
    }
}
