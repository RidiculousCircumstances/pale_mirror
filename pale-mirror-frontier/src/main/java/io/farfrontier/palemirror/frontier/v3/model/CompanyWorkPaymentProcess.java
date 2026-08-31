package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Holds then settles a completed works job atomically: settlement invoice, company wage, then employment audit. */
final class CompanyWorkPaymentProcess {
    private CompanyWorkPaymentProcess() { }

    static Optional<EmploymentContract> contractFor(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(job, "production job");
        return state.companies().activeWorksCompany(job.settlementId())
                .flatMap(company -> state.companies().activeEmployment(company.id(), job.workerId()));
    }

    static boolean canReserve(FrontierWorldState state, ProductionJob job) {
        return contractFor(state, job).map(contract -> {
            try { state.inventory().economics().reserve(reservation(job, contract)); return true; }
            catch (IllegalArgumentException unavailable) { return false; }
        }).orElse(true);
    }

    /** A reservation happens in the same reduction that opens the durable production job. */
    static FrontierWorldState reserve(FrontierWorldState state, ProductionJob job) {
        Optional<EmploymentContract> optional = contractFor(state, job);
        if (optional.isEmpty()) return state;
        EmploymentContract contract = optional.orElseThrow();
        EconomicLedger economics = state.inventory().economics().reserve(reservation(job, contract));
        return state.withInventory(state.inventory().withEconomics(economics));
    }

    static FrontierWorldState settle(FrontierWorldState state, ProductionJob job) {
        Optional<EmploymentContract> optional = contractFor(state, job);
        if (optional.isEmpty()) return state;
        return settle(state, job, optional.orElseThrow());
    }

    /**
     * Settles only a job with a durable non-replayable Minecraft transform already in flight.
     * A death may have terminated new-work authority after the transform began; it must not
     * retroactively void the exact reservation or the worker's already-earned wage.
     */
    static FrontierWorldState settleCommittedPhysicalWork(FrontierWorldState state, ProductionJob job) {
        EmploymentContract contract = settlementContractFor(state, job).orElseThrow(() ->
                new IllegalArgumentException("committed production has no exact historical employment contract"));
        return settle(state, job, contract);
    }

    static Optional<EmploymentContract> settlementContractFor(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(job, "production job");
        return state.companies().activeWorksCompany(job.settlementId()).flatMap(company -> state.companies().employmentContracts().values().stream()
                .filter(contract -> contract.companyId().equals(company.id()) && contract.residentId().equals(job.workerId())
                        && (contract.status() == EmploymentContractStatus.ACTIVE || contract.status() == EmploymentContractStatus.TERMINATED))
                .sorted(java.util.Comparator.comparing(EmploymentContract::id))
                .reduce((left, right) -> { throw new IllegalArgumentException("committed production has ambiguous historical employment"); }));
    }

    private static FrontierWorldState settle(FrontierWorldState state, ProductionJob job, EmploymentContract contract) {
        EconomicLedger economics = state.inventory().economics().settle(reservation(job, contract).id())
                .transfer(contract.companyId(), contract.residentId(), contract.wagePerCompletedJob());
        return state.withInventory(state.inventory().withEconomics(economics)).withCompanies(state.companies().settle(contract.id()));
    }

    static FinancialReservation reservation(ProductionJob job, EmploymentContract contract) {
        return new FinancialReservation(new SubjectId("reservation:" + job.id().value().substring("job:".length())), job.settlementId(),
                contract.companyId(), job.id(), contract.invoicePerCompletedJob());
    }
}
