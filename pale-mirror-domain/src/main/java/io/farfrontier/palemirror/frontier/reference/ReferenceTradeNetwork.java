package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Source-port of Python's aggregate-cash {@code TradeNetwork}. */
public final class ReferenceTradeNetwork {
    private static final double RISK_COST_MULTIPLIER = 1.8d;
    private static final double INFECTION_COST_MULTIPLIER = 3.0d;
    private static final double COST_PER_DISTANCE = 0.018d;
    private static final double MINIMUM_ROUTE_QUALITY = 0.25d;
    private static final int MAX_MATCHES_PER_RESOURCE = 80;
    private static final double MINIMUM_SELLABLE = 0.5d;
    private static final double MINIMUM_DESIRED_IMPORT = 0.5d;
    private static final double MINIMUM_BUYER_CASH = 0.1d;
    private static final double MINIMUM_ROUTE_CAPACITY = 0.25d;
    private static final double MINIMUM_SURPLUS = 0.02d;
    private static final double BARGAINING_SHARE = 0.5d;
    private static final double MINIMUM_PRICE = 0.01d;
    private static final double MINIMUM_LOT = 2.0d;
    private static final double LOT_TARGET_FRACTION = 0.10d;
    private static final double MINIMUM_SHIPMENT = 0.25d;
    private static final double INFECTION_LOSS_WEIGHT = 1.5d;
    private static final double CARGO_LOSS_PER_DANGER = 0.035d;
    private static final double CARGO_LOSS_MAXIMUM = 0.22d;

    private final ReferenceEconomyEngine economy;
    private final List<ReferenceRoute> routes = new ArrayList<>();
    private final List<ReferenceTradeRecord> history = new ArrayList<>();

    public ReferenceTradeNetwork(ReferenceEconomyEngine economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public List<ReferenceRoute> routes() {
        return List.copyOf(routes);
    }

    public List<ReferenceTradeRecord> history() {
        return List.copyOf(history);
    }

    /** Keep the network as the sole owner when later market phases emit transfers. */
    public void appendHistory(List<ReferenceTradeRecord> records) {
        history.addAll(List.copyOf(Objects.requireNonNull(records, "records")));
    }

    /** Replaces both persisted mutable ledgers only after an owner reader validated their identities. */
    void restoreState(List<ReferenceRoute> restoredRoutes, List<ReferenceTradeRecord> restoredHistory) {
        List<ReferenceRoute> routesRequired = List.copyOf(Objects.requireNonNull(restoredRoutes, "restoredRoutes"));
        List<ReferenceTradeRecord> historyRequired = List.copyOf(Objects.requireNonNull(restoredHistory, "restoredHistory"));
        Map<ReferenceRouteKey, ReferenceRoute> unique = new LinkedHashMap<>();
        for (ReferenceRoute route : routesRequired) {
            ReferenceRoute required = Objects.requireNonNull(route, "restored route");
            if (required.a() == required.b() || unique.putIfAbsent(required.key(), required) != null) {
                throw new IllegalArgumentException("restored routes are not unique");
            }
        }
        routes.clear();
        history.clear();
        routes.addAll(routesRequired);
        history.addAll(historyRequired);
    }

    public void addRoute(ReferenceRoute route) {
        ReferenceRoute required = Objects.requireNonNull(route, "route");
        if (required.a() == required.b() || routes.stream().anyMatch(item -> item.key().equals(required.key()))) return;
        routes.add(required);
    }

    /** Logistics friction is a valuation input, never an extra cash transfer. */
    public double shadowCost(ReferenceRoute route, ReferenceResource resource, Integer day) {
        Objects.requireNonNull(route, "route");
        double danger = 1.0d + RISK_COST_MULTIPLIER * route.risk() + INFECTION_COST_MULTIPLIER * route.infectionOn(day);
        return economy.resourceRule(Objects.requireNonNull(resource, "resource")).referenceValue() * COST_PER_DISTANCE
                * route.distance() * danger / Math.max(MINIMUM_ROUTE_QUALITY, route.quality());
    }

    public ReferenceTradePath shortestPath(
            int start,
            int end,
            ReferenceResource resource,
            Map<Integer, ReferenceSettlement> settlements,
            Integer day
    ) {
        if (start == end) return new ReferenceTradePath(0.0d, List.of(start), List.of());
        Map<Integer, List<AdjacentRoute>> adjacency = adjacency();
        PriorityQueue<PathVisit> queue = new PriorityQueue<>(Comparator.comparingDouble(PathVisit::cost).thenComparingInt(PathVisit::node));
        Map<Integer, Double> distances = new HashMap<>();
        Map<Integer, PreviousRoute> previous = new HashMap<>();
        queue.add(new PathVisit(0.0d, start));
        distances.put(start, 0.0d);
        while (!queue.isEmpty()) {
            PathVisit current = queue.remove();
            if (current.cost() > distances.getOrDefault(current.node(), Double.POSITIVE_INFINITY)) continue;
            if (current.node() == end) break;
            for (AdjacentRoute next : adjacency.getOrDefault(current.node(), List.of())) {
                ReferenceSettlement neighbour = settlements.get(next.neighbour());
                if (next.neighbour() != end && !neighbour.alive()) continue;
                double cost = current.cost() + shadowCost(next.route(), resource, day);
                if (cost + 1.0e-12d < distances.getOrDefault(next.neighbour(), Double.POSITIVE_INFINITY)) {
                    distances.put(next.neighbour(), cost);
                    previous.put(next.neighbour(), new PreviousRoute(current.node(), next.route()));
                    queue.add(new PathVisit(cost, next.neighbour()));
                }
            }
        }
        if (!distances.containsKey(end)) return null;
        List<Integer> nodes = new ArrayList<>();
        List<ReferenceRoute> edges = new ArrayList<>();
        int cursor = end;
        nodes.add(cursor);
        while (cursor != start) {
            PreviousRoute prior = previous.get(cursor);
            nodes.add(prior.node());
            edges.add(prior.route());
            cursor = prior.node();
        }
        java.util.Collections.reverse(nodes);
        java.util.Collections.reverse(edges);
        return new ReferenceTradePath(distances.get(end), nodes, edges);
    }

    public Map<ReferenceResource, Double> marketSignalFor(int settlementId, Map<Integer, ReferenceSettlement> settlements) {
        ReferenceSettlement settlement = settlements.get(settlementId);
        Map<ReferenceResource, Double> signal = new LinkedHashMap<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            double best = economy.localValue(settlement, resource);
            for (Map.Entry<Integer, ReferenceSettlement> entry : settlements.entrySet()) {
                if (entry.getKey() == settlementId || !entry.getValue().alive()) continue;
                ReferenceTradePath path = shortestPath(settlementId, entry.getKey(), resource, settlements, null);
                if (path != null) best = Math.max(best, economy.localValue(entry.getValue(), resource) - path.logisticsCost());
            }
            signal.put(resource, Math.max(0.0d, best));
        }
        return java.util.Collections.unmodifiableMap(signal);
    }

