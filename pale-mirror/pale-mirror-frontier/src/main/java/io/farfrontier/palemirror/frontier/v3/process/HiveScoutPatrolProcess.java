package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;

/**
 * COLD patrol for exact Scouts. The route is a bounded scouting circuit around the Scout's own
 * seed nest; it neither queries human routes nor target operations, and pauses while the same body is HOT.
 */
public final class HiveScoutPatrolProcess {

    private HiveScoutPatrolProcess() { }

    public static Optional<ActorExecutionId> active(FrontierWorldState state, SubjectId actor) {
        return Optional.ofNullable(state.actorExecutions().actors().get(actor)).flatMap(ActorExecution::current)
                .filter(id -> id.activityKind() == ActorActivityKind.SCOUT_PATROL && id.activityOwnerId().equals(actor));
    }

    public static ScoutPatrolStarted start(FrontierWorldState state, SubjectId actor) {
        return new ScoutPatrolStarted(state.actorExecutions().next(actor, ActorActivityKind.SCOUT_PATROL, actor));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ScoutPatrolStarted started) {
        var id = started.executionId();
        scout(state, id.actorId());
        if (!subject.equals(state.bootstrap().hive().id()) || active(state, id.actorId()).isPresent()
                || !HivePhysiologySupport.availableForIndependentOperation(state, id.actorId())
                || ActorExecutionCoordinator.sceneOwns(state, id.actorId()))
            throw new IllegalArgumentException("scout patrol admission has no independent exact living scout");
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, id)
                .commit(state, FrontierWorldStateUpdate.begin());
    }

    public static ActorExecutionId requireExecution(FrontierWorldState state, SubjectId actor) {
        var id = active(state, actor).orElseThrow(() -> new IllegalArgumentException("scout has no current patrol execution"));
        state.actorExecutions().requireCurrent(id);
        ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.SCOUT_PATROL).validateReference(state, id);
        return id;
    }

    public static ScheduledAction patrol(SubjectId scoutId, int ordinal, long dueAt) {
        if (ordinal < 1 || dueAt < 0L) throw new IllegalArgumentException("invalid scout patrol schedule");
        return new ScheduledAction(new ScheduleId("schedule:hive-scout-patrol-" + scoutId.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, scoutId, "frontier.hive.scout.patrol", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        Bioform scout = scout(state, action.subject());
        if (!action.kind().equals("frontier.hive.scout.patrol")) throw new IllegalArgumentException("scout patrol has an invalid action kind");
        // This exact scheduled cursor ceases to exist when its owner dies.  It is not a
        // hive-wide effect that may attempt a post-mortem move and quarantine the world.
        if (state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE) {
            return List.of(new ProposedEvent(scout.id(), new ScheduleEffect.Cancelled(action.id())));
        }
        if (!HivePhysiologySupport.permitsAmbientLease(state, scout.id())) {
            return List.of(new ProposedEvent(scout.id(), new ScheduleEffect.Cancelled(action.id())));
        }
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        if (active(state, scout.id()).isEmpty()) {
            var started = start(state, scout.id());
            try { state = reduceStarted(state, state.bootstrap().hive().id(), started); }
            catch (IllegalArgumentException unavailable) {
                return List.of(new ProposedEvent(scout.id(), new ScheduleEffect.Created(patrol(scout.id(), ordinal + 1,
                        Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveScoutPatrolInterval())))));
            }
            events.add(new ProposedEvent(state.bootstrap().hive().id(), started));
        }
        if (ActorExecutionCoordinator.coldAvailable(state, scout.id())) {
            BlockPosition prior = state.actorLocations().get(scout.id()).supportingSurface().support();
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScoutPatrolAdvanced(requireExecution(state, scout.id()), action.dueAt().ticks(),
                    nextPosition(state, scout, prior), prior)));
        }
        events.add(new ProposedEvent(scout.id(), new ScheduleEffect.Created(patrol(scout.id(), ordinal + 1,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveScoutPatrolInterval())))));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ScoutPatrolAdvanced advanced) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("scout patrol has a foreign owner");
        Bioform scout = scout(state, advanced.scoutId());
        state.actorExecutions().requireCurrent(advanced.executionId());
        if (!requireExecution(state, scout.id()).equals(advanced.executionId()))
            throw new IllegalArgumentException("scout patrol advance has a foreign execution");
        if (!HivePhysiologySupport.permitsAmbientLease(state, scout.id())) {
            throw new IllegalArgumentException("cocoon-retained scout may not advance a patrol");
        }
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        BlockPosition current = state.actorLocations().get(scout.id()).supportingSurface().support();
        if (state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || !advanced.priorPosition().equals(current)) {
            throw new IllegalArgumentException("scout patrol advance is not a current unleased perimeter step");
        }
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            if (lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.SCOUT_PATROL
                    || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(advanced.position())))
                    || !nextPosition(state, scout, current).equals(advanced.position())) {
                throw new IllegalArgumentException("HOT scout patrol advance lacks its exact cursor lease");
            }
            FrontierWorldState retargeted = AmbientLeaseStateProcess.retarget(state, scout.id(), AmbientGoalKind.SCOUT_PATROL,
                    BodyPosition.above(new SurfaceAnchor(nextPosition(state, scout, advanced.position()))));
            return retargeted.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(advanced.position())));
        }
        if (!ActorExecutionCoordinator.coldAvailable(state, scout.id()))
            throw new IllegalArgumentException("COLD scout patrol cannot advance a physically retained actor");
        if (!nextPosition(state, scout, current).equals(advanced.position())) {
            throw new IllegalArgumentException("COLD scout patrol advance skips its exact next cursor");
        }
        return state.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(advanced.position())));
    }

    public static BlockPosition nextPosition(FrontierWorldState state, Bioform scout) {
        return nextPosition(state, scout, state.actorLocations().get(scout.id()).supportingSurface().support());
    }

    /** Exact adapter-facing cursor lookup; it does not mutate state or inspect Minecraft. */
    public static BlockPosition nextPosition(FrontierWorldState state, SubjectId scoutId, BlockPosition current) {
        return nextPosition(state, scout(state, scoutId), current);
    }

    public static BlockPosition nextPosition(FrontierWorldState state, Bioform scout, BlockPosition current) {
        List<BlockPosition> perimeter = perimeter(state, scout); int index = perimeter.indexOf(current);
        if (index >= 0) return perimeter.get((index + 1) % perimeter.size());
        BlockPosition target = perimeter.get(Math.floorMod(scout.id().value().hashCode(), perimeter.size()));
        return stepToward(state, current, target);
    }

    private static List<BlockPosition> perimeter(FrontierWorldState state, Bioform scout) {
        HiveNest nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(scout.nestId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scout has no seed nest"));
        int radius = circuitRadius(state.bootstrap().bounds(), nest, state.bootstrap().ruleset());
        List<BlockPosition> positions = new java.util.ArrayList<>();
        int step = state.bootstrap().ruleset().spatial().hiveScoutPatrolStep();
        for (int z = 0; z <= radius; z += step) positions.add(nest.anchor().offset(radius, 0, z));
        for (int x = radius - step; x >= -radius; x -= step) positions.add(nest.anchor().offset(x, 0, radius));
        for (int z = radius - step; z >= -radius; z -= step) positions.add(nest.anchor().offset(-radius, 0, z));
        for (int x = -radius + step; x <= radius; x += step) positions.add(nest.anchor().offset(x, 0, -radius));
        for (int z = -radius + step; z < 0; z += step) positions.add(nest.anchor().offset(radius, 0, z));
        positions.forEach(position -> FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position));
        return List.copyOf(positions);
    }

    private static int circuitRadius(WorldBounds bounds, HiveNest nest, FrontierRuleset ruleset) {
        BlockPosition anchor = nest.anchor();
        int boundary = Math.min(Math.min(anchor.x() - bounds.minX(), bounds.maxXExclusive() - 1 - anchor.x()),
                Math.min(anchor.z() - bounds.minZ(), bounds.maxZExclusive() - 1 - anchor.z()));
        int step = ruleset.spatial().hiveScoutPatrolStep();
        int radius = Math.min(ruleset.spatial().hiveScoutPatrolRadius(), Math.floorDiv(boundary, step) * step);
        if (radius < step) throw new IllegalStateException("hive seed nest has no bounded scout circuit");
        return radius;
    }

    private static BlockPosition stepToward(FrontierWorldState state, BlockPosition current, BlockPosition target) {
        int x = step(state, current.x(), target.x()), z = current.x() == target.x() ? step(state, current.z(), target.z()) : current.z();
        return new BlockPosition(x, current.y(), z);
    }

    private static int step(FrontierWorldState state, int current, int target) {
        int step = state.bootstrap().ruleset().spatial().hiveScoutPatrolStep();
        return current + Math.max(-step, Math.min(step, target - current));
    }

    private static Bioform scout(FrontierWorldState state, SubjectId scoutId) {
        Bioform scout = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), scoutId);
        if (!scout.isScout()) throw new IllegalArgumentException("hive patrol requires an exact Sentinel Scout");
        return scout;
    }
}
