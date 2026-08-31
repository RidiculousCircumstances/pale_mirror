package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Set;

/** Typed owner and admission rules for cargo-free settlement-assault scenes. */
final class FrontierSettlementAssaultSceneSupport {
    private static final int MAX_HANDOFF_RADIUS = 32;
    private FrontierSettlementAssaultSceneSupport() { }

    static SettlementAssault require(FrontierWorldState state, SettlementAssaultSceneCause cause) {
        return require(state.strategicPlans(), cause);
    }

    static SettlementAssault require(StrategicPlanState plans, SettlementAssaultSceneCause cause) {
        SettlementAssault assault = plans.settlementAssaults().get(cause.assaultId());
        if (assault == null || !assault.settlementId().equals(cause.settlementId())) {
            throw new IllegalArgumentException("assault scene cause has no matching canonical assault");
        }
        return assault;
    }

    static SubjectId owner(FrontierWorldState state, SceneLease lease) {
        if (!(lease.cause() instanceof SettlementAssaultSceneCause cause)) {
            throw new IllegalArgumentException("scene is not a settlement assault");
        }
        return require(state, cause).hiveId();
    }

    static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        if (!(lease.cause() instanceof SettlementAssaultSceneCause cause)) {
            throw new IllegalArgumentException("assault scene requires its typed cause");
        }
        SettlementAssault assault = require(state, cause);
        if (lease.status() != SceneLeaseStatus.PREPARED || assault.status() != SettlementAssaultStatus.COLD_COMBAT
                || !lease.handoffPosition().equals(assault.settlementAnchor()) || !targetIntact(state, assault)) {
            throw new IllegalArgumentException("assault scene must prepare one intact COLD battle at its retained anchor");
        }
        Set<SubjectId> expected = new HashSet<>(assault.attackerIds());
        expected.addAll(assault.defenderIds());
        Set<SubjectId> actual = new HashSet<>();
        Set<BlockPosition> floors = new HashSet<>();
        for (SceneMember member : lease.members()) {
            ActorLocation actor = state.actorLocations().get(member.actorId());
            BlockPosition floor = lease.memberPosition(member.actorId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || !actor.position().equals(floor)
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
