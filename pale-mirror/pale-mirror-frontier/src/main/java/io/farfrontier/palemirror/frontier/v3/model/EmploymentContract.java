package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/**
 * Optional company work authorization and service terms, never resident wages or money.
 */
public record EmploymentContract(SubjectId id, WorkEmployer employer, SubjectId residentId,
                                 FixedScalar invoicePerCompletedJob,
                                 EmploymentContractStatus status, long openedAtTick,
                                 long completedJobs) {
    public EmploymentContract {
        Objects.requireNonNull(id, "employment contract id"); Objects.requireNonNull(employer, "employment employer");
        Objects.requireNonNull(residentId, "employment resident"); Objects.requireNonNull(invoicePerCompletedJob, "work invoice");
        Objects.requireNonNull(status, "employment status");
        if (!id.value().startsWith("contract:employment-")) throw new IllegalArgumentException("employment contract id must use contract:employment- namespace");
        if (!residentId.value().startsWith("resident:")) throw new IllegalArgumentException("employment must bind a resident identity");
        if (employer.kind() != EconomicOwnerKind.COMPANY || invoicePerCompletedJob.raw() <= 0L)
            throw new IllegalArgumentException("company agreement needs positive service terms, never public wages");
        if (openedAtTick < 0L || completedJobs < 0L) throw new IllegalArgumentException("negative work audit");
    }

    /**
     * Records an invoice whose physical work was already irreversibly committed.  A terminated
     * agreement cannot authorize another job, but it retains this narrow historical settlement
     * authority so death between a Minecraft effect and its observed receipt cannot erase a
     * service invoice or strand its matching reservation.
     */
    EmploymentContract settleOneCommittedJob() {
        if (status != EmploymentContractStatus.ACTIVE && status != EmploymentContractStatus.TERMINATED) {
            throw new IllegalArgumentException("only current or terminated employment may settle committed work");
        }
        return new EmploymentContract(id, employer, residentId, invoicePerCompletedJob, status, openedAtTick,
                Math.addExact(completedJobs, 1L));
    }

    EmploymentContract terminate() {
        if (status != EmploymentContractStatus.ACTIVE) throw new IllegalArgumentException("only active employment may terminate");
        return new EmploymentContract(id, employer, residentId, invoicePerCompletedJob,
                EmploymentContractStatus.TERMINATED, openedAtTick, completedJobs);
    }
}
