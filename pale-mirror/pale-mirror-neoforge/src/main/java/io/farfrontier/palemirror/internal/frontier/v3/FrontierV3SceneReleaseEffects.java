package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.Objects;
import java.util.Set;

/** Registered resource owner prepares the atomic scope exit; common release never interprets its job. */
interface FrontierV3SceneReleaseEffects {
    SceneCauseKind family();
    boolean matchesBody(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body);
    Decision prepare(ServerLevel level, FrontierWorldState state, SceneLease lease,
                     SceneLeaseReleased exit, Set<SubjectId> departed);

    sealed interface Decision permits Ready, Conflict { }
    record Ready(FrontierPayload payload) implements Decision {
        public Ready { Objects.requireNonNull(payload); }
    }
    record Conflict(String reason) implements Decision {
        public Conflict {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("release failure requires a reason");
        }
    }

    /** An explicit registration for families which may not release resource bindings. */
    record NoResourceHand(SceneCauseKind family) implements FrontierV3SceneReleaseEffects {
        public NoResourceHand { Objects.requireNonNull(family); }
        public boolean matchesBody(FrontierWorldState state, SceneLease lease, SceneMember member, Mob body) { return true; }
        public Decision prepare(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                SceneLeaseReleased exit, Set<SubjectId> departed) {
            return FrontierSceneLeaseStateSupport.hasBoundSceneHand(state, lease)
                    ? new Conflict("release-bound-hand-without-typed-owner") : new Ready(exit);
        }
    }
}
