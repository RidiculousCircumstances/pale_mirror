package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Volatile round-robin observation only; immutable canonical state still owns actor identity/order. */
final class FrontierV3ActorProbeSchedule {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();

    private FrontierV3ActorProbeSchedule() { }

    static List<SubjectId> next(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, ServerLevel level, int maximum, int advance) {
        long instant = runtime.canonicalState().orElseThrow().instant().ticks();
        Cursor cursor = CURSORS.computeIfAbsent(runtime, ignored -> new Cursor(state));
        cursor.synchronizeRoster(state);
        return cursor.nextDemanded(state, level, instant, maximum, advance);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    /** Evidence arrived for one declared actor. This schedules observation only;
     * the body owner revalidates the receipt and every custody fence when probed. */
    static void wake(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state,
                     SubjectId actor, long gameTime) {
        Cursor cursor = CURSORS.computeIfAbsent(runtime, ignored -> new Cursor(state));
        cursor.synchronizeRoster(state);
        cursor.wake(actor, gameTime);
    }

    /** A durable native receipt schedules observation on the server thread; it grants no body/activity permission. */
    static void whenDurable(ServerLevel level, io.farfrontier.palemirror.frontier.v3.api.WorldId world,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId body, long residence,
            java.util.concurrent.CompletableFuture<?> durable) {
        durable.thenRun(() -> level.getServer().execute(() -> {
            var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
            var current = runtime == null ? null : runtime.decodedState().orElse(null);
            if (current == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE
                    || !current.bootstrap().worldId().equals(world)
                    || !current.actorLocations().containsKey(body.actorId())
                    || !body.equals(io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(current, body.actorId()))
                    || !FrontierV3AmbientCarrierLedger.get(level, world).currentBodyResidence(body.actorId(), residence)) return;
            wake(runtime, current, body.actorId(), level.getGameTime());
        }));
    }

    static void defer(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actor, long gameTime) {
        Cursor cursor = CURSORS.get(runtime);
        if (cursor == null) throw new IllegalStateException("deferred actor without an active probe cursor");
        cursor.defer(actor, gameTime);
    }

    static void probed(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actor) {
        CURSORS.get(runtime).probed(actor);
    }

    static String diagnostic(FrontierV3ServerRuntime<?, ?> runtime, long gameTime) {
        Cursor cursor = CURSORS.get(runtime);
        return cursor == null ? "{\"depth\":0,\"oldestHostTicks\":0}" : cursor.diagnostic(gameTime);
    }

    static final class Cursor {
        private List<SubjectId> actors;
        private Set<SubjectId> actorIds;
        private Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.ActorLocation> observedLocations;
        private final Map<SubjectId, Long> retainedDemand = new LinkedHashMap<>();
        private final Map<SubjectId, Long> deferred = new LinkedHashMap<>();
        private final FrontierV3ActorSpatialCandidates spatial = new FrontierV3ActorSpatialCandidates();
        // Both lanes belong to this one observer cursor. Alternate actual probes,
        // so receipt-driven continuation and ordinary census cannot starve each other.
        private final Map<SubjectId, Long> awakened = new LinkedHashMap<>();
        private boolean preferWake = true;
        private int next;
        private int nextDemanded;

        Cursor(List<SubjectId> actors) {
            this.actors = List.copyOf(actors);
            this.actorIds = Set.copyOf(actors);
            if (actorIds.size() != actors.size()) throw new IllegalArgumentException("duplicate probe actor");
        }

        Cursor(FrontierWorldState state) {
            this(state.actorLocations().keySet().stream().sorted().toList());
            observedLocations = state.actorLocations();
        }

        /** Canonical membership selects probes only; it never grants birth or body permission.
         * Location-only updates preserve the cursor, queues and order without re-sorting. */
        void synchronizeRoster(FrontierWorldState state) {
            if (observedLocations == state.actorLocations()) return;
            synchronizeRoster(state.actorLocations().keySet());
            observedLocations = state.actorLocations();
        }

        void synchronizeRoster(Set<SubjectId> currentActors) {
            if (actorIds.equals(currentActors)) return;
            SubjectId nextActor = actors.isEmpty() ? null : actors.get(next);
            actors = currentActors.stream().sorted().toList();
            actorIds = Set.copyOf(currentActors);
            // Keep the next existing actor at the admission front. If it retired,
            // continue at its sorted successor instead of restarting at actor zero.
            int successor = nextActor == null ? 0 : java.util.Collections.binarySearch(actors, nextActor);
            next = actors.isEmpty() ? 0 : Math.floorMod(successor < 0 ? -successor - 1 : successor, actors.size());
            retainedDemand.keySet().retainAll(actorIds);
            deferred.keySet().retainAll(actorIds);
            awakened.keySet().retainAll(actorIds);
        }

        void defer(SubjectId actor) { defer(actor, 0L); }

        void defer(SubjectId actor, long gameTime) {
            if (!actorIds.contains(actor)) throw new IllegalArgumentException("deferred unknown actor");
            if (!awakened.containsKey(actor)) deferred.putIfAbsent(actor, gameTime);
        }

        void wake(SubjectId actor, long gameTime) {
            if (!actorIds.contains(actor)) throw new IllegalArgumentException("awakened unknown actor");
            Long prior = deferred.remove(actor);
            awakened.putIfAbsent(actor, prior == null ? gameTime : prior);
        }

        void probed(SubjectId actor) {
            deferred.remove(actor); awakened.remove(actor); preferWake = !preferWake;
        }

        String diagnostic(long gameTime) {
            long oldest = java.util.stream.Stream.concat(deferred.values().stream(), awakened.values().stream())
                    .mapToLong(Long::longValue).min().orElse(gameTime);
            return "{\"depth\":" + (deferred.size() + awakened.size()) + ",\"oldestHostTicks\":" + Math.max(0L, gameTime - oldest) + "}";
        }

        List<SubjectId> prioritizeDeferred(List<SubjectId> candidates, int maximum) {
            LinkedHashSet<SubjectId> result = new LinkedHashSet<>();
            var ordinary = deferred.keySet().iterator();
            var ready = awakened.keySet().iterator();
            boolean takeReady = preferWake;
            while (result.size() < maximum && (ordinary.hasNext() || ready.hasNext())) {
                if (ready.hasNext() && (takeReady || !ordinary.hasNext())) result.add(ready.next());
                else result.add(ordinary.next());
                takeReady = !takeReady;
            }
            for (SubjectId actor : candidates) {
                if (result.size() >= maximum) break;
                result.add(actor);
            }
            return List.copyOf(result);
        }

        List<SubjectId> next(int maximum, int advance) {
            if (maximum < 1 || advance < 1 || advance > maximum) throw new IllegalArgumentException("invalid probe window");
            if (actors.isEmpty()) return List.of();
            List<SubjectId> slice = new ArrayList<>(Math.min(maximum, actors.size()));
            int start = next;
            for (int count = 0; count < Math.min(maximum, actors.size()); count++) {
                slice.add(actors.get((start + count) % actors.size()));
            }
            next = (start + advance) % actors.size();
            return List.copyOf(slice);
        }

        private List<SubjectId> nextDemanded(FrontierWorldState state, ServerLevel level, long instant, int maximum, int advance) {
            List<SubjectId> demanded = demanded(state, level, instant);
            retainDemanded(demanded, state, level.getGameTime());
            demanded = retainedDemanded(demanded, state, level.getGameTime());
            if (demanded.isEmpty()) return prioritizeDeferred(next(maximum, advance), maximum);
            List<SubjectId> result = new ArrayList<>(maximum);
            int start = Math.floorMod(nextDemanded, demanded.size());
            int demandBudget = Math.min(FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK, maximum);
            for (int count = 0; count < Math.min(demandBudget, demanded.size()); count++) result.add(demanded.get((start + count) % demanded.size()));
            nextDemanded = (start + demandBudget) % demanded.size();
            for (SubjectId actor : next(maximum, advance)) if (result.size() < maximum && !result.contains(actor)) result.add(actor);
            return prioritizeDeferred(result, maximum);
        }

        private List<SubjectId> demanded(FrontierWorldState state, ServerLevel level, long instant) {
            LinkedHashSet<SubjectId> result = new LinkedHashSet<>();
            if (level.players().isEmpty()) return List.of();
            spatial.synchronize(state);
            for (var player : level.players().stream().filter(value -> !value.isSpectator())
                    .sorted(Comparator.comparing(value -> value.getUUID().toString())).limit(256).toList()) {
                BlockPos observer = player.blockPosition();
                // Index current bases and every chunk in a retained COLD segment,
                // then project only nearby candidates at the actual simulation tick.
                // This is not the old bootstrap-only index and does not load chunks.
                for (SubjectId actor : spatial.near(observer.getX(), observer.getZ(), FrontierV3SceneDemand.RADIUS_BLOCKS)) {
                    var location = state.actorLocations().get(actor);
                    if (location == null) continue;
                    var body = state.actorMovements().containsKey(actor)
                            ? io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.bodyAt(state, actor, instant)
                            : ResidentMealProcess.bodyAt(state, actor, instant);
                    if (observer.closerThan(new BlockPos(body.x(), body.y(), body.z()),
                            FrontierV3SceneDemand.RADIUS_BLOCKS)) result.add(actor);
                }
            }
            return List.copyOf(result);
        }

        private void retainDemanded(List<SubjectId> demanded, FrontierWorldState state, long gameTime) {
            for (SubjectId actor : demanded) {
                if (retainedDemand.size() == FrontierV3AmbientPendingAdmissions.MAX_ENTRIES && !retainedDemand.containsKey(actor)) {
                    retainedDemand.remove(retainedDemand.keySet().iterator().next());
                }
                if (state.ambientLeases().get(actor) != null) retainedDemand.put(actor, gameTime + FrontierV3AmbientActorExecutor.DRAIN_HYSTERESIS_TICKS + 1L);
            }
        }

        private List<SubjectId> retainedDemanded(List<SubjectId> demanded, FrontierWorldState state, long gameTime) {
            retainedDemand.entrySet().removeIf(entry -> entry.getValue() < gameTime
                    || state.ambientLeases().get(entry.getKey()) == null
                    || state.ambientLeases().get(entry.getKey()).status() == io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.CLOSED);
            LinkedHashSet<SubjectId> result = new LinkedHashSet<>(demanded); result.addAll(retainedDemand.keySet());
            return List.copyOf(result);
        }
    }
}
