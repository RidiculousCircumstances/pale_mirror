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

/** Volatile round-robin observation only; immutable canonical state still owns actor identity/order. */
final class FrontierV3ActorProbeSchedule {
    private static final Map<FrontierV3ServerRuntime<?, ?>, Cursor> CURSORS = new IdentityHashMap<>();

    private FrontierV3ActorProbeSchedule() { }

    static List<SubjectId> next(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state, ServerLevel level, int maximum, int advance) {
        long instant = runtime.canonicalState().orElseThrow().instant().ticks();
        return CURSORS.computeIfAbsent(runtime, ignored -> new Cursor(state))
                .nextDemanded(state, level, instant, maximum, advance);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

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
        private final List<SubjectId> actors;
        private final Map<SubjectId, Long> retainedDemand = new LinkedHashMap<>();
        private final Map<SubjectId, Long> deferred = new LinkedHashMap<>();
        private int next;
        private int nextDemanded;

        Cursor(List<SubjectId> actors) { this.actors = List.copyOf(actors); }

        Cursor(FrontierWorldState state) {
            this(state.actorLocations().keySet().stream().sorted().toList());
        }

        void defer(SubjectId actor) { defer(actor, 0L); }

        void defer(SubjectId actor, long gameTime) {
            if (!actors.contains(actor)) throw new IllegalArgumentException("deferred unknown actor");
            deferred.putIfAbsent(actor, gameTime);
        }

        void probed(SubjectId actor) { deferred.remove(actor); }

        String diagnostic(long gameTime) {
            long oldest = deferred.values().stream().mapToLong(Long::longValue).min().orElse(gameTime);
            return "{\"depth\":" + deferred.size() + ",\"oldestHostTicks\":" + Math.max(0L, gameTime - oldest) + "}";
        }

        List<SubjectId> prioritizeDeferred(List<SubjectId> candidates, int maximum) {
            LinkedHashSet<SubjectId> result = new LinkedHashSet<>();
            var iterator = deferred.keySet().iterator();
            while (iterator.hasNext() && result.size() < maximum) {
                result.add(iterator.next());
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
            for (var player : level.players().stream().filter(value -> !value.isSpectator())
                    .sorted(Comparator.comparing(value -> value.getUUID().toString())).limit(256).toList()) {
                BlockPos observer = player.blockPosition(); int radius = (FrontierV3SceneDemand.RADIUS_BLOCKS + 15) / 16;
                // COLD legitimately advances exact canonical positions.  The old one-time
                // bootstrap chunk index therefore made a returned actor invisible to the HOT
                // scheduler after a lawful COLD interval.  This remains bounded by the fixed
                // canonical actor inventory, and is deliberately not a loaded-entity scan.
                for (SubjectId actor : actors) {
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
