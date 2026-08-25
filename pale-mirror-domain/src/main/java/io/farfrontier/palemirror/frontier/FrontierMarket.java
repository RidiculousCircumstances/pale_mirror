package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily, source-neutral exchange resolver. It reads one immutable economic snapshot, reserves
 * stock, route capacity and mutual-credit room locally, then commits the selected physical loads.
 */
final class FrontierMarket {
    private static final long VALUE_SCALE = 1_000;
    private static final long MIN_CREDIT_LIMIT = 64;
    private static final long CREDIT_PER_PERSON = 8;
    private static final long ROUTE_FRICTION_PER_CELL = 30;
    private FrontierMarket() { }

    static void clear(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        Map<String, Account> accounts = accounts(state);
        List<Candidate> candidates = candidates(state, accounts);
        candidates.sort(Comparator.comparingLong(Candidate::surplusMilli).reversed().thenComparing(value -> value.resource().ordinal())
                .thenComparing(value -> value.route().id()).thenComparing(value -> value.sellerId()).thenComparing(value -> value.buyerId()));
        Map<String, Boolean> reservedRoutes = new LinkedHashMap<>();
        List<Load> loads = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (reservedRoutes.containsKey(candidate.route().id())) continue;
            Account seller = accounts.get(candidate.sellerId());
            Account buyer = accounts.get(candidate.buyerId());
            long amount = Math.min(candidate.route().capacity(), Math.min(seller.sellable(candidate.resource()), buyer.desired(candidate.resource())));
            amount = Math.min(amount, buyer.creditRoom() / candidate.unitPrice());
            if (amount < 1) continue;
            reservedRoutes.put(candidate.route().id(), true);
            long creditValue = Math.multiplyExact(amount, candidate.unitPrice());
            seller.reserveSale(candidate.resource(), amount, creditValue);
            buyer.reservePurchase(candidate.resource(), amount, creditValue);
            loads.add(new Load(candidate.route(), candidate.sellerId(), candidate.buyerId(), candidate.resource(), amount, creditValue));
        }
        for (Load load : loads) {
            FrontierSettlement seller = state.settlement(load.sellerId()).orElseThrow();
            FrontierSettlement buyer = state.settlement(load.buyerId()).orElseThrow();
            if (!seller.removeStock(load.resource(), load.amount())) throw new IllegalStateException("market snapshot diverged at commit");
            seller.changeCredit(load.creditValue());
            buyer.changeCredit(-load.creditValue());
            FrontierCargo cargo = new FrontierCargo(FrontierCargo.idFor(load.route().id(), state.day()), load.route().id(),
                    seller.id(), buyer.id(), load.resource(), load.amount(), load.creditValue(), state.day(), 0);
            state.putCargo(cargo);
            events.add(state.event(FrontierEvent.Type.CARGO_DISPATCHED, cargo.id(), causationId));
        }
    }

    private static Map<String, Account> accounts(FrontierWorldState state) {
        Map<String, Account> result = new LinkedHashMap<>();
        for (FrontierSettlement settlement : state.settlements().stream().sorted(Comparator.comparing(FrontierSettlement::id)).toList()) {
            result.put(settlement.id(), new Account(settlement.id(), state.alivePopulation(settlement.id()), settlement.stocks(), settlement.netCredit()));
        }
        return result;
    }

    private static List<Candidate> candidates(FrontierWorldState state, Map<String, Account> accounts) {
        List<Candidate> result = new ArrayList<>();
        for (FrontierRoute route : state.routes().stream().sorted(Comparator.comparing(FrontierRoute::id)).toList()) {
            if (state.cargoForRoute(route.id()).isPresent()) continue;
            Account left = accounts.get(route.leftSettlementId());
            Account right = accounts.get(route.rightSettlementId());
            for (FrontierResource resource : FrontierResource.values()) {
                candidate(state, route, resource, left, right).ifPresent(result::add);
                candidate(state, route, resource, right, left).ifPresent(result::add);
            }
        }
        return result;
    }

    private static java.util.Optional<Candidate> candidate(FrontierWorldState state, FrontierRoute route, FrontierResource resource,
                                                            Account seller, Account buyer) {
        if (seller.sellable(resource) < 1 || buyer.desired(resource) < 1 || buyer.creditRoom() < 1) return java.util.Optional.empty();
        long sellerValue = seller.valueMilli(resource);
        long buyerValue = buyer.valueMilli(resource);
        long friction = distance(state, seller.id(), buyer.id()) * ROUTE_FRICTION_PER_CELL;
        long surplus = buyerValue - sellerValue - friction;
        if (surplus < 1) return java.util.Optional.empty();
        long unitPrice = Math.max(1, (sellerValue + Math.max(sellerValue, buyerValue - friction) + VALUE_SCALE) / (2 * VALUE_SCALE));
        return java.util.Optional.of(new Candidate(route, seller.id(), buyer.id(), resource, surplus, unitPrice));
    }

    private static int distance(FrontierWorldState state, String leftId, String rightId) {
        FrontierPoint left = state.settlement(leftId).orElseThrow().center();
        FrontierPoint right = state.settlement(rightId).orElseThrow().center();
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }

    private record Candidate(FrontierRoute route, String sellerId, String buyerId, FrontierResource resource,
                             long surplusMilli, long unitPrice) { }
    private record Load(FrontierRoute route, String sellerId, String buyerId, FrontierResource resource,
                        long amount, long creditValue) { }

    private static final class Account {
        private final String id;
        private final int population;
        private final EnumMap<FrontierResource, Long> stocks = new EnumMap<>(FrontierResource.class);
        private long credit;
        private Account(String id, int population, Map<FrontierResource, Long> stocks, long credit) {
            this.id = id; this.population = population; this.stocks.putAll(stocks); this.credit = credit;
        }
        private String id() { return id; }
        private long target(FrontierResource resource) {
            return switch (resource) {
                case FOOD -> Math.max(12, population * 2L);
                case ORE, WOOD, POWER -> 16;
                case MEDICINE -> Math.max(8, population / 4L);
                case WEAPONS -> Math.max(4, population / 10L);
                case AMMO -> Math.max(16, population / 2L);
            };
        }
        private long sellable(FrontierResource resource) { return Math.max(0, stocks.get(resource) - target(resource)); }
        private long desired(FrontierResource resource) { return Math.max(0, target(resource) - stocks.get(resource)); }
        private long creditRoom() { return Math.max(0, Math.max(MIN_CREDIT_LIMIT, population * CREDIT_PER_PERSON) + credit); }
        private long valueMilli(FrontierResource resource) {
            long target = target(resource);
            long baseline = Math.multiplyExact(resource.referenceCredit(), VALUE_SCALE);
            long numerator = Math.multiplyExact(baseline, target + 1);
            return Math.max(VALUE_SCALE / 4, Math.min(32 * baseline, numerator / Math.max(1, stocks.get(resource) + 1)));
        }
        private void reserveSale(FrontierResource resource, long amount, long creditValue) {
            stocks.put(resource, stocks.get(resource) - amount); credit = Math.addExact(credit, creditValue);
        }
        private void reservePurchase(FrontierResource resource, long amount, long creditValue) {
            stocks.put(resource, stocks.get(resource) + amount); credit = Math.subtractExact(credit, creditValue);
        }
    }
}
