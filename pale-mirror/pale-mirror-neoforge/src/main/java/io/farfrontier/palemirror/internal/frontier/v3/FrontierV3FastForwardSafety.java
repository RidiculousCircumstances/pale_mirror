package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/** Prevents test-only canonical time travel from skipping a physically executable v3 effect. */
final class FrontierV3FastForwardSafety {
    private FrontierV3FastForwardSafety() { }

    /**
     * COLD intents outside naturally loaded chunks cannot physically execute, so they do not
     * obstruct operator time. A loaded effect or an active scene stops at its next exact tick.
     */
    static boolean requiresPhysicalStep(ServerLevel level, FrontierWorldState state) {
        Objects.requireNonNull(level, "physical level"); Objects.requireNonNull(state, "frontier state");
        return requiresPhysicalStep(state.physicalIntents().values(), state.sceneLeases().values(), intent -> affectedAreaLoaded(level, intent));
    }

    static boolean requiresPhysicalStep(Collection<PhysicalIntent> intents, Collection<SceneLease> sceneLeases,
                                        Predicate<PhysicalIntent> affectedAreaLoaded) {
        Objects.requireNonNull(intents, "physical intents"); Objects.requireNonNull(sceneLeases, "scene leases");
        Objects.requireNonNull(affectedAreaLoaded, "affected area loader");
        return intents.stream().filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                        .anyMatch(affectedAreaLoaded)
                || sceneLeases.stream().anyMatch(lease -> lease.status() == SceneLeaseStatus.PREPARED
                        || lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING);
    }

    private static boolean affectedAreaLoaded(ServerLevel level, PhysicalIntent intent) {
        int x = fixedBlock(intent.origin().x()); int y = fixedBlock(intent.origin().y()); int z = fixedBlock(intent.origin().z());
        int radius = intent.radiusBlocks();
        for (int currentX = x - radius; currentX <= x + radius; currentX = nextChunkEdge(currentX)) {
            for (int currentZ = z - radius; currentZ <= z + radius; currentZ = nextChunkEdge(currentZ)) {
                if (level.hasChunkAt(new BlockPos(currentX, y, currentZ))) return true;
            }
        }
        return false;
    }

    private static int fixedBlock(FixedScalar value) {
        return Math.toIntExact(Math.floorDiv(value.raw(), FixedScalar.SCALE));
    }

    private static int nextChunkEdge(int coordinate) {
        return Math.addExact(coordinate, 16 - Math.floorMod(coordinate, 16));
    }
}
