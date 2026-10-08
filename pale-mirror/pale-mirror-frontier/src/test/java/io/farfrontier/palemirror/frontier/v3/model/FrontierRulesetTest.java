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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierRulesetTest {
    @Test void caravanPaceIsSharedWithProvisionForecastAndOldWorldsKeepTheirClock() {
        var current = FrontierRulesets.production();
        var previous = FrontierRulesets.installed("frontier-v3-production-r14");
        assertEquals(15L, current.expedition().ticksPerRouteEdge());
        assertEquals(20L, previous.expedition().ticksPerRouteEdge());
        assertEquals(15L, FrontierRulesets.installed("frontier-v3-expedition-candidate-r2").expedition().ticksPerRouteEdge());
        assertEquals(20L, FrontierRulesets.installed("frontier-v3-expedition-candidate-r1").expedition().ticksPerRouteEdge());
        assertEquals(15L, FrontierRulesets.installed("frontier-v3-trade-playtest-r4").expedition().ticksPerRouteEdge());
        assertEquals(20L, FrontierRulesets.installed("frontier-v3-trade-playtest-r3").expedition().ticksPerRouteEdge());
        assertTrue(current.expedition().plannedDuration(300, 300) < previous.expedition().plannedDuration(300, 300));
        assertEquals(previous.resourceHarvestColdTravelTicksPerEdge(), current.resourceHarvestColdTravelTicksPerEdge());
        assertEquals(previous.expedition().formationSpacing(), current.expedition().formationSpacing());
        assertEquals(previous.expedition().maxFormationStretch(), current.expedition().maxFormationStretch());
        assertEquals(previous, FrontierRulesets.require(previous.id(), previous.schemaVersion(), previous.contentSha256()));
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:caravan-pace"), 41L, current));
        assertEquals(current, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)).bootstrap().ruleset());
    }
    @Test void tradePlaytestStockIsFinitePublicGenesisAndPinnedOnRecovery() {
        var selected = FrontierRulesets.installed("frontier-v3-trade-playtest-r3");
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:trade-playtest"), 41L, selected));
        var seller = new SubjectId("settlement:7");
        assertEquals(320, SettlementFoodPolicy.breadStock(initial, seller));
        assertTrue(initial.shipments().shipments().isEmpty());
        assertTrue(initial.companies().goodsTrade().orders().isEmpty());
        assertTrue(initial.companies().goodsTrade().contracts().isEmpty());
        var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(initial));
        assertEquals(selected, recovered.bootstrap().ruleset());
        assertEquals(320, SettlementFoodPolicy.breadStock(recovered, seller));
        assertEquals(64, SettlementFoodPolicy.breadStock(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:ordinary-stock"), 41L)), seller));
        assertEquals(17, FrontierRulesets.production().schemaVersion());
        assertEquals(12, initial.transportFleet().assets().size(), "adopted rules create a finite fleet, never dispatch-time animals");
        assertThrows(IllegalArgumentException.class, () -> FrontierRulesets.installed("unknown-rules"));
        assertThrows(IllegalArgumentException.class, () -> FrontierRulesets.require(selected.id(), selected.schemaVersion(), "wrong-digest"));
    }
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
                production.facilityCapacity(), production.combat(), production.hiveCommand());

        FrontierBootstrap normal = FrontierBootstrapper.create(new WorldId("frontier:ruleset-manifest"), 642L, production);
        FrontierBootstrap alternate = FrontierBootstrapper.create(new WorldId("frontier:ruleset-manifest"), 642L, changed);

        assertNotEquals(normal.ruleset().contentSha256(), alternate.ruleset().contentSha256());
        assertNotEquals(normal.canonicalSha256(), alternate.canonicalSha256());
    }

    @Test
    void changedHiveCommandBalanceChangesTheWorldManifestHash() {
        FrontierRuleset production = FrontierRulesets.production();
        FrontierRuleset changed = new FrontierRuleset("frontier-v3-test-ruleset-command", production.schemaVersion(), production.cadence(), production.spatial(),
                production.rates(), production.facilityCapacity(), production.combat(),
                new FrontierRuleset.HiveCommand(5, 1, 2, 1, 200L, 100L));

        assertNotEquals(production.contentSha256(), changed.contentSha256());
    }

    @Test
    void residentLifePolicyIsHashedByTheActiveSchema() {
        FrontierRuleset prior = FrontierRulesets.production();
        var changedLife = new FrontierRuleset.ResidentLife(24_000, 10_000,
                24_000L, 1, 3, 1, 255);
        FrontierRuleset retained = new FrontierRuleset(prior.id(), prior.schemaVersion(),
                prior.cadence(), prior.spatial(), prior.rates(), prior.facilityCapacity(),
                prior.combat(), prior.hiveCommand(), changedLife);
        assertNotEquals(prior.contentSha256(), retained.contentSha256());
        FrontierRuleset next = new FrontierRuleset("frontier-v3-resident-life-test", 7,
                prior.cadence(), prior.spatial(), prior.rates(), prior.facilityCapacity(),
                prior.combat(), prior.hiveCommand(), changedLife);
        FrontierRuleset other = new FrontierRuleset("frontier-v3-resident-life-test", 7,
                prior.cadence(), prior.spatial(), prior.rates(), prior.facilityCapacity(),
                prior.combat(), prior.hiveCommand(), FrontierRuleset.ResidentLife.initial());
        assertNotEquals(next.contentSha256(), other.contentSha256());
        assertEquals(10_000, SettlementDailySchedule.from(changedLife).nextWindowBoundaryAfter(0));
    }

    @Test
    void coldFieldTravelRateChangesThePersistedRulesetSelector() {
        FrontierRuleset production = FrontierRulesets.production();
        FrontierRuleset changed = new FrontierRuleset(production.id(), production.schemaVersion(),
                production.cadence(), production.spatial(), production.rates(), production.facilityCapacity(),
                production.combat(), production.hiveCommand(), production.residentLife(),
                production.resourceHarvestColdTravelTicksPerEdge() + 1L);
        assertEquals(1L, production.cadence().resourceHarvestTraversalInterval());
        assertEquals(20L, production.resourceHarvestColdTravelTicksPerEdge());
        assertNotEquals(production.contentSha256(), changed.contentSha256());
    }

    @Test
    void canonicalFacilityAndColdCombatReadTheSelectedRuleset() {
        FrontierRuleset production = FrontierRulesets.production();
        FrontierRuleset selected = new FrontierRuleset("frontier-v3-test-ruleset-r2", production.schemaVersion(), production.cadence(), production.spatial(),
                production.rates(), new FrontierRuleset.FacilityCapacity(51, 17, 5, 9, 5, 3, 4, 2),
                new FrontierRuleset.Combat(FixedScalar.whole(9), FixedScalar.whole(7), FixedScalar.whole(11), FixedScalar.whole(8), FixedScalar.whole(6)),
                production.hiveCommand());
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ruleset-projection"), 644L, selected));
        SubjectId guard = state.bootstrap().hive().bioforms().stream().filter(Bioform::isDefender).findFirst().orElseThrow().id();
        Settlement settlement = state.bootstrap().settlements().getFirst();

        assertEquals(FixedScalar.whole(9), FrontierCombatRules.damage(state, guard));
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
