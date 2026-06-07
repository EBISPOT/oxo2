package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers test fixtures (ADR-0009 two-phase model). Each fixture is run through the pipeline in
 * <b>isolation</b> (its own {@code loadData.nextflow} pass over only its set(s)), so a phase-2
 * (cross-set) rule's single {@code oxo2/inferences} output belongs to exactly that fixture.
 *
 * Two fixture shapes:
 * <ul>
 *   <li><b>Single-set rule fixture</b> — {@code testcases/minimal/rules/&lt;RULE&gt;.sssom.tsv}; one set,
 *       triggers one chain rule (phase 1 or phase 2).</li>
 *   <li><b>Cross-set fixture</b> — {@code testcases/minimal/crossset/&lt;NAME&gt;/*.sssom.tsv}; two or more
 *       sets whose mappings only chain when reasoned over together (phase 2), proving cross-set
 *       inference with per-leaf {@code mapping_id} provenance.</li>
 * </ul>
 */
public final class RuleFixtures {

    /** Suffix that all fixture TSVs end with. */
    public static final String FIXTURE_SUFFIX = ".sssom.tsv";

    private RuleFixtures() {}

    public static Path rulesDir() {
        return Env.repoRoot().resolve("testcases").resolve("minimal").resolve("rules");
    }

    public static Path crossSetDir() {
        return Env.repoRoot().resolve("testcases").resolve("minimal").resolve("crossset");
    }

    public static Path expectedDir() {
        return Env.repoRoot().resolve("testcases_expected_output").resolve("minimal");
    }

    /** Returns all fixtures (or just one if -Doxo2.it.rule is set), sorted by name. */
    public static List<Fixture> discover() throws IOException {
        String filter = Env.ruleFilter();
        List<Fixture> fixtures = new ArrayList<>();

        // Single-set rule fixtures: one TSV each, name == rule.
        Path rulesDirectory = rulesDir();
        if (Files.isDirectory(rulesDirectory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rulesDirectory, "*" + FIXTURE_SUFFIX)) {
                for (Path fixturePath : stream) {
                    String filename = fixturePath.getFileName().toString();
                    String name = filename.substring(0, filename.length() - FIXTURE_SUFFIX.length());
                    fixtures.add(new Fixture(name, List.of(fixturePath)));
                }
            }
        }

        // Cross-set fixtures: a directory of TSVs, name == directory name.
        Path crossSetDirectory = crossSetDir();
        if (Files.isDirectory(crossSetDirectory)) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(crossSetDirectory, Files::isDirectory)) {
                for (Path caseDir : dirs) {
                    List<Path> tsvs = new ArrayList<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(caseDir, "*" + FIXTURE_SUFFIX)) {
                        for (Path tsv : stream) tsvs.add(tsv);
                    }
                    Collections.sort(tsvs);
                    if (!tsvs.isEmpty()) {
                        fixtures.add(new Fixture(caseDir.getFileName().toString(), tsvs));
                    }
                }
            }
        }

        if (filter != null) {
            fixtures.removeIf(fixture -> !filter.equals(fixture.name));
            if (fixtures.isEmpty()) {
                throw new IllegalStateException(
                        "Rule filter -D" + Env.RULE_FILTER_PROPERTY + "=" + filter + " matched no fixture under "
                                + rulesDirectory + " or " + crossSetDirectory);
            }
        }

        Collections.sort(fixtures, (left, right) -> left.name.compareTo(right.name));
        return fixtures;
    }

    public static final class Fixture {
        public final String name;
        public final List<Path> tsvs;

        public Fixture(String name, List<Path> tsvs) {
            this.name = name;
            this.tsvs = tsvs;
        }

        /** Set base names this fixture loads. The downloader names each file after its registry id
         *  (the TSV's base name, minus the {@code .sssom.tsv} suffix), so all downstream per-set
         *  artifacts use these bare names. For a single-set fixture this is just {@code [name]}. */
        public List<String> setBaseNames() {
            List<String> names = new ArrayList<>();
            for (Path tsv : tsvs) {
                String filename = tsv.getFileName().toString();
                names.add(filename.endsWith(FIXTURE_SUFFIX)
                        ? filename.substring(0, filename.length() - FIXTURE_SUFFIX.length())
                        : filename);
            }
            return names;
        }
    }
}
