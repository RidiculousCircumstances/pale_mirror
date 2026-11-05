package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import java.lang.ref.WeakReference;
import java.util.*;

/** Shared reversible native passage interaction under current navigation permission.
 * It neither chooses a strategic goal nor publishes arrival or resource effects.
 * Property writes pass the same knowledge/write fence as ordinary native interactions.
 * Unload/restart discards these ephemeral visits; it never replays an old close command. */
final class FrontierV3PassageNavigation {
    private static final int MAX_OPEN_VISITS = 256;
    private static final Map<ServerLevel, Map<BlockPos, Visit>> VISITS = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> TURNS = new WeakHashMap<>();
    private static final Set<BlockPos> OWN_WRITES = new HashSet<>();
    private record Visitor(WeakReference<Mob> body, double initialSide) { }
    private record Visit(Map<BlockPos, BlockState> opened, Map<UUID, Visitor> users) { }
    private FrontierV3PassageNavigation() { }

    static void observeExternalWrite(ServerLevel level, BlockPos position) {
        if (OWN_WRITES.contains(position)) return;
        var visits = VISITS.get(level);
        if (visits == null) return;
        visits.remove(position);
        var lower = visits.get(position.below());
        if (lower != null && lower.opened().containsKey(position)) visits.remove(position.below());
    }
    static void tick(ServerLevel level) {
        var visits = VISITS.get(level);
        if (visits == null || visits.isEmpty() || Objects.equals(TURNS.put(level, level.getGameTime()), level.getGameTime())) return;
        for (var entry : List.copyOf(visits.entrySet())) {
            var base = entry.getKey(); var visit = entry.getValue();
            if (visit.opened().keySet().stream().anyMatch(pos -> !FrontierV3LoadedWorld.ready(level, pos))) { visits.remove(base, visit); continue; }
            if (!matches(level, visit.opened())) { visits.remove(base, visit); continue; }
            visit.users().values().removeIf(visitor -> {
                var body = visitor.body().get();
                return body == null || body.isRemoved() || !body.isAlive() || body.level() != level
                        || body.distanceToSqr(base.getX()+.5, body.getY(), base.getZ()+.5) > 6.25;
            });
            if (!mayClose(level, base, visit)) continue;
            change(level, null, base, visit.opened(), false,
                    () -> visits.get(base) == visit && mayClose(level, base, visit), () -> visits.remove(base, visit));
        }
    }
    private static boolean mayClose(ServerLevel level, BlockPos base, Visit visit) {
        if (visit.users().values().stream().anyMatch(visitor -> {
            var body = visitor.body().get();
            if (body == null || body.isRemoved() || !body.isAlive()) return false;
            double side = FrontierV3PassageBlocks.side(base, visit.opened().get(base), body.position());
            return Math.abs(side) <= body.getBbWidth()
                    || visitor.initialSide() != 0 && side * visitor.initialSide() >= 0;
        })) return false;
        // Include followers and the player: closing may never trap another body.
        var aperture = new AABB(base).expandTowards(0, 1, 0).inflate(1);
        return level.getEntitiesOfClass(LivingEntity.class, aperture, body -> body.isAlive() && !body.isSpectator()).isEmpty();
    }

    static boolean approach(ServerLevel level, Mob actor, Path path, FrontierV3NavigationScope scope,
            FrontierV3GoalNavigation.ProviderPermission permission) {
        if (!permission.current(actor)) return false;
        if (!permission.navigationProfile().map(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile::operatesPassages).orElse(false)) return true;
        if (path == null || path.isDone()) return true;
        var visits = VISITS.computeIfAbsent(level, unused -> new LinkedHashMap<>());
        double reach = permission.traversalPolicy().rules().manualInteractionReach();
        int first = Math.max(0, path.getNextNodeIndex()-1), end = Math.min(path.getNodeCount(), path.getNextNodeIndex()+3);
        var checked = new HashSet<BlockPos>();
        for (int index = first; index < end; index++) for (int dy = 0; dy <= 1; dy++) {
            var position = path.getNode(index).asBlockPos().above(dy);
            if (!FrontierV3LoadedWorld.ready(level, position)) return false;
            var actual = level.getBlockState(position);
            if (FrontierV3PassageBlocks.opened(actual).isEmpty()) continue;
            var base = FrontierV3PassageBlocks.base(position, actual).immutable();
            if (!checked.add(base) || !scope.permits(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(base.getX(), base.getY()-1, base.getZ()))) continue;
            if (actor.distanceToSqr(base.getX()+.5, actor.getY(), base.getZ()+.5) > reach*reach) continue;
            if (!FrontierV3PassageBlocks.isOpen(actual)) {
                if (visits.size() >= MAX_OPEN_VISITS && !visits.containsKey(base)) return false;
                var before = FrontierV3PassageBlocks.states(level, base, false);
                if (before.isEmpty() || !matches(level, before)) return false;
                if (!change(level, actor, base, before, true,
                        () -> actor.level() == level && permission.current(actor) && actor.distanceToSqr(base.getX()+.5, actor.getY(), base.getZ()+.5) <= reach*reach,
                        () -> {
                            var users = new HashMap<UUID, Visitor>();
                            users.put(actor.getUUID(), new Visitor(new WeakReference<>(actor), FrontierV3PassageBlocks.side(base, before.get(base), actor.position())));
                            visits.put(base, new Visit(FrontierV3PassageBlocks.states(level, base, true), users));
                        })) return false;
            }
            var visit = visits.get(base);
            if (visit != null) visit.users().putIfAbsent(actor.getUUID(), new Visitor(new WeakReference<>(actor),
                    FrontierV3PassageBlocks.side(base, visit.opened().get(base), actor.position())));
        }
        return true;
    }
    private static boolean matches(ServerLevel level, Map<BlockPos, BlockState> image) {
        return image.entrySet().stream().allMatch(entry -> FrontierV3LoadedWorld.ready(level, entry.getKey()) && level.getBlockState(entry.getKey()).equals(entry.getValue()));
    }
    private static boolean change(ServerLevel level, Mob actor, BlockPos base, Map<BlockPos, BlockState> before, boolean open,
            java.util.function.BooleanSupplier permitted, Runnable applied) {
        if (!permitted.getAsBoolean() || !matches(level, before)) return false;
        var after = FrontierV3PassageBlocks.states(level, base, open);
        if (after.isEmpty() || !after.keySet().equals(before.keySet())) return false;
        var writes = after.entrySet().stream().map(entry -> new FrontierV3ManagedBlockWrites.PlannedWrite(entry.getKey(), entry.getValue())).toList();
        var effect = FrontierV3ManagedBlockWrites.effect(level, writes, () -> {
            if (!permitted.getAsBoolean() || !matches(level, before)) return false;
            OWN_WRITES.addAll(after.keySet());
            try { FrontierV3PassageBlocks.set(level, actor, base, open); }
            finally { OWN_WRITES.removeAll(after.keySet()); }
            return matches(level, after);
        }, () -> change(level, actor, base, before, open, permitted, applied));
        boolean complete = effect.preparation().get().ready() && effect.mutation().get() == FrontierV3NativeEffect.Outcome.APPLIED;
        if (complete) applied.run();
        return complete;
    }
}
