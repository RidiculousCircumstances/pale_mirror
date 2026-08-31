package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded legal market state. Financial balances and physical custody remain in
 * their own aggregates; this book only records demand, offers and the one
 * accepted order that gives a work its commercial cause.
 */
public final class MarketOrderBook {
    static final int MAX_DEMANDS = 1_024;
    static final int MAX_QUOTES = 2_048;
    static final int MAX_WORK_ORDERS = 1_024;
    static final int RETAINED_TERMINAL_DEMANDS = 256;
    private final Map<SubjectId, MarketDemand> demands;
    private final Map<SubjectId, CompanyQuote> quotes;
    private final Map<SubjectId, MarketWorkOrder> workOrders;

    public MarketOrderBook(Map<SubjectId, MarketDemand> demands, Map<SubjectId, CompanyQuote> quotes,
                    Map<SubjectId, MarketWorkOrder> workOrders) {
        this.demands = Map.copyOf(demands); this.quotes = Map.copyOf(quotes); this.workOrders = Map.copyOf(workOrders);
        if (this.demands.size() > MAX_DEMANDS || this.quotes.size() > MAX_QUOTES || this.workOrders.size() > MAX_WORK_ORDERS) {
            throw new IllegalArgumentException("market order-book retention limit exceeded: demands=" + this.demands.size()
                    + " terminal=" + this.demands.values().stream().filter(this::terminal).count()
                    + ", quotes=" + this.quotes.size() + ", workOrders=" + this.workOrders.size()
                    + " terminal=" + this.workOrders.values().stream().filter(order -> order.status() != MarketWorkOrderStatus.ACCEPTED).count());
        }
        this.demands.forEach((id, demand) -> {
            if (!id.equals(demand.id())) throw new IllegalArgumentException("market demand key must match identity");
        });
        this.quotes.forEach((id, quote) -> {
            MarketDemand demand = this.demands.get(quote.demandId());
            if (!id.equals(quote.id()) || demand == null || demand.itemCount() != quote.itemCount()
                    || quote.totalPrice().compareTo(demand.maximumTotalPrice()) > 0) {
                throw new IllegalArgumentException("market quote must retain an exact affordable demand");
            }
        });
        this.workOrders.forEach((id, order) -> validateOrder(id, order));
        this.workOrders.values().stream().filter(order -> order.status() == MarketWorkOrderStatus.ACCEPTED)
                .collect(java.util.stream.Collectors.groupingBy(MarketWorkOrder::demandId)).values().forEach(active -> {
                    if (active.size() != 1) throw new IllegalArgumentException("market demand may retain only one accepted work order");
                });
        this.workOrders.values().stream().filter(order -> order.status() == MarketWorkOrderStatus.ACCEPTED)
                .collect(java.util.stream.Collectors.groupingBy(MarketWorkOrder::jobId)).values().forEach(active -> {
                    if (active.size() != 1) throw new IllegalArgumentException("production job may retain only one accepted work order");
                });
    }

    public static MarketOrderBook empty() { return new MarketOrderBook(Map.of(), Map.of(), Map.of()); }
    public Map<SubjectId, MarketDemand> demands() { return demands; }
    public Map<SubjectId, CompanyQuote> quotes() { return quotes; }
    public Map<SubjectId, MarketWorkOrder> workOrders() { return workOrders; }

    MarketOrderBook open(MarketDemand demand) {
        Objects.requireNonNull(demand, "market demand");
        if (demands.containsKey(demand.id()) || demands.values().stream().anyMatch(existing -> existing.reasonId().equals(demand.reasonId())
                && existing.status() != MarketDemandStatus.CANCELLED && existing.status() != MarketDemandStatus.EXPIRED)) {
            throw new IllegalArgumentException("market demand identity or active reason already exists");
        }
        Map<SubjectId, MarketDemand> next = new LinkedHashMap<>(demands); next.put(demand.id(), demand);
        return new MarketOrderBook(next, quotes, workOrders);
    }

    MarketOrderBook publish(CompanyQuote quote, long now) {
        Objects.requireNonNull(quote, "market quote");
        MarketDemand demand = demands.get(quote.demandId());
        if (quotes.containsKey(quote.id()) || demand == null || demand.status() != MarketDemandStatus.OPEN || now < quote.quotedAtTick()
                || now > quote.expiresAtTick() || now > demand.expiresAtTick()) {
            throw new IllegalArgumentException("market quote is not current for an open demand");
        }
        Map<SubjectId, CompanyQuote> next = new LinkedHashMap<>(quotes); next.put(quote.id(), quote);
        return new MarketOrderBook(demands, next, workOrders);
    }

