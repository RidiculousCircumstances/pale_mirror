package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.ArrayList;
import java.util.List;

/** One deterministic initial works company per settlement, emitted by the canonical scheduler. */
public final class CompanyFoundationProcess {
    private CompanyFoundationProcess() { }

    public static ScheduledAction review(SubjectId settlementId, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:company-foundation-" + suffix(settlementId) + "-" + ordinal),
                new SimInstant(dueAt), 0, settlementId, "frontier.company.foundation.review", 1);
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        List<ProposedEvent> events = new ArrayList<>();
        Company company = state.companies().companies().values().stream().filter(candidate -> candidate.settlementId().equals(settlement.id())
                && candidate.purpose() == CompanyPurpose.WORKS).sorted(java.util.Comparator.comparing(Company::id)).findFirst().orElse(null);
        if (company == null) {
            ResidentProfile founder = state.humanPopulation().residents().values().stream()
                    .filter(resident -> resident.settlementId().equals(settlement.id()) && resident.profession() == ResidentProfession.INDUSTRIAL_WORKER)
                    .sorted(java.util.Comparator.comparing(ResidentProfile::id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("settlement has no canonical works founder"));
            company = new Company(companyId(settlement.id()), settlement.id(), founder.id(), CompanyPurpose.WORKS, CompanyStatus.ACTIVE, action.dueAt().ticks());
            events.add(new ProposedEvent(settlement.id(), new CompanyRegistered(company)));
        }
        if (company.status() == CompanyStatus.ACTIVE && !state.companies().employmentContracts().containsKey(employmentId(settlement.id()))
                && state.actorLocations().get(company.founderId()).condition().status() == ActorLifeStatus.ALIVE) {
            events.add(new ProposedEvent(settlement.id(), new EmploymentContractOpened(employment(state, company, action.dueAt().ticks()))));
        }
        events.add(new ProposedEvent(settlement.id(), new ScheduleEffect.Created(review(settlement.id(), nextOrdinal(action),
                Math.addExact(action.dueAt().ticks(), state.bootstrap().ruleset().cadence().companyFoundationReviewInterval())))));
        return List.copyOf(events);
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
        if (founder == null || !founder.settlementId().equals(company.settlementId()) || founder.profession() != ResidentProfession.INDUSTRIAL_WORKER) {
            throw new IllegalArgumentException("company founder must be a current settlement crafter");
        }
        return state.registerCompany(company);
    }

    public static FrontierWorldState reduceEmployment(FrontierWorldState state, SubjectId subject, EmploymentContractOpened opened) {
        EmploymentContract contract = opened.contract(); Company company = state.companies().companies().get(contract.companyId());
        if (company == null || !subject.equals(company.settlementId()) || !contract.residentId().equals(company.founderId())) {
            throw new IllegalArgumentException("works employment must be opened by its founder's settlement");
        }
        EmploymentContract expected = employment(state, company, contract.openedAtTick());
        if (!expected.equals(contract)) throw new IllegalArgumentException("works employment must use canonical exact terms");
        return state.openEmployment(contract);
    }

    public static java.util.Optional<ProposedEvent> terminationForDeath(FrontierWorldState state, SubjectId residentId) {
        return state.companies().employmentContracts().values().stream()
                .filter(contract -> contract.residentId().equals(residentId) && contract.status() == EmploymentContractStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalStateException("resident has ambiguous active employment"); })
                .map(contract -> new ProposedEvent(state.companies().companies().get(contract.companyId()).settlementId(),
                        new EmploymentContractTerminated(contract.id(), residentId, EmploymentTerminationReason.DEATH)));
    }

    public static FrontierWorldState reduceEmploymentTermination(FrontierWorldState state, SubjectId subject, EmploymentContractTerminated terminated) {
        EmploymentContract contract = state.companies().employmentContracts().get(terminated.contractId());
        Company company = contract == null ? null : state.companies().companies().get(contract.companyId());
        ActorLocation actor = state.actorLocations().get(terminated.residentId());
        if (contract == null || company == null || !subject.equals(company.settlementId()) || !contract.residentId().equals(terminated.residentId())
                || terminated.reason() != EmploymentTerminationReason.DEATH || actor == null || actor.condition().status() != ActorLifeStatus.DEAD) {
            throw new IllegalArgumentException("employment termination lacks the resident's confirmed death");
        }
        return state.withCompanies(state.companies().terminate(contract.id()));
    }

    public static SubjectId companyId(SubjectId settlementId) { return new SubjectId("company:" + suffix(settlementId) + "-works"); }

    public static SubjectId employmentId(SubjectId settlementId) { return new SubjectId("contract:employment-" + suffix(settlementId) + "-works-founder"); }
    private static EmploymentContract employment(FrontierWorldState state, Company company, long tick) {
        return new EmploymentContract(employmentId(company.settlementId()), company.id(), company.founderId(),
                state.bootstrap().ruleset().rates().worksJobPrice(), FixedScalar.ONE,
                EmploymentContractStatus.ACTIVE, tick, 0L, FixedScalar.ZERO);
    }

    private static int nextOrdinal(ScheduledAction action) {
        String prefix = "schedule:company-foundation-" + suffix(action.subject()) + "-";
        if (!action.id().value().startsWith(prefix)) throw new IllegalArgumentException("company foundation schedule identity is invalid");
        return Math.addExact(Integer.parseInt(action.id().value().substring(prefix.length())), 1);
    }

    private static String suffix(SubjectId settlementId) {
        if (!settlementId.value().startsWith("settlement:")) throw new IllegalArgumentException("company foundation requires settlement subject");
        return settlementId.value().substring("settlement:".length());
    }
}
