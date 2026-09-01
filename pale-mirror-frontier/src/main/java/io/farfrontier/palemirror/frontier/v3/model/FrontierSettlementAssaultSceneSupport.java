package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Set;

/** Typed owner and admission rules for cargo-free settlement-assault scenes. */
public final class FrontierSettlementAssaultSceneSupport {
    private static final int MAX_HANDOFF_RADIUS = 32;
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
        return state.strategicPlans().settlementAssaults().values().stream()
                .filter(assault -> assault.status() == SettlementAssaultStatus.COLD_COMBAT)
                .sorted(java.util.Comparator.comparing(SettlementAssault::id))
                .map(assault -> FrontierSettlementAssaultBattlefield.candidate(state, assault)).flatMap(java.util.Optional::stream).toList();
    }

    static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        SettlementAssaultSceneCause cause = FrontierSceneBehaviors.settlementAssault(lease);
        SettlementAssault assault = require(state, cause);
        if (lease.status() != SceneLeaseStatus.PREPARED || assault.status() != SettlementAssaultStatus.COLD_COMBAT
                || !lease.handoffPosition().equals(assault.settlementAnchor()) || !targetIntact(state, assault)) {
            throw new IllegalArgumentException("assault scene must prepare one intact COLD battle at its retained anchor");
        }
        SettlementAssaultSceneCandidate candidate = FrontierSettlementAssaultBattlefield.candidate(state, assault)
                .orElseThrow(() -> new IllegalArgumentException("assault scene has no exact compiled battlefield"));
        Set<SubjectId> expected = new HashSet<>(candidate.memberPositions().keySet());
        Set<SubjectId> actual = new HashSet<>();
        Set<BlockPosition> floors = new HashSet<>();
        for (SceneMember member : lease.members()) {
            ActorLocation actor = state.actorLocations().get(member.actorId());
            BlockPosition floor = lease.memberPosition(member.actorId()).supportingSurface().support();
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || !actor.position().equals(floor)
                    || !floor.equals(candidate.memberPositions().get(member.actorId()))
                    || !actual.add(member.actorId()) || !floors.add(floor) || !near(assault.settlementAnchor(), floor)) {
                throw new IllegalArgumentException("assault scene needs exact living actors at distinct local hand-off floors");
            }
        }
        if (!actual.equals(expected)) throw new IllegalArgumentException("assault scene members must exactly match retained combatants");
    }

    static boolean targetIntact(FrontierWorldState state, SettlementAssault assault) {
        return targetIntact(state.bootstrap(), state.structureConditions(), assault);
    }

    static boolean targetIntact(FrontierBootstrap bootstrap, java.util.Map<SubjectId, StructureCondition> conditions, SettlementAssault assault) {
        Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, assault.settlementId());
        return settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL)
                .anyMatch(value -> conditions.get(value.id()) != StructureCondition.DESTROYED);
    }

    private static boolean near(BlockPosition anchor, BlockPosition floor) {
        long x = (long) anchor.x() - floor.x(), y = (long) anchor.y() - floor.y(), z = (long) anchor.z() - floor.z();
        return x * x + y * y + z * z <= (long) MAX_HANDOFF_RADIUS * MAX_HANDOFF_RADIUS;
    }
}
