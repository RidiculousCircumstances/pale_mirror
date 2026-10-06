package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;

import java.util.Collection;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounded per-runtime family service cursors, cleared with the scene executor lifecycle. */
final class FrontierV3SceneTurnScheduler {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneCauseKind, Family>> FAMILIES = new IdentityHashMap<>();

    private FrontierV3SceneTurnScheduler() { }

    static boolean run(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, SceneCauseKind kind,
                       Consumer<SceneLease> execute, BooleanSupplier admit) {
        return runInventory(runtime, state.sceneLeases().values(), kind, lease -> {
            // Recovery/release still progress. HOT business actions cannot compete with local avoidance.
            if (lease.status() == SceneLeaseStatus.HOT && lease.members().stream()
                    .anyMatch(member -> FrontierV3PedestrianCourtesy.active(state, member.actorId()))) return;
            execute.accept(lease);
        }, admit);
    }

    /** Selection consumes a read-only inventory; callbacks retain all execution authority. */
    static boolean runInventory(FrontierV3ServerRuntime<?, ?> runtime, Collection<SceneLease> inventory, SceneCauseKind kind,
                                Consumer<SceneLease> execute, BooleanSupplier admit) {
        var active = inventory.stream()
                .filter(lease -> lease.cause().kind() == kind)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED && lease.status() != SceneLeaseStatus.CONFLICT)
                .toList();
        return family(runtime, kind).active.run(active, SceneLease::id, execute, admit);
    }

    static <Candidate> Optional<Candidate> candidate(FrontierV3ServerRuntime<?, ?> runtime, SceneCauseKind kind,
                                                    Collection<Candidate> candidates, Function<Candidate, SubjectId> identity) {
        return family(runtime, kind).admission.next(candidates, identity);
    }

    private static Family family(FrontierV3ServerRuntime<?, ?> runtime, SceneCauseKind kind) {
        return FAMILIES.computeIfAbsent(runtime, ignored -> new EnumMap<>(SceneCauseKind.class))
                .computeIfAbsent(kind, ignored -> new Family());
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { FAMILIES.remove(runtime); }

    private static final class Family {
        private final FrontierV3FairTurn<SceneLeaseId> active = new FrontierV3FairTurn<>();
        private final FrontierV3FairTurn<SubjectId> admission = new FrontierV3FairTurn<>();
    }
}
