package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunction;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrontierV3ScenePresentationTest {


    @Test
    void keepsAmbientCivilianNameplatesQuietButMakesAMobilizedDefenderReadable(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.defenderEquipmentConfiguration(new WorldId("frontier:scene-presentation-defender"), 73L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var assault = state.strategicPlans().settlementAssaults().values().iterator().next();
        var defender = assault.defenderIds().stream().filter(id -> !id.equals(assault.defenderUnit().leaderId())).findFirst().orElseThrow();

        assertEquals(HumanTacticalFunction.MILITIA, HumanTacticalFunctionProjection.derive(state, defender));
        assertEquals(state.humanPopulation().resident(defender).name() + "\nUNIT IMPROVISED", FrontierSceneLabels.actor(state, defender, false));
        assertEquals(true, FrontierSceneLabels.ambientActorNameVisible(state, defender, false));

        var civilian = state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .filter(resident -> !assault.defenderIds().contains(resident.id())).findFirst().orElseThrow();
        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, civilian.id()));
        assertFalse(FrontierSceneLabels.ambientActorNameVisible(state, civilian.id(), false));
    }

    @Test
    void keepsHotMotionBoundToTheExactCanonicalAssaultPair(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.settlementAssaultConfiguration(new WorldId("frontier:scene-hot-pair"), 74L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        var assault = runtime.decodedState().orElseThrow().strategicPlans().settlementAssaults().values().iterator().next();

        var first = FrontierV3SettlementAssaultSceneExecutor.currentStrikePair(assault, 0L).orElseThrow();
        var second = FrontierV3SettlementAssaultSceneExecutor.currentStrikePair(assault, 1L).orElseThrow();

        assertEquals(assault.combatantAttackerIds().stream().sorted().toList().getFirst(), first.attackerId());
        assertEquals(assault.defenderIds().stream().sorted().toList().getFirst(), first.targetId());
        assertEquals(assault.defenderIds().stream().sorted().toList().get(1 % assault.defenderIds().size()), second.attackerId());
        assertEquals(assault.combatantAttackerIds().stream().sorted().toList().get(1 % assault.combatantAttackerIds().size()), second.targetId());
        runtime.shutdown();
    }
}
