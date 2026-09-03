package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.List;

/**
 * COLD patrol for exact Scouts. The route is a bounded scouting circuit around the Scout's own
 * seed nest; it neither queries human routes nor target operations, and pauses while the same body is HOT.
 */
public final class HiveScoutPatrolProcess {
    private static final int LEGACY_RADIUS = 48, LEGACY_STEP = 12;

    private HiveScoutPatrolProcess() { }

    public static ScheduledAction patrol(SubjectId scoutId, int ordinal, long dueAt) {
        if (ordinal < 1 || dueAt < 0L) throw new IllegalArgumentException("invalid scout patrol schedule");
        return new ScheduledAction(new ScheduleId("schedule:hive-scout-patrol-" + scoutId.value().replace(':', '-') + "-" + ordinal),
                new SimInstant(dueAt), 0, scoutId, "frontier.hive.scout.patrol", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        Bioform scout = scout(state, action.subject());
        if (!action.kind().equals("frontier.hive.scout.patrol")) throw new IllegalArgumentException("scout patrol has an invalid action kind");
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
            BlockPosition prior = state.actorLocations().get(scout.id()).supportingSurface().support();
            events.add(new ProposedEvent(state.bootstrap().hive().id(), new ScoutPatrolAdvanced(scout.id(), action.dueAt().ticks(),
                    nextPosition(state, scout, prior), java.util.Optional.of(prior))));
        }
        events.add(new ProposedEvent(scout.id(), new ScheduleEffect.Created(patrol(scout.id(), ordinal + 1,
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().hiveScoutPatrolInterval())))));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ScoutPatrolAdvanced advanced) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("scout patrol has a foreign owner");
        Bioform scout = scout(state, advanced.scoutId());
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        BlockPosition current = state.actorLocations().get(scout.id()).supportingSurface().support();
        if (state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE
                || advanced.priorPosition().isPresent() && !advanced.priorPosition().orElseThrow().equals(current)) {
            throw new IllegalArgumentException("scout patrol advance is not a current unleased perimeter step");
        }
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED) {
            if (lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.SCOUT_PATROL
                    || advanced.priorPosition().isEmpty() || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(advanced.position())))
                    || !nextPosition(state, scout, current).equals(advanced.position())) {
                throw new IllegalArgumentException("HOT scout patrol advance lacks its exact cursor lease");
            }
            FrontierWorldState retargeted = AmbientLeaseStateProcess.retarget(state, scout.id(), AmbientGoalKind.SCOUT_PATROL,
                    BodyPosition.above(new SurfaceAnchor(nextPosition(state, scout, advanced.position()))));
            return retargeted.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(advanced.position())));
        }
        if (advanced.priorPosition().isPresent() && !nextPosition(state, scout, current).equals(advanced.position())) {
            throw new IllegalArgumentException("COLD scout patrol advance skips its exact next cursor");
        }
        if (advanced.priorPosition().isEmpty() && !legacyPerimeter(state, scout).contains(advanced.position())) {
            throw new IllegalArgumentException("legacy scout patrol advance is outside its own recorded perimeter");
        }
        return state.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(advanced.position())));
    }

    /**
     * Reconciles one observed old HOT lease target without granting an unobserved patrol move.
     * This is retained for deployed leases written before the exact next-cursor contract.
     */
    public static FrontierWorldState reduceLeaseRecovered(FrontierWorldState state, SubjectId subject, ScoutPatrolLeaseRecovered recovered) {
        if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("scout patrol recovery has a foreign owner");
        Bioform scout = scout(state, recovered.scoutId());
        AmbientActorLease lease = state.ambientLeases().get(scout.id());
        BlockPosition current = state.actorLocations().get(scout.id()).supportingSurface().support();
        if (state.actorLocations().get(scout.id()).condition().status() != ActorLifeStatus.ALIVE || lease == null
                || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.SCOUT_PATROL
                || !recovered.priorCanonicalPosition().equals(current) || !recovered.observedLeasePosition().equals(lease.goalBody().supportingSurface().support())
                || !nextPosition(state, scout, recovered.observedLeasePosition()).equals(recovered.nextGoalPosition())) {
            throw new IllegalArgumentException("scout patrol recovery lacks one observed obsolete HOT lease cursor");
        }
        FrontierWorldState observed = state.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(recovered.observedLeasePosition())));
        return AmbientLeaseStateProcess.retarget(observed, scout.id(), AmbientGoalKind.SCOUT_PATROL, BodyPosition.above(new SurfaceAnchor(recovered.nextGoalPosition())));
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

    /** The predecessor-less deployed payload is constrained to its former smaller circuit. */
    private static List<BlockPosition> legacyPerimeter(FrontierWorldState state, Bioform scout) {
        HiveNest nest = state.bootstrap().hive().seedNests().stream().filter(value -> value.id().equals(scout.nestId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("scout has no seed nest"));
        List<BlockPosition> positions = new java.util.ArrayList<>();
        for (int z = 0; z <= LEGACY_RADIUS; z += LEGACY_STEP) positions.add(nest.anchor().offset(LEGACY_RADIUS, 0, z));
        for (int x = LEGACY_RADIUS - LEGACY_STEP; x >= -LEGACY_RADIUS; x -= LEGACY_STEP) positions.add(nest.anchor().offset(x, 0, LEGACY_RADIUS));
        for (int z = LEGACY_RADIUS - LEGACY_STEP; z >= -LEGACY_RADIUS; z -= LEGACY_STEP) positions.add(nest.anchor().offset(-LEGACY_RADIUS, 0, z));
        for (int x = -LEGACY_RADIUS + LEGACY_STEP; x <= LEGACY_RADIUS; x += LEGACY_STEP) positions.add(nest.anchor().offset(x, 0, -LEGACY_RADIUS));
        for (int z = -LEGACY_RADIUS + LEGACY_STEP; z < 0; z += LEGACY_STEP) positions.add(nest.anchor().offset(LEGACY_RADIUS, 0, z));
        return List.copyOf(positions);
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
