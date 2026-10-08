package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.Map;

/** Last immutable ownership image, not an engine, executor or new admission authority. */
final class FrontierV3NativeActorOwnership {
    private static final Map<MinecraftServer, FrontierWorldState> RETAINED = new IdentityHashMap<>();

    private FrontierV3NativeActorOwnership() { }

    static void retain(MinecraftServer server, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        if (runtime.status().kind() == FrontierV3RuntimeStatus.Kind.QUARANTINED)
            runtime.passiveOwnershipState().ifPresent(state -> RETAINED.put(server, state));
    }

    static void forget(MinecraftServer server) { RETAINED.remove(server); }

    static boolean recognizes(ServerLevel level, Entity entity) {
        var state = RETAINED.get(level.getServer());
        return state != null && FrontierV3ActorBodyController.recognizesPassiveBody(level, state, entity);
    }
}
