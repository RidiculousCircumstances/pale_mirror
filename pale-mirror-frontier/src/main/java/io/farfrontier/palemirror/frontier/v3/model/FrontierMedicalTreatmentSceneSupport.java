package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Admission and lease policy for one exact patient and their retained medical team. */
public final class FrontierMedicalTreatmentSceneSupport {
    private FrontierMedicalTreatmentSceneSupport() { }

    public static Optional<Candidate> candidate(FrontierWorldState state, MedicalEvacuationOperation operation) {
        if (hasScene(state, operation.id())) return Optional.empty();
        // An existing HOT ambient lease is not a reason to hide a medically ready operation.
        // The physical scene executor owns the explicit ambient -> scene hand-off before it
        // prepares the scene; suppressing this candidate would make that hand-off unreachable.
        return preparedCandidate(state, operation);
    }

    /** Validates the retained people/building/supply independently of ambient-to-scene transfer. */
    private static Optional<Candidate> preparedCandidate(FrontierWorldState state, MedicalEvacuationOperation operation) {
        if (operation.status() != MedicalEvacuationStatus.PREPARED) return Optional.empty();
        PhysicalIntent intent = state.physicalIntents().get(operation.consumptionIntentId());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure infirmary = FrontierWorldStateSupport.structure(settlement, operation.infirmaryId());
        java.util.List<SubjectId> members = operation.team().memberIds();
        if (intent == null || intent.status() != PhysicalIntentStatus.PREPARED || state.structureConditions().get(infirmary.id()) == StructureCondition.DESTROYED
                || state.actorLocations().get(operation.patientId()).condition().status() != ActorLifeStatus.ALIVE
                || members.stream().anyMatch(id -> state.actorLocations().get(id).condition().status() != ActorLifeStatus.ALIVE)) return Optional.empty();
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>(); positions.put(operation.patientId(), state.actorLocations().get(operation.patientId()).position());
        members.stream().sorted().forEach(id -> positions.put(id, state.actorLocations().get(id).position()));
        return Optional.of(new Candidate(operation.id(), infirmary.anchor(), Map.copyOf(positions)));
    }

    public static Optional<Candidate> nextCandidate(FrontierWorldState state) {
        return state.humanPopulation().medicalOperations().values().stream().sorted(java.util.Comparator.comparing(MedicalEvacuationOperation::id))
                .map(operation -> candidate(state, operation)).flatMap(Optional::stream).findFirst();
    }

    public static SubjectId owner(FrontierWorldState state, MedicalTreatmentSceneCause cause) { return require(state, cause).settlementId(); }

    public static MedicalEvacuationOperation require(FrontierWorldState state, MedicalTreatmentSceneCause cause) {
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(cause.operationId());
        if (operation == null) throw new IllegalArgumentException("medical scene has no active operation");
        return operation;
    }

    public static void validatePrepared(FrontierWorldState state, SceneLease lease) {
        MedicalEvacuationOperation operation = require(state, FrontierSceneBehaviors.medicalTreatment(lease));
        Candidate candidate = preparedCandidate(state, operation).orElseThrow(() -> new IllegalArgumentException("medical scene has no exact COLD-ready patient/team"));
        if (!lease.handoffPosition().equals(candidate.infirmaryAnchor()) || !lease.memberPositions().equals(candidate.memberPositions())) {
            throw new IllegalArgumentException("medical scene must retain current exact patient/team positions and infirmary");
        }
    }

    /** Exact supply consumption is allowed only during the operation's own HOT lease. */
    public static boolean permitsCurrentConsumptionIntent(FrontierWorldState state, PhysicalIntent intent) {
        MedicalEvacuationOperation operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        return operation != null && operation.requiresSupply() && operation.consumptionIntentId().equals(intent.id()) && state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isMedicalTreatment).filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .anyMatch(lease -> FrontierSceneBehaviors.medicalTreatment(lease).operationId().equals(operation.id()));
    }

    private static boolean hasScene(FrontierWorldState state, SubjectId operationId) {
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .filter(FrontierSceneBehaviors::isMedicalTreatment).anyMatch(lease -> FrontierSceneBehaviors.medicalTreatment(lease).operationId().equals(operationId));
    }

    public record Candidate(SubjectId operationId, BlockPosition infirmaryAnchor, Map<SubjectId, BlockPosition> memberPositions) { }
}
