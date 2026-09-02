package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure compiler for the exact, local floor columns of a settlement assault. */
public final class FrontierSettlementAssaultBattlefield {
    private static final int MAX_HANDOFF_RADIUS = 32;
    private FrontierSettlementAssaultBattlefield() { }

    public static Optional<List<BlockPosition>> attackerFloors(FrontierWorldState state, HiveSettlementKnowledge.Sighting sighting,
                                                         List<SubjectId> defenderIds, int attackerCount) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), sighting.settlementId());
        if (!settlement.anchor().equals(sighting.settlementAnchor()) || attackerCount < 1 || attackerCount > SettlementAssault.MAX_ATTACKERS) {
            return Optional.empty();
        }
        Set<BlockPosition> structureCells = FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures());
        Set<BlockPosition> occupied = new LinkedHashSet<>();
        for (SubjectId defender : defenderIds) {
            ActorLocation location = state.actorLocations().get(defender);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !localClearFloor(state.bootstrap().bounds(), settlement.anchor(), structureCells, location.supportingSurface().support()) || !occupied.add(location.supportingSurface().support())) return Optional.empty();
        }
        List<BlockPosition> choices = FrontierSettlementActorSlots.slots(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement.anchor(), structureCells,
                attackerCount + occupied.size() + 16).stream().filter(position -> localClearFloor(state.bootstrap().bounds(), settlement.anchor(), structureCells, position))
                .filter(position -> !occupied.contains(position)).limit(attackerCount).toList();
        return choices.size() == attackerCount ? Optional.of(choices) : Optional.empty();
    }

    public static Optional<SettlementAssaultSceneCandidate> candidate(FrontierWorldState state, SettlementAssault assault) {
        if ((assault.status() != SettlementAssaultStatus.WAITING_FOR_BATTLE && assault.status() != SettlementAssaultStatus.COLD_COMBAT)
                || !FrontierSettlementAssaultSceneSupport.targetIntact(state, assault)) {
            return Optional.empty();
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), assault.settlementId());
        Set<BlockPosition> structureCells = FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures());
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        List<SubjectId> members = new ArrayList<>(assault.attackerIds()); members.addAll(assault.defenderIds());
        members.sort(Comparator.naturalOrder());
        for (SubjectId member : members) {
            ActorLocation location = state.actorLocations().get(member);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !localClearFloor(state.bootstrap().bounds(), settlement.anchor(), structureCells, location.supportingSurface().support()) || positions.put(member, location.supportingSurface().support()) != null) {
                return Optional.empty();
            }
        }
        if (positions.values().stream().distinct().count() != positions.size()) return Optional.empty();
        return Optional.of(new SettlementAssaultSceneCandidate(assault.id(), settlement.id(), settlement.anchor(), positions));
    }

    static boolean localClearFloor(FrontierWorldState state, Settlement settlement, BlockPosition position) {
        return localClearFloor(state.bootstrap().bounds(), settlement.anchor(), FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures()), position);
    }

    private static boolean localClearFloor(WorldBounds bounds, BlockPosition anchor, Set<BlockPosition> structureCells, BlockPosition position) {
        return nearby(anchor, position) && FrontierSettlementActorSlots.clearFloor(bounds, structureCells, position);
    }

    private static boolean nearby(BlockPosition anchor, BlockPosition position) {
        long x = (long) anchor.x() - position.x(), y = (long) anchor.y() - position.y(), z = (long) anchor.z() - position.z();
        return x * x + y * y + z * z <= (long) MAX_HANDOFF_RADIUS * MAX_HANDOFF_RADIUS;
    }
}
