package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicStagePlan;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierExecutionMetrics;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Closed, stable physical execution plan for one Frontier v3 server tick.
 *
 * <p>Registration is explicit and deterministic.  It rejects missing dependencies, cycles,
 * duplicate IDs and competing writers before an executor can touch Minecraft.  The per-entry
 * budget is the declared maximum number of executor invocations in a server tick; individual
 * executors retain their existing bounded domain work limits, while H0.6 timing attributes one
 * noncanonical measurement span to each staged invocation.</p>
 */
final class FrontierV3PhysicalExecutorRegistry {
    /**
     * Stable execution order.  This is deliberately not {@link Enum#ordinal()}:
     * a source-order edit must never silently alter physical causality.
     */
    enum Stage {
        OBSERVATION(0),
        PROJECTION(1),
        /** Durable-before-effect release of a claimed physical custody object. */
        RELEASE(2),
        ACTOR(3),
        CUSTODY(4),
        EFFECT(5),
        SCENE(6);

        private final int order;

        Stage(int order) { this.order = order; }

        int order() { return order; }
    }

    @FunctionalInterface interface Tick { void run(ServerLevel world, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime); }

    record Definition(String id, Stage stage, Set<String> dependencies, Set<String> exclusiveWrites,
                      int maxInvocationsPerTick, Tick tick) {
        Definition {
            id = requireId(id, "executor id");
            stage = Objects.requireNonNull(stage, "stage");
            dependencies = normalized(dependencies, "executor dependency");
            exclusiveWrites = normalized(exclusiveWrites, "exclusive write kind");
            if (maxInvocationsPerTick < 1) throw new IllegalArgumentException("executor budget must be positive: " + id);
            tick = Objects.requireNonNull(tick, "tick");
        }
    }

    record Diagnostic(String id, Stage stage, List<String> dependencies, List<String> exclusiveWrites, int maxInvocationsPerTick) { }

    private final List<Definition> ordered;
    private final List<Diagnostic> diagnostics;

    FrontierV3PhysicalExecutorRegistry(Collection<Definition> definitions) {
        List<DeterministicStagePlan.Definition<Definition>> staged = List.copyOf(definitions).stream()
                .map(definition -> new DeterministicStagePlan.Definition<>(definition.id(), definition.stage().order(),
                        definition.dependencies(), definition.exclusiveWrites(), definition))
                .toList();
        ordered = new DeterministicStagePlan<>(staged).ordered().stream().map(DeterministicStagePlan.Definition::payload).toList();
        diagnostics = ordered.stream().map(definition -> new Diagnostic(definition.id(), definition.stage(),
                definition.dependencies().stream().sorted().toList(), definition.exclusiveWrites().stream().sorted().toList(),
                definition.maxInvocationsPerTick())).toList();
    }

    void tick(ServerLevel world, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        for (Definition definition : ordered) {
            // Definitions presently represent one invocation.  A future multi-slice executor must
            // declare and consume its own bounded work units rather than adding a second tick call.
            try (FrontierExecutionMetrics.Span ignored = FrontierExecutionMetrics.safelyBegin(runtime.executionMetrics(), FrontierExecutionMetrics.Stage.PHYSICAL,
                    definition.id(), definition.stage().name().toLowerCase(java.util.Locale.ROOT))) {
                definition.tick().run(world, runtime);
            }
        }
    }

    List<Diagnostic> diagnostics() { return diagnostics; }

    private static Set<String> normalized(Set<String> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return Set.copyOf(values.stream().map(value -> requireId(value, role)).toList());
    }

    private static String requireId(String value, String role) {
        String normalized = Objects.requireNonNull(value, role).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(role + " must not be blank");
        return normalized;
    }
}
