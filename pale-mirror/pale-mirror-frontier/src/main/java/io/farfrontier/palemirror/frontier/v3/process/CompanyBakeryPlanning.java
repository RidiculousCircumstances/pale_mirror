package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Company's own stock proposal uses the existing bakery admission and completion protocol. */
final class CompanyBakeryPlanning implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:company-manufacturing"; }

    static ProductionRights rights(Company company) {
        return new ProductionRights(ProductionRights.Mode.COMPANY_OWN_ACCOUNT,
                new GoodsTradeParty(company.id(), EconomicOwnerKind.COMPANY), FrontierWorldState.depotId(company.settlementId()),
                WorkEmployer.company(company));
    }

    static Optional<Company> requestingCompany(FrontierWorldState state, SubjectId home) {
        return state.companies().companies().values().stream().filter(company -> company.settlementId().equals(home)
                && company.purpose() == CompanyPurpose.WORKS).sorted(Comparator.comparing(Company::id)).filter(company -> {
                    var batch = BakeryBatchSelection.admissibleStock(state, rights(company));
                    var employment = state.companies().employmentContracts().values().stream()
                            .filter(contract -> contract.employer().id().equals(company.id()) && contract.status() == EmploymentContractStatus.ACTIVE)
                            .sorted(Comparator.comparing(EmploymentContract::id)).findFirst();
                    return employment.isPresent() && CompanyManufacturingPolicy.requestsProduction(new CompanyManufacturingPolicy.View(
                            company.status() == CompanyStatus.ACTIVE, state.companies().goodsTrade().participants().participants().containsKey(company.id()),
                            batch.map(BakeryBatchSelection::quantity).orElse(0)));
                }).findFirst();
    }

    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        if (requestingCompany(state, settlement.id()).isEmpty()) return Assessment.empty();
        return Assessment.offer(settlement.id(), new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_COMPANY_PRODUCTION,
                Optional.empty(), FixedScalar.SCALE), Priority.BACKGROUND);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, StrategicTask task, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        SettlementStructure workshop = ProductionProcess.workshop(settlement);
        var company = requestingCompany(state, settlement.id());
        if (company.isEmpty() || state.structureConditions().get(workshop.id()) != StructureCondition.INTACT)
            return List.of(new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.BLOCKED)));
        var rights = rights(company.orElseThrow());
        var selectedBatch = BakeryBatchSelection.admissible(state, rights);
        if (selectedBatch.isEmpty()) return retry(state, task, action);
        var batch = selectedBatch.orElseThrow();
        if (!SettlementCommitmentComposition.ADMISSION.facilityAvailable(state, workshop.id())
                || ReferenceContainerCustody.blocksCanonicalUse(state, batch.sourceContainer(state))
                || batch.fungibleInput().filter(input -> !FungibleResourceCustodySupport.canReserve(state, input)).isPresent())
            return retry(state, task, action);
        var job = ResidentWorkComposition.SELECTION.eligible(state, settlement.id(), ResidentWorkKind.BAKING, HumanCapability.INDUSTRY,
                action.dueAt().ticks()).stream().filter(worker -> state.companies().activeEmployment(
                        WorkEmployer.company(company.orElseThrow()), worker.id()).isPresent())
                .map(worker -> batch.exactInput().isPresent()
                        ? BakeryJobAdmission.exact(state, task, settlement, workshop, batch.exactInput().orElseThrow(), worker, rights)
                        : BakeryJobAdmission.fungible(state, task, settlement, workshop, batch.fungibleInput().orElseThrow(), worker, rights))
                .filter(candidate -> ProductionCommercialProcess.canReserve(state, candidate)).findFirst();
        if (job.isEmpty()) return retry(state, task, action);
        ProductionJob selected = job.orElseThrow();
        return List.of(new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE)),
                new ProposedEvent(task.ownerId(), new ProductionStarted(selected, selected.consumedItemId(),
                        Optional.of(BakeryAdmissionNavigation.propose(state, selected)))),
                new ProposedEvent(selected.id(), new ScheduleEffect.Created(ProductionProcess.complete(selected, action.dueAt().ticks() + 100L))));
    }

    private static List<ProposedEvent> retry(FrontierWorldState state, StrategicTask task, ScheduledAction action) {
        return List.of(ProductionProcess.reschedule(action, ProductionProcess.start(task, Math.addExact(action.dueAt().ticks(),
                state.bootstrap().ruleset().cadence().resourceHarvestRetryInterval()))));
    }
}
