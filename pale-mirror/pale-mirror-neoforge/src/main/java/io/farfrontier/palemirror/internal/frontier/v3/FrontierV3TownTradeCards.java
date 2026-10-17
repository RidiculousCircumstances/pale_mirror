package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import java.math.BigDecimal;
import java.util.*;

/** On-demand read model. No searches, trade decisions, duplicate accounting or retained UI history. */
final class FrontierV3TownTradeCards {
    private static final int ACCENT = 0xF4B942;
    private record Flow(boolean sold, SubjectId peer, String item) { }
    private record Total(long quantity, BigDecimal value, int receipts, long lastTick) {
        Total add(MarketPriceHistory.Sale sale) {
            return new Total(quantity + sale.quantity(), value.add(BigDecimal.valueOf(sale.unitPrice().raw(), 6)
                    .multiply(BigDecimal.valueOf(sale.quantity()))), receipts + 1, Math.max(lastTick, sale.atTick()));
        }
    }
    private FrontierV3TownTradeCards() { }

    static List<PlayerContextCard> pages(FrontierWorldState state, SettlementStructure hall, long now) {
        // Validate the exact hall through its existing owner before projecting extra detail.
        var overview = FrontierV3TownHallCard.from(state, hall);
        var home = hall.settlementId();
        var trade = state.companies().goodsTrade();
        var flows = new HashMap<Flow, Total>();
        trade.priceHistory().commodities().forEach((item, sales) -> sales.forEach(sale -> {
            boolean sold = sale.seller().equals(home), bought = sale.buyer().equals(home);
            if (!sold && !bought) return;
            var key = new Flow(sold, sold ? sale.buyer() : sale.seller(), item);
            flows.put(key, flows.getOrDefault(key, new Total(0, BigDecimal.ZERO, 0, 0)).add(sale));
        }));
        var missions = state.shipments().missions().values().stream()
                .filter(m -> m.stage() != TransportMission.Stage.COMPLETE)
                .filter(m -> !m.sender().settlementId().equals(m.receiver().settlementId()))
                .filter(m -> m.sender().settlementId().equals(home) || m.receiver().settlementId().equals(home))
                .sorted(Comparator.comparing(TransportMission::id)).toList();
        var contracts = trade.contracts().values().stream().filter(c -> !c.terminal())
                .filter(c -> c.seller().id().equals(home) || c.buyer().id().equals(home))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList();
        long bids = trade.orders().values().stream().filter(o -> o.party().id().equals(home)
                && o.side() == GoodsTradeOrder.Side.BUY && o.availableQuantity() > 0 && o.expiresAtTick() > now).count();
        long offers = trade.orders().values().stream().filter(o -> o.party().id().equals(home)
                && o.side() == GoodsTradeOrder.Side.SELL && o.availableQuantity() > 0 && o.expiresAtTick() > now).count();
        var summary = new ArrayList<>(overview.lines());
        summary.add("Trade: " + missions.size() + " caravans · " + contracts.size() + " contracts");
        summary.add("Open bids: " + bids + " · Offers: " + offers);
        var result = new ArrayList<PlayerContextCard>();
        result.add(new PlayerContextCard(overview.title(), summary, ACCENT));
        String title = clip(overview.title().replace(" · Town Hall", ""), 40);
        var history = new ArrayList<String>();
        history.add("Recent settled receipts, not lifetime totals");
        history.add("History: last " + MarketPriceHistory.MAX_SALES + "/item across the market");
        if (flows.isEmpty()) history.add("No retained purchases or sales");
        flows.entrySet().stream().sorted(Comparator
                .<Map.Entry<Flow, Total>>comparingLong(e -> e.getValue().lastTick()).reversed()
                .thenComparing(e -> e.getKey().peer()).thenComparing(e -> e.getKey().item())
                .thenComparing(e -> e.getKey().sold())).forEach(entry -> {
            var flow = entry.getKey(); var total = entry.getValue();
            history.add((flow.sold() ? "Sold " : "Bought ") + total.quantity() + " " + item(flow.item()));
            history.add((flow.sold() ? "To " : "From ") + party(state, flow.peer()) + " · $" + money(total.value()));
            history.add(total.receipts() + " receipts · Last tick " + total.lastTick());
        });
        addPages(result, title + " · Trade history", history);
        var obligations = new ArrayList<String>();
        obligations.add("Agreed goods still awaiting settlement");
        for (var contract : contracts) {
            boolean sell = contract.seller().id().equals(home);
            int remaining = contract.outstandingClaims().values().stream().mapToInt(Integer::intValue).sum();
            obligations.add((sell ? "Sell " : "Buy ") + remaining + " " + item(contract.itemKind())
                    + (sell ? " to " : " from ") + party(state, sell ? contract.buyer().id() : contract.seller().id()));
            obligations.add("Accepted " + contract.acceptedQuantity() + "/" + contract.quantity()
                    + " · Lost/withdrawn " + contract.disposedQuantity());
        }
        if (contracts.isEmpty()) obligations.add("No outstanding trade contracts");
        addPages(result, title + " · Pending trade", obligations);
        if (missions.isEmpty()) addPages(result, title + " · Caravans", List.of("No active incoming or outgoing caravans"));
        for (int i = 0; i < missions.size(); i++) {
            var mission = missions.get(i);
            var lines = new ArrayList<String>();
            lines.add("Goods: " + party(state, mission.sender().settlementId()) + " -> " + party(state, mission.receiver().settlementId()));
            lines.add("Home: " + party(state, mission.organization().home().settlementId()));
            lines.add("Stage: " + stage(mission.stage()));
            var group = state.unitGroups().groups().get(mission.groupId());
            if (group == null) lines.add("Group record unavailable");
            else {
                lines.add("Members: " + group.members().size() + " · " + group.phase().name().toLowerCase(Locale.ROOT));
                group.journey().ifPresent(journey -> lines.add("Route: " + journey.cursor() + "/" + (journey.route().size() - 1) + " nodes"));
            }
            lines.add(mission.transportAssetId().isPresent() ? "Pack animal assigned" : "Carried by residents");
            var cargo = new TreeMap<String, Integer>();
            int removed = 0;
            for (var id : mission.shipmentIds()) {
                var shipment = state.shipments().shipments().get(id);
                if (shipment == null) { removed++; continue; }
                String label = switch (shipment.status()) {
                    case AWAITING_LOAD -> "Load "; case CARRYING -> "Carrying "; case DELIVERED -> "Unloaded ";
                    case ALLOCATION_WITHDRAWN -> "Withdrawn "; case CARGO_DISPOSED -> "Lost ";
                    case RETURNING -> "Returning unsold "; case RETURNED -> "Returned unsold ";
                };
                if (shipment.quantity() > 0) cargo.merge(label + item(shipment.itemKind()), shipment.quantity(), Integer::sum);
                shipment.reception().ifPresent(receipt -> cargo.merge("Awaiting acceptance " + item(shipment.itemKind()), receipt.quantity(), Integer::sum));
            }
            cargo.forEach((label, quantity) -> lines.add(label + ": " + quantity));
            if (cargo.isEmpty()) lines.add("No retained cargo allocations");
            if (removed > 0) lines.add("Retired shipment records: " + removed);
            addPages(result, title + " · Caravan " + (i + 1) + "/" + missions.size(), lines);
        }
        return List.copyOf(result);
    }
    private static void addPages(List<PlayerContextCard> target, String title, List<String> lines) {
        for (int from = 0; from < lines.size(); from += 8)
            target.add(new PlayerContextCard(title, lines.subList(from, Math.min(lines.size(), from + 8)), ACCENT));
    }
    private static String party(FrontierWorldState state, SubjectId id) {
        return state.bootstrap().settlements().stream().filter(s -> s.id().equals(id)).map(s -> clip(s.displayName(), 22))
                .findFirst().orElseGet(() -> clip(id.value(), 22));
    }
    private static String item(String value) {
        return clip(value.startsWith("minecraft:") ? value.substring(10) : value, 24).replace('_', ' ');
    }
    private static String money(BigDecimal amount) { return amount.stripTrailingZeros().toPlainString(); }
    private static String clip(String text, int maximum) { return text.length() <= maximum ? text : text.substring(0, maximum - 1) + "…"; }
    private static String stage(TransportMission.Stage stage) {
        return switch (stage) {
            case PREPARING -> "Preparing"; case TO_SOURCE -> "Going to supplier"; case LOADING -> "Loading";
            case OUTBOUND -> "Delivering"; case UNLOADING -> "Unloading"; case RETURNING -> "Returning home"; case COMPLETE -> "Complete";
            case RECOVERING -> "Preparation failed: recovering cargo";
        };
    }
}
