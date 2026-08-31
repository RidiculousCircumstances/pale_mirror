package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/**
 * One exact legal work agreement. It owns neither an inventory nor money: the matching
 * resident and company accounts retain those truths, while this record retains the earned-work audit.
 */
public record EmploymentContract(SubjectId id, SubjectId companyId, SubjectId residentId,
                                 FixedScalar invoicePerCompletedJob, FixedScalar wagePerCompletedJob,
                                 EmploymentContractStatus status, long openedAtTick,
                                 long completedJobs, FixedScalar totalWagesPaid) {
    public EmploymentContract {
        Objects.requireNonNull(id, "employment contract id"); Objects.requireNonNull(companyId, "employment company");
        Objects.requireNonNull(residentId, "employment resident"); Objects.requireNonNull(invoicePerCompletedJob, "work invoice");
        Objects.requireNonNull(wagePerCompletedJob, "work wage"); Objects.requireNonNull(status, "employment status");
        Objects.requireNonNull(totalWagesPaid, "total wages paid");
        if (!id.value().startsWith("contract:employment-")) throw new IllegalArgumentException("employment contract id must use contract:employment- namespace");
        if (!companyId.value().startsWith("company:") || !residentId.value().startsWith("resident:")) throw new IllegalArgumentException("employment must bind company and resident identities");
        if (invoicePerCompletedJob.raw() <= 0L || wagePerCompletedJob.raw() <= 0L || wagePerCompletedJob.compareTo(invoicePerCompletedJob) > 0) {
            throw new IllegalArgumentException("employment invoice and wage must be positive with wage no greater than invoice");
        }
        if (openedAtTick < 0L || completedJobs < 0L || totalWagesPaid.raw() < 0L) throw new IllegalArgumentException("employment counters must not be negative");
        if (!totalWagesPaid.equals(wagePerCompletedJob.multiply(completedJobs))) throw new IllegalArgumentException("employment wage total must equal settled completed work");
    }

    /**
     * Records an invoice whose physical work was already irreversibly committed.  A terminated
     * agreement cannot authorize another job, but it retains this narrow historical settlement
     * authority so death between a Minecraft effect and its observed receipt cannot erase a
     * wage or strand its matching reservation.
     */
    EmploymentContract settleOneCommittedJob() {
        if (status != EmploymentContractStatus.ACTIVE && status != EmploymentContractStatus.TERMINATED) {
            throw new IllegalArgumentException("only current or terminated employment may settle committed work");
        }
        return new EmploymentContract(id, companyId, residentId, invoicePerCompletedJob, wagePerCompletedJob, status, openedAtTick,
                Math.addExact(completedJobs, 1L), totalWagesPaid.plus(wagePerCompletedJob));
    }

    EmploymentContract terminate() {
        if (status != EmploymentContractStatus.ACTIVE) throw new IllegalArgumentException("only active employment may terminate");
        return new EmploymentContract(id, companyId, residentId, invoicePerCompletedJob, wagePerCompletedJob,
                EmploymentContractStatus.TERMINATED, openedAtTick, completedJobs, totalWagesPaid);
    }
}
