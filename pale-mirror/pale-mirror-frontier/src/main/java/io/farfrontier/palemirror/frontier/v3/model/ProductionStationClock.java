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
    public static boolean updateNeeded(FrontierWorldState state, ProductionOperation operation, long tick, Optional<Attendance> attendance) {
        var machine = state.productionStations().require(operation.stationId());
        if (machine.pendingFuel().isPresent() || machine.pendingRecipe().isPresent() || operation.transformed()) return false;
        if (!operation.attendance().equals(attendance) || operation.progress().isEmpty()) return true;
        var clock = operation.progress().orElseThrow();
        if (clock.complete()) return false;
        if (clock.running()) return tick >= clock.activeUntilTick() || attendance.isEmpty();
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        return (!recipe.requiresCombustion() || machine.burnRemaining(tick) > 0)
                && (recipe.processingMode() == ProductionRecipe.ProcessingMode.AUTOMATIC || attendance.filter(value -> value.permittedUntilTick() > tick).isPresent());
    }
    public static WorkProgress next(FrontierWorldState state, ProductionOperation operation, long tick,
                                    Optional<Attendance> attendance) {
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        var machine = state.productionStations().require(operation.stationId());
        long poweredUntil = recipe.requiresCombustion() ? machine.combustion().filter(burn -> burn.burningAt(tick))
                .map(FuelCombustion::endsAtTick).orElse(tick) : Long.MAX_VALUE;
        return next(state.bootstrap().ruleset().production(), state.bootstrap().ruleset().workCatalog(), operation, tick, attendance, poweredUntil);
    }
    public static WorkProgress next(ProductionCatalog recipes, WorkCatalog work, ProductionOperation operation, long tick,
            Optional<Attendance> attendance, long poweredUntilTick) {
        var recipe = recipes.require(operation.recipeId());
        var definition = work.require(recipe.product().itemKind(), WorkOperation.PRODUCE);
        if (operation.transformed()) throw new IllegalArgumentException("completed output cannot accrue processing");
        long required = Math.multiplyExact(definition.workUnits(), operation.inputQuantity() / recipe.inputUnitsPerBatch());
        var prior = operation.progress().orElseGet(() -> WorkProgress.pending(required, tick));
        if (prior.requiredMilliWork() != Math.multiplyExact(required, 1_000L))
            throw new IllegalArgumentException("station clock differs from its pinned work definition");
        var settled = prior.pause(tick);
        if (settled.complete()) return settled;
        if (poweredUntilTick <= tick) return settled;
        return switch (recipe.processingMode()) {
            case AUTOMATIC -> {
                if (attendance.isPresent()) throw new IllegalArgumentException("automatic station cannot use resident work speed");
                yield settled.resume(tick, 1_000, poweredUntilTick);
            }
            case ATTENDED -> attendance.filter(value -> value.permittedUntilTick() > tick)
                    .map(value -> settled.resume(tick, value.speedPermille(), Math.min(value.permittedUntilTick(), poweredUntilTick))).orElse(settled);
        };
    }
}
