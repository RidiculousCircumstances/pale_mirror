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
                    || work.phase().active() && location.condition().status() != ActorLifeStatus.ALIVE) {
                throw new IllegalArgumentException("active service work must retain one living resident of its settlement");
            }
            if (work.kind() == SettlementServiceWorkKind.DECONTAMINATION
                    && (facility.kind() != StructureKind.INFIRMARY || worker.profession() != ResidentProfession.MEDICAL_WORKER
                    || !infection.containsKey(((SettlementServiceTarget.Infection) work.target()).cell())
                    || !InfectionTreatmentWorksite.candidates(bootstrap, ((SettlementServiceTarget.Infection) work.target()).cell())
                    .contains(work.workStation()))) {
                throw new IllegalArgumentException("decontamination service work must retain an active infirmary, medic and infection cell");
            }
            if (work.kind() == SettlementServiceWorkKind.STRUCTURAL_REPAIR
                    && (worker.profession() != ResidentProfession.ENGINEER || !(work.target() instanceof SettlementServiceTarget.StructureCell cell)
                    || !cell.structureId().equals(work.facilityId())
                    || FrontierGrayboxPlan.intactStructureCell(bootstrap.terrain(), facility, cell.position()) == null)) {
                throw new IllegalArgumentException("structural service work must retain one engineer and exact settlement structure cell");
            }
            validateDepotSource(bootstrap, work);
            PhysicalIntent inputIssue = intents.get(work.inputIssueIntentId());
            PhysicalIntent endpoint = intents.get(work.endpointIntentId());
            if (inputIssue == null || inputIssue.kind() != PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE
                    || !inputIssue.causeSubjectId().equals(work.id())
                    || !inputIssue.subjectIds().equals(java.util.List.of(work.id(), work.workerId(), work.inputItemId()))
                    || endpoint == null || !endpoint.causeSubjectId().equals(work.id())
                    || !endpoint.subjectIds().contains(work.inputItemId())
                    || work.kind() == SettlementServiceWorkKind.DECONTAMINATION && endpoint.kind() != PhysicalIntentKind.DECONTAMINATION
                    || work.kind() == SettlementServiceWorkKind.STRUCTURAL_REPAIR && endpoint.kind() != PhysicalIntentKind.STRUCTURAL_REPAIR) {
                throw new IllegalArgumentException("service work must retain exact input-issue and endpoint intents");
            }
            if (work.phase().active()) {
                if (!activeWorkers.add(work.workerId()) || !activeIntents.add(work.inputIssueIntentId()) || !activeIntents.add(work.endpointIntentId())) {
                    throw new IllegalArgumentException("active service work cannot share a worker or physical intent");
                }
                if (endpoint.status() == PhysicalIntentStatus.CONFIRMED
                        || work.phase() == SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART
                        && endpoint.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART && inputIssue.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART
                        || work.phase() != SettlementServiceWorkPhase.UNKNOWN_AFTER_RESTART
                        && (endpoint.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART || inputIssue.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                        || (work.phase() == SettlementServiceWorkPhase.PREPARED || work.phase() == SettlementServiceWorkPhase.APPROACH_INPUT
                        || work.phase() == SettlementServiceWorkPhase.INPUT_ISSUE_PENDING) && inputIssue.status() == PhysicalIntentStatus.CONFIRMED
                        || (work.phase() == SettlementServiceWorkPhase.APPROACH_WORK || work.phase() == SettlementServiceWorkPhase.WORKING
                        || work.phase() == SettlementServiceWorkPhase.EFFECT_READY) && inputIssue.status() != PhysicalIntentStatus.CONFIRMED) {
                    throw new IllegalArgumentException("service work has invalid input/endpoint intent phase");
                }
            }
        }
    }

    private static void validateDepotSource(FrontierBootstrap bootstrap, SettlementServiceWork work) {
        Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, work.settlementId());
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("service work settlement has no depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        if (!work.inputSource().containerId().equals(FrontierWorldState.depotId(work.settlementId()))
                || !port.stations().contains(work.inputStation())) {
            throw new IllegalArgumentException("service work source must be the settlement depot's declared service port");
        }
    }
}
