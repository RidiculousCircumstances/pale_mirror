package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Optional company invoice settlement. Public production never holds or transfers money. */
public final class ProductionCommercialStateSupport {
    private ProductionCommercialStateSupport() { }

    public static boolean requiresInvoice(ProductionJob job) {
        return job.rights().mode() == ProductionRights.Mode.BUYER_OWNED_SERVICE;
    }

    public static Optional<EmploymentContract> contractFor(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(job, "production job");
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) {
            job.rights().validate(state, job); return Optional.empty();
        }
        job.rights().employer().validate(state.inventory().economics(), state.companies().companies());
        return state.companies().activeEmployment(job.rights().employer(), job.workerId());
    }

    public static Optional<EmploymentContract> settlementContractFor(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(job, "production job");
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) return Optional.empty();
        return state.companies().employmentContracts().values().stream()
                .filter(contract -> contract.employer().equals(job.rights().employer()) && contract.residentId().equals(job.workerId())
                        && (contract.status() == EmploymentContractStatus.ACTIVE || contract.status() == EmploymentContractStatus.TERMINATED))
                .sorted(java.util.Comparator.comparing(EmploymentContract::id))
                .reduce((left, right) -> { throw new IllegalArgumentException("committed production has ambiguous historical employment"); });
    }

    public static FinancialReservation reservation(ProductionJob job, EmploymentContract contract) {
        if (!contract.employer().equals(job.rights().employer()) || !contract.residentId().equals(job.workerId()))
            throw new IllegalArgumentException("production payment has a foreign employer or worker agreement");
        if (!requiresInvoice(job))
            throw new IllegalArgumentException("non-commercial production cannot create a payment reservation");
        return new FinancialReservation(new SubjectId("reservation:" + job.id().value().substring("job:".length())), job.settlementId(),
                contract.employer().id(), job.id(), contract.invoicePerCompletedJob());
    }

    public static FrontierWorldState reserve(FrontierWorldState state, ProductionJob job) {
        job.rights().validate(state, job);
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) return state;
        Optional<EmploymentContract> optional = contractFor(state, job);
        if (optional.isEmpty()) throw new IllegalArgumentException("company production requires its declared agreement before admission");
        if (!requiresInvoice(job)) return state;
        EconomicLedger economics = state.inventory().economics().reserve(reservation(job, optional.orElseThrow()));
        return state.withInventory(state.inventory().withEconomics(economics));
    }

    public static FrontierWorldState settleCommittedPhysicalWork(FrontierWorldState state, ProductionJob job) {
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) return state;
        return settle(state, job, settlementContractFor(state, job).orElseThrow(() ->
                new IllegalArgumentException("committed production has no exact historical employment contract")));
    }

    public static FrontierWorldState settle(FrontierWorldState state, ProductionJob job) {
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) return state;
        return settle(state, job, contractFor(state, job).orElseThrow(() ->
                new IllegalArgumentException("completed production lost its exact declared employer agreement")));
    }

    private static FrontierWorldState settle(FrontierWorldState state, ProductionJob job, EmploymentContract contract) {
        EconomicLedger economics = requiresInvoice(job)
                ? state.inventory().economics().settle(reservation(job, contract).id()) : state.inventory().economics();
        return state.withInventory(state.inventory().withEconomics(economics)).withCompanies(state.companies().settle(contract.id()));
    }
}
