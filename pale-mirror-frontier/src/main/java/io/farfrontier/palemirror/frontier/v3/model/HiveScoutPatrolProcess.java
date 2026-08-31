package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;

/**
 * COLD patrol for exact Scouts. The route is a bounded perimeter of the Scout's own seed nest;
 * it neither queries human routes nor target operations, and pauses while the same body is HOT.
 */
final class HiveScoutPatrolProcess {
    static final long INTERVAL = 400L;
    private static final int RADIUS = 48;
    private static final int[][] PERIMETER = {{RADIUS, 0}, {RADIUS, RADIUS}, {0, RADIUS}, {-RADIUS, RADIUS},
            {-RADIUS, 0}, {-RADIUS, -RADIUS}, {0, -RADIUS}, {RADIUS, -RADIUS}};

    private HiveScoutPatrolProcess() { }

    static ScheduledAction patrol(SubjectId scoutId, int ordinal, long dueAt) {
        if (ordinal < 1 || dueAt < 0L) throw new IllegalArgumentException("invalid scout patrol schedule");
        return new ScheduledAction(new ScheduleId("schedule:hive-scout-patrol-" + scoutId.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, scoutId, "frontier.hive.scout.patrol", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        Bioform scout = scout(state, action.subject());
        if (!action.kind().equals("frontier.hive.scout.patrol")) throw new IllegalArgumentException("scout patrol has an invalid action kind");
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
            long phase = Math.floorDiv(action.dueAt().ticks(), INTERVAL);
            BlockPosition position = positionAt(state, scout, phase);
            if (!state.actorLocations().get(scout.id()).position().equals(position)) {
                events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScoutPatrolAdvanced(scout.id(), phase, position)));
            }
        }
        events.add(new ProposedEvent(scout.id(), new ScheduleEffect.Created(patrol(scout.id(), ordinal + 1,
                Math.addExact(action.dueAt().ticks(), INTERVAL)))));
        return List.copyOf(events);
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ScoutPatrolAdvanced advanced) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("scout patrol has a foreign owner");
        Bioform scout = scout(state, advanced.scoutId());
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        if ((lease != null && lease.status() != AmbientLeaseStatus.CLOSED)
                || state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || !positionAt(state, scout, advanced.phase()).equals(advanced.position())) {
            throw new IllegalArgumentException("scout patrol advance is not a current unleased perimeter step");
        }
        return state.withActorLocation(scout.id(), advanced.position());
    }

    static BlockPosition positionAt(FrontierWorldState state, Bioform scout, long phase) {
        HiveNest nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(scout.nestId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scout has no seed nest"));
        int offset = Math.floorMod(scout.id().value().hashCode(), PERIMETER.length);
        int index = Math.floorMod(phase + offset, PERIMETER.length);
        int[] step = PERIMETER[index];
        BlockPosition position = nest.anchor().offset(step[0], 0, step[1]);
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position);
        return position;
    }

    private static Bioform scout(FrontierWorldState state, SubjectId scoutId) {
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), scoutId);
        if (scout.role() != BioformRole.SCOUT) throw new IllegalArgumentException("hive patrol requires an exact Scout");
        return scout;
    }
}
