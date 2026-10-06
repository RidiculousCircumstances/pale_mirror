package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Optional legal company registration; it neither appoints workers nor creates mandatory intermediaries. */
public final class CompanyFoundationProcess {
    private CompanyFoundationProcess() { }

    /** Optional registration producer supplies the actual recurrent trade review, not a mandatory bootstrap company. */
    public static java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planRegistration(
            FrontierWorldState state, Company company, long atTick) {
        if (atTick != company.registeredAtTick()) throw new IllegalArgumentException("company registration has a foreign opening instant");
        var registered = new CompanyRegistered(company);
        reduce(state, company.settlementId(), registered);
        return java.util.List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(company.settlementId(), registered),
                new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(company.id(),
                        new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                                GoodsParticipantProcess.review(company.id(), Math.addExact(atTick,
                                        state.bootstrap().ruleset().goodsTrade().reviewInterval())))));
    }

    public static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, CompanyRegistered registered) {
        Company company = registered.company();
        if (!subject.equals(company.settlementId())) throw new IllegalArgumentException("company registration subject must be its settlement");
        FrontierWorldStateSupport.settlement(state.bootstrap(), company.settlementId());
        if (!company.id().equals(companyId(company.settlementId())) || company.purpose() != CompanyPurpose.WORKS
                || company.status() != CompanyStatus.ACTIVE) {
            throw new IllegalArgumentException("company foundation must register the one active deterministic works company");
        }
        ResidentProfile founder = state.humanPopulation().resident(company.founderId());
        if (founder == null || !founder.settlementId().equals(company.settlementId())
                || state.actorLocations().get(founder.id()).condition().status() != ActorLifeStatus.ALIVE) {
            throw new IllegalArgumentException("company founder must be a living declared local resident, not a prescribed profession");
        }
        return state.registerCompany(company);
    }

    public static SubjectId companyId(SubjectId settlementId) { return new SubjectId("company:" + suffix(settlementId) + "-works"); }

    private static String suffix(SubjectId settlementId) {
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("company foundation requires settlement subject");
        return settlementId.value().substring("settlement:".length());
    }
}
