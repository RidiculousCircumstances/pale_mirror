package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Settles a completed works job atomically: settlement invoice, company wage, then employment audit. */
final class CompanyWorkPaymentProcess {
    private CompanyWorkPaymentProcess() { }

    static Optional<EmploymentContract> contractFor(FrontierWorldState state, ProductionJob job) {
        Objects.requireNonNull(state, "world state"); Objects.requireNonNull(job, "production job");
        return state.companies().activeWorksCompany(job.settlementId())
                .flatMap(company -> state.companies().activeEmployment(company.id(), job.workerId()));
    }

    static boolean canSettle(FrontierWorldState state, ProductionJob job) {
        return contractFor(state, job).map(contract -> {
            try { settleEconomics(state.inventory().economics(), job.settlementId(), contract); return true; }
            catch (IllegalArgumentException unavailable) { return false; }
        }).orElse(true);
    }

    static FrontierWorldState settle(FrontierWorldState state, ProductionJob job) {
        Optional<EmploymentContract> optional = contractFor(state, job);
        if (optional.isEmpty()) return state;
        EmploymentContract contract = optional.orElseThrow();
        EconomicLedger economics = settleEconomics(state.inventory().economics(), job.settlementId(), contract);
        return state.withInventory(state.inventory().withEconomics(economics)).withCompanies(state.companies().settle(contract.id()));
    }

    private static EconomicLedger settleEconomics(EconomicLedger initial, SubjectId settlementId, EmploymentContract contract) {
        EconomicLedger invoiced = initial.transfer(settlementId, contract.companyId(), contract.invoicePerCompletedJob());
        return invoiced.transfer(contract.companyId(), contract.residentId(), contract.wagePerCompletedJob());
    }
}
