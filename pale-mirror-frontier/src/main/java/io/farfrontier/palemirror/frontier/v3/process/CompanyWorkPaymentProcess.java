package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Optional;

/** Holds then settles a completed works job atomically: settlement invoice, company wage, then employment audit. */
public final class CompanyWorkPaymentProcess {
    private CompanyWorkPaymentProcess() { }

    public static Optional<EmploymentContract> contractFor(FrontierWorldState state, ProductionJob job) {
        return CompanyWorkPaymentStateSupport.contractFor(state, job);
    }

    public static boolean canReserve(FrontierWorldState state, ProductionJob job) {
        return contractFor(state, job).map(contract -> {
            try { state.inventory().economics().reserve(reservation(job, contract)); return true; }
            catch (IllegalArgumentException unavailable) { return false; }
        }).orElse(true);
    }

    /** A reservation happens in the same reduction that opens the durable production job. */
    public static FrontierWorldState reserve(FrontierWorldState state, ProductionJob job) {
        return CompanyWorkPaymentStateSupport.reserve(state, job);
    }

    public static FrontierWorldState settle(FrontierWorldState state, ProductionJob job) {
        return CompanyWorkPaymentStateSupport.settle(state, job);
    }

    /**
     * Settles only a job with a durable non-replayable Minecraft transform already in flight.
     * A death may have terminated new-work authority after the transform began; it must not
     * retroactively void the exact reservation or the worker's already-earned wage.
     */
    public static FrontierWorldState settleCommittedPhysicalWork(FrontierWorldState state, ProductionJob job) {
        return CompanyWorkPaymentStateSupport.settleCommittedPhysicalWork(state, job);
    }

    public static Optional<EmploymentContract> settlementContractFor(FrontierWorldState state, ProductionJob job) {
        return CompanyWorkPaymentStateSupport.settlementContractFor(state, job);
    }

    public static FinancialReservation reservation(ProductionJob job, EmploymentContract contract) {
        return CompanyWorkPaymentStateSupport.reservation(job, contract);
    }
}