    MarketOrderBook accept(MarketWorkOrder order, long now) {
        Objects.requireNonNull(order, "market work order");
        MarketDemand demand = demands.get(order.demandId()); CompanyQuote quote = quotes.get(order.quoteId());
        if (workOrders.containsKey(order.id()) || demand == null || quote == null || demand.status() != MarketDemandStatus.OPEN
                || now > demand.expiresAtTick() || now > quote.expiresAtTick() || !quote.sellerId().equals(order.sellerId())
                || !quote.demandId().equals(order.demandId()) || !quote.totalPrice().equals(order.acceptedTotalPrice())
                || workOrders.values().stream().anyMatch(existing -> existing.demandId().equals(order.demandId())
                && existing.status() == MarketWorkOrderStatus.ACCEPTED)) {
            throw new IllegalArgumentException("market work order does not accept one current quote for an open demand");
        }
        Map<SubjectId, MarketDemand> nextDemands = new LinkedHashMap<>(demands); nextDemands.put(demand.id(), demand.withStatus(MarketDemandStatus.ORDERED));
        Map<SubjectId, MarketWorkOrder> nextOrders = new LinkedHashMap<>(workOrders); nextOrders.put(order.id(), order);
        return new MarketOrderBook(nextDemands, quotes, nextOrders);
    }

    MarketOrderBook complete(SubjectId orderId) { return terminal(orderId, MarketWorkOrderStatus.FULFILLED, MarketDemandStatus.FULFILLED); }
    MarketOrderBook cancel(SubjectId orderId, MarketWorkOrderStatus outcome) {
        if (outcome != MarketWorkOrderStatus.CANCELLED && outcome != MarketWorkOrderStatus.CONFLICT) {
            throw new IllegalArgumentException("market cancellation needs a cancellation outcome");
        }
        return terminal(orderId, outcome, MarketDemandStatus.CANCELLED);
    }

    MarketOrderBook expireOpen(long now) {
        Map<SubjectId, MarketDemand> next = new LinkedHashMap<>(demands); boolean changed = false;
        for (MarketDemand demand : demands.values()) {
            if (demand.status() == MarketDemandStatus.OPEN && now > demand.expiresAtTick()) {
                next.put(demand.id(), demand.withStatus(MarketDemandStatus.EXPIRED)); changed = true;
            }
        }
        return changed ? new MarketOrderBook(next, quotes, workOrders) : this;
    }

    MarketOrderBook cancelOpen(SubjectId demandId) {
        MarketDemand demand = demands.get(Objects.requireNonNull(demandId, "market demand id"));
        if (demand == null || demand.status() != MarketDemandStatus.OPEN) {
            throw new IllegalArgumentException("only an open market demand may be cancelled");
        }
        Map<SubjectId, MarketDemand> next = new LinkedHashMap<>(demands);
        next.put(demand.id(), demand.withStatus(MarketDemandStatus.CANCELLED));
        return new MarketOrderBook(next, quotes, workOrders);
    }

