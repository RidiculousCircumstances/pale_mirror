package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.Map;

/** Installed immutable ruleset catalog. Selection is always exact by id, schema and content digest. */
public final class FrontierRulesets {
    private static final FrontierRuleset PRODUCTION = ruleset("frontier-v3-production-r1");
    /**
     * Explicit anchor for snapshots written before selector persistence existed. It is not a
     * current default: decoding old bytes is a named compatibility migration with fixed data.
     */
    private static final FrontierRuleset LEGACY_PRE_RULESET_R79 = ruleset("frontier-v3-legacy-pre-ruleset-r79");
    private static final Map<String, FrontierRuleset> INSTALLED = Map.of(PRODUCTION.id(), PRODUCTION,
            LEGACY_PRE_RULESET_R79.id(), LEGACY_PRE_RULESET_R79);

    private FrontierRulesets() { }

    public static FrontierRuleset production() { return PRODUCTION; }

    public static FrontierRuleset legacyForSnapshotVersion(int version) {
        if (version < 41 || version > 79) throw new IllegalArgumentException("no explicit legacy ruleset for state version " + version);
        return LEGACY_PRE_RULESET_R79;
    }

    public static FrontierRuleset require(String id, int schemaVersion, String contentSha256) {
        FrontierRuleset ruleset = INSTALLED.get(id);
        if (ruleset == null || ruleset.schemaVersion() != schemaVersion || !ruleset.contentSha256().equals(contentSha256)) {
            throw new IllegalArgumentException("unavailable or incompatible Frontier ruleset: " + id);
        }
        return ruleset;
    }

    private static FrontierRuleset ruleset(String id) {
        return new FrontierRuleset(id, 3,
                new FrontierRuleset.Cadence(1L, 3_000L, 200L, 24_000L, 400L, 24_000L, 200L, 24_000L, 24_000L,
                        1_200L, 20L, 1_200L, 400L, 24_000L, 600L, 100L, 20L, 800L, 100L, 20L, 20L, 20L,
                        100L, 6_000L, 200L, 200L, 100L, 800L, 900L, 1_000L, 2_000L, 6_000L, 1_000L,
                        8_000L, 8_100L, 1_600L, 20L, 3_200L, 100L, 2_400L, 2_400L),
                new FrontierRuleset.Spatial(160, 160, 96, 32, 48, 64, 128, 16, 16, 64, 12),
                new FrontierRuleset.Rates(new FixedScalar(125_000L), new FixedScalar(250_000L), FixedScalar.whole(100L), FixedScalar.whole(2L)),
                new FrontierRuleset.FacilityCapacity(48, 16, 4, 8, 4, 2, 3, 1),
                new FrontierRuleset.Combat(FixedScalar.whole(4), FixedScalar.whole(2), FixedScalar.whole(6), FixedScalar.whole(3), FixedScalar.ONE),
                new FrontierRuleset.HiveCommand(6, 1, 2, 1, 200L, 100L));
    }
}
