package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** Initial finite wood reserve, separate from commercial demand. Actual cargo is counted once. */
public final class ForestryStockDemand {
    private ForestryStockDemand() { }
    public static boolean enabled(FrontierWorldState state, SubjectId home) {
        return state.bootstrap().ruleset().forestry().patches().stream().anyMatch(patch -> patch.knownBy().equals(home));
    }
    public static long held(FrontierWorldState state, SubjectId home, String kind) {
        return ProductiveResourceStock.held(state.inventory(), home, kind);
    }
    public static long committed(FrontierWorldState state, SubjectId home, String kind, Optional<SubjectId> excluding) {
        return state.forest().work().values().stream().filter(job -> job.home().equals(home) && job.outputKind().equals(kind)
                && excluding.filter(job.id()::equals).isEmpty()).mapToLong(job -> job.target().map(target -> {
                    if (job.felling().isPresent()) return (long) job.felling().orElseThrow().batch().effects().size() - job.appliedEffects();
                    return (long) Math.min(target.expectedLogs(), state.forest().require(target.tree()).remaining());
                }).orElse(0L)).sum();
    }
    public static boolean requested(FrontierWorldState state, SubjectId home, String kind, Optional<SubjectId> completing) {
        if (!enabled(state, home)) return false;
        long held = held(state, home, kind), reserved = committed(state, home, kind, completing);
        var rules = state.bootstrap().ruleset().forestry();
        boolean ongoing = state.forest().work().values().stream().anyMatch(job -> job.home().equals(home));
        return (ongoing || held < rules.resumeBelowItems()) && Math.addExact(held, reserved) < rules.reserveItems();
    }
}
