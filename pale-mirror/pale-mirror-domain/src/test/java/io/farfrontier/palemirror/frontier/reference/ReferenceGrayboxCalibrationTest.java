package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Yearly behavioural gate coupled directly to the Python-generated calibration fixture. */
class ReferenceGrayboxCalibrationTest {
    @Test
    void everyNamedSeedRemainsInsideItsSourceDerivedYearlyEnvelope() {
        ReferenceGrayboxCalibrationFixture.Fixture fixture = ReferenceGrayboxCalibrationFixture.load();
        List<ReferenceGrayboxCalibration.Run> runs = fixture.config().seeds().stream()
                .map(seed -> ReferenceGrayboxCalibration.run(seed, fixture.config().days())).toList();
        Map<Long, ReferenceGrayboxCalibration.Run> actual = new LinkedHashMap<>();
        List<String> deviations = new java.util.ArrayList<>();
        for (ReferenceGrayboxCalibration.Run run : runs) actual.put(run.seed(), run);

        assertEquals(fixture.config().seeds(), actual.keySet().stream().toList(), "Java runs every Python fixture seed in order");
        for (ReferenceGrayboxCalibrationFixture.ExpectedRun expected : fixture.measurements()) {
            ReferenceGrayboxCalibration.Run run = actual.get(expected.seed());
            assertNotNull(run, "Java run exists for Python seed " + expected.seed());
            assertEquals(expected.daysSimulated(), run.daysSimulated(), "seed " + expected.seed() + " reaches the source horizon");
            assertEquals(expected.metrics().keySet(), run.metrics().keySet(), "seed " + expected.seed() + " has the complete source metric surface");
            for (Map.Entry<String, Double> metric : expected.metrics().entrySet()) {
                double observed = run.metric(metric.getKey());
                if (!fixture.within(metric.getKey(), metric.getValue(), observed)) {
                    deviations.add("seed=" + expected.seed() + " metric=" + metric.getKey() + " Python=" + metric.getValue()
                            + " Java=" + observed + " allowed=" + fixture.expectedRange(metric.getKey(), metric.getValue()));
                }
            }
        }

        ReferenceGrayboxCalibration.Summary summary = ReferenceGrayboxCalibration.summarize(runs);
        assertEquals(fixture.acceptance().keySet(), summary.metrics().keySet(), "aggregate source metric surface");
        for (Map.Entry<String, ReferenceGrayboxCalibrationFixture.Acceptance> entry : fixture.acceptance().entrySet()) {
            double observed = summary.metric(entry.getKey());
            ReferenceGrayboxCalibrationFixture.Acceptance acceptance = entry.getValue();
            assertTrue(observed >= acceptance.minimum(), () -> "aggregate " + entry.getKey() + " below source minimum "
                    + acceptance.minimum() + ": " + observed);
            if (acceptance.maximum() != null) {
                assertTrue(observed <= acceptance.maximum(), () -> "aggregate " + entry.getKey() + " above source maximum "
                        + acceptance.maximum() + ": " + observed);
            }
        }
        assertTrue(deviations.isEmpty(), () -> "yearly per-seed calibration drift:\n" + String.join("\n", deviations));
    }

    @Test
    void everySourceSeedIsDeterministicAcrossTheYearlyHorizon() {
        ReferenceGrayboxCalibrationFixture.Fixture fixture = ReferenceGrayboxCalibrationFixture.load();
        for (long seed : fixture.config().seeds()) {
            assertEquals(ReferenceGrayboxCalibration.run(seed, fixture.config().days()),
                    ReferenceGrayboxCalibration.run(seed, fixture.config().days()),
                    "yearly Java run is deterministic for source seed " + seed);
        }
    }

