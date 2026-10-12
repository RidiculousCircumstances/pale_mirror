package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Medical owner contributes its exact retained source to the common before-effect fence. */
public final class MedicalPhysicalAuthority {
    private MedicalPhysicalAuthority() { }

    public static boolean pendingForContainer(FrontierWorldState state, SubjectId container) {
        return state.humanPopulation().medicalOperations().values().stream()
                .filter(operation -> operation.supplyContainerId().equals(container))
                .anyMatch(operation -> unsettled(state, operation));
    }

    private static boolean unsettled(FrontierWorldState state, MedicalEvacuationOperation operation) {
        PhysicalIntent intent = state.physicalIntents().get(operation.consumptionIntentId());
        if (intent == null) return false;
        requireIntent(operation, intent);
        if (intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
            return true; // A terminal clinical/body owner cannot revoke an already-started stock effect.
        return intent.status() == PhysicalIntentStatus.PREPARED && state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isMedicalTreatment)
                .filter(lease -> FrontierSceneBehaviors.medicalTreatment(lease).operationId().equals(operation.id()))
                .anyMatch(lease -> (lease.status() == SceneLeaseStatus.CONFLICT
                        || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART)
                        && !FrontierSceneLeaseStateSupport.canAbortPrepared(state, lease));
    }

    public static void requireIntent(MedicalEvacuationOperation operation, PhysicalIntent intent) {
        if (intent.lifecycleOwner() != PhysicalIntentLifecycleOwner.MEDICAL_TREATMENT
                || !intent.id().equals(operation.consumptionIntentId()) || !intent.causeSubjectId().equals(operation.id())
                || !intent.roles().equals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding
                        .medicalTreatmentConsumption(operation.id(), operation.supplyItemId())))
            throw new IllegalArgumentException("medical stock fence has a mismatched declared intent");
    }

    public static boolean mayBegin(FrontierWorldState state, PhysicalIntent intent) {
        var operation = state.humanPopulation().medicalOperations().get(intent.causeSubjectId());
        if (operation == null) return false;
        requireIntent(operation, intent);
        return intent.status() == PhysicalIntentStatus.PREPARED && operation.status() == MedicalEvacuationStatus.PREPARED
                && MedicalEvacuationStateSupport.supplyAvailable(state, operation.supplyContainerId())
                && !ContainerPhysicalAuthorityComposition.pending(state, operation.supplyContainerId());
    }
}
