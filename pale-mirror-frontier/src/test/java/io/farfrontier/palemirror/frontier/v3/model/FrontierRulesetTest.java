package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldSnapshotHeader;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRecoveryConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierRulesetTest {
    @Test
    void sameSeedAndExactRulesetProduceTheSameManifestStateAndSchedule() {
        FrontierRuleset ruleset = FrontierRulesets.production();
        var first = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ruleset-repeatable"), 641L, ruleset);
        var second = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ruleset-repeatable"), 641L, ruleset);

        assertEquals(first.initialState(), second.initialState());
        assertEquals(first.initialSchedules(), second.initialSchedules());
        assertEquals(first.initialState().bootstrap().canonicalSha256(), second.initialState().bootstrap().canonicalSha256());
        assertArrayEquals(first.stateCodec().encode(first.initialState()), second.stateCodec().encode(second.initialState()));
    }

    @Test
    void changedRulesetChangesTheWorldManifestHash() {
        FrontierRuleset production = FrontierRulesets.production();
        FrontierRuleset changed = new FrontierRuleset("frontier-v3-test-ruleset-r1", production.schemaVersion(), production.cadence(), production.spatial(),
                new FrontierRuleset.Rates(new FixedScalar(production.rates().hiveInfectionPulseGain().raw() + 1L),
                        production.rates().decontaminationReduction(), production.rates().initialSettlementTreasury(), production.rates().worksJobPrice()),
                production.facilityCapacity(), production.combat());

        FrontierBootstrap normal = FrontierBootstrapper.create(new WorldId("frontier:ruleset-manifest"), 642L, production);
        FrontierBootstrap alternate = FrontierBootstrapper.create(new WorldId("frontier:ruleset-manifest"), 642L, changed);

        assertNotEquals(normal.ruleset().contentSha256(), alternate.ruleset().contentSha256());
        assertNotEquals(normal.canonicalSha256(), alternate.canonicalSha256());
    }

    @Test
    void canonicalFacilityAndColdCombatReadTheSelectedRuleset() {
        FrontierRuleset production = FrontierRulesets.production();
        FrontierRuleset selected = new FrontierRuleset("frontier-v3-test-ruleset-r2", production.schemaVersion(), production.cadence(), production.spatial(),
                production.rates(), new FrontierRuleset.FacilityCapacity(51, 17, 5, 9, 5, 3, 4, 2),
                new FrontierRuleset.Combat(FixedScalar.whole(9), FixedScalar.whole(7), FixedScalar.whole(11), FixedScalar.whole(8), FixedScalar.whole(6)));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ruleset-projection"), 644L, selected));
        SubjectId guard = state.bootstrap().hive().bioforms().stream().filter(bioform -> bioform.role() == BioformRole.GUARD).findFirst().orElseThrow().id();
        Settlement settlement = state.bootstrap().settlements().getFirst();

        assertEquals(FixedScalar.whole(9), RouteEngagementCombatRules.damage(state, guard));
        assertEquals(51, SettlementFacilityCapability.housingCapacity(state, settlement.id()));
        assertEquals(2, SettlementFacilityCapability.forCondition(selected, StructureKind.FARM, StructureCondition.DAMAGED).workCapacity());
    }

    @Test
    void unavailablePersistedRulesetFailsClosedBeforeHydratingState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ruleset-fail-closed"), 643L));
        byte[] encoded = new FrontierWorldStateCodec().encode(state);
        byte[] selector = state.bootstrap().ruleset().id().getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(encoded, selector);
        encoded[offset] = (byte) 'x';

        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(encoded));
    }

    @Test
    void preRulesetSnapshotsSelectTheirNamedCompatibilityAnchorRatherThanTheCurrentDefault() {
        FrontierRuleset legacy = FrontierRulesets.legacyForSnapshotVersion(79);

        assertNotEquals(FrontierRulesets.production().id(), legacy.id());
        assertEquals(legacy, FrontierRulesets.legacyForSnapshotVersion(41));
        assertThrows(IllegalArgumentException.class, () -> FrontierRulesets.legacyForSnapshotVersion(80));
    }

    @Test
    void recoveryPinsTheExplicitLegacyHeaderInsteadOfRebindingItToCurrentProduction() {
        WorldId world = new WorldId("frontier:legacy-recovery");
        long seed = 645L;
        FrontierWorldState legacyState = FrontierWorldState.initial(FrontierBootstrapper.create(world, seed, FrontierRulesets.legacyForSnapshotVersion(79)));
        byte[] legacyHeader = new FrontierWorldStateCodec().encode(legacyState);
        legacyHeader[4] = 79; // Header selection is intentionally independent of the old body layout.
        CheckpointImage checkpoint = new CheckpointImage(world, Revision.ZERO, SimInstant.ZERO, legacyHeader, java.util.List.of(), java.util.List.of());

        assertEquals(FrontierRulesets.legacyForSnapshotVersion(79), FrontierWorldSnapshotHeader.read(legacyHeader).ruleset());
        assertEquals(FrontierRulesets.legacyForSnapshotVersion(79), FrontierWorldRecoveryConfiguration
                .select(world, seed, java.util.Optional.of(checkpoint)).initialState().bootstrap().ruleset());
        assertThrows(IllegalArgumentException.class, () -> FrontierWorldRecoveryConfiguration
                .select(world, seed + 1L, java.util.Optional.of(checkpoint)));
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        for (int offset = 0; offset <= bytes.length - target.length; offset++) {
            if (Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + target.length), target)) return offset;
        }
        throw new AssertionError("persisted ruleset id is absent from snapshot");
    }
}
