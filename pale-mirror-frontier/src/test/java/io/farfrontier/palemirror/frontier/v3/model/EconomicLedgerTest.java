package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicLedgerTest {
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
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY.multiply(state.bootstrap().settlements().size()), issued);
        SubjectId payer = new SubjectId("settlement:1"), payee = state.bootstrap().hive().id();
        EconomicLedger after = state.inventory().economics().transfer(payer, payee, FixedScalar.whole(3));
        assertEquals(issued, after.accounts().values().stream().map(EconomicAccount::balance).reduce(FixedScalar.ZERO, FixedScalar::plus));
        assertThrows(IllegalArgumentException.class, () -> after.transfer(payer, payee, EconomicLedger.INITIAL_SETTLEMENT_TREASURY));
    }

    @Test
    void namedReservationPreventsDoubleCommitmentThenSettlesWithoutMinting() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economy-reservation"), 91L));
        SubjectId payer = new SubjectId("settlement:1"), payee = state.bootstrap().hive().id();
        FinancialReservation hold = new FinancialReservation(new SubjectId("reservation:test-payment"), payer, payee,
                new SubjectId("job:test-payment"), FixedScalar.whole(60));
        EconomicLedger reserved = state.inventory().economics().reserve(hold);
        FrontierWorldState reservedState = state.withInventory(state.inventory().withEconomics(reserved));

        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY, reserved.require(payer).balance());
        assertEquals(FixedScalar.whole(40), reserved.availableToReserve(payer));
        assertEquals(reservedState, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reservedState)));
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(new FinancialReservation(new SubjectId("reservation:overcommit"), payer, payee,
                new SubjectId("job:overcommit"), FixedScalar.whole(41))));
        EconomicLedger released = reserved.release(hold.id());
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY, released.require(payer).balance());
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY, released.availableToReserve(payer));
        assertThrows(IllegalArgumentException.class, () -> released.settle(hold.id()));
        EconomicLedger settled = reserved.settle(hold.id());
        assertTrue(settled.reservations().isEmpty());
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY.minus(hold.amount()), settled.require(payer).balance());
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
        assertEquals(EconomicLedger.INITIAL_SETTLEMENT_TREASURY.minus(FixedScalar.whole(3L)), paid.require(payer).balance());
        assertThrows(IllegalArgumentException.class, () -> paid.transfer(insolvent, payer, FixedScalar.ONE));
    }
}
