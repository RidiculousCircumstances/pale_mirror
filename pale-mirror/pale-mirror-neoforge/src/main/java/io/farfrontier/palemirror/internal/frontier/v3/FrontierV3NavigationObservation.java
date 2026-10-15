package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Bounded, demand-local shape observation. No chunk loading, block repair or actor movement. */
final class FrontierV3NavigationObservation {
    private static final Map<ServerLevel, Window> WINDOWS = new WeakHashMap<>();
    private static final class Window {
        final Object bootstrap;
        Window(Object bootstrap) { this.bootstrap = bootstrap; }
        int cursor;
        List<io.farfrontier.palemirror.frontier.v3.api.SubjectId> roster = List.of();
        Object declarations;
        boolean fenceRejected;
        final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Pose> seen = new HashMap<>();
        final Set<BlockPosition> dirty = new LinkedHashSet<>();
        final Map<BlockPosition, Optional<NavigationCell>> captured = new HashMap<>();
    }
    private record Pose(BodyPosition position, java.lang.ref.WeakReference<Object> loadedChunk) {
        boolean matches(BodyPosition current, Object chunk) { return position.equals(current) && loadedChunk.get() == chunk; }
    }
    private FrontierV3NavigationObservation() { }
    private static Window window(ServerLevel level, FrontierWorldState state) {
        var existing = WINDOWS.get(level);
        if (existing != null && existing.bootstrap == state.bootstrap()) return existing;
        var fresh = new Window(state.bootstrap());
        WINDOWS.put(level, fresh);
        return fresh;
    }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime) {
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        var window = window(level, state);
        if (registerDefinitions(level, runtime, state, window)) return;
        if (observePending(level, runtime, state, window)) return;
        if (window.seen.size() > 4096) window.seen.clear();
        if (window.cursor >= window.roster.size()) {
            window.roster = state.actorLocations().keySet().stream().sorted().toList(); window.cursor = 0;
        }
        // One bounded batch per turn, including tall/wide profiles. The rest of
        // their envelope stays queued rather than silently omitted.
        for (int i = 0; i < 16 && window.cursor < window.roster.size(); i++) {
            var id = window.roster.get(window.cursor++); var actor = state.actorLocations().get(id);
            if (actor == null) { window.seen.remove(id); continue; }
            var position = actor.body();
            var body = level.getEntity(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), id));
            if (body instanceof net.minecraft.world.entity.Mob mob && mob.isAlive()
                    && FrontierV3ActorBodyController.recognizesRecordedBody(level, state, mob)) {
                var feet = mob.blockPosition(); position = new BodyPosition(feet.getX(), feet.getY(), feet.getZ());
            }
            var chunk = level.getChunkSource().getChunkNow(position.x() >> 4, position.z() >> 4);
            if (chunk == null) { window.seen.remove(id); continue; }
            var prior = window.seen.get(id);
            if (prior != null && prior.matches(position, chunk)) continue;
            var pose = new Pose(position, new java.lang.ref.WeakReference<>(chunk));
            int radius = Math.max(2, actor.navigationProfile().footprintRadius() + 1);
            int top = actor.navigationProfile().bodyLayers() + 1;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) for (int dy = -2; dy <= top; dy++) {
                var cell = new BlockPosition(position.x() + dx, position.y() + dy, position.z() + dz);
                if (state.bootstrap().bounds().contains(cell)) window.dirty.add(cell);
            }
            window.seen.put(id, pose);
            observePending(level, runtime, state, window);
            return;
        }
    }
    private static boolean observePending(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, Window window) {
        if (window.dirty.isEmpty()) return false;
        var changes = new LinkedHashMap<BlockPosition, Optional<NavigationCell>>();
        for (var position : window.dirty.stream().limit(125).toList())
            changes.put(position, window.captured.containsKey(position) ? window.captured.get(position)
                    : FrontierV3NavigationShapes.capture(level, position));
        var surveyed = Set.copyOf(changes.keySet());
        if (publish(runtime, state, changes)) { window.dirty.removeAll(surveyed); surveyed.forEach(window.captured::remove); }
        return true;
    }
    private static boolean registerDefinitions(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierWorldState state, Window window) {
        var declarations = KnownNavigationBlocks.forState(state);
        if (window.declarations == declarations) return false;
        var additions = new LinkedHashMap<NavigationBlockState, Optional<NavigationCell>>();
        for (var block : declarations.values()) {
            if (state.navigationGeometry().definitions().containsKey(block) || additions.containsKey(block)) continue;
            var nativeState = FrontierV3BlockStates.resolve(block);
            var cell = nativeState.hasOffsetFunction() ? Optional.<NavigationCell>empty()
                    : FrontierV3NavigationShapes.declared(nativeState, new BlockPosition(0, 0, 0), level.getHeight(), level.getMinBuildHeight());
            additions.put(block, cell);
            if (additions.size() == 32) break;
        }
        if (additions.isEmpty()) { window.declarations = declarations; return false; }
        FrontierV3CommandSubmission.submitResult(runtime, "navigation-definitions", NavigationGeometryObserved.OWNER.value(),
                new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(), additions));
        return true;
    }
    static boolean blockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           BlockPos physical, net.minecraft.world.level.block.state.BlockState replacement, boolean committed) {
        var state = runtime.passiveOwnershipState().orElse(null); if (state == null) return true;
        var position = new BlockPosition(physical.getX(), physical.getY(), physical.getZ());
        if (!state.bootstrap().bounds().contains(position)) return true;
        var changes = new LinkedHashMap<BlockPosition, Optional<NavigationCell>>();
        var window = window(level, state);
        if (committed) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
                var cell = position.offset(dx, dy, dz);
                if (window.dirty.contains(cell)) window.captured.put(cell, FrontierV3NavigationShapes.capture(level, cell));
            }
            return true;
        }
        var chunk = level.getChunkSource().getChunkNow(physical.getX() >> 4, physical.getZ() >> 4);
        if (chunk == null || chunk.getBlockState(physical).equals(replacement)) return true;
        var before = FrontierV3NavigationShapes.capture(level, position);
        var after = FrontierV3NavigationShapes.declared(replacement, position, level.getHeight(), level.getMinBuildHeight());
        if (before.isPresent() && after.isPresent() && before.orElseThrow().shape().equals(after.orElseThrow().shape())
                && before.orElseThrow().dry() == after.orElseThrow().dry()) return true;
        if (!state.navigationGeometry().cells().containsKey(position) && after.isPresent()) {
            var planned = KnownPedestrianRouteKnowledge.forFrontier(state).geometry().collisions().orElseThrow().cell(position);
            if (planned.isPresent() && planned.orElseThrow().shape().equals(after.orElseThrow().shape())
                    && planned.orElseThrow().dry() == after.orElseThrow().dry()) return true;
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) {
            var cell = new BlockPosition(physical.getX() + dx, physical.getY() + dy, physical.getZ() + dz);
            if (state.bootstrap().bounds().contains(cell) && (cell.equals(position) || state.navigationGeometry().cells().containsKey(cell))) {
                // Fence before mutation. Re-capture in one bounded observation batch,
                // not a second forced durable write for every block callback.
                changes.put(cell, Optional.empty());
                window.dirty.add(cell);
                window.captured.remove(cell);
            }
        }
        if (!changes.isEmpty() && (runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE || !publish(runtime, state, changes))) {
            if (!window.fenceRejected) io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                    "PMV3_NAVIGATION_WRITE_WAIT position={} geometryRevision={} reason=KNOWLEDGE_FENCE_NOT_ADMITTED runtime={}",
                    position, state.navigationGeometry().revision(), runtime.status());
            window.fenceRejected = true;
            return false; // Refuse this physical write, never kill the server or mutate without its fence.
        }
        window.fenceRejected = false;
        return true;
    }
    private static boolean publish(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                   Map<BlockPosition, Optional<NavigationCell>> observations) {
        var known = KnownPedestrianRouteKnowledge.forFrontier(state).geometry().collisions().orElseThrow();
        observations.entrySet().removeIf(entry -> {
            if (state.navigationGeometry().cells().containsKey(entry.getKey())) return false;
            var expected = known.cell(entry.getKey()); var actual = entry.getValue();
            return expected.isPresent() && actual.isPresent() && expected.orElseThrow().shape().equals(actual.orElseThrow().shape())
                    && expected.orElseThrow().dry() == actual.orElseThrow().dry();
        });
        observations.entrySet().removeIf(entry -> Objects.equals(state.navigationGeometry().cells().get(entry.getKey()), entry.getValue()));
        if (observations.isEmpty()) return true;
        return FrontierV3CommandSubmission.submitResult(runtime, "navigation-geometry", NavigationGeometryObserved.OWNER.value(),
                new NavigationGeometryObserved(state.navigationGeometry().revision(), observations)) instanceof CommandResult.Accepted;
    }
}