    public List<ReferenceTradeRecord> clearDay(int day, Map<Integer, ReferenceSettlement> settlements) {
        return clearDay(day, settlements, MAX_MATCHES_PER_RESOURCE);
    }

    public List<ReferenceTradeRecord> clearDay(
            int day,
            Map<Integer, ReferenceSettlement> settlements,
            int maxMatchesPerResource
    ) {
        List<ReferenceTradeRecord> records = new ArrayList<>();
        Map<ReferenceRouteKey, Double> remainingCapacity = new LinkedHashMap<>();
        for (ReferenceRoute route : routes) remainingCapacity.put(route.key(), route.capacityOn(day));
        for (ReferenceResource resource : ReferenceResource.values()) {
            for (int match = 0; match < maxMatchesPerResource; match++) {
                TradeCandidate best = bestCandidate(resource, settlements, day, remainingCapacity);
                if (best == null) break;
                ReferenceSettlement seller = best.seller();
                ReferenceSettlement buyer = best.buyer();
                double sellerValue = economy.localValue(seller, resource);
                double buyerValue = economy.localValue(buyer, resource);
                double price = Math.max(MINIMUM_PRICE, sellerValue + BARGAINING_SHARE
                        * Math.max(0.0d, buyerValue - best.logistics() - sellerValue));
                double pathCapacity = best.edges().isEmpty() ? Double.POSITIVE_INFINITY : pathCapacity(best.edges(), remainingCapacity);
                double target = economy.targetStock(buyer, resource);
                double lotCap = Math.max(economy.humanAmount(MINIMUM_LOT), target * LOT_TARGET_FRACTION);
                double shipped = Math.min(Math.min(Math.min(economy.sellableQuantity(seller, resource), economy.desiredImport(buyer, resource)),
                        Math.min(pathCapacity, lotCap)), buyer.cash() / price);
                if (shipped <= economy.humanAmount(MINIMUM_SHIPMENT)) break;
                double lossFraction = lossFraction(best.edges(), day);
                double delivered = shipped * (1.0d - lossFraction);
                seller.remove(resource, shipped);
                buyer.add(resource, delivered);
                double payment = delivered * price;
                buyer.cash(buyer.cash() - payment);
                seller.cash(seller.cash() + payment);
                for (ReferenceRoute edge : best.edges()) {
                    ReferenceRouteKey key = edge.key();
                    remainingCapacity.put(key, remainingCapacity.get(key) - shipped);
                }
                ReferenceTradeRecord record = new ReferenceTradeRecord(day, seller.id(), buyer.id(), resource, shipped, delivered, price, best.nodes());
                records.add(record);
                history.add(record);
            }
        }
        return List.copyOf(records);
    }

