package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Sole exact building-integrity projection. Availability, resources and work remain separate owners. */
public final class StructureIntegrity {
    private StructureIntegrity() { }
    public enum Disposition { ABSENT, REPLACED, UNKNOWN }
    public record RepairCandidate(SubjectId structure, BlockPosition position, StructureMaterial original,
                                  GrayboxSemanticPart part, String cause, Disposition disposition) { }
    private static Disposition disposition(FrontierWorldState state, BlockPosition position) {
        var observation = state.navigationGeometry().cells().get(position);
        if (observation == null || observation.cell().isEmpty()) return Disposition.UNKNOWN;
        return observation.cell().orElseThrow().observedState()
                .map(block -> block.kind().equals("minecraft:air") ? Disposition.ABSENT : Disposition.REPLACED)
                .orElse(Disposition.UNKNOWN);
    }
    public static List<RepairCandidate> candidates(FrontierWorldState state, SubjectId structure) {
        var building = FrontierWorldStateSupport.structureById(state.bootstrap(), structure);
        var damage = state.structureDamage().getOrDefault(structure, StructureDamage.empty(structure));
        return damage.cells().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator
                .comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)))
                .map(e -> new RepairCandidate(structure, e.getKey(), FrontierGrayboxPlan.intactStructureCell(
                        state.bootstrap().terrain(), building, e.getKey()).material(), e.getValue().semanticPart(), e.getValue().cause(), disposition(state, e.getKey())))
                .toList(); // Candidates are evidence; player replacements still require explicit repair admission.
    }
    /** Other confirmed physical protocols supply exact damage without inventing collision evidence. */
    public static FrontierWorldState recordDamage(FrontierWorldState state, StructureDamaged damage) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(damage, "structure damage");
        SettlementStructure structure = FrontierWorldStateSupport.structureById(state.bootstrap(), damage.structureId());
        GrayboxCell expected = FrontierGrayboxPlan.intactStructureCell(state.bootstrap().terrain(), structure, damage.position());
        if (expected == null || expected.semanticPart() != damage.semanticPart()) throw new IllegalArgumentException("observed damage is not an exact cell of its named structure");
        StructureDamage current = state.structureDamage().getOrDefault(damage.structureId(), StructureDamage.empty(damage.structureId()));
        StructureDamage nextDamage = current.record(damage.position(), damage.semanticPart(), damage.cause());
        Map<SubjectId, StructureDamage> nextDamageIndex = new LinkedHashMap<>(state.structureDamage()); nextDamageIndex.put(damage.structureId(), nextDamage);
        int destructiveThreshold = (FrontierGrayboxPlan.intactStructureCellCount(state.bootstrap().terrain(), structure) + 2) / 3;
        StructureCondition currentCondition = state.structureConditions().get(damage.structureId());
        StructureCondition nextCondition = currentCondition == StructureCondition.DESTROYED || nextDamage.cells().size() >= destructiveThreshold
                ? StructureCondition.DESTROYED : StructureCondition.DAMAGED;
        Map<SubjectId, StructureCondition> nextConditions = new LinkedHashMap<>(state.structureConditions()); nextConditions.put(damage.structureId(), nextCondition);
        return state.withChanges(FrontierWorldStateUpdate.begin().structureConditions(nextConditions).structureDamage(nextDamageIndex));
    }
    static FrontierWorldState restore(FrontierWorldState state, SubjectId structure, BlockPosition position, GrayboxSemanticPart part) {
        var damaged=state.structureDamage().get(structure);
        var remaining=damaged==null?null:damaged.repair(position,part);
        var deltas=new LinkedHashMap<>(state.physicalDeltas());deltas.remove(position);
        var damages=new LinkedHashMap<>(state.structureDamage());
        if(remaining==null||remaining.cells().isEmpty())damages.remove(structure);else damages.put(structure,remaining);
        var building=FrontierWorldStateSupport.structureById(state.bootstrap(),structure);
        int threshold=(FrontierGrayboxPlan.intactStructureCellCount(state.bootstrap().terrain(),building)+2)/3;
        var conditions=new LinkedHashMap<>(state.structureConditions());
        int count=remaining==null?0:remaining.cells().size();
        conditions.put(structure,count==0?StructureCondition.INTACT:count>=threshold?StructureCondition.DESTROYED:StructureCondition.DAMAGED);
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(deltas).structureDamage(damages).structureConditions(conditions));
    }
    public static FrontierWorldState observe(FrontierWorldState state, StructureCellObserved fact) {
        var building = FrontierWorldStateSupport.structureById(state.bootstrap(), fact.structureId());
        var original = FrontierGrayboxPlan.intactStructureCell(state.bootstrap().terrain(), building, fact.position());
        if (original == null || original.semanticPart() != fact.part())
            throw new IllegalArgumentException("structural outcome lost its exact declared cell");
        if (fact.originalMaterial() && original.material() instanceof StructureMaterial.Authored authored
                && !fact.observation().cell().orElseThrow().observedState().map(block -> StructuralMaterialIdentity.matches(authored.block(), block)).orElse(false))
            throw new IllegalArgumentException("restoration evidence has a foreign structural material");
        var old = state.physicalDeltas().get(fact.position());
        var target = new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE, fact.structureId());
        if (old != null && (!old.semanticTarget().equals(Optional.of(target)) || !old.semanticPart().equals(Optional.of(fact.part()))))
            throw new IllegalArgumentException("structural outcome has foreign damage ownership");
        var deltas = new LinkedHashMap<>(state.physicalDeltas());
        var damage = state.structureDamage().getOrDefault(fact.structureId(), StructureDamage.empty(fact.structureId()));
        if (fact.originalMaterial()) {
            deltas.remove(fact.position());
            if (damage.cells().containsKey(fact.position())) damage = damage.repair(fact.position(), fact.part());
        } else if (!damage.cells().containsKey(fact.position())) {
            damage = damage.record(fact.position(), fact.part(), fact.cause());
            deltas.put(fact.position(), new PhysicalDelta(fact.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(target), Optional.of(fact.part()), fact.cause()));
        }
        var index = new LinkedHashMap<>(state.structureDamage());
        if (damage.cells().isEmpty()) index.remove(fact.structureId()); else index.put(fact.structureId(), damage);
        var conditions = new LinkedHashMap<>(state.structureConditions());
        int threshold = (FrontierGrayboxPlan.intactStructureCellCount(state.bootstrap().terrain(), building) + 2) / 3;
        conditions.put(fact.structureId(), damage.cells().isEmpty() ? StructureCondition.INTACT
                : damage.cells().size() >= threshold ? StructureCondition.DESTROYED : StructureCondition.DAMAGED);
        var geometry = state.navigationGeometry().observe(state.navigationGeometry().revision(), Map.of(fact.position(), fact.observation()));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(deltas).structureDamage(index)
                .structureConditions(conditions).navigationGeometry(geometry));
    }
}
