package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneLabels;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
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
        var settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(resident.settlementId())).findFirst().orElseThrow();
        var cargo = state.inventory().cargo().get(operation.cargoId());
        var stack = state.inventory().items().get(cargo.itemIds().getFirst());

        String residentName = FrontierSceneLabels.actor(state, resident.id(), false);
        String bioformName = FrontierSceneLabels.actor(state, state.bootstrap().hive().bioforms().getFirst().id(), true);
        String cargoName = FrontierSceneLabels.cargo(state, cargo);

        assertEquals(settlement.displayName() + " " + resident.role().name(), residentName);
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
}
