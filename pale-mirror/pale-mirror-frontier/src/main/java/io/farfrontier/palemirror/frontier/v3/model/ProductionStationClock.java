package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Optional;

/** One processing clock. Task/need systems supply attendance; machines never inspect those systems. */
public final class ProductionStationClock {
    public record Attendance(int speedPermille, long permittedUntilTick) {
        public Attendance {
            if (speedPermille < 1 || speedPermille > 100_000 || permittedUntilTick < 1)
                throw new IllegalArgumentException("invalid station attendance interval");
        }
    }
    private ProductionStationClock() { }
    public static WorkProgress next(FrontierWorldState state, ProductionOperation operation, long tick,
                                    Optional<Attendance> attendance) {
        return next(state.bootstrap().ruleset().production(), state.bootstrap().ruleset().workCatalog(), operation, tick, attendance);
    }
    public static WorkProgress next(ProductionCatalog recipes, WorkCatalog work, ProductionOperation operation, long tick,
                                    Optional<Attendance> attendance) {
        var recipe = recipes.require(operation.recipeId());
        var definition = work.require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        if (operation.transformed()) throw new IllegalArgumentException("completed output cannot accrue processing");
        var prior = operation.progress().orElseGet(() -> WorkProgress.pending(definition.workUnits(), tick));
        if (prior.requiredMilliWork() != Math.multiplyExact(definition.workUnits(), 1_000L))
            throw new IllegalArgumentException("station clock differs from its pinned work definition");
        var settled = prior.pause(tick);
        if (settled.complete()) return settled;
        return switch (recipe.processingMode()) {
            case AUTOMATIC -> {
                if (attendance.isPresent()) throw new IllegalArgumentException("automatic station cannot use resident work speed");
                yield settled.resume(tick, 1_000, Long.MAX_VALUE);
            }
            case ATTENDED -> attendance.filter(value -> value.permittedUntilTick() > tick)
                    .map(value -> settled.resume(tick, value.speedPermille(), value.permittedUntilTick())).orElse(settled);
        };
    }
}
