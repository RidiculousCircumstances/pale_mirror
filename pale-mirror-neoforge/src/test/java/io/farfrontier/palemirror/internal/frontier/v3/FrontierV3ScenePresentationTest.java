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
    void derivesRoleAndExactCargoNamesWithoutLeakingCanonicalIds(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.operationAssemblyConfiguration(new WorldId("frontier:scene-presentation"), 71L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var operation = state.operations().values().iterator().next();
        var resident = state.bootstrap().settlements().stream().flatMap(value -> value.residents().stream())
                .filter(value -> value.id().equals(operation.participantIds().getFirst())).findFirst().orElseThrow();
        var profile = state.humanPopulation().resident(resident.id());
        var settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(resident.settlementId())).findFirst().orElseThrow();
        var cargo = state.inventory().cargo().get(operation.cargoId());
        var stack = state.inventory().items().get(cargo.itemIds().getFirst());

        String residentName = FrontierSceneLabels.actor(state, resident.id(), false);
        String bioformName = FrontierSceneLabels.actor(state, state.bootstrap().hive().bioforms().getFirst().id(), true);
        String cargoName = FrontierSceneLabels.cargo(state, cargo);

        assertEquals(settlement.displayName() + " " + profile.profession().name().replace('_', ' '), residentName);
        assertEquals("HIVE " + state.bootstrap().hive().bioforms().getFirst().role().name(), bioformName);
        assertEquals(settlement.displayName().toUpperCase(java.util.Locale.ROOT) + " CARAVAN\n"
                + stack.itemKind().substring(stack.itemKind().indexOf(':') + 1).replace('_', ' ').toUpperCase(java.util.Locale.ROOT)
                + " ×" + stack.count(), cargoName);
        assertFalse(residentName.contains(resident.id().value()) || cargoName.contains(cargo.id().value()),
                "canonical IDs belong in diagnostics, not a player's in-world scene");
    }

    @Test
    void rendersAnUnknownTestActorAsASafeGenericRoleRatherThanItsTechnicalId(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.operationAssemblyConfiguration(new WorldId("frontier:scene-presentation-negative"), 72L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);

        assertEquals("FRONTIER RESIDENT", FrontierSceneLabels.actor(runtime.decodedState().orElseThrow(),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:missing"), false));
    }

    @Test
    void keepsAmbientCivilianNameplatesQuietButMakesAMobilizedDefenderReadable(@TempDir Path directory) {
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(
                FrontierV3FixtureCatalog.defenderEquipmentConfiguration(new WorldId("frontier:scene-presentation-defender"), 73L),
                new FrontierFileStore(directory, FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        FrontierWorldState state = runtime.decodedState().orElseThrow();
        var assault = state.strategicPlans().settlementAssaults().values().iterator().next();
        var defender = assault.defenderIds().stream().filter(id -> !id.equals(assault.defenderUnit().leaderId())).findFirst().orElseThrow();

        assertEquals(HumanTacticalFunction.MILITIA, HumanTacticalFunctionProjection.derive(state, defender));
        assertEquals("Northwatch MILITIA\nUNIT IMPROVISED", FrontierSceneLabels.actor(state, defender, false));
        assertEquals(true, FrontierSceneLabels.ambientActorNameVisible(state, defender, false));

        var civilian = state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .filter(resident -> !assault.defenderIds().contains(resident.id())).findFirst().orElseThrow();
        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, civilian.id()));
        assertFalse(FrontierSceneLabels.ambientActorNameVisible(state, civilian.id(), false));
    }
}
