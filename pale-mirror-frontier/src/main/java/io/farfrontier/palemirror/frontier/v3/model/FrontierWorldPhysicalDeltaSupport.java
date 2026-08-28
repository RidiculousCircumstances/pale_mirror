package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure bounded validation and domain consequences for observed physical block deltas. */
final class FrontierWorldPhysicalDeltaSupport {
    static final int MAX_PHYSICAL_DELTAS = 65_536;

    private FrontierWorldPhysicalDeltaSupport() { }

    /** Validates retained evidence; a superseded route loss stays historical even after rerouting. */
    static void validate(FrontierBootstrap bootstrap, HiveColony colony, Map<BlockPosition, PhysicalDelta> deltas) {
        if (deltas.size() > MAX_PHYSICAL_DELTAS) throw new IllegalArgumentException("physical delta retention limit exceeded");
        for (Map.Entry<BlockPosition, PhysicalDelta> entry : deltas.entrySet()) {
            BlockPosition position = entry.getKey(); PhysicalDelta delta = entry.getValue();
            if (!position.equals(delta.position())) throw new IllegalArgumentException("physical delta key differs from position evidence");
            FrontierWorldStateSupport.requirePosition(bootstrap.bounds(), position);
            if (delta.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS) continue;
            if (FrontierRouteNetwork.OWNER.equals(delta.ownerId().orElseThrow())) {
                if (delta.semanticPart().orElseThrow() != GrayboxSemanticPart.ROUTE_SURFACE) {
                    throw new IllegalArgumentException("route loss must name a route surface");
                }
                continue;
            }
            GrayboxCell expected = FrontierGrayboxPlan.intactSemanticCell(bootstrap, colony, delta.ownerId().orElseThrow(), position);
            if (expected == null || expected.semanticPart() != delta.semanticPart().orElseThrow()) {
                throw new IllegalArgumentException("known physical delta is not an exact semantic cell");
            }
        }
    }

    static FrontierWorldState record(FrontierWorldState state, PhysicalDelta delta) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(delta, "physical delta");
        if (state.physicalDeltas().containsKey(delta.position())) throw new IllegalArgumentException("physical delta is already recorded at this position");
        if (state.physicalDeltas().size() >= MAX_PHYSICAL_DELTAS) throw new IllegalArgumentException("physical delta retention limit exceeded");
        validateCurrent(state, delta);
        Map<BlockPosition, PhysicalDelta> next = new LinkedHashMap<>(state.physicalDeltas()); next.put(delta.position(), delta);
        FrontierWorldState changed = new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(),
                state.inventory(), state.productionJobs(), state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), next, state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
        if (delta.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS || !delta.ownerId().orElseThrow().value().startsWith("structure:")) return changed;
        return changed.recordStructureDamage(new StructureDamaged(delta.ownerId().orElseThrow(), delta.position(), delta.semanticPart().orElseThrow(), delta.cause()));
    }

    private static void validateCurrent(FrontierWorldState state, PhysicalDelta delta) {
        if (delta.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS) return;
        GrayboxCell expected = FrontierGrayboxPlan.intactSemanticCell(state.bootstrap(), state.hiveColony(), state.routeTopology(),
                delta.ownerId().orElseThrow(), delta.position());
        if (expected == null || expected.semanticPart() != delta.semanticPart().orElseThrow()) {
            throw new IllegalArgumentException("known physical delta is not an exact current semantic cell");
        }
    }

    static FrontierWorldState recordStructureDamage(FrontierWorldState state, StructureDamaged damage) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(damage, "structure damage");
        SettlementStructure structure = FrontierWorldStateSupport.structureById(state.bootstrap(), damage.structureId());
        GrayboxCell expected = FrontierGrayboxPlan.intactStructureCell(structure, damage.position());
        if (expected == null || expected.semanticPart() != damage.semanticPart()) throw new IllegalArgumentException("observed damage is not an exact cell of its named structure");
        StructureDamage current = state.structureDamage().getOrDefault(damage.structureId(), StructureDamage.empty(damage.structureId()));
        StructureDamage nextDamage = current.record(damage.position(), damage.semanticPart(), damage.cause());
        Map<SubjectId, StructureDamage> nextDamageIndex = new LinkedHashMap<>(state.structureDamage()); nextDamageIndex.put(damage.structureId(), nextDamage);
        int destructiveThreshold = (FrontierGrayboxPlan.intactStructureCellCount(structure) + 2) / 3;
        StructureCondition currentCondition = state.structureConditions().get(damage.structureId());
        StructureCondition nextCondition = currentCondition == StructureCondition.DESTROYED || nextDamage.cells().size() >= destructiveThreshold
                ? StructureCondition.DESTROYED : StructureCondition.DAMAGED;
        Map<SubjectId, StructureCondition> nextConditions = new LinkedHashMap<>(state.structureConditions()); nextConditions.put(damage.structureId(), nextCondition);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), nextConditions, state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                nextDamageIndex, state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans());
    }

    static boolean organOperational(FrontierBootstrap bootstrap, HiveColony colony, Map<BlockPosition, PhysicalDelta> deltas, SubjectId organId) {
        HiveOrgan organ = bootstrap.hive().organs().stream().filter(value -> value.id().equals(organId)).findFirst().orElse(colony.addedOrgans().get(organId));
        if (organ == null) throw new IllegalArgumentException("unknown hive organ: " + organId.value());
        long lost = deltas.values().stream().filter(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                && delta.ownerId().orElseThrow().equals(organId)).count();
        return lost < (FrontierGrayboxPlan.intactOrganCellCount(organ) + 2L) / 3L;
    }
}
