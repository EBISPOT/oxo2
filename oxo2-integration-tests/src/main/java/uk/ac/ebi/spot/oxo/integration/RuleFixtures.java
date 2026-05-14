package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuleFixtures {

    /** Suffix that all rule fixture TSVs end with. */
    public static final String FIXTURE_SUFFIX = ".sssom.tsv";

    private RuleFixtures() {}

    public static Path fixturesDir() {
        return Env.repoRoot().resolve("testcases").resolve("minimal").resolve("rules");
    }

    public static Path expectedDir() {
        return Env.repoRoot().resolve("testcases_expected_output").resolve("minimal").resolve("rules");
    }

    /** Returns all fixtures (or just one if -Doxo2.it.rule is set), sorted by rule name. */
    public static List<Fixture> discover() throws IOException {
        String filter = Env.ruleFilter();
        Path dir = fixturesDir();
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Fixtures dir does not exist: " + dir);
        }
        List<Fixture> fixtures = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + FIXTURE_SUFFIX)) {
            for (Path p : stream) {
                String filename = p.getFileName().toString();
                String rule = filename.substring(0, filename.length() - FIXTURE_SUFFIX.length());
                if (filter == null || filter.equals(rule)) {
                    fixtures.add(new Fixture(rule, p));
                }
            }
        }
        if (filter != null && fixtures.isEmpty()) {
            throw new IllegalStateException(
                    "Rule filter -D" + Env.RULE_FILTER_PROPERTY + "=" + filter +
                    " matched no fixture in " + dir);
        }
        Collections.sort(fixtures, (a, b) -> a.rule.compareTo(b.rule));
        return fixtures;
    }

    public static final class Fixture {
        public final String rule;
        public final Path tsv;

        public Fixture(String rule, Path tsv) {
            this.rule = rule;
            this.tsv = tsv;
        }

        /** Pipeline-derived basename. The downloader names files after the registry id (the
         *  rule name), not after the source file. So T1.sssom.tsv -> downloader writes
         *  $OXO2_DATA/sssom/T1.tsv -> all downstream files use bare "T1" basename. */
        public String baseName() {
            return rule;
        }
    }
}
