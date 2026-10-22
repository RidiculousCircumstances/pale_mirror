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
    enum Admission { REQUIRED, DEFERABLE_PREPARATION }

    record Definition(String id, Stage stage, Set<String> dependencies, Set<String> exclusiveWrites,
                      int maxInvocationsPerTick, Admission admission, Tick tick) {
        Definition {
            id = requireId(id, "executor id");
            stage = Objects.requireNonNull(stage, "stage");
            dependencies = normalized(dependencies, "executor dependency");
            exclusiveWrites = normalized(exclusiveWrites, "exclusive write kind");
            if (maxInvocationsPerTick < 1) throw new IllegalArgumentException("executor budget must be positive: " + id);
            tick = Objects.requireNonNull(tick, "tick");
            admission = Objects.requireNonNull(admission, "admission");
        }
    }

    record Diagnostic(String id, Stage stage, List<String> dependencies, List<String> exclusiveWrites, int maxInvocationsPerTick) { }

    private final List<Definition> ordered;
    private final List<Diagnostic> diagnostics;
    private final java.util.Map<ServerLevel, FrontierV3PreparationAdmission> preparation = new java.util.WeakHashMap<>();
    private final java.util.Map<ServerLevel, FrontierV3PreparationAdmission.Turn> activePreparation = new java.util.IdentityHashMap<>();
    private final java.util.Map<FrontierV3ServerRuntime<?, ?>, FrontierV3CooperativeStageCursor> continuations = new java.util.WeakHashMap<>();

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
        var admission = preparation.computeIfAbsent(world, ignored -> new FrontierV3PreparationAdmission(
                ordered.stream().filter(definition -> definition.admission() == Admission.DEFERABLE_PREPARATION)
                        .map(Definition::id).toList())).begin((long)
                (io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_BUDGET_MILLIS.get() * 1_000_000D), System::nanoTime);
        activePreparation.put(world, admission);
        try { continuations.computeIfAbsent(runtime, ignored -> new FrontierV3CooperativeStageCursor()).run(ordered.size(), () -> {
            var capacity = runtime.commandAdmissionCapacity();
            // A predecessor can quarantine the runtime. Preserve that primary trace and
            // stop this turn instead of throwing a second missing-Optional exception.
            return capacity.isPresent() && capacity.orElseThrow().hasCausalHeadroom() && !runtime.awaitingCanonicalDurability();
        }, index -> {
            Definition definition = ordered.get(index);
            // Definitions presently represent one invocation.  A future multi-slice executor must
            // declare and consume its own bounded work units rather than adding a second tick call.
            try (FrontierExecutionMetrics.Span ignored = FrontierExecutionMetrics.safelyBegin(runtime.executionMetrics(), FrontierExecutionMetrics.Stage.PHYSICAL,
                    definition.id(), definition.stage().name().toLowerCase(java.util.Locale.ROOT))) {
                definition.tick().run(world, runtime);
            }
        }); } finally { activePreparation.remove(world); }
    }

    boolean admitPreparation(ServerLevel world, String id) {
        var turn = activePreparation.get(world);
        return turn == null || turn.admit(id); // Direct owner/recovery entry is not a whole registry turn.
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
