package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure projection of exact source people and bioforms into their semantic
 * physical admission spaces. This owns no executor state and never turns a
 * person or swarm into an aggregate presentation marker.
 */
final class ReferenceGrayboxActorProjection {
    private ReferenceGrayboxActorProjection() { }

    static List<ReferenceGrayboxSnapshot.Bioform> bioforms(ReferenceWorld world) {
        List<ReferenceGrayboxSnapshot.Bioform> result = new ArrayList<>();
        for (ReferenceSwarm swarm : sorted(world.infection().swarms(), ReferenceSwarm::id)) {
            ReferenceGrayboxLayout.Point anchor = ReferenceGrayboxLayout.position(swarm.x(), swarm.y());
            int localOrdinal = 0;
            for (Map.Entry<ReferenceBioformKind, List<String>> entry : swarm.bioformIds().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ReferenceBioformKind::id))).toList()) {
                for (String bioformId : entry.getValue()) {
                    result.add(new ReferenceGrayboxSnapshot.Bioform(bioformId, swarm.id(), entry.getKey().id(),
                            ReferenceGrayboxLayout.cellActorSlot(anchor, localOrdinal++), swarm.phase().id(), swarm.feral(),
                            "bioform." + entry.getKey().id()));
                }
            }
        }
        return List.copyOf(result);
    }

    static List<ReferenceGrayboxSnapshot.Resident> residents(
            ReferenceWorld world, Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas,
            Map<Integer, ReferenceGrayboxLayout.Point> operationPositions, Map<Integer, ReferenceGrayboxLayout.Point> postPositions
    ) {
        Map<String, List<ResidentDraft>> grouped = new TreeMap<>();
        for (ReferenceSettlement settlement : sorted(world.settlements().values(), ReferenceSettlement::id)) {
            ReferenceResidentLedger ledger = settlement.residents();
            if (ledger == null) throw new IllegalStateException("graybox settlement has no resident ledger: " + settlement.id());
            for (String residentId : ledger.livingIds()) {
                ReferenceResident resident = ledger.resident(residentId);
                ReferenceGrayboxLayout.Point anchor = residentAnchor(resident, settlementAreas, operationPositions, postPositions);
                String owner = resident.location().name() + ":" + (resident.locationRef() == null ? resident.homeSettlementId() : resident.locationRef());
                grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new ResidentDraft(resident, anchor));
            }
        }
        List<ReferenceGrayboxSnapshot.Resident> result = new ArrayList<>();
        for (List<ResidentDraft> group : grouped.values()) {
            group.sort(Comparator.comparing(item -> item.resident().id()));
            for (int index = 0; index < group.size(); index++) {
                ResidentDraft draft = group.get(index);
                ReferenceResident resident = draft.resident();
                ReferenceGrayboxLayout.Point position = resident.location() == ReferenceResidentLocation.SETTLEMENT
                        ? ReferenceGrayboxLayout.settlementActorSlot(requiredArea(settlementAreas, resident.homeSettlementId()), index)
                        : ReferenceGrayboxLayout.cellActorSlot(draft.anchor(), index);
                result.add(new ReferenceGrayboxSnapshot.Resident(resident.id(), resident.homeSettlementId(), resident.occupation(),
                        resident.economicClass(), resident.location().name().toLowerCase(java.util.Locale.ROOT), resident.locationRef(),
                        resident.condition().name().toLowerCase(java.util.Locale.ROOT), resident.deploymentRole(), position,
                        resident.condition() == ReferenceResidentCondition.WOUNDED ? "resident.wounded" : "resident." + resident.occupation()));
            }
        }
        return List.copyOf(result);
    }

    private static ReferenceGrayboxLayout.Point residentAnchor(ReferenceResident resident,
                                                                 Map<Integer, ReferenceGrayboxLayout.Rectangle> settlementAreas,
                                                                 Map<Integer, ReferenceGrayboxLayout.Point> operations,
                                                                 Map<Integer, ReferenceGrayboxLayout.Point> posts) {
        return switch (resident.location()) {
            case SETTLEMENT -> centre(requiredArea(settlementAreas, resident.homeSettlementId()));
            case OPERATION -> requiredPoint(operations, resident.locationRef(), "operation", resident.id());
            case FIELD_POST -> requiredPoint(posts, resident.locationRef(), "field post", resident.id());
        };
    }

    private static ReferenceGrayboxLayout.Point centre(ReferenceGrayboxLayout.Rectangle rectangle) {
        return new ReferenceGrayboxLayout.Point(rectangle.centreX(), rectangle.centreZ());
    }

    private static <K> ReferenceGrayboxLayout.Rectangle requiredArea(Map<K, ReferenceGrayboxLayout.Rectangle> values, K key) {
        ReferenceGrayboxLayout.Rectangle area = values.get(key);
        if (area == null) throw new IllegalStateException("graybox area is missing for " + key);
        return area;
    }

    private static ReferenceGrayboxLayout.Point requiredPoint(Map<Integer, ReferenceGrayboxLayout.Point> values, Integer key,
                                                               String owner, String residentId) {
        ReferenceGrayboxLayout.Point point = key == null ? null : values.get(key);
        if (point == null) throw new IllegalStateException("resident " + residentId + " has no live " + owner + " owner");
        return point;
    }

    private static <T, U extends Comparable<? super U>> List<T> sorted(Iterable<T> values, java.util.function.Function<T, U> key) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        result.sort(Comparator.comparing(key));
        return result;
    }

    private record ResidentDraft(ReferenceResident resident, ReferenceGrayboxLayout.Point anchor) { }
}
