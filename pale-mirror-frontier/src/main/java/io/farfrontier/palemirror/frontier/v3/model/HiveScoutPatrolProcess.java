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
    private static final int RADIUS = 48, STEP = 12;

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
            BlockPosition prior = state.actorLocations().get(scout.id()).position();
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScoutPatrolAdvanced(scout.id(), action.dueAt().ticks(),
                    nextPosition(state, scout, prior), java.util.Optional.of(prior))));
        }
        events.add(new ProposedEvent(scout.id(), new ScheduleEffect.Created(patrol(scout.id(), ordinal + 1,
                Math.addExact(action.dueAt().ticks(), INTERVAL)))));
        return List.copyOf(events);
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ScoutPatrolAdvanced advanced) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("scout patrol has a foreign owner");
        Bioform scout = scout(state, advanced.scoutId());
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        BlockPosition current = state.actorLocations().get(scout.id()).position();
        if (state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || advanced.priorPosition().isPresent() && !advanced.priorPosition().orElseThrow().equals(current)) {
            throw new IllegalArgumentException("scout patrol advance is not a current unleased perimeter step");
        }
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            if (lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.SCOUT_PATROL
                    || advanced.priorPosition().isEmpty() || !lease.goalPosition().equals(advanced.position())
                    || !nextPosition(state, scout, current).equals(advanced.position())) {
                throw new IllegalArgumentException("HOT scout patrol advance lacks its exact cursor lease");
            }
            FrontierWorldState retargeted = AmbientLeaseStateProcess.retarget(state, scout.id(), AmbientGoalKind.SCOUT_PATROL,
                    nextPosition(state, scout, advanced.position()));
            return retargeted.withActorLocation(scout.id(), advanced.position());
        }
        if (advanced.priorPosition().isPresent() && !nextPosition(state, scout, current).equals(advanced.position())) {
            throw new IllegalArgumentException("COLD scout patrol advance skips its exact next cursor");
        }
        if (advanced.priorPosition().isEmpty() && !perimeter(state, scout).contains(advanced.position())) {
            throw new IllegalArgumentException("legacy scout patrol advance is outside its own perimeter");
        }
        return state.withActorLocation(scout.id(), advanced.position());
    }

    static BlockPosition nextPosition(FrontierWorldState state, Bioform scout) {
        return nextPosition(state, scout, state.actorLocations().get(scout.id()).position());
    }

    static BlockPosition nextPosition(FrontierWorldState state, Bioform scout, BlockPosition current) {
        List<BlockPosition> perimeter = perimeter(state, scout); int index = perimeter.indexOf(current);
        if (index >= 0) return perimeter.get((index + 1) % perimeter.size());
        BlockPosition target = perimeter.get(Math.floorMod(scout.id().value().hashCode(), perimeter.size()));
        return stepToward(current, target);
    }

    private static List<BlockPosition> perimeter(FrontierWorldState state, Bioform scout) {
        HiveNest nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(scout.nestId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scout has no seed nest"));
        List<BlockPosition> positions = new java.util.ArrayList<>();
        for (int z = 0; z <= RADIUS; z += STEP) positions.add(nest.anchor().offset(RADIUS, 0, z));
        for (int x = RADIUS - STEP; x >= -RADIUS; x -= STEP) positions.add(nest.anchor().offset(x, 0, RADIUS));
        for (int z = RADIUS - STEP; z >= -RADIUS; z -= STEP) positions.add(nest.anchor().offset(-RADIUS, 0, z));
        for (int x = -RADIUS + STEP; x <= RADIUS; x += STEP) positions.add(nest.anchor().offset(x, 0, -RADIUS));
        for (int z = -RADIUS + STEP; z < 0; z += STEP) positions.add(nest.anchor().offset(RADIUS, 0, z));
        positions.forEach(position -> FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position));
        return List.copyOf(positions);
    }

    private static BlockPosition stepToward(BlockPosition current, BlockPosition target) {
        int x = step(current.x(), target.x()), z = current.x() == target.x() ? step(current.z(), target.z()) : current.z();
        return new BlockPosition(x, current.y(), z);
    }

    private static int step(int current, int target) {
        return current + Math.max(-STEP, Math.min(STEP, target - current));
    }

    private static Bioform scout(FrontierWorldState state, SubjectId scoutId) {
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), scoutId);
        if (scout.role() != BioformRole.SCOUT) throw new IllegalArgumentException("hive patrol requires an exact Scout");
        return scout;
    }
}
