package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

/** Aggregate validation for exact local medical/evacuation ownership. */
public final class MedicalEvacuationStateSupport {
    /** The temporary graybox remedy is a normal exact Minecraft stack, never a hidden counter. */
    public static final String FIRST_TREATMENT_SUPPLY = "minecraft:honey_bottle";

    private MedicalEvacuationStateSupport() { }

    public static void validate(FrontierBootstrap bootstrap, HumanPopulation population, java.util.Map<SubjectId, ActorLocation> actors,
                         java.util.Map<SubjectId, StructureCondition> structures, ExactInventory inventory,
                         java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        for (MedicalEvacuationOperation operation : population.medicalOperations().values()) {
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, operation.settlementId());
            SettlementStructure infirmary = FrontierWorldStateSupport.structure(settlement, operation.infirmaryId());
            if (infirmary.kind() != StructureKind.INFIRMARY) throw new IllegalArgumentException("medical operation must use its local infirmary");
            ResidentProfile patient = population.resident(operation.patientId());
            if (patient == null || !patient.settlementId().equals(settlement.id())) throw new IllegalArgumentException("medical operation has a foreign patient");
            if (operation.active()) {
                ResidentHealthStatus health = population.health(operation.patientId()).status();
                boolean consumed = intents.get(operation.consumptionIntentId()) != null
                        && intents.get(operation.consumptionIntentId()).status() == PhysicalIntentStatus.CONFIRMED;
                if (operation.requiresSupply() && !consumed && health != ResidentHealthStatus.INFECTED) {
                    throw new IllegalArgumentException("active treatment needs an infected patient");
                }
                if (structures.get(infirmary.id()) == StructureCondition.DESTROYED) {
                    throw new IllegalArgumentException("active medical operation needs an intact infirmary");
                }
                ExactItemStack supply = inventory.items().get(operation.supplyItemId());
                if (operation.requiresSupply() && !consumed && (supply == null || !FIRST_TREATMENT_SUPPLY.equals(supply.itemKind()) || supply.count() < 1
                        || !supply.economicOwnerId().equals(settlement.id()) || !(supply.custody() instanceof InventoryCustody.ContainerSlot slot)
                        || !slot.containerId().equals(FrontierWorldState.depotId(settlement.id()))
                        || inventory.surfaces().get(slot.containerId()) == null
                        || inventory.surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE)) {
                    throw new IllegalArgumentException("active medical operation needs one exact active local treatment supply");
                }
                for (SubjectId member : operation.team().memberIds()) {
                    ResidentProfile medic = population.resident(member);
                    ActorLocation actor = actors.get(member);
                    if (medic == null || medic.profession() != ResidentProfession.MEDICAL_WORKER
                            || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) {
                        throw new IllegalArgumentException("active medical operation needs living local medical workers");
                    }
                }
            }
        }
    }
}
