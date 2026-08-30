package io.farfrontier.palemirror.frontier.v3.model;

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
final class CompanyFoundationProcess {
    private static final long REVIEW_INTERVAL = 24_000L;
    private CompanyFoundationProcess() { }

    static ScheduledAction review(SubjectId settlementId, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:company-foundation-" + suffix(settlementId) + "-" + ordinal),
                new SimInstant(dueAt), 0, settlementId, "frontier.company.foundation.review", 1);
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        List<ProposedEvent> events = new ArrayList<>();
        Company company = state.companies().companies().values().stream().filter(candidate -> candidate.settlementId().equals(settlement.id())
                && candidate.purpose() == CompanyPurpose.WORKS).sorted(java.util.Comparator.comparing(Company::id)).findFirst().orElse(null);
        if (company == null) {
            ResidentProfile founder = state.humanPopulation().residents().values().stream()
                    .filter(resident -> resident.settlementId().equals(settlement.id()) && resident.role() == ResidentRole.CRAFTER)
                    .sorted(java.util.Comparator.comparing(ResidentProfile::id)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("settlement has no canonical works founder"));
            company = new Company(companyId(settlement.id()), settlement.id(), founder.id(), CompanyPurpose.WORKS, CompanyStatus.ACTIVE, action.dueAt().ticks());
            events.add(new ProposedEvent(settlement.id(), new CompanyRegistered(company)));
        }
        if (company.status() == CompanyStatus.ACTIVE && state.companies().activeEmployment(company.id(), company.founderId()).isEmpty()) {
            events.add(new ProposedEvent(settlement.id(), new EmploymentContractOpened(employment(company, action.dueAt().ticks()))));
        }
        events.add(new ProposedEvent(settlement.id(), new ScheduleEffect.Created(review(settlement.id(), nextOrdinal(action),
                Math.addExact(action.dueAt().ticks(), REVIEW_INTERVAL)))));
        return List.copyOf(events);
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, CompanyRegistered registered) {
        Company company = registered.company();
        if (!subject.equals(company.settlementId())) throw new IllegalArgumentException("company registration subject must be its settlement");
        FrontierWorldStateSupport.settlement(state.bootstrap(), company.settlementId());
        if (!company.id().equals(companyId(company.settlementId())) || company.purpose() != CompanyPurpose.WORKS
                || company.status() != CompanyStatus.ACTIVE) {
            throw new IllegalArgumentException("company foundation must register the one active deterministic works company");
        }
        ResidentProfile founder = state.humanPopulation().resident(company.founderId());
        if (founder == null || !founder.settlementId().equals(company.settlementId()) || founder.role() != ResidentRole.CRAFTER) {
            throw new IllegalArgumentException("company founder must be a current settlement crafter");
        }
        return state.registerCompany(company);
    }

    static FrontierWorldState reduceEmployment(FrontierWorldState state, SubjectId subject, EmploymentContractOpened opened) {
        EmploymentContract contract = opened.contract(); Company company = state.companies().companies().get(contract.companyId());
        if (company == null || !subject.equals(company.settlementId()) || !contract.residentId().equals(company.founderId())) {
            throw new IllegalArgumentException("works employment must be opened by its founder's settlement");
        }
        EmploymentContract expected = employment(company, contract.openedAtTick());
        if (!expected.equals(contract)) throw new IllegalArgumentException("works employment must use canonical exact terms");
        return state.openEmployment(contract);
    }

    static SubjectId companyId(SubjectId settlementId) { return new SubjectId("company:" + suffix(settlementId) + "-works"); }

    static SubjectId employmentId(SubjectId settlementId) { return new SubjectId("contract:employment-" + suffix(settlementId) + "-works-founder"); }
    private static EmploymentContract employment(Company company, long tick) {
        return new EmploymentContract(employmentId(company.settlementId()), company.id(), company.founderId(), FixedScalar.whole(2L), FixedScalar.ONE,
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
