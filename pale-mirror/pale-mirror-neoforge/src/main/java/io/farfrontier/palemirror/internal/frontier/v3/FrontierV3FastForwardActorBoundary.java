package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Canonical-only acceleration cannot replace the shared body's physical execution turn. */
final class FrontierV3FastForwardActorBoundary {
    private static final Map<ServerLevel, FrontierV3ActorSpatialCandidates> INDEXES = new WeakHashMap<>();
    private FrontierV3FastForwardActorBoundary() { }

    static Optional<String> retainedBody(FrontierWorldState state) {
        return state.fencedRecovery().current().values().stream()
                .filter(binding -> binding.asset() == FencedRecoveryAsset.BODY)
                .filter(binding -> ActorBodyAuthority.retainsPhysicalCustody(state, binding.ownerId()))
                .findFirst().map(binding -> "actor=" + binding.ownerId().value()
                        + ":physical-body:" + binding.phase() + ":epoch=" + binding.authorityEpoch());
    }

    static Optional<String> blocker(ServerLevel level, FrontierWorldState state, long tick) {
        var retained = retainedBody(state);
        if (retained.isPresent()) return retained;
        // Player ingress can precede the ordinary turn which prepares body authority.
        // Use the same spatial candidates and projected positions as ambient admission;
        // neither a loaded chunk alone nor a distant spectator stops COLD time.
        if (level.players().stream().allMatch(player -> player.isSpectator())) return Optional.empty();
        var index = INDEXES.computeIfAbsent(level, ignored -> new FrontierV3ActorSpatialCandidates());
        index.synchronize(state);
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            var observer = player.blockPosition();
            for (var actor : index.near(observer.getX(), observer.getZ(), FrontierV3SceneDemand.RADIUS_BLOCKS)) {
                var location = state.actorLocations().get(actor);
                if (location == null || location.condition().status() != ActorLifeStatus.ALIVE) continue;
                var body = state.actorMovements().containsKey(actor) ? ActorMovementProcess.bodyAt(state, actor, tick)
                        : ResidentMealProcess.bodyAt(state, actor, tick);
                if (FrontierV3SceneDemand.observe(level, body.supportingSurface().support()).active())
                    return Optional.of("actor=" + actor.value() + ":presentation-demand");
            }
        }
        return Optional.empty();
    }
}
