package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
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
            for (var event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement.id(), 1, 4_000L))) {
                if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlement.id(), registered);
                if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlement.id(), opened);
            }
        }

        assertEquals(state.bootstrap().settlements().size(), state.companies().companies().size());
        for (Settlement settlement : state.bootstrap().settlements()) {
            SubjectId companyId = CompanyFoundationProcess.companyId(settlement.id());
            Company company = state.companies().companies().get(companyId);
            assertEquals(settlement.id(), company.settlementId());
            assertEquals(CompanyStatus.ACTIVE, company.status());
            assertEquals(EconomicOwnerKind.COMPANY, state.inventory().economics().require(companyId).ownerKind());
            EmploymentContract employment = state.companies().employmentContracts().get(CompanyFoundationProcess.employmentId(settlement.id()));
            assertEquals(company.founderId(), employment.residentId());
            assertEquals(EconomicOwnerKind.RESIDENT, state.inventory().economics().require(employment.residentId()).ownerKind());
        }
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        CompanyRegistered encoded = new CompanyRegistered(state.companies().companies().values().iterator().next());
        assertEquals(encoded, FrontierWorldRuntimeDefinition.payloadCodecs().decode(encoded.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(encoded)));
        assertTrue(CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(state.bootstrap().settlements().getFirst().id(), 2, 28_000L))
                .stream().map(event -> event.payload()).anyMatch(ScheduleEffect.Created.class::isInstance));
        EmploymentContract contract = state.companies().employmentContracts().get(CompanyFoundationProcess.employmentId(state.bootstrap().settlements().getFirst().id()));
        assertEquals(new EmploymentContractOpened(contract), FrontierWorldRuntimeDefinition.payloadCodecs().decode("frontier.employment_contract_opened",
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new EmploymentContractOpened(contract))));
        EmploymentContractTerminated terminated = new EmploymentContractTerminated(contract.id(), contract.residentId(), EmploymentTerminationReason.DEATH);
        assertEquals(terminated, FrontierWorldRuntimeDefinition.payloadCodecs().decode(terminated.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(terminated)));
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
        EmploymentContract employment = new EmploymentContract(CompanyFoundationProcess.employmentId(settlement.id()), company.id(), founder.id(),
                FixedScalar.whole(2L), FixedScalar.ONE, EmploymentContractStatus.ACTIVE, 1L, 0L, FixedScalar.ZERO);
        FrontierWorldState employed = CompanyFoundationProcess.reduceEmployment(registered, settlement.id(), new EmploymentContractOpened(employment));
        assertThrows(IllegalArgumentException.class, () -> CompanyFoundationProcess.reduceEmployment(employed, settlement.id(), new EmploymentContractOpened(employment)));
    }

    @Test
    void completedCompanyWorkInvoicesThenPaysOneExactFounderOrBlocksWithoutFunds() {
        FrontierWorldState state = foundedState(); Settlement settlement = state.bootstrap().settlements().getFirst();
        Company company = state.companies().companies().get(CompanyFoundationProcess.companyId(settlement.id()));
        EmploymentContract contract = state.companies().employmentContracts().get(CompanyFoundationProcess.employmentId(settlement.id()));
        ProductionJob job = new ProductionJob(new SubjectId("job:production-company-payment"), settlement.id(),
                settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id(),
                company.founderId(), new SubjectId("item:company-payment-wheat"), new SubjectId("item:company-payment-bread"), "minecraft:bread", 64);

        assertTrue(CompanyWorkPaymentProcess.canReserve(state, job));
        FrontierWorldState reserved = CompanyWorkPaymentProcess.reserve(state, job);
        assertEquals(1, reserved.inventory().economics().reservations().size());
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY, reserved.inventory().economics().require(settlement.id()).balance());
        FrontierWorldState settled = CompanyWorkPaymentProcess.settle(reserved, job);
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY.minus(FixedScalar.whole(2L)), settled.inventory().economics().require(settlement.id()).balance());
        assertEquals(FixedScalar.ONE, settled.inventory().economics().require(company.id()).balance());
        assertEquals(FixedScalar.ONE, settled.inventory().economics().require(contract.residentId()).balance());
        assertEquals(1L, settled.companies().employmentContracts().get(contract.id()).completedJobs());

        java.util.Map<SubjectId, EconomicAccount> accounts = new java.util.LinkedHashMap<>(state.inventory().economics().accounts());
        accounts.put(settlement.id(), new EconomicAccount(settlement.id(), EconomicOwnerKind.SETTLEMENT_TREASURY, EconomicAccountStatus.ACTIVE,
                FixedScalar.ZERO, FixedScalar.ZERO));
        FrontierWorldState insolvent = state.withInventory(state.inventory().withEconomics(new EconomicLedger(accounts)));
        assertTrue(!CompanyWorkPaymentProcess.canReserve(insolvent, job));
        assertThrows(IllegalArgumentException.class, () -> CompanyWorkPaymentProcess.reserve(insolvent, job));
    }

    private static FrontierWorldState foundedState() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:company-payment"), 73L));
        for (Settlement settlement : state.bootstrap().settlements()) {
            for (var event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlement.id(), 1, 4_000L))) {
                if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlement.id(), registered);
                if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlement.id(), opened);
            }
        }
        return state;
    }
}
