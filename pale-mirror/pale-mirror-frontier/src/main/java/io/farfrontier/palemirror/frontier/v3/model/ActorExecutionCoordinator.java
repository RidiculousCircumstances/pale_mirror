package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sole domain policy for exclusive actor execution and safe ownership transfer.
 * Existing jobs, movement orders and leases are the durable records, not a second queue.
 * Scheduling chooses purpose; family owners prove effects; this owner arbitrates authority.
 */
public final class ActorExecutionCoordinator {
    private ActorExecutionCoordinator() { }

    public enum Wait {
        DEAD_OR_MISSING, SELF_CARE, ASSIGNED_WORK, SCENE_AUTHORITY,
        AMBIENT_RECOVERY, FOREIGN_AMBIENT_PURPOSE, RESOURCE_IN_HAND
    }

    public sealed interface WorkAdmission permits Cold, AmbientTransfer, Waiting {
        default boolean permitted() { return !(this instanceof Waiting); }
    }
    public record Cold() implements WorkAdmission { }
    public record AmbientTransfer(SubjectId actorId, long authorityRevision) implements WorkAdmission {
        public AmbientTransfer {
            Objects.requireNonNull(actorId, "transfer actor");
            if (authorityRevision < 1) throw new IllegalArgumentException("transfer needs an exact epoch");
        }
    }
    public record Waiting(Wait reason) implements WorkAdmission {
        public Waiting { Objects.requireNonNull(reason, "execution wait"); }
    }

