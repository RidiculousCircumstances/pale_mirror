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

/** Plans one exact reagent-backed, physically observable infection-cell treatment at a time. */
public final class DecontaminationProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:decontamination");
    private static final long RESPONSE_RADIUS_SQUARED = 25_600L;
    private DecontaminationProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:decontamination-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.decontamination.scan", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal + 1, action.dueAt().ticks() + 200L)));
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.DECONTAMINATION
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<StrategicTask> task = pendingTask(state);
        if (task.isEmpty()) return List.of(next);
        Optional<Candidate> candidate = candidate(state, task.orElseThrow());
        if (candidate.isEmpty()) return List.of(new ProposedEvent(task.orElseThrow().ownerId(), new StrategicTaskTransition(task.orElseThrow().id(), StrategicTaskStatus.BLOCKED)), next);
        Candidate value = candidate.orElseThrow(); InfectionCell cell = value.cell();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:decontamination-" + ordinal), PhysicalIntentKind.DECONTAMINATION,
                PhysicalIntentStatus.PREPARED, value.facility().id(), List.of(value.facility().id(), value.material().id()),
                new FixedPosition(FixedScalar.whole(cell.originAtY(0).x()), FixedScalar.whole(0), FixedScalar.whole(cell.originAtY(0).z())), 0,
                PhysicalPostcondition.DECONTAMINATION_OBSERVED);
        return List.of(new ProposedEvent(value.settlement().id(), new StrategicTaskTransition(value.task().id(), StrategicTaskStatus.ACTIVE)),
                new ProposedEvent(value.settlement().id(), new PhysicalIntentPrepared(intent)), next);
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.DECONTAMINATION) throw new IllegalArgumentException("decontamination intent kind is invalid");
        Settlement settlement = owner(state, intent.causeSubjectId());
        if (!subject.equals(settlement.id())) throw new IllegalArgumentException("decontamination intent has a foreign settlement owner");
        taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
        DecontaminationStateSupport.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        Settlement settlement = owner(state, intent.causeSubjectId());
        StrategicTask task = taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(settlement.id(), transition);
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) {
            return List.of(physical, new ProposedEvent(settlement.id(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.COMPLETED)));
        }
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(physical, new ProposedEvent(settlement.id(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED)));
        }
        return List.of(physical);
    }

    public static StrategicTask taskForIntent(FrontierWorldState state, PhysicalIntent intent, StrategicTaskStatus requiredStatus) {
        InfectionCell target = DecontaminationStateSupport.cell(intent); Settlement settlement = owner(state, intent.causeSubjectId());
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL
                && task.ownerId().equals(settlement.id()) && task.infectionTarget().equals(Optional.of(target)) && task.status() == requiredStatus)
                .sorted(Comparator.comparing(StrategicTask::id)).reduce((left, right) -> { throw new IllegalArgumentException("decontamination task binding is ambiguous"); })
                .orElseThrow(() -> new IllegalArgumentException("decontamination intent has no active strategic task"));
    }

    private static Optional<StrategicTask> pendingTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL
                && task.status() == StrategicTaskStatus.PENDING).sorted(Comparator.comparing(StrategicTask::id)).findFirst();
    }
    private static Optional<Candidate> candidate(FrontierWorldState state, StrategicTask task) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        Optional<SettlementStructure> facility = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        Optional<ExactItemStack> material = state.inventory().items().values().stream().filter(item -> item.itemKind().equals(DecontaminationPolicy.REAGENT))
                .filter(item -> ownedActiveMaterial(state, settlement, item)).sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
        InfectionCell cell = task.infectionTarget().orElseThrow();
        return facility.isPresent() && material.isPresent() && state.infection().containsKey(cell) && nearby(facility.orElseThrow(), cell)
                ? Optional.of(new Candidate(task, settlement, facility.orElseThrow(), material.orElseThrow(), cell)) : Optional.empty();
    }

    private static boolean ownedActiveMaterial(FrontierWorldState state, Settlement settlement, ExactItemStack item) {
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot)) return false;
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        return container != null && container.ownerId().equals(settlement.id()) && surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean nearby(SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        return dx * dx + dz * dz <= RESPONSE_RADIUS_SQUARED;
    }

    public static Settlement owner(FrontierWorldState state, SubjectId facilityId) {
        return owner(state.bootstrap(), facilityId);
    }

    public static Settlement owner(FrontierBootstrap bootstrap, SubjectId facilityId) {
        for (Settlement settlement : bootstrap.settlements()) for (SettlementStructure structure : settlement.structures()) {
            if (structure.id().equals(facilityId) && structure.kind() == StructureKind.INFIRMARY) return settlement;
        }
        throw new IllegalArgumentException("decontamination facility is not a settlement infirmary: " + facilityId.value());
    }

    record Candidate(StrategicTask task, Settlement settlement, SettlementStructure facility, ExactItemStack material, InfectionCell cell) { }
}
