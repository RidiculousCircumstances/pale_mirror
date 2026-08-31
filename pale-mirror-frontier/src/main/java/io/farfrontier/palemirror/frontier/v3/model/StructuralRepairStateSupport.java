package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure confirmation rules for one exact settlement structural-repair physical intent. */
final class StructuralRepairStateSupport {
    private StructuralRepairStateSupport() { }

    static void validateReceipt(PhysicalIntent intent, StructuralRepairObservation repair) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.STRUCTURAL_REPAIR) {
            throw new IllegalArgumentException("repair receipt belongs to a non-repair intent");
        }
        if (!intent.subjectIds().contains(repair.itemId())) {
            throw new IllegalArgumentException("repair receipt lacks its exact structure and material");
        }
        if (!repair.position().equals(blockPosition(intent.origin()))) throw new IllegalArgumentException("repair receipt position differs from intent origin");
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent current, StructuralRepairObservation repair,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateReceipt(current, repair);
        PhysicalDelta delta = state.physicalDeltas().get(repair.position());
        if (delta == null || delta.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS || !delta.ownerId().equals(java.util.Optional.of(current.causeSubjectId()))) {
            throw new IllegalArgumentException("repair has no matching known physical loss");
        }
        GrayboxCell expected = FrontierGrayboxPlan.intactSemanticCell(state.bootstrap(), state.hiveColony(), state.routeTopology(), current.causeSubjectId(), repair.position());
        ExactItemStack material = state.inventory().items().get(repair.itemId());
        if (expected == null || material == null || !material.itemKind().equals(expected.material().repairItemKind())) {
            throw new IllegalArgumentException("repair material does not match its lost semantic cell");
        }
        if (!(material.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !state.inventory().containers().get(slot.containerId()).ownerId().equals(FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), current.causeSubjectId()))) {
            throw new IllegalArgumentException("repair material is not in its semantic owner's container");
        }
        if (!current.causeSubjectId().value().startsWith("structure:")) return completeHiveOrgan(state, current, repair, nextIntents);
        StructureDamage damage = state.structureDamage().get(current.causeSubjectId());
        StructureDamage repairedDamage = damage == null ? null : damage.repair(repair.position(), delta.semanticPart().orElseThrow());
        Map<BlockPosition, PhysicalDelta> nextDeltas = new LinkedHashMap<>(state.physicalDeltas()); nextDeltas.remove(repair.position());
        Map<SubjectId, StructureDamage> nextDamage = new LinkedHashMap<>(state.structureDamage());
        if (repairedDamage == null || repairedDamage.cells().isEmpty()) nextDamage.remove(current.causeSubjectId());
        else nextDamage.put(current.causeSubjectId(), repairedDamage);
        SettlementStructure structure = FrontierWorldStateSupport.structureById(state.bootstrap(), current.causeSubjectId());
        int threshold = (FrontierGrayboxPlan.intactStructureCellCount(structure) + 2) / 3;
        StructureCondition condition = repairedDamage != null && repairedDamage.cells().size() >= threshold ? StructureCondition.DESTROYED
                : repairedDamage != null && !repairedDamage.cells().isEmpty() ? StructureCondition.DAMAGED : StructureCondition.INTACT;
        Map<SubjectId, StructureCondition> nextConditions = new LinkedHashMap<>(state.structureConditions()); nextConditions.put(structure.id(), condition);
        nextIntents.put(current.id(), current.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(repair.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> nextObservations = new LinkedHashMap<>(state.physicalObservations()); nextObservations.put(repair.id(), repair);
        return state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(nextConditions).inventory(state.inventory().consumeOne(repair.itemId()))
                .physicalIntents(nextIntents).physicalObservations(nextObservations).structureDamage(nextDamage).physicalDeltas(nextDeltas));
    }

    private static FrontierWorldState completeHiveOrgan(FrontierWorldState state, PhysicalIntent current, StructuralRepairObservation repair,
                                                         Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        if (FrontierRouteNetwork.OWNER.equals(current.causeSubjectId())) return completeRoute(state, current, repair, nextIntents);
        if (!FrontierWorldStateSupport.isHiveOrgan(state.bootstrap(), state.hiveColony(), current.causeSubjectId())) {
            throw new IllegalArgumentException("repair owner is neither a settlement structure nor hive organ");
        }
        Map<BlockPosition, PhysicalDelta> deltas = new LinkedHashMap<>(state.physicalDeltas()); deltas.remove(repair.position());
        nextIntents.put(current.id(), current.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(repair.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(repair.id(), repair);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeOne(repair.itemId())).physicalIntents(nextIntents)
                .physicalObservations(observations).physicalDeltas(deltas));
    }

    private static FrontierWorldState completeRoute(FrontierWorldState state, PhysicalIntent current, StructuralRepairObservation repair,
                                                     Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        Map<BlockPosition, PhysicalDelta> deltas = new LinkedHashMap<>(state.physicalDeltas()); deltas.remove(repair.position());
        nextIntents.put(current.id(), current.withStatus(PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(repair.id())));
        Map<PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(); observations.putAll(state.physicalObservations()); observations.put(repair.id(), repair);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeOne(repair.itemId())).physicalIntents(nextIntents)
                .physicalObservations(observations).physicalDeltas(deltas));
    }

    private static BlockPosition blockPosition(io.farfrontier.palemirror.frontier.v3.api.FixedPosition position) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (position.x().raw() % scale != 0L || position.y().raw() % scale != 0L || position.z().raw() % scale != 0L) {
            throw new IllegalArgumentException("repair origin must be a whole block position");
        }
        return new BlockPosition(Math.toIntExact(position.x().raw() / scale), Math.toIntExact(position.y().raw() / scale), Math.toIntExact(position.z().raw() / scale));
    }
}
