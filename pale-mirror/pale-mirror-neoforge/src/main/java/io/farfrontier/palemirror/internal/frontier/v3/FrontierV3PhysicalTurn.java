package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;

/** Orders retained physical completion, player ingress and the registered executor turn.
 * Journal delivery stays live even when new effects lack canonical capacity. */
final class FrontierV3PhysicalTurn {
    private FrontierV3PhysicalTurn() { }
    static void run(ServerLevel physicalWorld, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        FrontierV3NativeBodyResidence.restore(physicalWorld);
        FrontierV3PhysicalExecutors.registry().tick(Objects.requireNonNull(physicalWorld, "physical world"),
                Objects.requireNonNull(runtime, "runtime"));
        FrontierV3PhysicalStores.advance(physicalWorld);
    }
    static void observeAndRun(ServerLevel physicalWorld,
                                        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        // Regain causal batch headroom before a physical turn. Canonical time still advances
        // in the ordinary tick branch, so recovered receipt pressure can expire naturally.
        if (!runtime.commandAdmissionCapacity().orElseThrow().hasCausalHeadroom()) {
            // Completed journal closures release retained receipts even when those
            // very receipts currently prevent admitting another physical action.
            FrontierV3PhysicalStores.advance(physicalWorld);
            return;
        }
        for (ServerPlayer player : physicalWorld.players()) {
            FrontierV3GrayboxExecutor.observePlayerIngress(runtime,
                    new net.minecraft.world.level.ChunkPos(player.blockPosition()));
        }
        run(physicalWorld, runtime);
    }
}
