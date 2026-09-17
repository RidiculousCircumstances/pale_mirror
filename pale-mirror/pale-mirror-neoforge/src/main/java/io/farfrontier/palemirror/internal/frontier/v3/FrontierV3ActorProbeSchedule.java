package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
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
        return CURSORS.computeIfAbsent(runtime, ignored -> new Cursor(state)).nextDemanded(state, level, maximum, advance);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) { CURSORS.remove(runtime); }

    static final class Cursor {
        private final List<SubjectId> actors;
        private final Map<SubjectId, Long> retainedDemand = new LinkedHashMap<>();
        private int next;
        private int nextDemanded;

        Cursor(List<SubjectId> actors) { this.actors = List.copyOf(actors); }

        Cursor(FrontierWorldState state) {
            this(state.actorLocations().keySet().stream().sorted().toList());
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

        private List<SubjectId> nextDemanded(FrontierWorldState state, ServerLevel level, int maximum, int advance) {
            List<SubjectId> demanded = demanded(state, level);
            retainDemanded(demanded, state, level.getGameTime());
            demanded = retainedDemanded(demanded, state, level.getGameTime());
            if (demanded.isEmpty()) return next(maximum, advance);
            List<SubjectId> result = new ArrayList<>(maximum);
            int start = Math.floorMod(nextDemanded, demanded.size());
            int demandBudget = Math.min(FrontierV3AmbientAdmissionPolicy.MAX_ACTORS_PER_TICK, maximum);
            for (int count = 0; count < Math.min(demandBudget, demanded.size()); count++) result.add(demanded.get((start + count) % demanded.size()));
            nextDemanded = (start + demandBudget) % demanded.size();
            for (SubjectId actor : next(maximum, advance)) if (result.size() < maximum && !result.contains(actor)) result.add(actor);
            return List.copyOf(result);
        }

        private List<SubjectId> demanded(FrontierWorldState state, ServerLevel level) {
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
                    if (location != null && observer.closerThan(new BlockPos(location.body().x(), location.body().y(), location.body().z()),
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
