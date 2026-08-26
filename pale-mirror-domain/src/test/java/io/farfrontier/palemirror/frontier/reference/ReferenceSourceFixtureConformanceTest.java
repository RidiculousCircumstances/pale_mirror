package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Couples the Java conformance run to the source-generated fixture, rather
 * than relying only on copied digest literals in individual regression tests.
 */
class ReferenceSourceFixtureConformanceTest {
    private static final List<Integer> CANONICAL_DAYS = List.of(0, 1, 2, 3, 5, 10, 15, 20, 25, 30);
    private static final List<Integer> MULTI_SEED_DAYS = List.of(0, 1, 10, 30, 60);
    private static final List<Long> MULTI_SEEDS = List.of(7L, 17L, 41L, 73L);
    private static final Pattern SOURCE_TREE = Pattern.compile("\\\"tree_sha256\\\":\\\"([0-9a-f]{64})\\\"");
    private static final Pattern CHECKPOINT = Pattern.compile(
            "\\\"day\\\":(\\d+),\\\"numeric_conformance_sha256\\\":\\\"([0-9a-f]{64})\\\"");
    private static final Pattern MULTI_SEED_CHECKPOINT = Pattern.compile(
            "\\\"day\\\":(\\d+),\\\"events\\\":\\d+,\\\"front_campaigns\\\":\\d+,\\\"operations\\\":\\d+,"
                    + "\\\"state_numeric_conformance_sha256\\\":\\\"([0-9a-f]{64})\\\"");
    private static final Pattern RUN = Pattern.compile(
            "\\{\\\"checkpoints\\\":\\[(.*?)\\],\\\"seed\\\":(\\d+)\\}");

    @Test
    void javaMatchesEverySourceGeneratedCanonicalCheckpoint() {
        Map<Integer, String> expected = canonicalCheckpoints(sourceFixture("frontier-reference-v2-canonical-state.json"));
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        for (int day = 0; day <= CANONICAL_DAYS.getLast(); day++) {
            if (expected.containsKey(day)) {
                assertEquals(expected.get(day),
                        ReferenceCanonicalStateConformance.sha256(ReferenceCanonicalState.capture(world)),
                        "source fixture canonical checkpoint day " + day);
            }
            if (day < CANONICAL_DAYS.getLast()) world.tick();
        }
    }

    @Test
    void javaMatchesEverySourceGeneratedMultiSeedCheckpoint() {
        Map<Long, Map<Integer, String>> expected = multiSeedCheckpoints(
                sourceFixture("frontier-reference-v2-multiseed-canonical-state.json"));

        for (long seed : MULTI_SEEDS) {
            ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                    64, 44, 12, seed, 2, true, ReferenceSimulationProfile.SOURCE_V2));
            Map<Integer, String> checkpoints = expected.get(seed);
            for (int day = 0; day <= MULTI_SEED_DAYS.getLast(); day++) {
                if (checkpoints.containsKey(day)) {
                    assertEquals(checkpoints.get(day),
                            ReferenceCanonicalStateConformance.sha256(ReferenceCanonicalState.capture(world)),
                            "source fixture seed " + seed + " checkpoint day " + day);
                }
                if (day < MULTI_SEED_DAYS.getLast()) world.tick();
            }
        }
    }

    @Test
    void missingSourceFixtureFailsClosedInsteadOfFallingBackToCopiedHashes() {
        Path missing = Path.of("build", "source-fixture-test", "missing.json").toAbsolutePath();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> readFixture(missing));

        assertEquals("source fixture is missing: " + missing, failure.getMessage());
    }

    private static Map<Integer, String> canonicalCheckpoints(Path fixture) {
        String content = readFixture(fixture);
        assertSourceFixture(content, fixture);
        Map<Integer, String> checkpoints = checkpoints(content, fixture, CHECKPOINT);
        assertEquals(CANONICAL_DAYS, List.copyOf(checkpoints.keySet()), "canonical source fixture checkpoint days");
        return checkpoints;
    }

    private static Map<Long, Map<Integer, String>> multiSeedCheckpoints(Path fixture) {
        String content = readFixture(fixture);
        assertSourceFixture(content, fixture);
        Map<Long, Map<Integer, String>> runs = new LinkedHashMap<>();
        Matcher matcher = RUN.matcher(content);
        while (matcher.find()) {
            long seed = Long.parseLong(matcher.group(2));
            if (runs.put(seed, checkpoints(matcher.group(1), fixture, MULTI_SEED_CHECKPOINT)) != null) {
                throw new IllegalStateException("duplicate multi-seed source fixture seed " + seed + ": " + fixture);
            }
        }
        if (!List.copyOf(runs.keySet()).equals(MULTI_SEEDS)) {
            throw new IllegalStateException("unexpected multi-seed source fixture seeds in " + fixture + ": " + runs.keySet());
        }
        for (Map.Entry<Long, Map<Integer, String>> entry : runs.entrySet()) {
            if (!List.copyOf(entry.getValue().keySet()).equals(MULTI_SEED_DAYS)) {
                throw new IllegalStateException("unexpected checkpoint days for seed " + entry.getKey() + " in " + fixture);
            }
        }
        return Map.copyOf(runs);
    }

    private static Map<Integer, String> checkpoints(String content, Path fixture, Pattern pattern) {
        Map<Integer, String> result = new LinkedHashMap<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            if (result.put(day, matcher.group(2)) != null) {
                throw new IllegalStateException("duplicate source fixture checkpoint day " + day + ": " + fixture);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("source fixture has no canonical checkpoints: " + fixture);
        return result;
    }

    private static void assertSourceFixture(String content, Path fixture) {
        Matcher tree = SOURCE_TREE.matcher(content);
        if (!tree.find()) throw new IllegalStateException("source fixture has no source tree fingerprint: " + fixture);
    }

    private static Path sourceFixture(String filename) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            Path candidate = current.resolve("docs").resolve(filename);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("source fixture is missing from the Pale Mirror checkout: " + filename);
    }

    private static String readFixture(Path fixture) {
        if (!Files.isRegularFile(fixture)) throw new IllegalStateException("source fixture is missing: " + fixture);
        try {
            return Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read source fixture: " + fixture, exception);
        }
    }
}
