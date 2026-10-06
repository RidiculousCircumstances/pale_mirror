package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.ArrayList;
import java.util.List;

/** Optional company agreement admission; public work needs no employment contract. */
public final class SettlementEmploymentProcess {
    private SettlementEmploymentProcess() { }
    public static FrontierWorldState reduceEmployment(FrontierWorldState state, SubjectId subject, EmploymentContractOpened opened) {
        var contract = opened.contract(); var employer = contract.employer();
        employer.validate(state.inventory().economics(), state.companies().companies());
        var resident = state.humanPopulation().resident(contract.residentId());
        var actor = state.actorLocations().get(contract.residentId());
        if (!subject.equals(employer.settlementId()) || resident == null
                || !resident.settlementId().equals(employer.settlementId())
                || !SettlementWorkPolicy.permissions(state, employer.settlementId()).permits(ResidentWorkKind.BAKING, resident.id())
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !agreement(state, employer, resident.id(), contract.openedAtTick()).equals(contract))
            throw new IllegalArgumentException("production employment needs a living authorized worker and exact declared terms");
        return state.openEmployment(contract);
    }
    public static FrontierWorldState reduceEmploymentTermination(FrontierWorldState state, SubjectId subject,
                                                                 EmploymentContractTerminated terminated) {
        var contract = state.companies().employmentContracts().get(terminated.contractId());
        var actor = state.actorLocations().get(terminated.residentId());
        if (contract == null || !subject.equals(contract.employer().settlementId())
                || !contract.residentId().equals(terminated.residentId())
                || terminated.reason() != EmploymentTerminationReason.DEATH || actor == null
                || actor.condition().status() != ActorLifeStatus.DEAD)
            throw new IllegalArgumentException("employment termination lacks the exact resident's confirmed death");
        return state.withCompanies(state.companies().terminate(contract.id()));
    }
    public static SubjectId employmentId(WorkEmployer employer, SubjectId residentId) {
        return new SubjectId("contract:employment-" + employer.id().value().replace(':', '-') + "-" + residentId.value().replace(':', '-'));
    }
    public static EmploymentContract agreement(FrontierWorldState state, WorkEmployer employer, SubjectId residentId, long tick) {
        if (employer.kind() != EconomicOwnerKind.COMPANY)
            throw new IllegalArgumentException("public work uses settlement permissions, not employment");
        return new EmploymentContract(employmentId(employer, residentId), employer, residentId,
                state.bootstrap().ruleset().rates().worksJobPrice(), EmploymentContractStatus.ACTIVE, tick, 0L);
    }
}
