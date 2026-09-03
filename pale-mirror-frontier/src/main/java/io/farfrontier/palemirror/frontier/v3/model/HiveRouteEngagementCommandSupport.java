package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** One pure admission/check boundary for route-interception command authority. */
public final class HiveRouteEngagementCommandSupport {
    private HiveRouteEngagementCommandSupport() { }

    public static Optional<HiveOperationCommandAuthority> admit(FrontierWorldState state, List<EngagementAttacker> attackers, long now) {
        List<SubjectId> roster = attackers.stream().map(EngagementAttacker::actorId).toList();
        Map<SubjectId, Bioform> bioforms = bioforms(state);
        Optional<Bioform> overseer = bioforms.values().stream()
                .filter(Bioform::isOverseer).filter(value -> roster.contains(value.id()))
                .filter(value -> availableForNewRouteEngagement(state, value.id())).sorted(Comparator.comparing(Bioform::id)).findFirst();
        if (overseer.isPresent()) {
            SubjectId id = overseer.orElseThrow().id();
            if (HiveCommandCapacity.admits(state.bootstrap().ruleset(), id, roster, bioforms)) {
                return Optional.of(new HiveOperationCommandAuthority(HiveCommandAuthorityKind.OVERSEER, id, id, roster,
                        HiveCommandCapacity.usedCapacity(state.bootstrap().ruleset(), id, roster, bioforms), Optional.empty(),
                        HiveCommandSignalPhase.CONNECTED, now));
            }
        }
        return relayAuthority(state, attackers, roster, now);
    }

    public static boolean connected(FrontierWorldState state, RouteEngagement engagement) {
        HiveOperationCommandAuthority authority = engagement.commandAuthority();
        if (authority.kind() == HiveCommandAuthorityKind.OVERSEER) return liveController(state, authority.currentAuthorityId());
        HiveRelayCoverageProof coverage = authority.relayCoverage().orElseThrow();
        if (!state.isHiveOrganOperational(authority.currentAuthorityId()) || !state.isHiveOrganOperational(coverage.ganglionId())) return false;
        return engagement.attackers().stream().flatMap(attacker -> attacker.route().stream()).allMatch(coverage::covers);
    }

    /**
     * Validates the one admission carried by a newly started operation.  It is deliberately
     * stricter than {@link #connected(FrontierWorldState, RouteEngagement)}: a live nearby
     * Overseer or an operational nearby Relay is not permission to invent a roster, capacity
     * balance or coverage proof in a replayed start payload.
     */
    public static boolean admitsStartedEngagement(FrontierWorldState state, RouteEngagement engagement) {
        HiveOperationCommandAuthority authority = engagement.commandAuthority();
        if (authority.signalPhase() != HiveCommandSignalPhase.CONNECTED
                || !engagement.attackerIds().equals(authority.rosterIds())
                || !authority.rosterIds().stream().allMatch(id -> availableForNewRouteEngagement(state, id))) {
            return false;
        }
        Map<SubjectId, Bioform> bioforms = bioforms(state);
        if (authority.kind() == HiveCommandAuthorityKind.OVERSEER) {
            return authority.originalAuthorityId().equals(authority.currentAuthorityId())
                    && HiveCommandCapacity.admits(state.bootstrap().ruleset(), authority.currentAuthorityId(), authority.rosterIds(), bioforms)
                    && authority.subordinateWeight() == HiveCommandCapacity.usedCapacity(state.bootstrap().ruleset(), authority.currentAuthorityId(), authority.rosterIds(), bioforms);
        }
        HiveRelayCoverageProof proof = authority.relayCoverage().orElse(null);
        if (proof == null || authority.subordinateWeight() != 0) return false;
        HiveOrgan relay = organs(state).filter(organ -> organ.id().equals(authority.currentAuthorityId()))
                .filter(organ -> organ.kind() == HiveOrganKind.RELAY).filter(organ -> state.isHiveOrganOperational(organ.id())).findFirst().orElse(null);
        HiveOrgan ganglion = organs(state).filter(organ -> proof.ganglionId().equals(organ.id()))
                .filter(organ -> organ.kind() == HiveOrganKind.GANGLION).filter(organ -> state.isHiveOrganOperational(organ.id())).findFirst().orElse(null);
        return relay != null && ganglion != null && relay.nestId().equals(ganglion.nestId())
                && proof.centre().equals(relay.anchor()) && proof.radius() == state.bootstrap().ruleset().spatial().hivePerceptionRadius()
                && engagement.attackers().stream().flatMap(attacker -> attacker.route().stream()).allMatch(proof::covers);
    }

