package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Deterministic settlement repair planner; it creates work only from exact deposited concrete. */
public final class StructuralRepairProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:structural-repair");
    private StructuralRepairProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:structural-repair-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.structural_repair.scan", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int nextOrdinal = FrontierWorldScheduleSupport.ordinal(action.id().value()) + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(nextOrdinal, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().structuralRepairScanInterval())));
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<PhysicalDelta> repairable = state.physicalDeltas().values().stream()
                .filter(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .filter(delta -> repairableOwner(state, delta.ownerId().orElseThrow()))
                .sorted(Comparator.comparingInt((PhysicalDelta delta) -> delta.position().x()).thenComparingInt(delta -> delta.position().y())
                        .thenComparingInt(delta -> delta.position().z())).findFirst();
        if (repairable.isEmpty()) return List.of(next);
        PhysicalDelta loss = repairable.orElseThrow(); SubjectId structureId = loss.ownerId().orElseThrow();
        GrayboxCell expected = FrontierGrayboxPlan.intactSemanticCell(state.bootstrap(), state.hiveColony(), state.routeTopology(), structureId, loss.position());
        if (expected == null) return List.of(next);
        SubjectId settlementId = FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), structureId);
        Optional<ExactItemStack> material = state.inventory().items().values().stream()
                .filter(item -> item.itemKind().equals(expected.material().repairItemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && state.inventory().containers().get(slot.containerId()).ownerId().equals(settlementId)
                        && state.inventory().surfaces().get(slot.containerId()).status() == ContainerSurfaceStatus.ACTIVE)
                .sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
        if (material.isEmpty()) return List.of(next);
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:repair-" + loss.position().x() + "-" + loss.position().y() + "-" + loss.position().z()),
                PhysicalIntentKind.STRUCTURAL_REPAIR, PhysicalIntentStatus.PREPARED, structureId, List.of(structureId, material.orElseThrow().id()),
                new FixedPosition(FixedScalar.whole(loss.position().x()), FixedScalar.whole(loss.position().y()), FixedScalar.whole(loss.position().z())),
                0, PhysicalPostcondition.STRUCTURAL_REPAIR_OBSERVED);
        return List.of(new ProposedEvent(settlementId, new PhysicalIntentPrepared(intent)), next);
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.STRUCTURAL_REPAIR || !repairableOwner(state, intent.causeSubjectId())) {
            throw new IllegalArgumentException("structural repair intent has an invalid owner");
        }
        if (!subject.equals(FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId()))
                || intent.subjectIds().size() != 2 || !intent.subjectIds().contains(intent.causeSubjectId())) {
            throw new IllegalArgumentException("structural repair intent lacks its settlement owner or material");
        }
        if (state.physicalIntents().values().stream().anyMatch(existing -> existing.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR
                && (existing.status() == PhysicalIntentStatus.PREPARED || existing.status() == PhysicalIntentStatus.RUNNING))) {
            throw new IllegalArgumentException("only one structural repair may be physically active");
        }
        return state.preparePhysicalIntent(intent);
    }

    private static boolean repairableOwner(FrontierWorldState state, SubjectId ownerId) {
        if (ownerId.value().startsWith("structure:")) return state.structureConditions().get(ownerId) != StructureCondition.DESTROYED;
        if (FrontierRouteNetwork.OWNER.equals(ownerId)) return true;
        return FrontierWorldStateSupport.isHiveOrgan(state.bootstrap(), state.hiveColony(), ownerId);
    }
}
