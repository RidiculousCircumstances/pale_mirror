package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import net.minecraft.world.phys.Vec3;

/** Pure ambient-idle target grammar; directed work keeps its retained lease target. */
final class FrontierV3AmbientActorLocalTargets {
    /**
     * Presentation-only idling may not cross the actor's retained support column.  A wider
     * orbit would turn a noncanonical visual flourish into a different block position, making
     * a later exact HOT hand-off depend on rounding or collision push-out rather than on the
     * canonical cursor.  Purposeful travel uses a retained topology and the normal motion
     * provider instead.
     */
    // A Villager needs a visibly legible local motion envelope, but its centre still stays
    // within the single retained support surface accepted by FrontierV3SurfaceObservation.
    private static final double MAX_PRESENTATION_RADIUS = 0.40D;

    private FrontierV3AmbientActorLocalTargets() { }

    static Vec3 localTarget(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease, long gameTime) {
        if (state == null || actorId == null) throw new IllegalArgumentException("ambient local target requires state and actor");
        var location = state.actorLocations().get(actorId);
        if (location == null) throw new IllegalArgumentException("ambient local target has no canonical actor location: " + actorId.value());
        return localTargetAt(state, actorId, location.body(), lease, gameTime);
    }

    /**
     * Same target grammar with an explicit canonical feet cell for physical fixtures.  Production
     * obtains that cell only through {@link #localTarget(FrontierWorldState, SubjectId,
     * AmbientActorLease, long)}; this overload lets a GameTest place a bounded synthetic arena
     * without pretending that its far-away GameTest coordinates belong to the finite world.
     */
    static Vec3 localTargetAt(FrontierWorldState state, SubjectId actorId, BodyPosition currentBody,
                              AmbientActorLease lease, long gameTime) {
        if (state == null || actorId == null || currentBody == null || lease == null) {
            throw new IllegalArgumentException("ambient local target requires canonical inputs");
        }
        if (directedGoal(lease)) {
            // Minecraft's Vec3 is the entity's feet position.  A support-column Y is one
            // block below that position; using it here made every ambient goal a phantom
            // downward edge.  The canonical lease already owns the typed feet cell, so keep
            // that distinction at the adapter boundary instead of teaching motion to recover
            // it heuristically.
            var target = lease.goalBody();
            return new Vec3(target.x() + 0.5D, targetFeetY(target), target.z() + 0.5D);
        }
        LocalBrain brain = localBrain(state, actorId);
        long cycle = Math.floorMod(gameTime, brain.periodTicks());
        double phase = (cycle / (double) brain.periodTicks()) + (brain.identityPhase() / 16.0D);
        double angle = phase * Math.PI * 2.0D;
        // The immutable lease hand-off body identifies the original admission point.  It is
        // deliberately not the actor's ongoing positional authority: a canonical process may
        // advance the exact actor body while the ambient lease remains HOT.  Local presentation
        // must converge on that single current ActorLocation, otherwise an old lease anchor can
        // keep the physical Villager on a neighbouring column and make a later scene hand-off
        // fail despite an otherwise valid canonical cursor.
        BlockPosition anchor = currentBody.supportingSurface().support();
        double radius = boundedPresentationRadius(brain.radius());
        return new Vec3(anchor.x() + 0.5D + Math.cos(angle) * radius, targetFeetY(currentBody),
                anchor.z() + 0.5D + Math.sin(angle) * radius);
    }

    static boolean directedGoal(AmbientActorLease lease) {
        return lease.goal() == AmbientGoalKind.TRANSIT || lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY
                || lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY || lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY
                || lease.goal() == AmbientGoalKind.SCOUT_PATROL;
    }

    static double boundedPresentationRadius(double requestedRadius) {
        if (!Double.isFinite(requestedRadius) || requestedRadius < 0.0D) throw new IllegalArgumentException("ambient presentation radius is invalid");
        return Math.min(requestedRadius, MAX_PRESENTATION_RADIUS);
    }

    /** Pure source-of-truth boundary for tests and the physical presentation adapter. */
    static BlockPosition localAnchor(FrontierWorldState state, SubjectId actorId) {
        if (state == null || actorId == null) throw new IllegalArgumentException("ambient local anchor requires state and actor");
        var location = state.actorLocations().get(actorId);
        if (location == null) throw new IllegalArgumentException("ambient local target has no canonical actor location: " + actorId.value());
        return location.supportingSurface().support();
    }

    /** Kept pure/testable: Minecraft Vec3 positions are body feet, never support-block Y. */
    static int targetFeetY(BodyPosition body) {
        if (body == null) throw new IllegalArgumentException("ambient target body is required");
        return body.y();
    }

    private static LocalBrain localBrain(FrontierWorldState state, SubjectId actorId) {
        int identityPhase = Math.floorMod(actorId.value().hashCode(), 16);
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) return switch (resident.role()) {
            case FARMER -> new LocalBrain(0.24D, 360L, identityPhase);
            case BUILDER -> new LocalBrain(0.32D, 300L, identityPhase);
            case CRAFTER -> new LocalBrain(0.22D, 280L, identityPhase);
            case GUARD -> new LocalBrain(0.40D, 480L, identityPhase);
            case MEDIC -> new LocalBrain(0.18D, 240L, identityPhase);
            case HAULER -> new LocalBrain(0.36D, 320L, identityPhase);
        };
        Bioform bioform = FrontierV3AmbientActorExecutor.bioformProfile(state, actorId);
        if (bioform.isScout()) return new LocalBrain(0.40D, 240L, identityPhase);
        if (bioform.isExplosiveAssaulter()) return new LocalBrain(0.36D, 300L, identityPhase);
        if (bioform.isDefender()) return new LocalBrain(0.32D, 480L, identityPhase);
        return new LocalBrain(0.28D, 320L, identityPhase);
    }

    private record LocalBrain(double radius, long periodTicks, int identityPhase) { }
}
