package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Final naturally-loaded facts are captured in the native unload callback; the next bounded
 * owner turn settles canonical custody. Unload must never synchronously drain the runtime. */
final class FrontierV3TerrainDeparture {
    private FrontierV3TerrainDeparture() { }
    static void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, LevelChunk chunk) {
        FrontierV3ForestryObservation.unloaded(level, chunk.getPos());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        runtime.decodedState().ifPresent(state -> FrontierV3ActorBodyController.observeTerrainDeparture(level, state, chunk));
        for (var owner : FrontierV3WorksiteRegistry.OWNERS) owner.departure(level, runtime, chunk);
    }
}
