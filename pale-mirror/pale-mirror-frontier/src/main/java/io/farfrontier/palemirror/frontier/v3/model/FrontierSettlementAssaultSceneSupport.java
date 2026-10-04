package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/** Typed owner and admission rules for cargo-free settlement-assault scenes. */
public final class FrontierSettlementAssaultSceneSupport {
    private FrontierSettlementAssaultSceneSupport() { }

    public static SettlementAssault require(FrontierWorldState state, SettlementAssaultSceneCause cause) {
        return require(state.strategicPlans(), cause);
    }

    public static SettlementAssault require(StrategicPlanState plans, SettlementAssaultSceneCause cause) {
        SettlementAssault assault = plans.settlementAssaults().get(cause.assaultId());
        if (assault == null || !assault.settlementId().equals(cause.settlementId())) {
            throw new IllegalArgumentException("assault scene cause has no matching canonical assault");
        }
        return assault;
    }

    public static SubjectId owner(FrontierWorldState state, SceneLease lease) {
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        return require(state, cause).hiveId();
    }

    static java.util.List<SettlementAssaultSceneCandidate> candidates(FrontierWorldState state) {
        FrontierSettlementAssaultBattlefield.Provider provider = FrontierSettlementAssaultBattlefield.providerView(state);
        return candidates(state, provider);
    }

