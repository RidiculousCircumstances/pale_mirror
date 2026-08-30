package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyFoundationProcessTest {
    @Test
    void canonicalFoundationCreatesOneCompanyAndOneAccountPerSettlement() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:company-foundation"), 71L));
        for (Settlement settlement : state.bootstrap().settlements()) {
            CompanyRegistered registered = CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement.id(), 1, 4_000L))
                    .stream().map(event -> event.payload()).filter(CompanyRegistered.class::isInstance).map(CompanyRegistered.class::cast).findFirst().orElseThrow();
            state = CompanyFoundationProcess.reduce(state, settlement.id(), registered);
        }

        assertEquals(state.bootstrap().settlements().size(), state.companies().companies().size());
        for (Settlement settlement : state.bootstrap().settlements()) {
            SubjectId companyId = CompanyFoundationProcess.companyId(settlement.id());
            Company company = state.companies().companies().get(companyId);
            assertEquals(settlement.id(), company.settlementId());
            assertEquals(CompanyStatus.ACTIVE, company.status());
            assertEquals(EconomicOwnerKind.COMPANY, state.inventory().economics().require(companyId).ownerKind());
        }
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        CompanyRegistered encoded = new CompanyRegistered(state.companies().companies().values().iterator().next());
        assertEquals(encoded, FrontierWorldRuntimeDefinition.payloadCodecs().decode(encoded.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(encoded)));
        assertTrue(CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(state.bootstrap().settlements().getFirst().id(), 2, 28_000L))
                .stream().map(event -> event.payload()).anyMatch(ScheduleEffect.Created.class::isInstance));
    }

    @Test
    void duplicateOrUnpairedCompanyCannotEnterCanonicalState() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:company-negative"), 72L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        ResidentProfile founder = initial.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id()) && resident.role() == ResidentRole.CRAFTER).findFirst().orElseThrow();
        Company company = new Company(CompanyFoundationProcess.companyId(settlement.id()), settlement.id(), founder.id(), CompanyPurpose.WORKS, CompanyStatus.ACTIVE, 1L);

        assertThrows(IllegalArgumentException.class, () -> initial.withCompanies(new CompanyRegistry(java.util.Map.of(company.id(), company))));
        assertThrows(IllegalArgumentException.class, () -> CompanyFoundationProcess.reduce(initial, settlement.id(),
                new CompanyRegistered(new Company(company.id(), settlement.id(), founder.id(), CompanyPurpose.WORKS, CompanyStatus.DISSOLVED, 1L))));
        FrontierWorldState registered = CompanyFoundationProcess.reduce(initial, settlement.id(), new CompanyRegistered(company));
        assertTrue(registered.inventory().economics().accounts().containsKey(company.id()));
        assertThrows(IllegalArgumentException.class, () -> CompanyFoundationProcess.reduce(registered, settlement.id(), new CompanyRegistered(company)));
    }
}