    /** Physical presence is not employment: a HOT idle body can transfer to a new job. */
    public static WorkAdmission ordinaryWorkAdmission(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state, "execution state"); Objects.requireNonNull(actorId, "actor");
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().resident(actorId) == null) return new Waiting(Wait.DEAD_OR_MISSING);
        if (state.humanPopulation().meals().containsKey(actorId)
                || state.actorMovements().containsKey(actorId)) return new Waiting(Wait.SELF_CARE);
        if (!HumanAssignmentProjection.compile(state).idle(actorId)) return new Waiting(Wait.ASSIGNED_WORK);
        if (sceneOwns(state, actorId)) return new Waiting(Wait.SCENE_AUTHORITY);
        if (carriesResource(state, actorId)) return new Waiting(Wait.RESOURCE_IN_HAND);
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) return new Cold();
        if (ambient.status() != AmbientLeaseStatus.HOT) return new Waiting(Wait.AMBIENT_RECOVERY);
        if (!ordinaryAmbientPurpose(ambient.goal())) return new Waiting(Wait.FOREIGN_AMBIENT_PURPOSE);
        // handoffBody is historical evidence, never a completion predicate. The subsequent
        // scene handoff captures the actual body and closes this exact ambient epoch atomically.
        return new AmbientTransfer(actorId, ambient.revision());
    }

    public static boolean ambientAvailable(FrontierWorldState state, Collection<SubjectId> actors) {
        Objects.requireNonNull(state, "execution state"); Objects.requireNonNull(actors, "actors");
        return actors.stream().allMatch(actor -> {
            AmbientActorLease lease = state.ambientLeases().get(Objects.requireNonNull(actor, "actor"));
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        });
    }

    public static boolean coldAvailable(FrontierWorldState state, SubjectId actorId) {
        return ambientAvailable(state, List.of(actorId)) && !sceneOwns(state, actorId);
    }

    public static void requireOrdinaryWorkAdmission(FrontierWorldState state, SubjectId actorId) {
        WorkAdmission admission = ordinaryWorkAdmission(state, actorId);
        if (admission instanceof Waiting waiting)
            throw new IllegalArgumentException("work admission waits: " + waiting.reason());
    }

    /** Purpose comes from activity policy; this owner performs the common HOT retarget. */
    public static FrontierWorldState retargetAmbient(FrontierWorldState state, SubjectId actorId,
                                                     AmbientGoalKind goal, BodyPosition goalBody) {
        AmbientActorLease current = state.ambientLeases().get(Objects.requireNonNull(actorId, "actor"));
        if (current == null || current.status() != AmbientLeaseStatus.HOT || sceneOwns(state, actorId))
            throw new IllegalArgumentException("retarget requires exclusive HOT ambient authority");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), goalBody.supportingSurface().support());
        var leases = new LinkedHashMap<>(state.ambientLeases());
        leases.put(actorId, current.withGoal(goal, goalBody));
        return state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases));
    }

    public static boolean sceneOwns(FrontierWorldState state, SubjectId actorId) {
        return state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(actorId));
    }

    /** Every registered family enters the same exclusive scene admission boundary. */
    static void requireScenePreparation(FrontierWorldState state, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            ActorLocation actor = state.actorLocations().get(member.actorId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                    || !coldAvailable(state, member.actorId()))
                throw new IllegalArgumentException("scene preparation lacks exclusive living actor authority");
            if (!actor.body().equals(lease.memberPosition(member.actorId())))
                throw new IllegalArgumentException("scene preparation must retain the exact captured body");
        }
    }

    /** One shared safe-point assessment for self-care and every adapted work owner. */
    public static ResidentWorkYield workYield(FrontierWorldState state, HumanAssignment assignment) {
        Objects.requireNonNull(state, "yield state"); Objects.requireNonNull(assignment, "assignment");
        SubjectId resident = assignment.residentId();
        AmbientActorLease ambient = state.ambientLeases().get(resident);
        boolean safeAmbient = ambient != null && ambient.status() == AmbientLeaseStatus.HOT
                && ordinaryAmbientPurpose(ambient.goal());
        if ((!ambientAvailable(state, List.of(resident)) && !safeAmbient) || sceneOwns(state, resident))
            return new ResidentWorkYield(resident, assignment, ResidentWorkYield.Status.SCENE_OR_AMBIENT_AUTHORITY);
        if (carriesResource(state, resident))
            return new ResidentWorkYield(resident, assignment, ResidentWorkYield.Status.CARRYING_RESOURCE);
        return ActivityExecutionCapabilities.assess(state, assignment);
    }

    private static boolean ordinaryAmbientPurpose(AmbientGoalKind goal) {
        return goal == AmbientGoalKind.PATROL || goal == AmbientGoalKind.WORK || goal == AmbientGoalKind.MEAL;
    }

    private static boolean carriesResource(FrontierWorldState state, SubjectId actorId) {
        return state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                account.custody().equals(new ResourceCustody.Actor(actorId)));
    }

    /** Actual observed body transfer, one canonical transaction and no despawn/replacement. */
    static FrontierWorldState transferToScene(FrontierWorldState state, SceneLeaseHandoff handoff) {
        Objects.requireNonNull(handoff, "scene hand-off");
        var actors = new LinkedHashMap<>(state.actorLocations());
        var ambient = new LinkedHashMap<>(state.ambientLeases());
        Set<SubjectId> captured = handoff.ambientMembers().stream().map(SceneMemberPosition::actorId)
                .collect(java.util.stream.Collectors.toSet());
        for (SceneMember member : handoff.lease().members()) {
            if (sceneOwns(state, member.actorId())) throw new IllegalArgumentException("actor already has scene authority");
            AmbientActorLease current = ambient.get(member.actorId());
            if (current == null || current.status() == AmbientLeaseStatus.CLOSED) {
                if (captured.contains(member.actorId())) throw new IllegalArgumentException("captured an unleased actor");
            } else if (current.status() != AmbientLeaseStatus.HOT || !captured.contains(member.actorId())) {
                throw new IllegalArgumentException("active ambient member must be HOT and captured");
            }
        }
        for (SceneMemberPosition capture : handoff.ambientMembers()) {
            AmbientActorLease current = ambient.get(capture.actorId()); ActorLocation actor = actors.get(capture.actorId());
            if (current == null || current.status() != AmbientLeaseStatus.HOT || actor == null
                    || actor.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("capture lacks one living HOT ambient actor");
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), capture.body().supportingSurface().support());
            actors.put(capture.actorId(), new ActorLocation(capture.body(), actor.condition().withHealth(capture.health())));
            ambient.put(capture.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        }
        FrontierWorldState capturedState = state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(actors).ambientLeases(ambient));
        return FrontierSceneLeaseStateSupport.prepare(capturedState, handoff.lease());
    }
}
