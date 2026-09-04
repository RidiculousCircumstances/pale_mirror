package io.farfrontier.palemirror.frontier.v3.process;

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
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Canonical admission owner for reusable resident service work.
 *
 * <p>Decontamination is the first form. It may reserve neither a physical effect nor a worker
 * independently: this one owner retains the exact task, medic, depot slot, two immutable legs,
 * field station and endpoint intent atomically.</p>
 */
public final class SettlementServiceWorkProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:decontamination");
    private SettlementServiceWorkProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:decontamination-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.decontamination.scan", 1);
    }

    public static List<ProposedEvent> planDecontamination(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal + 1,
                action.dueAt().ticks() + state.bootstrap().ruleset().cadence().decontaminationScanInterval())));
        if (state.serviceWorks().values().stream().anyMatch(work -> work.kind() == SettlementServiceWorkKind.DECONTAMINATION && work.phase().active())) {
            return List.of(next);
        }
        Optional<StrategicTask> task = pendingTask(state);
        if (task.isEmpty()) return List.of(next);
        Optional<Candidate> candidate = candidate(state, task.orElseThrow(), ordinal);
        if (candidate.isEmpty()) {
            return List.of(new ProposedEvent(task.orElseThrow().ownerId(), new StrategicTaskTransition(task.orElseThrow().id(), StrategicTaskStatus.BLOCKED)), next);
        }
        Candidate value = candidate.orElseThrow();
        return List.of(new ProposedEvent(value.settlement().id(), new StrategicTaskTransition(value.task().id(), StrategicTaskStatus.ACTIVE)),
                new ProposedEvent(value.settlement().id(), new SettlementServiceWorkStarted(value.task().id(), value.work(), value.inputIssue(), value.endpoint())), next);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, SettlementServiceWorkStarted started) {
        SettlementServiceWork work = started.work();
        if (!subject.equals(work.settlementId())) throw new IllegalArgumentException("service work start has a foreign settlement owner");
        StrategicTask task = state.strategicPlans().tasks().get(started.taskId());
        if (work.kind() != SettlementServiceWorkKind.DECONTAMINATION
                || !(work.target() instanceof SettlementServiceTarget.Infection infection)) {
            throw new IllegalArgumentException("service-work start has an invalid decontamination shape");
        }
        if (task == null || task.kind() != StrategicTaskKind.DECONTAMINATE_INFECTION_CELL || task.status() != StrategicTaskStatus.ACTIVE
                || !task.ownerId().equals(work.settlementId()) || !task.infectionTarget().equals(Optional.of(infection.cell()))) {
            throw new IllegalArgumentException("service-work start has no exact active decontamination task");
        }
        Candidate expected = candidate(state, task, ordinal(work.id())).orElseThrow(() ->
                new IllegalArgumentException("service-work start no longer has one deterministic exact candidate"));
        if (!expected.work().equals(work) || !expected.inputIssue().equals(started.inputIssueIntent()) || !expected.endpoint().equals(started.endpointIntent())) {
            throw new IllegalArgumentException("service-work start differs from its deterministic admission plan");
        }
        if (state.serviceWorks().containsKey(work.id()) || state.physicalIntents().containsKey(work.inputIssueIntentId())
                || state.physicalIntents().containsKey(work.endpointIntentId())) {
            throw new IllegalArgumentException("service-work start reuses a durable identity");
        }
        LinkedHashMap<SubjectId, SettlementServiceWork> works = new LinkedHashMap<>(state.serviceWorks());
        works.put(work.id(), work);
        LinkedHashMap<PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        intents.put(work.inputIssueIntentId(), started.inputIssueIntent());
        intents.put(work.endpointIntentId(), started.endpointIntent());
        return state.withChanges(FrontierWorldStateUpdate.begin().serviceWorks(works).physicalIntents(intents));
    }

    private static Optional<StrategicTask> pendingTask(FrontierWorldState state) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL
                && task.status() == StrategicTaskStatus.PENDING).sorted(Comparator.comparing(StrategicTask::id)).findFirst();
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, StrategicTask task, int ordinal) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        InfectionCell cell = task.infectionTarget().orElseThrow();
        Optional<SettlementStructure> facility = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) == StructureCondition.INTACT).min(Comparator.comparing(SettlementStructure::id));
        Optional<ResidentProfile> worker = FrontierWorldStateSupport.availableFieldResident(state, settlement.id(), ResidentProfession.MEDICAL_WORKER);
        Optional<ExactItemStack> material = state.inventory().items().values().stream().filter(item -> item.itemKind().equals(DecontaminationPolicy.REAGENT))
                .filter(item -> activeDepotMaterial(state, settlement, item)).sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
        if (facility.isEmpty() || worker.isEmpty() || material.isEmpty() || !state.infection().containsKey(cell)
                || !nearby(state.bootstrap().ruleset(), facility.orElseThrow(), cell)) return Optional.empty();
        SubjectId workId = new SubjectId("service:decontamination-" + ordinal);
        SettlementServiceWorkTraversal.Plan traversal;
        try {
            traversal = SettlementServiceWorkTraversal.compileDecontamination(state.bootstrap(), settlement,
                    state.actorLocations().get(worker.orElseThrow().id()), cell, workId);
        } catch (IllegalArgumentException unavailable) { return Optional.empty(); }
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) material.orElseThrow().custody();
        PhysicalIntentId inputIssueId = new PhysicalIntentId("intent:service-input-issue-" + ordinal);
        PhysicalIntentId endpointId = new PhysicalIntentId("intent:service-decontamination-" + ordinal);
        SettlementServiceWork work = new SettlementServiceWork(workId, SettlementServiceWorkKind.DECONTAMINATION, settlement.id(), worker.orElseThrow().id(),
                facility.orElseThrow().id(), source, traversal.inputStation(), traversal.workStation(), material.orElseThrow().id(),
                new SettlementServiceTarget.Infection(cell), inputIssueId, endpointId, traversal.inputTraversal(), 0, traversal.workTraversal(), 0,
                SettlementServiceWorkPhase.PREPARED, 0);
        PhysicalIntent inputIssue = new PhysicalIntent(inputIssueId, PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE, PhysicalIntentStatus.PREPARED,
                work.id(), List.of(work.id(), work.workerId(), work.inputItemId()), fixed(work.inputStation().support()), 0,
                PhysicalPostcondition.SETTLEMENT_SERVICE_INPUT_ISSUED_OBSERVED);
        PhysicalIntent endpoint = new PhysicalIntent(endpointId, PhysicalIntentKind.DECONTAMINATION, PhysicalIntentStatus.PREPARED,
                work.id(), List.of(work.id(), work.workerId(), work.inputItemId()), fixed(cell.originAtY(0)), 0,
                PhysicalPostcondition.DECONTAMINATION_OBSERVED);
        return Optional.of(new Candidate(task, settlement, work, inputIssue, endpoint));
    }

    private static boolean activeDepotMaterial(FrontierWorldState state, Settlement settlement, ExactItemStack item) {
        if (!(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))) return false;
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        return surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    private static boolean nearby(FrontierRuleset ruleset, SettlementStructure facility, InfectionCell cell) {
        BlockPosition position = cell.originAtY(facility.anchor().y()); long dx = position.x() - facility.anchor().x(), dz = position.z() - facility.anchor().z();
        int radius = ruleset.spatial().decontaminationResponseRadius();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static FixedPosition fixed(BlockPosition position) {
        return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z()));
    }

    private static int ordinal(SubjectId workId) {
        String prefix = "service:decontamination-";
        if (!workId.value().startsWith(prefix)) throw new IllegalArgumentException("service work id has no deterministic decontamination ordinal");
        try { return Integer.parseInt(workId.value().substring(prefix.length())); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("service work id has invalid decontamination ordinal", invalid); }
    }

    private record Candidate(StrategicTask task, Settlement settlement, SettlementServiceWork work,
                             PhysicalIntent inputIssue, PhysicalIntent endpoint) { }
}
