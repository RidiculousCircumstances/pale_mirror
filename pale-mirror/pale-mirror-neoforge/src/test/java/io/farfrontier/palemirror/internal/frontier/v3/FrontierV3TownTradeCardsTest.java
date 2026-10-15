package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3TownTradeCardsTest {
    @Test void incomingCollectionAndEmptyReturningCaravansRemainVisibleButCompletedOnesDoNot() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:caravan-cards"), 91));
        var home = state.bootstrap().settlements().get(0); var peer = state.bootstrap().settlements().get(1);
        var a = SurfaceAnchor.at(0, 63, 0); var b = SurfaceAnchor.at(10, 63, 0);
        var source = new ShipmentEndpoint.Storage(peer.id(), new SubjectId("container:card-source"), b, Optional.empty(), b);
        var target = new ShipmentEndpoint.Storage(home.id(), new SubjectId("container:card-target"), a, Optional.empty(), a);
        var id = new SubjectId("mission:card-return");
        var mission = new TransportMission(id, new SubjectId("group:card-return"), List.of(new SubjectId("shipment:retired")),
                source, target, new TransportOrganization(new EconomicIdentity(home.id(), EconomicOwnerKind.SETTLEMENT_TREASURY),
                    target, TransportOrganization.Itinerary.DESTINATION_COLLECTION), a, b, TransportMission.Stage.RETURNING, 5);
        state = state.withChanges(FrontierWorldStateUpdate.begin().shipments(new ShipmentState(Map.of(), Map.of(id, mission))));
        var hall = home.structures().stream().filter(s -> s.kind() == StructureKind.HALL).findFirst().orElseThrow();
        var lines = FrontierV3TownTradeCards.pages(state, hall, 0).stream().flatMap(p -> p.lines().stream()).toList();
        assertTrue(lines.contains("Stage: Returning home"));
        assertTrue(lines.contains("Goods: " + peer.displayName() + " -> " + home.displayName()));
        assertTrue(lines.contains("Home: " + home.displayName()));
        assertTrue(lines.contains("No retained cargo allocations"));
        assertTrue(lines.contains("Retired shipment records: 1"));
        var complete = state.withChanges(FrontierWorldStateUpdate.begin().shipments(new ShipmentState(Map.of(), Map.of(id, mission.advance(TransportMission.Stage.COMPLETE)))));
        assertFalse(FrontierV3TownTradeCards.pages(complete, hall, 0).stream().flatMap(p -> p.lines().stream())
                .anyMatch(line -> line.contains("Stage:")));
    }
    @Test void recentCompletedTradeIsGroupedByDirectionPeerAndResourceWithoutCountingUnrelatedSales() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:trade-cards"), 91));
        var a = state.bootstrap().settlements().get(0); var b = state.bootstrap().settlements().get(1);
        var c = state.bootstrap().settlements().get(2);
        var history = MarketPriceHistory.empty()
                .record("minecraft:bread", sale("sale:a", a.id(), b.id(), 1, 32, 2))
                .record("minecraft:bread", sale("sale:b", a.id(), b.id(), 2, 16, 3))
                .record("minecraft:bread", sale("sale:c", b.id(), c.id(), 3, 99, 7))
                .record("minecraft:coal", sale("sale:d", b.id(), a.id(), 4, 8, 5));
        var trade = state.companies().goodsTrade();
        state = state.withCompanies(state.companies().withGoodsTrade(new GoodsTradeState(
                trade.orders(), trade.contracts(), trade.participants(), history)));
        var hall = a.structures().stream().filter(s -> s.kind() == StructureKind.HALL).findFirst().orElseThrow();
        var inventory = state.inventory(); var companies = state.companies();
        var pages = FrontierV3TownTradeCards.pages(state, hall, 10);
        var lines = pages.stream().flatMap(p -> p.lines().stream()).toList();
        assertTrue(lines.contains("Sold 48 bread"));
        assertTrue(lines.contains("To " + b.displayName() + " · $112"));
        assertTrue(lines.contains("Bought 8 coal"));
        assertTrue(lines.contains("From " + b.displayName() + " · $40"));
        assertTrue(lines.contains("2 receipts · Last tick 2"));
        assertTrue(lines.contains("Recent settled receipts, not lifetime totals"));
        assertFalse(lines.stream().anyMatch(line -> line.contains("99")));
        assertTrue(lines.contains("No active incoming or outgoing caravans"));
        assertTrue(pages.stream().allMatch(page -> page.lines().size() < PlayerContextCard.MAX_LINES));
        assertSame(inventory, state.inventory()); assertSame(companies, state.companies());
        assertEquals(pages, FrontierV3TownTradeCards.pages(state, hall, 10));
    }
    private static MarketPriceHistory.Sale sale(String id, SubjectId seller, SubjectId buyer, long at, int count, int price) {
        return new MarketPriceHistory.Sale(new SubjectId(id), seller, buyer, at, count, FixedScalar.whole(price));
    }
}
