package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Exact local care owner: physical supply receipt precedes every patient recovery. */
public final class MedicalTreatmentProcess {
    private MedicalTreatmentProcess() { }

    /** Returns no event when a settlement health review has no admissible patient/team/supply. */
    public static List<ProposedEvent> planStart(FrontierWorldState state, SubjectId settlementId, int ordinal) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        if (state.humanPopulation().medicalOperations().values().stream().anyMatch(operation -> operation.active() && operation.settlementId().equals(settlementId))) {
            return List.of();
        }
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        Optional<ResidentProfile> patient = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.humanPopulation().health(resident.id()).status() == ResidentHealthStatus.INFECTED)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> assignments.idle(resident.id()))
                .min(Comparator.comparing(ResidentProfile::id));
        Optional<ResidentProfile> medic = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId) && resident.profession() == ResidentProfession.MEDICAL_WORKER)
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> assignments.idle(resident.id()))
                .min(Comparator.comparing(ResidentProfile::id));
        Optional<ExactItemStack> supply = state.inventory().items().values().stream()
                .filter(item -> MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY.equals(item.itemKind()) && item.count() >= 1)
                .filter(item -> item.economicOwnerId().equals(settlementId) && item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && slot.containerId().equals(FrontierWorldState.depotId(settlementId)))
                .filter(item -> state.inventory().surfaces().get(FrontierWorldState.depotId(settlementId)).status() == ContainerSurfaceStatus.ACTIVE)
                .min(Comparator.comparing(ExactItemStack::id));
        Optional<SettlementStructure> infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY)
                .filter(structure -> state.structureConditions().get(structure.id()) != StructureCondition.DESTROYED).min(Comparator.comparing(SettlementStructure::id));
        if (patient.isEmpty() || medic.isEmpty() || supply.isEmpty() || infirmary.isEmpty()) return List.of();
        SubjectId id = new SubjectId("medical:" + settlementId.value().substring("settlement:".length()) + "-" + ordinal);
        MedicalEvacuationOperation operation = new MedicalEvacuationOperation(id, settlementId, patient.orElseThrow().id(), infirmary.orElseThrow().id(),
                MedicalEvacuationTeam.forOperation(id, settlementId, List.of(medic.orElseThrow().id())), supply.orElseThrow().id(),
                new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId("intent:" + id.value().replace(':', '-') + "-consume"), MedicalEvacuationStatus.PREPARED, -1L);
        PhysicalIntent intent = new PhysicalIntent(operation.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION, PhysicalIntentStatus.PREPARED,
                id, List.of(id, operation.supplyItemId()), new FixedPosition(FixedScalar.whole(infirmary.orElseThrow().anchor().x()),
                FixedScalar.whole(infirmary.orElseThrow().anchor().y()), FixedScalar.whole(infirmary.orElseThrow().anchor().z())), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        return List.of(new ProposedEvent(settlementId, new MedicalTreatmentStarted(operation)), new ProposedEvent(settlementId, new PhysicalIntentPrepared(intent)));
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        MedicalEvacuationOperation operation = operationForIntent(state, intent);
        ProposedEvent physical = new ProposedEvent(operation.settlementId(), transition);
        return switch (transition.status()) {
            case RUNNING -> {
                if (operation.status() != MedicalEvacuationStatus.PREPARED) throw new IllegalArgumentException("medical treatment may start only from prepared care");
                yield List.of(physical, new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.TREATING)));
            }
            case CONFIRMED -> {
                if (operation.status() == MedicalEvacuationStatus.BLOCKED) yield List.of(physical);
                if (operation.status() != MedicalEvacuationStatus.TREATING && operation.status() != MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART) {
                    throw new IllegalArgumentException("medical treatment receipt has no active care operation");
                }
                if (state.actorLocations().get(operation.patientId()).condition().status() != ActorLifeStatus.ALIVE) yield List.of(physical,
                        new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.BLOCKED)));
                yield List.of(physical, new ProposedEvent(operation.settlementId(),
                        new ResidentHealthTransition(operation.patientId(), ResidentHealthStatus.RECOVERING, now)),
                        new ProposedEvent(operation.settlementId(), new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.COMPLETED)));
            }
            case UNKNOWN_AFTER_RESTART -> {
                if (operation.status() == MedicalEvacuationStatus.BLOCKED) yield List.of(physical);
                if (operation.status() != MedicalEvacuationStatus.PREPARED && operation.status() != MedicalEvacuationStatus.TREATING) {
                    throw new IllegalArgumentException("medical treatment uncertainty has no active care operation");
                }
                yield List.of(physical, new ProposedEvent(operation.settlementId(),
                        new MedicalTreatmentTransition(operation.id(), MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART)));
            }
            default -> List.of(physical);
        };
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, MedicalTreatmentStarted started) {
        MedicalEvacuationOperation operation = started.operation();
        if (!subject.equals(operation.settlementId())) throw new IllegalArgumentException("medical treatment start lacks settlement owner");
        MedicalEvacuationStateSupport.validate(state.bootstrap(), state.humanPopulation().startMedicalOperation(operation), state.actorLocations(),
                state.structureConditions(), state.inventory(), state.physicalIntents());
        return state.withHumanPopulation(state.humanPopulation().startMedicalOperation(operation));
    }

    public static FrontierWorldState reduceTransition(FrontierWorldState state, SubjectId subject, long atTick, MedicalTreatmentTransition transition) {
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(transition.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("medical treatment transition lacks operation owner");
        if (transition.status() == MedicalEvacuationStatus.TREATING && operation.status() != MedicalEvacuationStatus.PREPARED
                || transition.status() == MedicalEvacuationStatus.COMPLETED && operation.status() != MedicalEvacuationStatus.TREATING
                        && operation.status() != MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART
                || transition.status() == MedicalEvacuationStatus.UNKNOWN_AFTER_RESTART && operation.status() != MedicalEvacuationStatus.PREPARED
                        && operation.status() != MedicalEvacuationStatus.TREATING
                || transition.status() == MedicalEvacuationStatus.BLOCKED && !operation.active()) {
            throw new IllegalArgumentException("medical treatment lifecycle transition is invalid");
        }
        return state.withHumanPopulation(state.humanPopulation().transitionMedicalOperation(operation.id(), transition.status(), atTick));
    }

    public static MedicalEvacuationOperation operationForIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("medical treatment has invalid physical intent kind");
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        if (operation == null || !operation.consumptionIntentId().equals(intent.id()) || !intent.subjectIds().equals(List.of(operation.id(), operation.supplyItemId()))) {
            throw new IllegalArgumentException("medical treatment intent has no active exact operation");
        }
        return operation;
    }
}
