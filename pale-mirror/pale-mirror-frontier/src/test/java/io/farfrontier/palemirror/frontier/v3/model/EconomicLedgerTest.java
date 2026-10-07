package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicLedgerTest {
    @Test void expeditionBudgetAndPurchaseHoldsNeverDoubleCountOrMintMoney() {
        SubjectId sender = new SubjectId("settlement:sender"), host = new SubjectId("settlement:host");
        var initial = new EconomicLedger(Map.of(sender, new EconomicAccount(sender, EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.whole(100), FixedScalar.ZERO),
                host, new EconomicAccount(host, EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO)));
        var budget = new FinancialBudget(new SubjectId("budget:expedition"), sender,
                FinancialBudget.OwnerKind.TRANSPORT_MISSION, new SubjectId("mission:expedition"), FixedScalar.whole(20));
        var funded = initial.reserveBudget(budget);
        assertEquals(FixedScalar.whole(80), funded.availableToReserve(sender));
        var purchase = new FinancialReservation(new SubjectId("reservation:food"), sender, host,
                new SubjectId("contract:food"), FixedScalar.whole(7), java.util.Optional.of(budget.id()));
        var ordered = funded.reserveFromBudget(budget.id(), purchase);
        assertEquals(FixedScalar.whole(80), ordered.availableToReserve(sender));
        assertEquals(FixedScalar.whole(13), ordered.budgets().get(budget.id()).remaining());
        assertThrows(IllegalArgumentException.class, () -> ordered.reserveFromBudget(budget.id(), purchase));
        assertThrows(IllegalArgumentException.class, () -> ordered.closeBudget(budget.id()));
        assertThrows(IllegalArgumentException.class, () -> ordered.reserve(new FinancialReservation(
                new SubjectId("reservation:double-spend"), sender, host, new SubjectId("contract:other"), FixedScalar.whole(81))));
        var partial = ordered.settlePortion(purchase.id(), FixedScalar.whole(3));
        assertEquals(FixedScalar.whole(97), partial.require(sender).balance());
        assertEquals(FixedScalar.whole(3), partial.require(host).balance());
        assertEquals(purchase.budgetId(), partial.reservations().get(purchase.id()).budgetId());
        var cancelledRemainder = partial.release(purchase.id());
        assertEquals(FixedScalar.whole(17), cancelledRemainder.budgets().get(budget.id()).remaining());
        assertEquals(FixedScalar.whole(80), cancelledRemainder.availableToReserve(sender));
        var completed = cancelledRemainder.closeBudget(budget.id());
        assertEquals(FixedScalar.whole(97), completed.availableToReserve(sender));
        assertEquals(FixedScalar.whole(100), completed.require(sender).balance().plus(completed.require(host).balance()));
        assertTrue(completed.releaseBudget(budget.id()).budgets().isEmpty());
    }

    @Test void restoredHoldsCannotOvercommitTreasuryOrLoseTheirBudget() {
        SubjectId sender = new SubjectId("settlement:sender"), host = new SubjectId("settlement:host");
        var accounts = Map.of(sender, new EconomicAccount(sender, EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.whole(10), FixedScalar.ZERO),
                host, new EconomicAccount(host, EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO));
        var budget = new FinancialBudget(new SubjectId("budget:expedition"), sender,
                FinancialBudget.OwnerKind.TRANSPORT_MISSION, new SubjectId("mission:expedition"), FixedScalar.whole(8));
        var purchase = new FinancialReservation(new SubjectId("reservation:food"), sender, host,
                new SubjectId("contract:food"), FixedScalar.whole(3), java.util.Optional.of(budget.id()));
        assertThrows(IllegalArgumentException.class, () -> new EconomicLedger(accounts, Map.of(purchase.id(), purchase)));
        assertThrows(IllegalArgumentException.class, () -> new EconomicLedger(accounts, Map.of(purchase.id(), purchase), Map.of(budget.id(), budget)));
    }

    @Test
    void bootstrapRegistersEveryCurrentExactClaimHolderAsAnAccount() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-owners"), 91L));

        assertEquals(14, state.inventory().economics().accounts().size());
        assertEquals(EconomicOwnerKind.SETTLEMENT_TREASURY, state.inventory().economics().require(new SubjectId("settlement:1")).ownerKind());
        assertEquals(EconomicOwnerKind.HIVE_COLLECTIVE, state.inventory().economics().require(state.bootstrap().hive().id()).ownerKind());
        assertEquals(EconomicOwnerKind.PUBLIC_INFRASTRUCTURE, state.inventory().economics().require(FrontierRouteNetwork.OWNER).ownerKind());
        assertTrue(state.inventory().containers().values().stream().allMatch(container -> state.inventory().economics().accounts().containsKey(container.ownerId())));
        assertTrue(state.inventory().items().values().stream().allMatch(item -> state.inventory().economics().accounts().containsKey(item.economicOwnerId())));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void missingAccountCannotOwnAnExactContainerItemOrCargo() {
        SubjectId owner = new SubjectId("settlement:one"); SubjectId container = new SubjectId("container:one");
        Map<SubjectId, ContainerRecord> containers = Map.of(container, new ContainerRecord(container, owner, 1));
        Map<SubjectId, ContainerSurface> surfaces = Map.of(container, new ContainerSurface(container, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED));

        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(containers, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), surfaces,
                new EconomicLedger(Map.of())));
    }

    @Test
    void finiteGenesisReserveIsTheOnlyInitialMoneyAndOrdinaryLedgerMovesAreZeroSum() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-money"), 91L));
        FixedScalar issued = state.bootstrap().settlements().stream().map(settlement -> state.inventory().economics().require(settlement.id()).balance())
                .reduce(FixedScalar.ZERO, FixedScalar::plus);
        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury().multiply(state.bootstrap().settlements().size()), issued);
        SubjectId payer = new SubjectId("settlement:1"), payee = state.bootstrap().hive().id();
        EconomicLedger after = state.inventory().economics().transfer(payer, payee, FixedScalar.whole(3));
        assertEquals(issued, after.accounts().values().stream().map(EconomicAccount::balance).reduce(FixedScalar.ZERO, FixedScalar::plus));
        assertThrows(IllegalArgumentException.class, () -> after.transfer(payer, payee, state.bootstrap().ruleset().rates().initialSettlementTreasury()));
    }

    @Test
    void namedReservationPreventsDoubleCommitmentThenSettlesWithoutMinting() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-reservation"), 91L));
        SubjectId payer = new SubjectId("settlement:1"), payee = state.bootstrap().hive().id();
        FinancialReservation hold = new FinancialReservation(new SubjectId("reservation:test-payment"), payer, payee,
                new SubjectId("job:test-payment"), FixedScalar.whole(60));
        EconomicLedger reserved = state.inventory().economics().reserve(hold);
        FrontierWorldState reservedState = state.withInventory(state.inventory().withEconomics(reserved));

        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury(), reserved.require(payer).balance());
        assertEquals(FixedScalar.whole(40), reserved.availableToReserve(payer));
        assertEquals(reservedState, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reservedState)));
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(new FinancialReservation(new SubjectId("reservation:overcommit"), payer, payee,
                new SubjectId("job:overcommit"), FixedScalar.whole(41))));
        EconomicLedger released = reserved.release(hold.id());
        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury(), released.require(payer).balance());
        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury(), released.availableToReserve(payer));
        assertThrows(IllegalArgumentException.class, () -> released.settle(hold.id()));
        EconomicLedger settled = reserved.settle(hold.id());
        assertTrue(settled.reservations().isEmpty());
        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury().minus(hold.amount()), settled.require(payer).balance());
        assertEquals(hold.amount(), settled.require(payee).balance());
    }

    @Test
    void insolventAccountCanReceiveAnExactPaymentButCannotCreateANewDebit() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-insolvent-credit"), 92L));
        SubjectId payer = new SubjectId("settlement:1"), insolvent = state.bootstrap().hive().id();
        Map<SubjectId, EconomicAccount> accounts = new java.util.LinkedHashMap<>(state.inventory().economics().accounts());
        accounts.put(insolvent, new EconomicAccount(insolvent, EconomicOwnerKind.HIVE_COLLECTIVE, EconomicAccountStatus.INSOLVENT,
                FixedScalar.whole(-4L), FixedScalar.ZERO));
        EconomicLedger ledger = new EconomicLedger(accounts);

        EconomicLedger paid = ledger.transfer(payer, insolvent, FixedScalar.whole(3L));
        assertEquals(FixedScalar.whole(-1L), paid.require(insolvent).balance());
        assertEquals(state.bootstrap().ruleset().rates().initialSettlementTreasury().minus(FixedScalar.whole(3L)), paid.require(payer).balance());
        assertThrows(IllegalArgumentException.class, () -> paid.transfer(insolvent, payer, FixedScalar.ONE));
    }
}