    /**
     * Retains a compact recent audit tail only after the caller proves no live
     * job, money hold or physical intent refers to the commercial record.
     */
    MarketOrderBook compactTerminal(Set<SubjectId> protectedIds) {
        Objects.requireNonNull(protectedIds, "market protected references");
        List<MarketDemand> removable = demands.values().stream().filter(this::terminal).filter(demand -> safeToDrop(demand, protectedIds))
                .sorted(Comparator.comparingLong(MarketDemand::openedAtTick).thenComparing(MarketDemand::id)).toList();
        int drop = Math.max(0, removable.size() - RETAINED_TERMINAL_DEMANDS);
        if (drop == 0) return this;
        Set<SubjectId> discarded = removable.subList(0, drop).stream().map(MarketDemand::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<SubjectId, MarketDemand> nextDemands = new LinkedHashMap<>(demands); discarded.forEach(nextDemands::remove);
        Map<SubjectId, CompanyQuote> nextQuotes = new LinkedHashMap<>(quotes); nextQuotes.values().removeIf(quote -> discarded.contains(quote.demandId()));
        Map<SubjectId, MarketWorkOrder> nextOrders = new LinkedHashMap<>(workOrders); nextOrders.values().removeIf(order -> discarded.contains(order.demandId()));
        return new MarketOrderBook(nextDemands, nextQuotes, nextOrders);
    }

    Optional<CompanyQuote> bestCurrentQuote(SubjectId demandId, long now) {
        MarketDemand demand = demands.get(Objects.requireNonNull(demandId, "market demand id"));
        if (demand == null || demand.status() != MarketDemandStatus.OPEN || now > demand.expiresAtTick()) return Optional.empty();
        return quotes.values().stream().filter(quote -> quote.demandId().equals(demandId) && now <= quote.expiresAtTick())
                .min(Comparator.comparing(CompanyQuote::totalPrice).thenComparing(CompanyQuote::id));
    }

    Optional<MarketWorkOrder> acceptedForJob(SubjectId jobId) {
        return workOrders.values().stream().filter(order -> order.jobId().equals(Objects.requireNonNull(jobId, "production job id"))
                && order.status() == MarketWorkOrderStatus.ACCEPTED).findFirst();
    }

    @Override public boolean equals(Object other) {
        return other instanceof MarketOrderBook value && demands.equals(value.demands) && quotes.equals(value.quotes) && workOrders.equals(value.workOrders);
    }
    @Override public int hashCode() { return Objects.hash(demands, quotes, workOrders); }

    private MarketOrderBook terminal(SubjectId orderId, MarketWorkOrderStatus orderStatus, MarketDemandStatus demandStatus) {
        MarketWorkOrder order = workOrders.get(Objects.requireNonNull(orderId, "market work order id"));
        if (order == null || order.status() != MarketWorkOrderStatus.ACCEPTED) {
            throw new IllegalArgumentException("only an accepted market work order may become terminal");
        }
        MarketDemand demand = demands.get(order.demandId());
        Map<SubjectId, MarketDemand> nextDemands = new LinkedHashMap<>(demands); nextDemands.put(demand.id(), demand.withStatus(demandStatus));
        Map<SubjectId, MarketWorkOrder> nextOrders = new LinkedHashMap<>(workOrders); nextOrders.put(order.id(), order.withStatus(orderStatus));
        return new MarketOrderBook(nextDemands, quotes, nextOrders);
    }

    private boolean terminal(MarketDemand demand) {
        return demand.status() == MarketDemandStatus.FULFILLED || demand.status() == MarketDemandStatus.CANCELLED || demand.status() == MarketDemandStatus.EXPIRED;
    }
    private boolean safeToDrop(MarketDemand demand, Set<SubjectId> protectedIds) {
        if (protectedIds.contains(demand.id())) return false;
        return workOrders.values().stream().filter(order -> order.demandId().equals(demand.id())).allMatch(order -> order.status() != MarketWorkOrderStatus.ACCEPTED
                && !protectedIds.contains(order.id()) && !protectedIds.contains(order.jobId()) && !protectedIds.contains(order.reservationId()));
    }

    private void validateOrder(SubjectId id, MarketWorkOrder order) {
        MarketDemand demand = demands.get(order.demandId()); CompanyQuote quote = quotes.get(order.quoteId());
        if (!id.equals(order.id()) || demand == null || quote == null || !quote.sellerId().equals(order.sellerId())
                || !quote.demandId().equals(order.demandId()) || !quote.totalPrice().equals(order.acceptedTotalPrice())) {
            throw new IllegalArgumentException("market work order must retain its exact demand and quote");
        }
        if (order.status() == MarketWorkOrderStatus.ACCEPTED && demand.status() != MarketDemandStatus.ORDERED) {
            throw new IllegalArgumentException("accepted market work order requires ordered demand");
        }
        if (order.status() == MarketWorkOrderStatus.FULFILLED && demand.status() != MarketDemandStatus.FULFILLED) {
            throw new IllegalArgumentException("fulfilled market work order requires fulfilled demand");
        }
        if ((order.status() == MarketWorkOrderStatus.CANCELLED || order.status() == MarketWorkOrderStatus.CONFLICT)
                && demand.status() != MarketDemandStatus.CANCELLED) {
            throw new IllegalArgumentException("cancelled market work order requires cancelled demand");
        }
    }
}
