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
    private static final int MAX_PENDING_CELLS = 8192;
    private static final class Window {
        final Object bootstrap;
        Window(Object bootstrap) { this.bootstrap = bootstrap; }
        int cursor;
        List<io.farfrontier.palemirror.frontier.v3.api.SubjectId> roster = List.of();
        Object declarations;
        final Set<NavigationBlockState> checkedDefinitions = new HashSet<>();
        boolean fenceRejected;
        final Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, Pose> seen = new HashMap<>();
        final Set<BlockPosition> dirty = new LinkedHashSet<>();
        Set<BlockPosition> demands = Set.of();
        Iterator<BlockPosition> demandCursor = Collections.emptyIterator();
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
        enqueueDemands(level, runtime, state, window);
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
            int envelopeSize = (radius * 2 + 1) * (radius * 2 + 1) * (top + 3);
            if (window.dirty.size() + envelopeSize > MAX_PENDING_CELLS) {
                // Retire only unavailable *requests*, never their durable unknown fences.
                // The loaded actor roster is rearmed, so a later visit resamples them.
                window.dirty.removeIf(cell -> level.getChunkSource().getChunkNow(cell.x() >> 4, cell.z() >> 4) == null);
                window.seen.clear();
                if (window.dirty.size() + envelopeSize > MAX_PENDING_CELLS) return;
            }
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
        var changes = new LinkedHashMap<BlockPosition, NavigationObservation>();
        // Absence of a loaded chunk is NOT an observation that invalidates retained
        // COLD knowledge. Rotate unavailable requests so a chunk edge cannot fence
        // observation of other actors; actual pre-write unknown fences stay retained.
        var batch = window.dirty.stream().limit(125).toList();
        for (var position : batch) {
            window.dirty.remove(position); window.dirty.add(position);
        }
        changes.putAll(captureAvailable(batch,
                position -> level.getChunkSource().getChunkNow(position.x() >> 4, position.z() >> 4) != null,
                position -> FrontierV3GrayboxExecutor.staticVisibilityComplete(runtime,
                        new net.minecraft.world.level.ChunkPos(position.x() >> 4, position.z() >> 4)),
                position -> FrontierV3NavigationShapes.capture(level, position)));
        if (changes.isEmpty()) return false;
        var surveyed = Set.copyOf(changes.keySet());
        if (publish(runtime, state, changes)) window.dirty.removeAll(surveyed);
        return true;
    }
    private static void enqueueDemands(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime,
            FrontierWorldState state, Window window) {
        var demands = FrontierV3PedestrianPlanning.knowledgeDemands(runtime);
        if (window.demands != demands || !window.demandCursor.hasNext()) {
            window.demands = demands; window.demandCursor = demands.iterator();
        }
        for (int i = 0; i < 125 && window.demandCursor.hasNext() && window.dirty.size() < MAX_PENDING_CELLS; i++) {
            var position = window.demandCursor.next();
            if (!state.bootstrap().bounds().contains(position)
                    || level.getChunkSource().getChunkNow(position.x() >> 4, position.z() >> 4) == null) continue;
            var prior = state.navigationGeometry().cells().get(position);
            if (prior != null && switch (prior.kind()) {
                case KNOWN, WRITE_PENDING, NOT_OBSERVED, CHUNK_UNAVAILABLE, NEIGHBOUR_UNAVAILABLE -> false;
                default -> true;
            }) continue;
            if (prior != null && prior.dependencies().keySet().stream().anyMatch(dependency ->
                    level.getChunkSource().getChunkNow(dependency.x() >> 4, dependency.z() >> 4) == null)) continue;
            window.dirty.add(position);
        }
    }
    /** Native terrain is evidence only after authored geometry has been projected.
     * Before then, air at a declared floor is an incomplete replica, not a world mutation.
     * Keep the request queued, including any contextual shape dependencies. */
    static Map<BlockPosition, NavigationObservation> captureAvailable(List<BlockPosition> positions,
            java.util.function.Predicate<BlockPosition> available,
            java.util.function.Predicate<BlockPosition> projected,
            java.util.function.Function<BlockPosition, NavigationObservation> capture) {
        var result = new LinkedHashMap<BlockPosition, NavigationObservation>();
        for (var position : positions) if (available.test(position) && projected.test(position)) {
            var observation = capture.apply(position);
            if (observation.dependencies().keySet().stream().allMatch(projected)) result.put(position, observation);
        }
        return result;
    }
    private static boolean registerDefinitions(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                               FrontierWorldState state, Window window) {
        var declarations = KnownNavigationBlocks.forState(state);
        if (window.declarations == declarations) return false;
        var additions = new LinkedHashMap<NavigationBlockState, NavigationObservation>();
        var checked = new HashSet<NavigationBlockState>();
        for (var block : declarations.values()) {
            // Definitions are adapter-produced facts. Revalidate once per runtime,
            // including retained UNKNOWN from an older adapter, without loading land.
            if (window.checkedDefinitions.contains(block) || !checked.add(block)) continue;
            var nativeState = FrontierV3BlockStates.resolve(block);
            var cell = FrontierV3NavigationShapes.declared(nativeState, new BlockPosition(0, 0, 0), level.getHeight(), level.getMinBuildHeight());
            if (!Objects.equals(state.navigationGeometry().definitions().get(block), cell)) additions.put(block, cell);
            if (checked.size() == 32) break;
        }
        if (additions.isEmpty()) {
            window.checkedDefinitions.addAll(checked);
            if (checked.isEmpty()) { window.declarations = declarations; return false; }
            return true;
        }
        if (FrontierV3CommandSubmission.submitResult(runtime, "navigation-definitions", NavigationGeometryObserved.OWNER.value(),
                new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(), additions)) instanceof CommandResult.Accepted)
            window.checkedDefinitions.addAll(checked);
        return true;
    }
    static boolean blockWrite(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           BlockPos physical, net.minecraft.world.level.block.state.BlockState replacement, boolean committed) {
        var state = runtime.passiveOwnershipState().orElse(null); if (state == null) return true;
        var position = new BlockPosition(physical.getX(), physical.getY(), physical.getZ());
        if (!state.bootstrap().bounds().contains(position)) return true;
        var changes = new LinkedHashMap<BlockPosition, NavigationObservation>();
        var window = window(level, state);
        if (committed) {
            // Capture at publication time: later neighbour updates in the same physical
            // operation must not leave a cached pre-update dependent shape.
            return true;
        }
        var chunk = level.getChunkSource().getChunkNow(physical.getX() >> 4, physical.getZ() >> 4);
        if (chunk == null || chunk.getBlockState(physical).equals(replacement)) return true;
        var before = FrontierV3NavigationShapes.capture(level, position).cell();
        var after = FrontierV3NavigationShapes.declared(replacement, position, level.getHeight(), level.getMinBuildHeight()).cell();
        // Retained contextual samples own exact neighbour dependencies. Invalidate
        // them before the write even when this block's own collision stays equal.
        int radius = NavigationObservation.DEPENDENCY_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) for (int dz = -radius; dz <= radius; dz++) {
            var dependent = position.offset(dx, dy, dz);
            var evidence = state.navigationGeometry().cells().get(dependent);
            if (evidence != null && evidence.dependencies().containsKey(position))
                changes.put(dependent, NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING, evidence.dependencies()));
        }
        if (changes.isEmpty() && before.isPresent() && after.isPresent() && before.orElseThrow().shape().equals(after.orElseThrow().shape())
                && before.orElseThrow().dry() == after.orElseThrow().dry()) return true;
        if (changes.isEmpty() && !state.navigationGeometry().cells().containsKey(position) && after.isPresent()) {
            var planned = KnownPedestrianRouteKnowledge.forFrontier(state).geometry().collisions().orElseThrow().cell(position);
            if (planned.isPresent() && planned.orElseThrow().shape().equals(after.orElseThrow().shape())
                    && planned.orElseThrow().dry() == after.orElseThrow().dry()) return true;
        }
        changes.put(position, NavigationObservation.unavailable(NavigationObservation.Kind.WRITE_PENDING));
        window.dirty.addAll(changes.keySet());
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
                                   Map<BlockPosition, NavigationObservation> observations) {
        var confirmed = new HashSet<BlockPosition>();
        observations.forEach((position, evidence) -> { if (evidence.cell().isPresent()) confirmed.add(position); });
        // A pre-write UNKNOWN fence has no shape to compare. Do not rebuild global
        // navigation merely to publish this local invalidation. Resolve knowledge
        // only when an actual new shape could be omitted as redundant.
        boolean needsComparison = observations.entrySet().stream().anyMatch(entry ->
                !state.navigationGeometry().cells().containsKey(entry.getKey())
                        && entry.getValue().dependencies().isEmpty() && entry.getValue().cell().isPresent());
        var known = needsComparison ? KnownPedestrianRouteKnowledge.forFrontier(state).geometry().collisions().orElseThrow() : null;
        observations.entrySet().removeIf(entry -> {
            if (state.navigationGeometry().cells().containsKey(entry.getKey()) || !entry.getValue().dependencies().isEmpty()
                    || entry.getValue().cell().isEmpty()) return false;
            var expected = known.cell(entry.getKey()); var actual = entry.getValue().cell();
            return expected.isPresent() && actual.isPresent() && expected.orElseThrow().shape().equals(actual.orElseThrow().shape())
                    && expected.orElseThrow().dry() == actual.orElseThrow().dry();
        });
        observations.entrySet().removeIf(entry -> Objects.equals(state.navigationGeometry().cells().get(entry.getKey()), entry.getValue()));
        // A worker on an older immutable view may finish AFTER the publication
        // which supplied its missing fact. A demand-driven no-op resample must
        // still wake that wait; it needs no duplicate durable geometry event.
        if (observations.isEmpty()) {
            FrontierV3PedestrianPlanning.geometryObserved(runtime, confirmed);
            return true;
        }
        boolean accepted = FrontierV3CommandSubmission.submitResult(runtime, "navigation-geometry", NavigationGeometryObserved.OWNER.value(),
                new NavigationGeometryObserved(state.navigationGeometry().revision(), observations)) instanceof CommandResult.Accepted;
        if (accepted) {
            confirmed.addAll(observations.keySet());
            FrontierV3PedestrianPlanning.geometryObserved(runtime, confirmed);
        }
        return accepted;
    }
}
