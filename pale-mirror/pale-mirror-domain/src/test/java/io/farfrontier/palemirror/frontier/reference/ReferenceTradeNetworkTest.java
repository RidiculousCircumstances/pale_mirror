package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceTradeNetworkTest {
    @Test
    void matchesSourceRoutesShortestPathsSignalsAndCashClearingMicroTrace() {
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceSettlement seller = settlement(1, 0.0d, ReferenceSimulationProfile.SOURCE_V2);
        ReferenceSettlement buyer = settlement(2, 100.0d, ReferenceSimulationProfile.SOURCE_V2);
        ReferenceSettlement intermediate = settlement(3, 0.0d, ReferenceSimulationProfile.SOURCE_V2);
        seller.add(ReferenceResource.FOOD, 500.0d);
        Map<Integer, ReferenceSettlement> settlements = ordered(seller, buyer, intermediate);
        ReferenceTradeNetwork network = new ReferenceTradeNetwork(economy);
        ReferenceRoute first = firstRoute();
        ReferenceRoute second = secondRoute();
        ReferenceRoute direct = directRoute();
        network.addRoute(first);
        network.addRoute(second);
        network.addRoute(direct);
        network.addRoute(new ReferenceRoute(1, 1, 1.0d, 1.0d));
        network.addRoute(new ReferenceRoute(2, 1, 1.0d, 1.0d));

        assertEquals(3, network.routes().size());
        assertEquals(new ReferenceRouteKey(1, 3), first.key());
        assertEquals(80.0d, first.capacityOn(null));
        assertEquals(7.2d, first.capacityOn(5));
        assertEquals(80.0d, first.capacityOn(20));
        assertEquals(0.4d, first.infectionOn(null));
        assertEquals(0.12d, first.infectionOn(5));
        assertEquals(0.4608000000000001d, network.shadowCost(first, ReferenceResource.FOOD, 20));
        assertEquals(0.30960000000000004d, network.shadowCost(first, ReferenceResource.FOOD, 5));

        assertPath(network.shortestPath(1, 2, ReferenceResource.FOOD, settlements, 20),
                0.6610500000000001d, List.of(1, 3, 2), List.of(new ReferenceRouteKey(1, 3), new ReferenceRouteKey(2, 3)));
        assertPath(network.shortestPath(1, 2, ReferenceResource.FOOD, settlements, 5),
                0.50985d, List.of(1, 3, 2), List.of(new ReferenceRouteKey(1, 3), new ReferenceRouteKey(2, 3)));
        assertEquals(22.540426394252428d, network.marketSignalFor(1, settlements).get(ReferenceResource.FOOD));

        List<ReferenceTradeRecord> records = network.clearDay(20, settlements, 1);
        assertEquals(1, records.size());
        ReferenceTradeRecord record = records.getFirst();
        assertEquals(1, record.sellerId());
        assertEquals(2, record.buyerId());
        assertEquals(ReferenceResource.FOOD, record.resource());
        assertEquals(8.845028888943219d, record.shipped());
        assertEquals(8.65928328227541d, record.delivered());
        assertEquals(11.30578557239147d, record.unitPrice());
        assertEquals(97.89999999999998d, record.value());
        assertEquals(List.of(1, 3, 2), record.path());
        assertEquals(491.1549711110568d, seller.amount(ReferenceResource.FOOD));
        assertEquals(8.65928328227541d, buyer.amount(ReferenceResource.FOOD));
        assertEquals(97.89999999999998d, seller.cash());
        assertEquals(2.1000000000000227d, buyer.cash());
        assertEquals(97.89999999999998d, network.recentVolume(5, 20));
        assertEquals(0.0d, network.recentVolume(5, 25));

        intermediate.alive(false);
        assertPath(network.shortestPath(1, 2, ReferenceResource.FOOD, settlements, 20),
                3.01392d, List.of(1, 2), List.of(new ReferenceRouteKey(1, 2)));
        ReferenceTradeNetwork disconnected = new ReferenceTradeNetwork(economy);
        disconnected.addRoute(first);
        disconnected.addRoute(second);
        assertNull(disconnected.shortestPath(1, 2, ReferenceResource.FOOD, settlements, 20));
        assertFalse(network.routes().isEmpty());
    }

    @Test
    void matchesSourceGrayboxLotAndCargoLossScalingMicroTrace() {
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceSettlement seller = settlement(1, 0.0d, ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceSettlement buyer = settlement(2, economy.humanAmount(1_000.0d), ReferenceSimulationProfile.GRAYBOX_1_40);
        seller.add(ReferenceResource.FOOD, economy.humanAmount(2_000.0d));
        ReferenceTradeNetwork network = new ReferenceTradeNetwork(economy);
        network.addRoute(directRoute(economy.humanAmount(100.0d)));

        List<ReferenceTradeRecord> records = network.clearDay(20, ordered(seller, buyer), 1);
        assertEquals(1, records.size());
        ReferenceTradeRecord record = records.getFirst();
        assertEquals(1.9530000000000005d, record.shipped());
        assertEquals(1.8743917500000005d, record.delivered());
        assertEquals(10.218528834578677d, record.unitPrice());
        assertEquals(48.047d, seller.amount(ReferenceResource.FOOD));
        assertEquals(1.8743917500000005d, buyer.amount(ReferenceResource.FOOD));
        assertEquals(19.153526144671392d, seller.cash());
        assertEquals(5.8464738553286075d, buyer.cash());
    }

    private static ReferenceSettlement settlement(int id, double cash, ReferenceSimulationProfile profile) {
        return new ReferenceSettlement(id, Integer.toString(id), 0, 0, 100.0d, cash,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), profile);
    }

    private static Map<Integer, ReferenceSettlement> ordered(ReferenceSettlement... settlements) {
        Map<Integer, ReferenceSettlement> result = new LinkedHashMap<>();
        for (ReferenceSettlement settlement : settlements) result.put(settlement.id(), settlement);
        return result;
    }

    private static ReferenceRoute firstRoute() {
        ReferenceRoute route = new ReferenceRoute(1, 3, 5.0d, 100.0d);
        route.risk(0.2d);
        route.quality(0.5d);
        route.infection(0.4d);
        route.quarantineUntil(5);
        route.disruptionUntil(9);
        route.disruptionMultiplier(0.5d);
        route.checkpointCapacityMultiplier(0.8d);
        return route;
    }

    private static ReferenceRoute secondRoute() {
        ReferenceRoute route = new ReferenceRoute(3, 2, 5.0d, 100.0d);
        route.risk(0.1d);
        route.quality(0.8d);
        route.infection(0.2d);
        return route;
    }

    private static ReferenceRoute directRoute() {
        return directRoute(100.0d);
    }

    private static ReferenceRoute directRoute(double capacity) {
        ReferenceRoute route = new ReferenceRoute(1, 2, 13.0d, capacity);
        route.risk(0.4d);
        route.quality(0.25d);
        route.infection(0.5d);
        return route;
    }

    private static void assertPath(
            ReferenceTradePath path,
            double cost,
            List<Integer> nodes,
            List<ReferenceRouteKey> edgeKeys
    ) {
        assertEquals(cost, path.logisticsCost());
        assertEquals(nodes, path.nodes());
        assertEquals(edgeKeys, path.edges().stream().map(ReferenceRoute::key).toList());
    }
}
