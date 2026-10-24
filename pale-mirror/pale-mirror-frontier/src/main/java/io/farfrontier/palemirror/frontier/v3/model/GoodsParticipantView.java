package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Read-only economic projection for one participant. A policy receives no mutable world or other stocks. */
public record GoodsParticipantView(GoodsParticipant participant, int residents, FixedScalar availableMoney,
                                   Map<String, Stock> stocks, Map<String, Integer> purchaseLimits,
                                   Optional<io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan> supplyPlan,
                                   Set<String> awaitingReview) {
    public GoodsParticipantView(GoodsParticipant participant, int residents, FixedScalar availableMoney,
            Map<String, Stock> stocks, Map<String, Integer> purchaseLimits,
            Optional<io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan> supplyPlan) {
        this(participant, residents, availableMoney, stocks, purchaseLimits, supplyPlan, Set.of());
    }
    private static final ImmutableInputView<Map<GoodsParticipant, GoodsParticipantView>> VIEWS = new ImmutableInputView<>();
    public GoodsParticipantView(GoodsParticipant participant, int residents, FixedScalar availableMoney,
                                Map<String, Stock> stocks, Map<String, Integer> purchaseLimits) {
        this(participant, residents, availableMoney, stocks, purchaseLimits, Optional.empty());
    }
    public GoodsParticipantView(GoodsParticipant participant, int residents, FixedScalar availableMoney, Map<String, Stock> stocks) {
        this(participant, residents, availableMoney, stocks, Map.of());
    }
    public record Stock(int ownedInStorage, int unclaimedInStorage, int expectedIncoming, int protectedMinimum) {
        public Stock(int ownedInStorage, int unclaimedInStorage, int expectedIncoming) {
            this(ownedInStorage, unclaimedInStorage, expectedIncoming, 0);
        }
        public Stock {
            if (ownedInStorage < 0 || unclaimedInStorage < 0 || unclaimedInStorage > ownedInStorage || expectedIncoming < 0 || protectedMinimum < 0)
                throw new IllegalArgumentException("invalid participant stock view");
        }
    }
    public int target(GoodsTradeRules.Commodity commodity) {
        if (supplyPlan.isPresent() && !supplyPlan.orElseThrow().reserves().isEmpty())
            return Math.toIntExact(Math.max(supplyPlan.orElseThrow().reserves().get(commodity.itemKind()).quantity(),
                    supplyPlan.orElseThrow().require(commodity.itemKind()).protectedInput()));
        return StockTargetDemand.target(commodity, residents, stocks.get(commodity.itemKind()).protectedMinimum());
    }
    public int saleable(GoodsTradeRules.Commodity commodity) {
        if (awaitingReview.contains(commodity.itemKind())) return 0;
        return supplyPlan.flatMap(plan -> plan.economy())
                .map(plan -> Math.min(Math.toIntExact(plan.saleableStock().get(commodity.itemKind())),
                        Math.max(0, stocks.get(commodity.itemKind()).unclaimedInStorage() - stocks.get(commodity.itemKind()).protectedMinimum())))
                .orElseGet(() -> Math.max(0, stocks.get(commodity.itemKind()).unclaimedInStorage() - target(commodity)));
    }
    public GoodsParticipantView {
        Objects.requireNonNull(participant); Objects.requireNonNull(availableMoney); stocks = Map.copyOf(stocks);
        supplyPlan = Objects.requireNonNull(supplyPlan); awaitingReview = Set.copyOf(awaitingReview);
        purchaseLimits = Map.copyOf(purchaseLimits);
        if (purchaseLimits.values().stream().anyMatch(value -> value < 0)) throw new IllegalArgumentException("negative purchase limit");
        if (residents < 0 || availableMoney.raw() < 0) throw new IllegalArgumentException("invalid participant economic view");
    }
    /** Facts only. Evaluation belongs to the explicit economic review owner. */
    public static GoodsParticipantView observe(FrontierWorldState state, GoodsParticipant participant) {
        // Depend on stock, eligibility and accepted protection, never the world/tick.
        var inventory = state.inventory();
        var views = VIEWS.get(List.of(state.bootstrap(), inventory.items(), inventory.containers(), inventory.surfaces(),
                inventory.fungibleResources().accounts(), inventory.fungibleResources().lots(), inventory.fungibleResources().claims(),
                inventory.economics(), state.shipments(), state.replicaCustody(), state.fencedRecovery(),
                state.companies().goodsTrade().contracts(), state.companies().goodsTrade().participants().planning(),
                LivingResidentStockView.counts(state), WorkEquipmentClaimComposition.reserved(state)), HashMap::new);
        var prior = views.get(participant);
        if (prior != null) return prior;
        var result = project(state, participant);
        if (views.size() >= 64) views.clear();
        views.put(participant, result);
        return result;
    }
    private static GoodsParticipantView project(FrontierWorldState state, GoodsParticipant participant) {
        participant.party().validate(state.inventory().economics());
        var ledger = state.inventory().fungibleResources();
        var result = new HashMap<String, Stock>();
        for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())) {
            var containers = GoodsParticipantStorage.containers(state, participant, commodity.itemKind());
            var accounts = containers.stream().flatMap(container -> FungibleResourceCustodySupport.accountAtContainer(state, container).stream()).toList();
            int owned = accounts.stream().flatMap(value -> value.lotQuantities().entrySet().stream()).filter(entry -> {
                var lot = ledger.lots().get(entry.getKey());
                return lot.economicOwnerId().equals(participant.party().id()) && lot.itemKind().equals(commodity.itemKind());
            }).mapToInt(Map.Entry::getValue).reduce(0, Math::addExact);
            int unclaimed = accounts.stream().mapToInt(value -> ledger.unclaimedQuantity(value.id(), participant.party().id(), commodity.itemKind())).reduce(0, Math::addExact);
            if (commodity.stock().representation() == GoodsStockSpecification.Representation.INDIVIDUAL) {
                owned = Math.toIntExact(ExactItemReadIndex.owned(state.inventory(), participant.party().id(), commodity.itemKind()).stream().filter(item ->
                        item.economicOwnerId().equals(participant.party().id()) && item.itemKind().equals(commodity.itemKind())
                        && commodity.stock().accepts(item) && item.custody() instanceof InventoryCustody.ContainerSlot slot
                        && containers.contains(slot.containerId())).count());
                unclaimed = containers.stream().mapToInt(container -> GoodsParticipantStorage.individualStock(state, participant, container, commodity.itemKind()).size()).sum();
            }
            int incoming = state.companies().goodsTrade().contracts().values().stream()
                    .filter(contract -> !contract.terminal() && contract.buyer().equals(participant.party())
                            && contract.itemKind().equals(commodity.itemKind()) && containers.contains(contract.receiverContainerId()))
                    .mapToInt(GoodsTradeContract::remainingQuantity).reduce(0, Math::addExact);
            for (var container : containers) incoming = Math.addExact(incoming, InternalShipmentIncomingStock.quantity(
                    state.shipments(), participant.party().id(), container, commodity.itemKind()));
            int protectedMinimum = participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT
                    ? PublicStockProtection.initialMinimum(state, participant.party().id(), commodity.itemKind()) : 0;
            result.put(commodity.itemKind(), new Stock(owned, unclaimed, incoming, protectedMinimum));
        }
        int residents = LivingResidentStockView.counts(state).getOrDefault(participant.settlementId(), 0);
        return new GoodsParticipantView(participant, residents,
                state.inventory().economics().availableToReserve(participant.party().id()), result);
    }
    static GoodsParticipantView planned(FrontierWorldState state, GoodsParticipantView observed,
            Optional<io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan> plan) {
        var participant = observed.participant();
        var result = new HashMap<>(observed.stocks());
        int residents = observed.residents();
        var advice = plan.isPresent() ? planningAdvice(plan.orElseThrow()) : participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT
                && !state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()
                ? SettlementFoodPurchases.assess(state, participant.party().id(), residents, result)
                : new SettlementFoodPurchases.Advice(Map.of(), Map.of());
        advice.protectedInputs().forEach((kind, minimum) -> {
            var stock = result.get(kind);
            int local = plan.flatMap(value -> value.economy()).isPresent()
                    ? Math.toIntExact(plan.orElseThrow().reserves().get(kind).protectedQuantity()) : stock.protectedMinimum();
            result.put(kind, new Stock(stock.ownedInStorage(), stock.unclaimedInStorage(), stock.expectedIncoming(),
                    Math.max(minimum, local)));
        });
        return new GoodsParticipantView(participant, residents, observed.availableMoney(), result, advice.limits(), plan);
    }
    private static SettlementFoodPurchases.Advice planningAdvice(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner.Plan plan) {
        var limits = new HashMap<String, Integer>(); var protection = new HashMap<String, Integer>();
        plan.demands().forEach((item, demand) -> {
            limits.put(item, Math.toIntExact(demand.purchase()));
            protection.put(item, Math.toIntExact(demand.protectedInput()));
        });
        return new SettlementFoodPurchases.Advice(limits, protection);
    }
}
