package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class QuarryFrontFixtureTest {
    @Test void declaresExactWorkersAndTheirNeedClockWithoutMiningOrFeedingThem() {
        var world = new WorldId("frontier:graybox");
        long seed = 20_260_918_065L;
        var base = FrontierWorldRuntimeDefinition.configuration(world, seed,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r7"));
        var configuration = FrontierV3FixtureCatalog.configuration("quarry-front-vera", world, seed);
        var state = configuration.initialState();
        var worker = state.humanPopulation().resident(QuarryFrontFixture.WORKER);
        assertEquals("Vera Wells", worker.name());
        assertEquals(Set.of(QuarryFrontFixture.WORKER, QuarryFrontFixture.PEER),
                SettlementWorkPolicy.permissions(state, QuarryFrontFixture.SETTLEMENT).workers(ResidentWorkKind.EXTRACTION));
        assertEquals(QuarryFrontFixture.METABOLISM, worker.characteristics().effectiveMetabolismPermille(0));
        assertEquals(base.initialState().inventory(), state.inventory());
        assertEquals(base.initialState().extractionSites(), state.extractionSites());
        assertEquals(base.initialState().actorLocations(), state.actorLocations());
        assertEquals(base.initialState().actorExecutions(), state.actorExecutions());
        assertEquals(base.initialState().humanPopulation().nutrition(worker.id()), state.humanPopulation().nutrition(worker.id()));
        assertTrue(state.humanPopulation().meals().isEmpty());
        assertTrue(state.actorMovements().isEmpty());
        assertTrue(state.extractionSites().work().isEmpty());
        long due = state.humanPopulation().nutrition(worker.id()).nextThresholdTick(
                state.bootstrap().ruleset().residentLife(), QuarryFrontFixture.METABOLISM);
        assertEquals(java.util.List.of(ResidentNeedProcess.review(worker.id(), due)),
                configuration.initialSchedules().stream().filter(action -> action.subject().equals(worker.id())
                        && action.kind().equals(ResidentNeedProcess.REVIEW)).toList());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        configuration.stateValidator().validateRecoveryInitial(state, configuration.initialSchedules());
    }
}
