package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drift guard between {@code sssom.rls} and {@link ChainRulesEnum}. Nemo names every inferred
 * conclusion by its rule name in the trace; {@link NemoHelper} resolves that name through
 * {@link ChainRulesEnum#fromJson}, and a name the enum does not know resolves to {@code null},
 * silently shipping an inferred mapping whose root explanation has an <em>empty</em> chain rule.
 *
 * <p>That is exactly what happened when {@code RCE1-2}/{@code RCE2-2} were split into the
 * strength-tiered {@code RCE1-2a}/{@code RCE1-2b}/{@code RCE2-2a}/{@code RCE2-2b} rules without
 * updating the enum. This test fails the build the moment a rule name in {@code sssom.rls} has no
 * enum counterpart (or a non-{@code Asserted} enum entry names a rule the ruleset no longer defines),
 * so the two can never drift again.
 */
class ChainRuleNameSyncTest {

    /** Matches {@code #[name("RULE-NAME")]} annotations in the ruleset. */
    private static final Pattern RULE_NAME = Pattern.compile("#\\[name\\(\"([^\"]+)\"\\)]");

    /** {@code sssom.rls} lives at the module root; Surefire runs with the module dir as cwd. */
    private static final Path SSSOM_RLS = Path.of("sssom.rls");

    private static Set<String> ruleNamesInRuleset() {
        String ruleset;
        try {
            ruleset = Files.readString(SSSOM_RLS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SSSOM_RLS.toAbsolutePath(), e);
        }
        Set<String> names = new TreeSet<>();
        Matcher matcher = RULE_NAME.matcher(ruleset);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> enumRuleNames() {
        Set<String> names = new TreeSet<>();
        for (ChainRulesEnum rule : ChainRulesEnum.values()) {
            names.add(rule.getName());
        }
        return names;
    }

    @Test
    void everyRulesetNameResolvesInTheEnum() {
        Set<String> rulesetNames = ruleNamesInRuleset();
        assertTrue(rulesetNames.size() >= 20,
                "Expected to parse the sssom.rls rule names but found only " + rulesetNames);

        List<String> unresolved = rulesetNames.stream()
                .filter(name -> {
                    try {
                        ChainRulesEnum.fromJson(name);
                        return false;
                    } catch (IllegalArgumentException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

        assertTrue(unresolved.isEmpty(),
                "sssom.rls names with no ChainRulesEnum entry (their inferred mappings would ship "
                        + "with an empty chain rule): " + unresolved);
    }

    @Test
    void noEnumEntryNamesARuleTheRulesetDropped() {
        Set<String> rulesetNames = ruleNamesInRuleset();
        // "Asserted" is a synthetic leaf marker, not a rule in sssom.rls.
        List<String> orphaned = enumRuleNames().stream()
                .filter(name -> !name.equals(ChainRulesEnum.ASSERTED.getName()))
                .filter(name -> !rulesetNames.contains(name))
                .collect(Collectors.toList());

        assertEquals(List.of(), orphaned,
                "ChainRulesEnum entries that name no rule in sssom.rls (dead entries — remove or "
                        + "re-add the rule): " + orphaned);
    }
}
