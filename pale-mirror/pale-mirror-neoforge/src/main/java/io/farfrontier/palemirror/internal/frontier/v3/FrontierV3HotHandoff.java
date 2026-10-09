package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.List;
import java.util.Objects;

/** Read-only handoff composition. Owners prove current surfaces; this coordinator never writes them. */
final class FrontierV3HotHandoff {
    enum Status { READY, WAITING, CONFLICT }
    record Check(SubjectId owner, Status status, String reason) {
        Check { Objects.requireNonNull(owner); Objects.requireNonNull(status); Objects.requireNonNull(reason); }
    }
    record Review(long revision, List<Check> checks) {
        Review { checks = List.copyOf(checks); }
        boolean ready() { return checks.stream().allMatch(check -> check.status() == Status.READY); }
        // A classified conflict must remain visible for inspection/repair, but cannot grant HOT authority.
        boolean presentable() { return checks.stream().noneMatch(check -> check.status() == Status.WAITING); }
    }
    interface Participant {
        List<Check> inspect(ServerLevel level, FrontierWorldState state, ChunkPos chunk);
    }
    private static final List<Participant> PARTICIPANTS = List.of(
            FrontierV3ResourceFieldHandoff::inspect, FrontierV3ReferenceContainerHandoff::inspect, FrontierV3WorksiteHandoff::inspect);
    private FrontierV3HotHandoff() { }
    static Review inspect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ChunkPos chunk) {
        var canonical = runtime.canonicalState().orElseThrow();
        var checks = PARTICIPANTS.stream().flatMap(owner -> owner.inspect(level, canonical.state(), chunk).stream()).toList();
        return new Review(canonical.revision().value(), checks);
    }
}