    public double recentVolume(int days, int currentDay) {
        int threshold = currentDay - days;
        double volume = 0.0d;
        for (ReferenceTradeRecord record : history) if (record.day() > threshold) volume += record.value();
        return volume;
    }

    private TradeCandidate bestCandidate(
            ReferenceResource resource,
            Map<Integer, ReferenceSettlement> settlements,
            int day,
            Map<ReferenceRouteKey, Double> remainingCapacity
    ) {
        List<ReferenceSettlement> sellers = new ArrayList<>();
        List<ReferenceSettlement> buyers = new ArrayList<>();
        for (ReferenceSettlement settlement : settlements.values()) {
            if (settlement.alive() && economy.sellableQuantity(settlement, resource) > economy.humanAmount(MINIMUM_SELLABLE)) sellers.add(settlement);
            if (settlement.alive() && economy.desiredImport(settlement, resource) > economy.humanAmount(MINIMUM_DESIRED_IMPORT)
                    && settlement.cash() > economy.humanAmount(MINIMUM_BUYER_CASH)) buyers.add(settlement);
        }
        TradeCandidate best = null;
        for (ReferenceSettlement seller : sellers) {
            double sellerValue = economy.localValue(seller, resource);
            for (ReferenceSettlement buyer : buyers) {
                if (buyer.id() == seller.id()) continue;
                ReferenceTradePath path = shortestPath(seller.id(), buyer.id(), resource, settlements, day);
                if (path == null || (!path.edges().isEmpty() && pathCapacity(path.edges(), remainingCapacity)
                        <= economy.humanAmount(MINIMUM_ROUTE_CAPACITY))) continue;
                double surplus = economy.localValue(buyer, resource) - sellerValue - path.logisticsCost();
                if (surplus <= MINIMUM_SURPLUS) continue;
                if (best == null || surplus > best.surplus()) {
                    best = new TradeCandidate(surplus, seller, buyer, path.logisticsCost(), path.nodes(), path.edges());
                }
            }
        }
        return best;
    }

    private Map<Integer, List<AdjacentRoute>> adjacency() {
        Map<Integer, List<AdjacentRoute>> result = new LinkedHashMap<>();
        for (ReferenceRoute route : routes) {
            result.computeIfAbsent(route.a(), ignored -> new ArrayList<>()).add(new AdjacentRoute(route.b(), route));
            result.computeIfAbsent(route.b(), ignored -> new ArrayList<>()).add(new AdjacentRoute(route.a(), route));
        }
        return result;
    }

    private static double pathCapacity(List<ReferenceRoute> edges, Map<ReferenceRouteKey, Double> capacity) {
        double minimum = Double.POSITIVE_INFINITY;
        for (ReferenceRoute edge : edges) minimum = Math.min(minimum, capacity.get(edge.key()));
        return minimum;
    }

    private static double lossFraction(List<ReferenceRoute> edges, int day) {
        if (edges.isEmpty()) return 0.0d;
        double totalDanger = 0.0d;
        for (ReferenceRoute edge : edges) totalDanger += edge.risk() + INFECTION_LOSS_WEIGHT * edge.infectionOn(day);
        return Math.min(CARGO_LOSS_MAXIMUM, totalDanger / edges.size() * CARGO_LOSS_PER_DANGER);
    }

    private record AdjacentRoute(int neighbour, ReferenceRoute route) { }
    private record PreviousRoute(int node, ReferenceRoute route) { }
    private record PathVisit(double cost, int node) { }
    private record TradeCandidate(
            double surplus,
            ReferenceSettlement seller,
            ReferenceSettlement buyer,
            double logistics,
            List<Integer> nodes,
            List<ReferenceRoute> edges
    ) { }
}
