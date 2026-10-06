package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import java.util.Optional;

/** Admission and settlement of optional inter-owner service invoices, never wages. */
public final class ProductionCommercialProcess {
    private ProductionCommercialProcess() { }

    public static Optional<EmploymentContract> contractFor(FrontierWorldState state, ProductionJob job) {
        return ProductionCommercialStateSupport.contractFor(state, job);
    }

    public static boolean canReserve(FrontierWorldState state, ProductionJob job) {
        job.rights().validate(state, job);
        if (job.rights().mode() == ProductionRights.Mode.PUBLIC_PRODUCTION) return true;
        return contractFor(state, job).map(contract -> {
            if (!ProductionCommercialStateSupport.requiresInvoice(job)) return true;
            try { state.inventory().economics().reserve(reservation(job, contract)); return true; }
            catch (IllegalArgumentException unavailable) { return false; }
        }).orElse(false);
    }

    /** A reservation happens in the same reduction that opens the durable production job. */
    public static FrontierWorldState reserve(FrontierWorldState state, ProductionJob job) {
        return ProductionCommercialStateSupport.reserve(state, job);
    }

    public static FrontierWorldState settle(FrontierWorldState state, ProductionJob job) {
        return ProductionCommercialStateSupport.settle(state, job);
    }

    /**
     * Settles only a job with a durable non-replayable Minecraft transform already in flight.
     * A death may have terminated new-work authority after the transform began; it must not
     * retroactively void the exact commercial reservation.
     */
    public static FrontierWorldState settleCommittedPhysicalWork(FrontierWorldState state, ProductionJob job) {
        return ProductionCommercialStateSupport.settleCommittedPhysicalWork(state, job);
    }

    public static Optional<EmploymentContract> settlementContractFor(FrontierWorldState state, ProductionJob job) {
        return ProductionCommercialStateSupport.settlementContractFor(state, job);
    }

    public static FinancialReservation reservation(ProductionJob job, EmploymentContract contract) {
        return ProductionCommercialStateSupport.reservation(job, contract);
    }
}
