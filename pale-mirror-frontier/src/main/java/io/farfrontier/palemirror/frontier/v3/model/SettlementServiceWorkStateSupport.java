package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Aggregate validation for bounded resident-owned service work. */
final class SettlementServiceWorkStateSupport {
    private SettlementServiceWorkStateSupport() { }

    static void validate(FrontierBootstrap bootstrap, HumanPopulation population,
                         Map<SubjectId, ActorLocation> actorLocations,
                         Map<InfectionCell, io.farfrontier.palemirror.frontier.v3.api.FixedRatio> infection,
                         Map<SubjectId, SettlementServiceWork> works,
                         Map<PhysicalIntentId, PhysicalIntent> intents) {
        if (works.size() > SettlementServiceWork.MAX_RETAINED) {
            throw new IllegalArgumentException("settlement service-work retention limit exceeded");
        }
        Set<SubjectId> activeWorkers = new HashSet<>();
        Set<PhysicalIntentId> activeIntents = new HashSet<>();
        for (Map.Entry<SubjectId, SettlementServiceWork> entry : works.entrySet()) {
            SettlementServiceWork work = entry.getValue();
            if (!entry.getKey().equals(work.id())) throw new IllegalArgumentException("service-work map key must match work identity");
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, work.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, work.facilityId());
            ResidentProfile worker = population.resident(work.workerId());
            ActorLocation location = actorLocations.get(work.workerId());
            if (worker == null || location == null || !worker.settlementId().equals(settlement.id())
                    || location.condition().status() != ActorLifeStatus.ALIVE) {
                throw new IllegalArgumentException("service work must retain one living resident of its settlement");
            }
            if (work.kind() == SettlementServiceWorkKind.DECONTAMINATION
                    && (facility.kind() != StructureKind.INFIRMARY || worker.profession() != ResidentProfession.MEDICAL_WORKER
                    || !infection.containsKey(((SettlementServiceTarget.Infection) work.target()).cell()))) {
                throw new IllegalArgumentException("decontamination service work must retain an active infirmary, medic and infection cell");
            }
            if (work.kind() == SettlementServiceWorkKind.STRUCTURAL_REPAIR
                    && (worker.profession() != ResidentProfession.ENGINEER || !(work.target() instanceof SettlementServiceTarget.StructureCell cell)
                    || !cell.structureId().equals(work.facilityId())
                    || FrontierGrayboxPlan.intactStructureCell(bootstrap.terrain(), facility, cell.position()) == null)) {
                throw new IllegalArgumentException("structural service work must retain one engineer and exact settlement structure cell");
            }
            PhysicalIntent intent = intents.get(work.intentId());
            if (intent == null || !intent.causeSubjectId().equals(work.id()) || !intent.subjectIds().contains(work.inputItemId())
                    || work.kind() == SettlementServiceWorkKind.DECONTAMINATION && intent.kind() != PhysicalIntentKind.DECONTAMINATION
                    || work.kind() == SettlementServiceWorkKind.STRUCTURAL_REPAIR && intent.kind() != PhysicalIntentKind.STRUCTURAL_REPAIR) {
                throw new IllegalArgumentException("service work must retain its exact typed endpoint intent");
            }
            if (work.phase().active()) {
                if (!activeWorkers.add(work.workerId()) || !activeIntents.add(work.intentId())) {
                    throw new IllegalArgumentException("active service work cannot share a worker or physical intent");
                }
                if (intent.status() == PhysicalIntentStatus.CONFIRMED || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
                    throw new IllegalArgumentException("active service work cannot retain a terminal endpoint intent");
                }
            }
        }
    }
}
