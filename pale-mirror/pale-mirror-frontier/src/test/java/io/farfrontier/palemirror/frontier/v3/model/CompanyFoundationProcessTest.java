package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public work is moneyless; optional inter-owner invoices still conserve money. */
class CompanyFoundationProcessTest {
    @Test void publicWorkNeedsNeitherAgreementNorMoney() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        assertTrue(state.companies().employmentContracts().isEmpty());
        assertTrue(state.companies().companies().isEmpty());
        assertTrue(state.inventory().economics().accounts().values().stream().noneMatch(account -> account.ownerKind() == EconomicOwnerKind.RESIDENT));
        var accounts = new java.util.LinkedHashMap<>(state.inventory().economics().accounts());
        accounts.put(home.id(), new EconomicAccount(home.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO));
        state = state.withInventory(state.inventory().withEconomics(new EconomicLedger(accounts)));
        var worker = SettlementWorkPolicy.permissions(state, home.id()).workers(ResidentWorkKind.BAKING).stream().sorted().findFirst().orElseThrow();
        var job = job(state, home.id(), worker);
        assertEquals(ProductionRights.Mode.PUBLIC_PRODUCTION, job.rights().mode());
        assertTrue(ProductionCommercialProcess.canReserve(state, job));
        assertSame(state, ProductionCommercialProcess.reserve(state, job));
        assertSame(state, ProductionCommercialProcess.settle(state, job));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        accounts.put(home.id(), new EconomicAccount(home.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.INSOLVENT, FixedScalar.ZERO, FixedScalar.ZERO));
        var insolvent = state.withInventory(state.inventory().withEconomics(new EconomicLedger(accounts)));
        assertTrue(ProductionCommercialProcess.canReserve(insolvent, job));
        assertSame(insolvent, ProductionCommercialProcess.reserve(insolvent, job));
        assertTrue(ProductionCommercialProcess.contractFor(insolvent, job).isEmpty());
    }
    @Test void optionalCompanyServicePaysOnlyItsDeclaredCompany() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var worker = SettlementWorkPolicy.permissions(state, home.id()).workers(ResidentWorkKind.BAKING).stream().sorted().findFirst().orElseThrow();
        var company = new Company(CompanyFoundationProcess.companyId(home.id()), home.id(), worker, CompanyPurpose.WORKS, CompanyStatus.ACTIVE, 1);
        state = CompanyFoundationProcess.reduce(state, home.id(), new CompanyRegistered(company));
        var contract = SettlementEmploymentProcess.agreement(state, WorkEmployer.company(company), worker, 1);
        state = SettlementEmploymentProcess.reduceEmployment(state, home.id(), new EmploymentContractOpened(contract));
        var payload = new EmploymentContractOpened(contract);
        assertEquals(payload, FrontierWorldRuntimeDefinition.payloadCodecs().decode(payload.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(payload)));
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> SettlementEmploymentProcess.reduceEmployment(before, home.id(), payload));
        var job = job(state, home.id(), worker).withRights(new ProductionRights(ProductionRights.Mode.BUYER_OWNED_SERVICE,
                GoodsParticipantDeclarations.publicParty(home.id()), FrontierWorldState.depotId(home.id()), WorkEmployer.company(company)));
        var settled = ProductionCommercialProcess.settle(ProductionCommercialProcess.reserve(state, job), job);
        assertEquals(state.inventory().economics().require(home.id()).balance().minus(contract.invoicePerCompletedJob()),
                settled.inventory().economics().require(home.id()).balance());
        assertEquals(contract.invoicePerCompletedJob(), settled.inventory().economics().require(company.id()).balance());
        assertFalse(settled.inventory().economics().accounts().containsKey(worker));
        assertEquals(1L, settled.companies().employmentContracts().get(contract.id()).completedJobs());
        assertThrows(IllegalArgumentException.class, () -> ProductionCommercialProcess.settle(settled, job));
        assertEquals(settled, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(settled)));
        var accounts = new java.util.LinkedHashMap<>(state.inventory().economics().accounts());
        accounts.put(home.id(), new EconomicAccount(home.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO));
        var insolvent = state.withInventory(state.inventory().withEconomics(new EconomicLedger(accounts)));
        assertFalse(ProductionCommercialProcess.canReserve(insolvent, job));
    }
    @Test void residentMoneyAndForgedPublicFinanceFailClosed() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var worker = home.residents().getFirst().id();
        assertThrows(IllegalArgumentException.class, () -> new EconomicAccount(worker, EconomicOwnerKind.RESIDENT,
                EconomicAccountStatus.ACTIVE, FixedScalar.ONE, FixedScalar.ZERO));
        assertThrows(IllegalArgumentException.class, () -> SettlementEmploymentProcess.agreement(state, WorkEmployer.settlement(home.id()), worker, 0));
        var forged = new WorkEmployer(home.id(), EconomicOwnerKind.COMPANY, home.id());
        assertThrows(IllegalArgumentException.class, () -> forged.validate(state.inventory().economics(), state.companies().companies()));
    }
    private static ProductionJob job(FrontierWorldState state, SubjectId homeId, SubjectId worker) {
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), homeId);
        return new ProductionJob(new SubjectId("job:production-community"), new SubjectId("task:production-community"),
                home.id(), home.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id(),
                worker, new SubjectId("item:community-wheat"), new SubjectId("item:community-bread"), "minecraft:bread", 64);
    }
    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:community-foundation"), 71));
    }
}
