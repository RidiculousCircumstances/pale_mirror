package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Ephemeral safety budget shared by repeated projection calls in one physical turn. */
final class FrontierV3ProjectionWriteBudget {
    private static final long MAX_TURN_NANOS = 5_000_000L;
    private static final Map<ServerLevel, FrontierV3ProjectionWriteBudget> TURNS = new WeakHashMap<>();
    private final long turn;
    private final java.util.function.LongSupplier clock;
    private long started;
    private boolean effectsStarted;
    private final int limit;
    private int remaining;

    private FrontierV3ProjectionWriteBudget(long turn, int limit) {
        this(turn, limit, System::nanoTime);
    }
    FrontierV3ProjectionWriteBudget(long turn, int limit, java.util.function.LongSupplier clock) {
        if (limit < 1 || limit > 1024) throw new IllegalArgumentException("projection limit exceeds safety ceiling");
        this.turn = turn; this.limit = limit; this.remaining = limit; this.clock = java.util.Objects.requireNonNull(clock);
    }
    static FrontierV3ProjectionWriteBudget current(ServerLevel level, int limit) {
        var budget = TURNS.get(level);
        if (budget == null || budget.turn != level.getGameTime()) {
            budget = new FrontierV3ProjectionWriteBudget(level.getGameTime(), limit); TURNS.put(level, budget);
        }
        if (budget.limit != limit) throw new IllegalArgumentException("competing projection budgets in the same turn");
        return budget;
    }
    static FrontierV3ProjectionWriteBudget current(ServerLevel level) {
        return current(level, Math.min(FrontierV3ManagedBlockWrites.MAX_PLANNED_WRITES,
                io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get()));
    }
    int remaining() { return remaining; }
    /** The runtime owns one turn budget; component fixtures outside its world do not. */
    static FrontierV3NativeEffect admit(ServerLevel level, FrontierV3NativeEffect effect) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return effect;
        return new FrontierV3NativeEffect(() -> current(level).admit(effect.preparation().get()), effect.mutation());
    }
    FrontierV3NativeEffect.Admission admit(FrontierV3NativeEffect.Admission preparation) {
        // A pending fence cannot spend capacity. Capacity is checked before the
        // common executor consumes a live attempt, never inside its mutation.
        if (!preparation.ready()) return preparation;
        return take() ? preparation : FrontierV3NativeEffect.Admission.waiting("projection-turn-capacity");
    }
    // Preparation is already count-bounded and may await durable fences. Charging
    // that wait to the effect window could starve it forever. Start once at the
    // first effect, never reset for a second invocation in the same native tick.
    boolean available() { return remaining > 0 && (!effectsStarted || clock.getAsLong() - started < MAX_TURN_NANOS); }
    boolean take() {
        if (!available()) return false;
        if (!effectsStarted) { effectsStarted = true; started = clock.getAsLong(); }
        remaining--; return true;
    }
}
