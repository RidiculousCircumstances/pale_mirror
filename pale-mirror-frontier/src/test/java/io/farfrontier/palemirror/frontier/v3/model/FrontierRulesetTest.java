package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
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
        SubjectId guard = state.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender).findFirst().orElseThrow().id();
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
    void preCurrentStateBytesAreRejectedInsteadOfSelectingACompatibilityRuleset() {
        byte[] state = new FrontierWorldStateCodec().encode(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:pre-current-rejection"), 645L)));
        state[4] = 91;
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldStateCodec().decode(state));
    }

    private static int indexOf(byte[] bytes, byte[] target) {
        for (int offset = 0; offset <= bytes.length - target.length; offset++) {
            if (Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + target.length), target)) return offset;
        }
        throw new AssertionError("persisted ruleset id is absent from snapshot");
    }
}