    static java.util.List<SettlementAssaultSceneCandidate> candidates(FrontierWorldState state,
                                                                       FrontierSettlementAssaultBattlefield.Provider provider) {
        return state.strategicPlans().settlementAssaults().values().stream()
                .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT
                        && assault.tacticalPlan().currentFor(state.strategicPlans()))
                .sorted(java.util.Comparator.comparing(SettlementAssault::id))
                .map(assault -> FrontierSettlementAssaultBattlefield.candidate(state, assault, provider)).flatMap(java.util.Optional::stream).toList();
    }

    /** The approach is the same exact assault cause, but owns only its retained hive roster. */
    public static Optional<SettlementAssaultSceneCandidate> marchCandidate(FrontierWorldState state, SettlementAssault assault) {
        if (assault.status() != SettlementAssaultStatus.APPROACHING || assault.march().complete()
                || !assault.tacticalPlan().currentFor(state.strategicPlans())) return Optional.empty();
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, BodyPosition> member : assault.formationBodies().entrySet()) {
            ActorLocation actor = state.actorLocations().get(member.getKey());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || !actor.body().equals(member.getValue())) return Optional.empty();
            positions.put(member.getKey(), member.getValue().supportingSurface().support());
        }
        return Optional.of(new SettlementAssaultSceneCandidate(assault.id(), assault.settlementId(),
                positions.get(assault.overseerId()), positions));
    }

    static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        SettlementAssault assault = require(state, cause);
        boolean march = assault.status() == SettlementAssaultStatus.APPROACHING;
        if (lease.status() != SceneLeaseStatus.PREPARED || (!march && assault.status() != SettlementAssaultStatus.COLD_COMBAT)
                || !assault.tacticalPlan().currentFor(state.strategicPlans())
                || (march ? !lease.handoffPosition().equals(assault.formationBodies().get(assault.overseerId()).supportingSurface().support())
                : !lease.handoffPosition().equals(assault.settlementAnchor()) || !targetIntact(state, assault))) {
            throw new IllegalArgumentException("assault scene must prepare one intact COLD battle at its retained anchor");
        }
        // The registered NeoForge admission transaction already established that these exact
        // support columns are serviceable through its compatible immutable projection provider.
        // This pure reducer owns canonical identity, status, target and position checks; it must
        // not rebuild a physical projection merely to repeat that observation.
        Set<SubjectId> expected = new HashSet<>(assault.attackerIds());
        if (!march) expected.addAll(assault.defenderIds());
        if (!march) expected.removeIf(id -> {
            ActorLocation actor = state.actorLocations().get(id);
            if (actor == null) throw new IllegalArgumentException("assault retained an unknown combatant");
            return actor.condition().status() == ActorLifeStatus.DEAD;
        });
        Set<SubjectId> actual = new HashSet<>();
        Set<BlockPosition> floors = new HashSet<>();
        for (SceneMember member : lease.members()) {
            ActorLocation actor = state.actorLocations().get(member.actorId());
            BlockPosition floor = lease.memberBody(state.actorLocations(), member.actorId()).supportingSurface().support();
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || !actor.supportingSurface().support().equals(floor)
                    || !actual.add(member.actorId()) || !floors.add(floor)
                    || (march && !actor.body().equals(assault.formationBodies().get(member.actorId()))) || !march && !FrontierSettlementAssaultBattlefield.localClearFloor(state,
                    FrontierWorldStateSupport.settlement(state.bootstrap(), assault.settlementId()), floor)) {
                throw new IllegalArgumentException("assault scene needs exact living actors at distinct local hand-off floors");
            }
        }
        if (!actual.equals(expected)) throw new IllegalArgumentException("assault scene members must exactly match retained combatants");
    }

    public static FrontierWorldState advanceFormationObserved(FrontierWorldState state, SubjectId subject,
                                                               SettlementAssaultFormationObserved observed) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
        if (assault != null) SettlementAssaultExecutionAuthority.requireCurrent(state, assault, observed.executions());
        if (assault == null || !subject.equals(assault.hiveId()) || assault.status() != SettlementAssaultStatus.HOT) {
            throw new IllegalArgumentException("expedition formation observation has no HOT owner");
        }
        SceneLease lease = state.sceneLeases().get(observed.leaseId());
        if (lease == null || !FrontierSceneBehaviors.isSettlementAssault(lease)
                || !FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(assault.id())
                || !lease.memberBodies(state.actorLocations()).equals(assault.formationBodies())) {
            throw new IllegalArgumentException("expedition formation observation has no matching lease/cursor");
        }
        SettlementAssault next = assault.advanceFormation();
        if (!next.formationBodies().equals(observed.bodies())) throw new IllegalArgumentException("expedition formation did not reach its retained next edge");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        observed.bodies().forEach((actor, body) -> actors.put(actor, actors.get(actor).withBody(body)));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .strategicPlans(state.strategicPlans().replaceSettlementAssault(next)));
    }

    /** Closes the exact loaded march lease with durable, member/edge-specific evidence. */
    public static FrontierWorldState recordMarchIssue(FrontierWorldState state, SubjectId subject,
                                                       SettlementAssaultMarchIssueObserved observed) {
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(observed.assaultId());
        if (assault != null) SettlementAssaultExecutionAuthority.requireCurrent(state, assault, observed.executions());
        if (assault == null || !subject.equals(assault.hiveId()) || assault.status() != SettlementAssaultStatus.HOT
                || assault.tacticalPlan().phase() != TacticalPlanPhase.TRAVEL) {
            throw new IllegalArgumentException("expedition march issue has no current HOT travel owner");
        }
        SceneLease lease = state.sceneLeases().get(observed.leaseId());
        if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isSettlementAssault(lease)
                || !FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(assault.id())
                || !lease.memberBodies(state.actorLocations()).equals(assault.formationBodies())) {
            throw new IllegalArgumentException("expedition march issue has no matching current lease/cursor");
        }
        SettlementAssault blocked = assault.recordMarchIssue(observed.issue()).withStatus(SettlementAssaultStatus.CONFLICT);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.put(lease.id(), lease.withStatus(SceneLeaseStatus.CONFLICT));
        return state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().replaceSettlementAssault(blocked)).sceneLeases(leases));
    }

    static boolean targetIntact(FrontierWorldState state, SettlementAssault assault) {
        return targetIntact(state.bootstrap(), state.structureConditions(), assault);
    }

    static boolean targetIntact(FrontierBootstrap bootstrap, java.util.Map<SubjectId, StructureCondition> conditions, SettlementAssault assault) {
        Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, assault.settlementId());
        return settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL)
                .anyMatch(value -> conditions.get(value.id()) != StructureCondition.DESTROYED);
    }
}
