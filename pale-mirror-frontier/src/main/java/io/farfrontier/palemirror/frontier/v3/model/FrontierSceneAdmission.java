package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Pure admission predicates for exclusive scene and ambient execution. */
public final class FrontierSceneAdmission {
    private FrontierSceneAdmission() { }

    /** A scene must wait for every existing ambient executor to finish its own durable hand-off. */
    public static boolean available(FrontierWorldState state, Collection<SubjectId> actorIds) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorIds, "actor ids");
        return actorIds.stream().allMatch(actorId -> {
            AmbientActorLease lease = state.ambientLeases().get(Objects.requireNonNull(actorId, "actor id"));
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        });
    }

    /**
     * A non-terminal HOT/COLD hand-off exclusively owns its route operation.  A COLD planner
     * must not advance or resolve that operation until the lease is closed: otherwise one
     * operation would have two simultaneous execution authorities.
     */
    public static boolean hasActiveSceneLease(FrontierWorldState state, SubjectId operationId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(operationId, "operation id");
        return state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operationId)
                && lease.status() != SceneLeaseStatus.CLOSED);
    }

    /** A generic route scene must yield while the hive already owns an unresolved interception. */
    public static boolean hasUnresolvedRouteEngagement(FrontierWorldState state, SubjectId operationId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(operationId, "operation id");
        return state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.operationId().equals(operationId)
                && engagement.status() != RouteEngagementStatus.RESOLVED);
    }

    /** Admission for beginning a new COLD interception, not for progressing its own engagement. */
    public static boolean coldInterceptionAvailable(FrontierWorldState state, SubjectId operationId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(operationId, "operation id");
        RouteOperation operation = state.operations().get(operationId);
        return operation != null && !hasActiveSceneLease(state, operationId) && !hasUnresolvedRouteEngagement(state, operationId)
                && available(state, operation.participantIds());
    }

    /**
     * A COLD engagement may advance or strike only while every exact combatant is free of
     * Minecraft-side authority.  {@code UNKNOWN_AFTER_RESTART} remains an active authority:
     * the loaded-world inspector must settle it before a strategic action may change that actor.
     */
    public static boolean coldEngagementAvailable(FrontierWorldState state, RouteEngagement engagement) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(engagement, "engagement");
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || hasActiveSceneLease(state, operation.id())) return false;
        return coldEngagementActorsAvailable(state, engagement);
    }

    /** Exact actor-authority check without assuming that a route is still en route. */
    public static boolean coldEngagementActorsAvailable(FrontierWorldState state, RouteEngagement engagement) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(engagement, "engagement");
        RouteOperation operation = state.operations().get(engagement.operationId());
        if (operation == null) return false;
        Collection<SubjectId> actors = new ArrayList<>(operation.participantIds());
        actors.addAll(engagement.attackerIds());
        return available(state, actors);
    }

    /**
     * A non-closed scene keeps every exact participant exclusively reserved, even if its owning
     * operation has already become terminal due to restart recovery evidence.  Otherwise an
     * ambient executor could race the still-authoritative UNKNOWN scene and force a quarantine.
     */
    public static boolean reserved(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorId, "actor id");
        return reservedActors(state).contains(actorId);
    }

    /**
     * A generic ambient goal may not take an actor that a strategic engagement, assault or
     * already-prepared scene owns. An ordinary en-route logistics operation is intentionally
     * excluded: its own ambient body is the legal precursor to its later scene hand-off.
     */
    public static boolean reservedFromGenericAmbient(FrontierWorldState state, SubjectId actorId) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(actorId, "actor id");
        return state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))
                || state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.status() != RouteEngagementStatus.RESOLVED
                && engagement.attackerIds().contains(actorId))
                || state.strategicPlans().settlementAssaults().values().stream().anyMatch(assault -> assault.status() != SettlementAssaultStatus.RESOLVED
                && assault.attackerIds().contains(actorId))
                || FrontierEngineeringWorkSceneSupport.nextCandidate(state).stream()
                .anyMatch(candidate -> candidate.memberPositions().containsKey(actorId))
                || FrontierResourceSiteHarvestSceneSupport.nextCandidate(state).stream()
                .anyMatch(candidate -> candidate.memberPositions().containsKey(actorId));
    }

    /**
     * Exact read-only reservation index for one immutable canonical revision. Materialization
     * may reuse it for many actor observations; it does not retain, order or alter canonical
     * state. This prevents recompiling every COLD scene candidate once per candidate body.
     */
    public static Set<SubjectId> reservedActors(FrontierWorldState state) {
        Objects.requireNonNull(state, "state");
        Set<SubjectId> reserved = new LinkedHashSet<>();
        state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .forEach(lease -> lease.members().forEach(member -> reserved.add(member.actorId())));
        state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.EN_ROUTE)
                .forEach(operation -> reserved.addAll(operation.participantIds()));
        // A strategic COLD engagement owns its exact actors before a physical scene candidate
        // exists.  Otherwise an ambient visit between departure and battlefield arrival could
        // recreate one of the same identities as an unrelated patrol body.
        state.strategicPlans().routeEngagements().values().stream()
                .filter(engagement -> engagement.status() != RouteEngagementStatus.RESOLVED)
                .forEach(engagement -> reserved.addAll(engagement.attackerIds()));
        state.strategicPlans().settlementAssaults().values().stream()
                .filter(assault -> assault.status() != SettlementAssaultStatus.RESOLVED)
                .forEach(assault -> reserved.addAll(assault.attackerIds()));
        state.coldEngagementSceneCandidates().forEach(candidate -> reserved.addAll(candidate.actorIds()));
        state.coldSettlementAssaultSceneCandidates().forEach(candidate -> reserved.addAll(candidate.memberPositions().keySet()));
        // A completed engineering assembly is the next exclusive physical owner, even before
        // its scene lease is prepared.  Without this reservation an ordinary ambient visit can
        // re-open one of the exact crew between COLD completion and scene admission (notably
        // after restart), leaving the worksite indefinitely unavailable to itself.
        FrontierEngineeringWorkSceneSupport.nextCandidate(state)
                .ifPresent(candidate -> reserved.addAll(candidate.memberPositions().keySet()));
        // Field work uses the typed atomic ambient-to-scene hand-off.  It deliberately is not
        // a pre-lease COLD reservation: the loaded Villager must remain available for that
        // hand-off rather than be drained and respawned at a guessed canonical surface.
        return Set.copyOf(reserved);
    }

    /** The assault itself may progress COLD; every other authority remains exclusive. */
    public static boolean reservedByOtherThanSettlementAssault(FrontierWorldState state, SubjectId actorId, SubjectId assaultId) {
        Objects.requireNonNull(assaultId, "assault id");
        return state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                && (!FrontierSceneBehaviors.isSettlementAssault(lease) || !FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(assaultId))
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))
                || state.operations().values().stream().anyMatch(operation -> operation.stage() == OperationStage.EN_ROUTE
                && operation.participantIds().contains(actorId))
                || state.strategicPlans().routeEngagements().values().stream().anyMatch(engagement -> engagement.status() != RouteEngagementStatus.RESOLVED
                && engagement.attackerIds().contains(actorId))
                || state.strategicPlans().settlementAssaults().values().stream().anyMatch(assault -> !assault.id().equals(assaultId)
                && assault.status() != SettlementAssaultStatus.RESOLVED && assault.attackerIds().contains(actorId))
                || state.coldEngagementSceneCandidates().stream().anyMatch(candidate -> candidate.actorIds().contains(actorId))
                || state.coldSettlementAssaultSceneCandidates().stream().anyMatch(candidate -> !candidate.assaultId().equals(assaultId)
                && candidate.memberPositions().containsKey(actorId));
    }
}