    public static boolean canReclaim(FrontierWorldState state, RouteEngagement engagement, SubjectId candidate) {
        HiveOperationCommandAuthority authority = engagement.commandAuthority();
        Bioform bioform = bioforms(state).get(candidate);
        ActorLocation location = state.actorLocations().get(candidate);
        return authority.kind() == HiveCommandAuthorityKind.OVERSEER && authority.signalPhase() == HiveCommandSignalPhase.INSTINCT
                && !candidate.equals(authority.currentAuthorityId()) && !liveController(state, authority.currentAuthorityId())
                && bioform != null && bioform.isOverseer() && availableForNewRouteEngagement(state, candidate)
                && location != null && location.supportingSurface().support().equals(engagement.intercept())
                && HiveCommandCapacity.admits(state.bootstrap().ruleset(), candidate, authority.rosterIds(), bioforms(state));
    }

    public static Optional<SubjectId> reclaimingOverseer(FrontierWorldState state, RouteEngagement engagement) {
        return bioforms(state).values().stream().filter(Bioform::isOverseer).map(Bioform::id)
                .filter(candidate -> canReclaim(state, engagement, candidate)).sorted().findFirst();
    }

    private static Optional<HiveOperationCommandAuthority> relayAuthority(FrontierWorldState state, List<EngagementAttacker> attackers,
                                                                            List<SubjectId> roster, long now) {
        return organs(state).filter(organ -> organ.kind() == HiveOrganKind.RELAY).filter(organ -> state.isHiveOrganOperational(organ.id()))
                .sorted(Comparator.comparing(HiveOrgan::id)).map(relay -> ganglion(state, relay.nestId()).map(ganglion -> {
                    HiveRelayCoverageProof proof = new HiveRelayCoverageProof(ganglion.id(), relay.anchor(), state.bootstrap().ruleset().spatial().hivePerceptionRadius());
                    return attackers.stream().flatMap(attacker -> attacker.route().stream()).allMatch(proof::covers)
                            ? Optional.of(new HiveOperationCommandAuthority(HiveCommandAuthorityKind.RELAY, relay.id(), relay.id(), roster, 0,
                            Optional.of(proof), HiveCommandSignalPhase.CONNECTED, now)) : Optional.<HiveOperationCommandAuthority>empty();
                }).orElseGet(Optional::empty)).flatMap(Optional::stream).findFirst();
    }

    private static Optional<HiveOrgan> ganglion(FrontierWorldState state, SubjectId nestId) {
        return organs(state).filter(organ -> organ.nestId().equals(nestId) && organ.kind() == HiveOrganKind.GANGLION)
                .filter(organ -> state.isHiveOrganOperational(organ.id())).sorted(Comparator.comparing(HiveOrgan::id)).findFirst();
    }
    private static Stream<HiveOrgan> organs(FrontierWorldState state) {
        return Stream.concat(state.bootstrap().hive().organs().stream(), state.hiveColony().addedOrgans().values().stream());
    }

    private static Map<SubjectId, Bioform> bioforms(FrontierWorldState state) {
        return Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Bioform::id, value -> value));
    }
    /** Admission may not take a body already owned by an unrelated active operation or HOT executor. */
    public static boolean availableForNewRouteEngagement(FrontierWorldState state, SubjectId id) {
        ActorLocation actor = state.actorLocations().get(id);
        return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE && HivePhysiologySupport.availableForIndependentOperation(state, id)
                && (state.ambientLeases().get(id) == null || state.ambientLeases().get(id).status() == AmbientLeaseStatus.CLOSED)
                && state.strategicPlans().routeEngagements().values().stream().noneMatch(value -> value.status() != RouteEngagementStatus.RESOLVED
                && value.attackerIds().contains(id))
                && state.strategicPlans().settlementAssaults().values().stream().noneMatch(value -> value.status() != SettlementAssaultStatus.RESOLVED
                && value.attackerIds().contains(id));
    }

    /**
     * A retained controller does not need to be newly admissible: its own operation or scene may
     * already own its body. Vitality and lifecycle remain the only proof of a live signal source;
     * an ambient lease is deliberately not treated as a second controller or as a loss of signal.
     */
    private static boolean liveController(FrontierWorldState state, SubjectId id) {
        ActorLocation actor = state.actorLocations().get(id);
        return actor != null && actor.condition().status() == ActorLifeStatus.ALIVE
                && HivePhysiologySupport.permitsAmbientLease(state, id);
    }
}