    @Test
    void fixtureReaderRejectsMissingAndWrongProfileEvidence() throws IOException {
        Path missing = Path.of("build", "source-fixture-test", "missing-graybox-calibration.json").toAbsolutePath();
        IllegalStateException absent = assertThrows(IllegalStateException.class, () -> ReferenceGrayboxCalibrationFixture.load(missing));
        assertEquals("graybox calibration fixture is missing: " + missing, absent.getMessage());

        Path fixture = fixturePath();
        String profileMismatch = Files.readString(fixture, StandardCharsets.UTF_8)
                .replace("\"profile\":\"graybox_1_40\"", "\"profile\":\"wrong_profile\"");
        IllegalStateException mismatch = assertThrows(IllegalStateException.class,
                () -> ReferenceGrayboxCalibrationFixture.parse(profileMismatch));
        assertEquals("graybox calibration fixture has an unexpected source profile", mismatch.getMessage());
    }

    @Test
    void fixtureRulesRejectMaterialDriftButPermitTheApprovedSingleZeroEvent() {
        ReferenceGrayboxCalibrationFixture.Fixture fixture = ReferenceGrayboxCalibrationFixture.load();
        ReferenceGrayboxCalibrationFixture.ExpectedRun sample = fixture.measurements().getFirst();
        double population = sample.metrics().get("population_ratio");
        assertFalse(fixture.within("population_ratio", population, population * 1.16d));

        double campaigns = sample.metrics().get("front_campaigns_started");
        ReferenceGrayboxCalibrationFixture.Rule campaignRule = fixture.ruleFor("front_campaigns_started");
        double campaignTolerance = Math.max(campaignRule.absoluteTolerance() == null ? 0.0d : campaignRule.absoluteTolerance(),
                campaigns * (campaignRule.relativeTolerance() == null ? 0.0d : campaignRule.relativeTolerance()));
        assertFalse(fixture.within("front_campaigns_started", campaigns, campaigns + campaignTolerance + 1.0d));
        assertTrue(fixture.within("destroyed_cores", 0.0d, 1.0d));
        assertFalse(fixture.within("destroyed_cores", 0.0d, 2.0d));
    }

    @Test
    void civicEmergencyRationsReachTheMarketConsumptionOwner() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
        assertNotNull(world.marketWorld().rationAuthority(), "a V2 world must expose civic rations to household consumption");

        for (int day = 0; day < 57; day++) world.tick();

        ReferenceSettlement settlement = world.settlements().get(9);
        assertEquals(ReferenceCivicState.EMERGENCY, world.v2().civics().get(9).state());
        assertEquals(.92d, world.v2().civics().get(9).rationFraction());
        assertEquals(.92d, settlement.foodFulfillment(), 1.0e-12d,
                "the market must consume the current civic-ration issue, not an implicit full meal");
    }

    @Test
    void sourceWorldBindsExactSiteSurveyRulesBeforeMarketExpansion() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));
        ReferenceSiteSurveyor surveyor = world.marketWorld().siteSurveyor();
        ReferenceSiteSurveyor.ReferenceSitePosition position = surveyor.place(
                world.settlements().get(1), ReferenceSiteKind.FARM);

        assertTrue(position.x() > 0 && position.x() < world.config().width() - 1);
        assertTrue(position.y() > 0 && position.y() < world.config().height() - 1);
        assertTrue(surveyor.quality(ReferenceSiteKind.FARM, position.x(), position.y()) >= .20d);
    }

    @Test
    void licensedSiteSurveyMatchesThePythonV2EligibilityCheckpoint() {
        // Python graybox_1_40, seed 41: this farm is the first expansion
        // whose choice changes if sector access/infection eligibility is
        // omitted.  It is deliberately checked before the annual envelope,
        // so a later legal-looking hive branch cannot conceal the cause.
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(41L));
        world.run(108);

        ReferenceResourceSite site = world.resourceSites().get(35);
        assertNotNull(site, "source checkpoint has the completed licensed site");
        assertEquals(ReferenceSiteKind.FARM, site.kind());
        assertEquals(21, site.x());
        assertEquals(14, site.y());
        assertEquals(1.060714285714286d, site.quality());
        assertEquals(.013750000000000002d, site.capacity());
    }

    private static Path fixturePath() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            Path candidate = current.resolve("docs").resolve(ReferenceGrayboxCalibrationFixture.FILENAME);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("graybox calibration fixture is missing from the Pale Mirror checkout");
    }
}
