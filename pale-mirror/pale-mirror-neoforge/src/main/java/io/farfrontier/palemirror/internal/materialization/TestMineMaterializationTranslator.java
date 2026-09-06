package io.farfrontier.palemirror.internal.materialization;

import java.util.List;
import java.util.ArrayList;

import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.world.EncounterRecord;
import io.farfrontier.palemirror.internal.world.GatePresentationRecord;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;

/** Deterministically translates a mine's desired domain state into executor operations. */
public final class TestMineMaterializationTranslator {
    public static final String POLICY_ID = "pale_mirror:pm_anchor";
    public static final String POLICY_VERSION = "10";

    public MaterializationPlan translate(FacilityState facility) {
        return translate(facility, null, EncounterRecord.none(), GatePresentationRecord.none());
    }

    public MaterializationPlan translate(FacilityState facility, EncounterProfile profile, EncounterRecord encounter) {
        return translate(facility, profile, encounter, GatePresentationRecord.none());
    }

    public MaterializationPlan translate(FacilityState facility, EncounterProfile profile, EncounterRecord encounter,
                                         GatePresentationRecord gate) {
        return translate(facility, profile, encounter, gate, true);
    }

    public MaterializationPlan translate(FacilityState facility, EncounterProfile profile, EncounterRecord encounter,
                                         GatePresentationRecord gate, boolean encounterEnabled) {
        WorldObjectId id = facility.id();
        long revision = facility.desiredRevision();
        if (facility.status() == FacilityStatus.INFECTED) {
            List<MaterializationOperation> operations = new ArrayList<>();
            operations.add(operation(id, revision, operations.size(), MaterializationOperationType.ENSURE_PM_ANCHOR, ""));
            // Once the scheduler has prepared the encounter, its SavedData
            // record is the pinned materialization intent.  In particular a
            // datapack composition must not be read again while a job is
            // executing, otherwise reload could reshuffle physical actors.
            // The profile fallback exists only for the legacy direct-plan
            // call site before an encounter record has been prepared.
            List<String> actorSlots = !encounterEnabled || profile == null ? List.of()
                    : !encounter.actors().isEmpty()
                    ? encounter.actors().stream().map(actor -> actor.slotId()).toList()
                    : profile.actors().stream().map(actor -> actor.id()).toList();
            actorSlots.forEach(slotId -> operations.add(operation(id, revision, operations.size(),
                    MaterializationOperationType.ENSURE_SOURCE_ENCOUNTER_ACTOR, slotId)));
            if (!encounterEnabled) encounter.actors().stream()
                    .filter(actor -> actor.status() == io.farfrontier.palemirror.internal.world.EncounterActorRef.Status.ACTIVE)
                    .forEach(actor -> operations.add(operation(id, revision, operations.size(),
                            MaterializationOperationType.REMOVE_SOURCE_ENCOUNTER_ACTOR, actor.slotId())));
            gate.parts().stream().filter(part -> encounterEnabled || part.status() == SourceGatePartRef.Status.ACTIVE)
                    .filter(part -> !encounterEnabled || part.status() != SourceGatePartRef.Status.DEFEATED)
                    .filter(part -> part.status() != SourceGatePartRef.Status.REMOVED)
                    .forEach(part -> operations.add(operation(id, revision, operations.size(),
                            encounterEnabled ? MaterializationOperationType.ENSURE_SOURCE_GATE_PART
                                    : MaterializationOperationType.REMOVE_SOURCE_GATE_PART, part.slotId())));
            // The visible encounter must not wait for every semantic overlay chunk to be visited.
            // Sparse block presentation reconciles last and remains crash-safe/incremental.
            operations.add(operation(id, revision, operations.size(), MaterializationOperationType.ENSURE_OVERLAY,
                    facility.threatTier().name()));
            return new MaterializationPlan(POLICY_ID, POLICY_VERSION, revision, operations);
        }
        List<MaterializationOperation> operations = new ArrayList<>();
        encounter.actors().forEach(actor -> operations.add(operation(id, revision, operations.size(),
                MaterializationOperationType.REMOVE_SOURCE_ENCOUNTER_ACTOR, actor.slotId())));
        gate.parts().forEach(part -> operations.add(operation(id, revision, operations.size(),
                MaterializationOperationType.REMOVE_SOURCE_GATE_PART, part.slotId())));
        operations.add(operation(id, revision, operations.size(), MaterializationOperationType.REMOVE_PM_ANCHOR, ""));
        operations.add(operation(id, revision, operations.size(), MaterializationOperationType.REMOVE_OVERLAY, ""));
        return new MaterializationPlan(POLICY_ID, POLICY_VERSION, revision, operations);
    }

    private static MaterializationOperation operation(WorldObjectId id, long revision, int index,
                                                       MaterializationOperationType type, String target) {
        String key = id.value() + ":" + revision + ":" + type.name().toLowerCase();
        String targetSuffix = target.isBlank() ? "" : ":" + target;
        return new MaterializationOperation("op-" + index, key + targetSuffix, type, target, OperationState.PENDING, 0, "");
    }
}
