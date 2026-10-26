package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.*;

/** Cooperative native scheduler admission. Deferred records return to vanilla's persistent chunk queues. */
public final class FrontierV3ScheduledNativeTicks {
    private record Scope(BooleanSupplier available, Consumer<Runnable> execute) { }
    private record Key<T>(T type, BlockPos position) { }
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private FrontierV3ScheduledNativeTicks() { }
    public static void batch(ServerLevel level, Runnable action) {
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (!FrontierV3PhysicalWorld.isPhysical(level) || runtime == null) { action.run(); return; }
        within(runtime::scheduledNativeTickAvailable, runtime::runScheduledNativeTick, action);
    }
    static void within(BooleanSupplier available, Consumer<Runnable> execute, Runnable action) {
        var previous = CURRENT.get(); CURRENT.set(new Scope(available, execute));
        try { action.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    /** Bound the temporary collected suffix as well as execution; uncollected records never leave their chunk. */
    public static int maximum(int requested) {
        return !active() ? requested : available() ? Math.min(requested, 256) : 0;
    }
    public static boolean active() { return CURRENT.get() != null; }
    public static boolean available() { return Objects.requireNonNull(CURRENT.get()).available().getAsBoolean(); }
    public static void execute(Runnable action) { Objects.requireNonNull(CURRENT.get()).execute().accept(action); }

    /** Equivalent to leaving the unexecuted suffix uncollected. A callback may have
     * scheduled a later duplicate after collection; the earlier original keeps its turn.
     * Group by chunk so removing those duplicates scans each affected queue only once. */
    public static <T> void restore(Queue<ScheduledTick<T>> deferred,
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<LevelChunkTicks<T>> containers,
            Consumer<ScheduledTick<T>> schedule) {
        var chunks = new LinkedHashMap<Long, List<ScheduledTick<T>>>();
        while (!deferred.isEmpty()) {
            var tick = deferred.remove();
            chunks.computeIfAbsent(ChunkPos.asLong(tick.pos()), ignored -> new ArrayList<>()).add(tick);
        }
        for (var entry : chunks.entrySet()) {
            var container = Objects.requireNonNull(containers.get(entry.getKey()), "deferred native tick lost its loaded container");
            var keys = new HashSet<Key<T>>();
            entry.getValue().forEach(tick -> keys.add(new Key<>(tick.type(), tick.pos())));
            container.removeIf(tick -> keys.contains(new Key<>(tick.type(), tick.pos())));
            entry.getValue().forEach(schedule);
        }
    }
}
