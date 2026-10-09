package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3InspectionCardsTest {
    @Test
    void townHallReadsActualBalanceReservationAndExpeditionBudgetWithoutMutation() {
        var state = initial();
        var home = state.bootstrap().settlements().getFirst();
        var destination = state.bootstrap().settlements().get(1);
        var accounts = new LinkedHashMap<>(state.inventory().economics().accounts());
        accounts.put(home.id(), new EconomicAccount(home.id(), EconomicOwnerKind.SETTLEMENT_TREASURY,
                EconomicAccountStatus.ACTIVE, FixedScalar.whole(100), FixedScalar.whole(20)));
        var reservation = new FinancialReservation(new SubjectId("reservation:inspection"), home.id(), destination.id(),
                new SubjectId("trade:inspection"), FixedScalar.whole(12));
        var budget = new FinancialBudget(new SubjectId("budget:inspection"), home.id(), FinancialBudget.OwnerKind.TRANSPORT_MISSION,
                new SubjectId("mission:inspection"), FixedScalar.whole(8));
        var ledger = new EconomicLedger(accounts, Map.of(reservation.id(), reservation), Map.of(budget.id(), budget));
        state = state.withInventory(state.inventory().withEconomics(ledger));
        var hall = home.structures().stream().filter(structure -> structure.kind() == StructureKind.HALL).findFirst().orElseThrow();
        var card = FrontierV3TownHallCard.from(state, hall);
        assertEquals("Treasury: 100", card.lines().get(0));
        assertEquals("Held: 20 · Available: 100", card.lines().get(1));
        assertEquals("Credit limit: 20", card.lines().get(2));
        assertSame(ledger, state.inventory().economics());
        var nonHall = home.structures().stream().filter(structure -> structure.kind() != StructureKind.HALL).findFirst().orElseThrow();
        var inspected = state;
        assertThrows(IllegalArgumentException.class, () -> FrontierV3TownHallCard.from(inspected, nonHall));
    }

    @Test
    void residentCardShowsNamedPersonAllSkillsAndActualCharacteristicWithoutGrantingRole() {
        var state = initial();
        var person = state.humanPopulation().residents().values().iterator().next();
        var card = FrontierV3ResidentCard.from(state, person.id(), 0L);
        assertEquals(person.name(), card.title());
        assertEquals(9, card.lines().size());
        assertEquals("Role: Unassigned · Idle", card.lines().get(2));
        assertTrue(card.lines().get(3).contains("Agr " + person.skill(ResidentSkill.AGRICULTURE)));
        assertTrue(card.lines().get(4).contains("Med " + person.skill(ResidentSkill.MEDICINE)));
        assertTrue(card.lines().get(7).contains("Hunger ×"));
        assertTrue(card.lines().stream().allMatch(line -> line.length() <= 112));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:inspection-cards"), 91L));
    }
}
