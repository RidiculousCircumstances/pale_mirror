package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Domain admission and presentation-scope coordination. Retained execution and body
 * lifecycles own their separate authority; this read model never writes HOT positions.
 * Scheduling chooses purpose and registered family owners prove interruption safety.
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

    /** Physical presence is not employment: ordinary presentation can yield to a new job. */
    public static WorkAdmission ordinaryWorkAdmission(FrontierWorldState state, SubjectId actorId) {
        return workAdmission(state, actorId, false);
    }
    /** The declared group alone may reacquire a vacant participant reserved by its retained roster. */
    public static WorkAdmission groupWorkAdmission(FrontierWorldState state, SubjectId actorId, SubjectId groupId) {
        var group = state.unitGroups().groups().get(Objects.requireNonNull(groupId));
        if (group == null || group.phase() == io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup.Phase.CLOSED)
            throw new IllegalArgumentException("group admission lacks its declared active roster");
        group.member(actorId);
        var assignment = HumanAssignmentProjection.compile(state).assignment(actorId);
        if (assignment.kind() != HumanAssignmentKind.GROUP_MEMBER || !assignment.ownerId().equals(java.util.Optional.of(groupId)))
            return new Waiting(Wait.ASSIGNED_WORK);
        return workAdmission(state, actorId, true);
    }
    private static WorkAdmission workAdmission(FrontierWorldState state, SubjectId actorId, boolean groupMember) {
        Objects.requireNonNull(state, "execution state"); Objects.requireNonNull(actorId, "actor");
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().resident(actorId) == null) return new Waiting(Wait.DEAD_OR_MISSING);
        if (state.humanPopulation().meals().containsKey(actorId)
                || state.actorMovements().containsKey(actorId)) return new Waiting(Wait.SELF_CARE);
        if (!groupMember && !HumanAssignmentProjection.compile(state).idle(actorId)) return new Waiting(Wait.ASSIGNED_WORK);
        if (sceneOwns(state, actorId)) return new Waiting(Wait.SCENE_AUTHORITY);
        if (carriesResource(state, actorId)) return new Waiting(Wait.RESOURCE_IN_HAND);
        AmbientActorLease ambient = state.ambientLeases().get(actorId);
        if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) return new Cold();
        if (ambient.status() != AmbientLeaseStatus.HOT) return new Waiting(Wait.AMBIENT_RECOVERY);
        if (!ordinaryAmbientPurpose(ambient.goal()) && !(groupMember && ambient.goal() == AmbientGoalKind.ACTOR_MOVEMENT))
            return new Waiting(Wait.FOREIGN_AMBIENT_PURPOSE);
        // handoffBody is historical evidence, never a completion predicate. The subsequent
        // scene admission references common inspection and closes this exact presentation epoch atomically.
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
        return !ActorBodyAuthority.retainsPhysicalCustody(state, actorId)
                && ambientAvailable(state, List.of(actorId)) && !sceneOwns(state, actorId);
    }

    /** A collective COLD transition must exclude physical custody for every participant. */
    public static boolean coldAvailable(FrontierWorldState state, Collection<SubjectId> actorIds) {
        Objects.requireNonNull(actorIds, "actors");
        return actorIds.stream().allMatch(actor -> coldAvailable(state, actor));
    }

    public static void requireOrdinaryWorkAdmission(FrontierWorldState state, SubjectId actorId) {
        WorkAdmission admission = ordinaryWorkAdmission(state, actorId);
        if (admission instanceof Waiting waiting)
            throw new IllegalArgumentException("work admission waits: " + waiting.reason());
    }

    /** Purpose comes from activity policy; this owner retargets presentation, not physical motion. */
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
                    || !ambientAvailable(state, List.of(member.actorId())) || sceneOwns(state, member.actorId()))
                throw new IllegalArgumentException("scene preparation lacks exclusive living actor authority");
        }
    }

    /** One shared safe-point assessment for self-care and every adapted work owner. */
    public static ResidentWorkYield workYield(FrontierWorldState state, HumanAssignment assignment) {
        Objects.requireNonNull(state, "yield state"); Objects.requireNonNull(assignment, "assignment");
        // Presentation scope is not an interruption policy. The exact activity owner
        // assesses its effect/continuation checkpoint; UAE commits the actual switch.
        return ActivityExecutionCapabilities.assess(state, assignment);
    }

    private static boolean ordinaryAmbientPurpose(AmbientGoalKind goal) {
        // GUARD is ordinary resident presentation. Actual defence keeps its own
        // assignment capability and cannot become interruptible through this admission.
        return goal == AmbientGoalKind.PATROL || goal == AmbientGoalKind.WORK
                || goal == AmbientGoalKind.GUARD || goal == AmbientGoalKind.MEAL;
    }

    private static boolean carriesResource(FrontierWorldState state, SubjectId actorId) {
        return state.inventory().fungibleResources().accounts().values().stream().anyMatch(account ->
                account.custody().equals(new ResourceCustody.Actor(actorId)));
    }

    /** Switch presentation scopes after common inspection; never transfer the physical body or write its pose. */
    static FrontierWorldState transferToScene(FrontierWorldState state, SceneLeaseHandoff handoff) {
        Objects.requireNonNull(handoff, "scene hand-off");
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
            AmbientActorLease current = ambient.get(capture.actorId()); ActorLocation actor = state.actorLocations().get(capture.actorId());
            if (current == null || current.status() != AmbientLeaseStatus.HOT || actor == null
                    || actor.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("capture lacks one living HOT ambient actor");
            FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), capture.body().supportingSurface().support());
            if (!actor.body().equals(capture.body()) || !actor.condition().health().equals(capture.health()))
                throw new IllegalArgumentException("scene handoff requires independently recorded common body observation");
            ambient.put(capture.actorId(), current.withStatus(AmbientLeaseStatus.CLOSED));
        }
        FrontierWorldState capturedState = state.withChanges(FrontierWorldStateUpdate.begin()
                .ambientLeases(ambient));
        return FrontierSceneLeaseStateSupport.prepare(capturedState, handoff.lease());
    }
}
