package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.WeakHashMap;

/** Ephemeral safety budget shared by repeated projection calls in one physical turn. */
final class FrontierV3ProjectionWriteBudget {
    private static final long MAX_TURN_NANOS = 5_000_000L;
    private static final Map<ServerLevel, FrontierV3ProjectionWriteBudget> TURNS = new WeakHashMap<>();
    private final long turn;
    private final long started = System.nanoTime();
    private final int limit;
    private int remaining;

    private FrontierV3ProjectionWriteBudget(long turn, int limit) {
        if (limit < 1 || limit > 1024) throw new IllegalArgumentException("projection limit exceeds safety ceiling");
        this.turn = turn; this.limit = limit; this.remaining = limit;
    }
    static FrontierV3ProjectionWriteBudget current(ServerLevel level, int limit) {
        var budget = TURNS.get(level);
        if (budget == null || budget.turn != level.getGameTime()) {
            budget = new FrontierV3ProjectionWriteBudget(level.getGameTime(), limit); TURNS.put(level, budget);
        }
        if (budget.limit != limit) throw new IllegalArgumentException("competing projection budgets in the same turn");
        return budget;
    }
    int remaining() { return remaining; }
    boolean available() { return remaining > 0 && System.nanoTime() - started < MAX_TURN_NANOS; }
    boolean take() {
        if (!available()) return false;
        remaining--; return true;
    }
}
