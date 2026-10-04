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
                .commit(state, FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().withScoutPatrol(
                        new ScoutPatrolJourney(id, 1L, new SurfaceAnchor(nextPosition(state, scout(state, id.actorId()),
                                state.actorLocations().get(id.actorId()).supportingSurface().support()))))));
    }

    public static ActorExecutionId requireExecution(FrontierWorldState state, SubjectId actor) {
        var id = active(state, actor).orElseThrow(() -> new IllegalArgumentException("scout has no current patrol execution"));
        state.actorExecutions().requireCurrent(id);
        ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.SCOUT_PATROL).validateReference(state, id);
        return id;
    }

    public static ScoutPatrolJourney journey(FrontierWorldState state, SubjectId actor) {
        var execution = requireExecution(state, actor);
        var journey = state.strategicPlans().scoutPatrols().get(actor);
        if (journey == null || !journey.executionId().equals(execution))
            throw new IllegalArgumentException("scout has no current declared journey");
        return journey;
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
            var journey = journey(state, scout.id());
            var prior = state.actorLocations().get(scout.id()).supportingSurface();
            try {
                HiveGroundNavigation.scoutRoute(state, scout, prior, journey.target());
                events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScoutPatrolAdvanced(journey.executionId(),
                        journey.goalRevision(), action.dueAt().ticks(), journey.target(), prior, Optional.empty())));
            } catch (HiveGroundNavigation.RouteUnavailable unavailable) {
                // Known obstruction holds the same goal; the existing due action is its bounded retry.
            }
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
        var journey = journey(state, scout.id());
        if (journey.goalRevision() != advanced.goalRevision() || !journey.target().equals(advanced.target()))
            throw new IllegalArgumentException("scout arrival has a stale or foreign semantic goal");
        var current = state.actorLocations().get(scout.id());
        if (current.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("dead scout cannot arrive");
        FrontierWorldState prepared = state;
        if (advanced.hotArrival().isPresent()) {
            var witness = advanced.hotArrival().orElseThrow();
            ActorBodyAuthority.requireActuation(state, witness.actuation());
            AmbientActorLease lease = state.ambientLeases().get(scout.id());
            if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.SCOUT_PATROL
                    || lease.revision() != witness.scopeRevision() || !lease.goalBody().equals(journey.target().standingBody())
                    || !current.body().equals(journey.target().standingBody()))
                throw new IllegalArgumentException("HOT scout arrival lacks independently inspected exact body/scope");
        } else {
            if (!ActorExecutionCoordinator.coldAvailable(state, scout.id()) || !current.supportingSurface().equals(advanced.priorSurface()))
                throw new IllegalArgumentException("COLD scout arrival has a held body or stale departure");
            HiveGroundNavigation.scoutRoute(state, scout, current.supportingSurface(), journey.target());
            prepared = state.withActorBody(scout.id(), journey.target().standingBody());
        }
        var next = journey.next(new SurfaceAnchor(nextPosition(prepared, scout, journey.target().support())));
        prepared = prepared.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(prepared.strategicPlans().withScoutPatrol(next)));
        if (advanced.hotArrival().isPresent())
            prepared = AmbientLeaseStateProcess.retarget(prepared, scout.id(), AmbientGoalKind.SCOUT_PATROL, next.target().standingBody());
        return prepared;
    }

    public static BlockPosition nextPosition(FrontierWorldState state, Bioform scout) {
        if (active(state, scout.id()).isPresent()) return journey(state, scout.id()).target().support();
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
        var ground = KnownPedestrianGround.forFrontier(state);
        return positions.stream().map(position -> ground.at(position.x(), position.z()).support()).toList();
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
        return KnownPedestrianGround.forFrontier(state).at(x, z).support();
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
