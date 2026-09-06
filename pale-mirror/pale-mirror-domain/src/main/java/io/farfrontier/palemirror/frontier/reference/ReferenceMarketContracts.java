package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Source-order company contracts and spot-market clearing. */
final class ReferenceMarketContracts {
    private static final int CONTRACT_REVIEW_DAYS = 12;
    private static final int CONTRACT_DURATION_DAYS = 30;
    private static final double CONTRACT_SUPPLY_SHARE = 0.38d;
    private static final double CONTRACT_PRICE_FLOOR = 0.75d;
    private static final double CONTRACT_PRICE_CEILING = 1.25d;
    private static final double SPOT_LOT_FRACTION = 0.16d;
    private static final int MAXIMUM_MATCHES_PER_RESOURCE = 160;
    private static final double ESSENTIAL_PRICE_CAP_MULTIPLIER = 4.0d;
    private static final Set<ReferenceResource> ESSENTIAL = EnumSet.of(
            ReferenceResource.FOOD, ReferenceResource.MEDICINE, ReferenceResource.ENERGY);

    private final ReferenceMarketEconomy market;
    private final ReferenceEconomyEngine economy;

    ReferenceMarketContracts(ReferenceMarketEconomy market) {
        this.market = market;
        economy = market.economy();
    }

    List<ReferenceTradeRecord> clear(ReferenceMarketWorld world, ReferenceMarketFinance finance) {
        Map<ReferenceRouteKey, Double> remaining = new LinkedHashMap<>();
        for (ReferenceRoute route : world.trade().routes()) remaining.put(route.key(), route.capacityOn(world.day()));
        expire(world.day());
        if (world.day() % CONTRACT_REVIEW_DAYS == 1) create(world);
        List<ReferenceTradeRecord> records = execute(world, remaining, finance);
        records.addAll(clearSpot(world, remaining, finance));
        return List.copyOf(records);
    }

    private void expire(int day) {
        for (ReferenceContract contract : market.mutableContracts().values()) {
            if (contract.status().equals("active") && day > contract.endDay()) contract.status("complete");
        }
    }

    private void create(ReferenceMarketWorld world) {
        Set<ContractKey> existing = new HashSet<>();
        for (ReferenceContract contract : market.mutableContracts().values()) {
            if (contract.status().equals("active")) {
                existing.add(new ContractKey(contract.sellerCompanyId(), contract.buyerSettlementId(), contract.resource()));
            }
        }
        List<ReferenceCompany> ordered = market.mutableCompanies().values().stream()
                .sorted(Comparator.comparingInt(ReferenceCompany::id)).toList();
        for (ReferenceCompany company : ordered) {
            ReferenceResource resource = company.output();
            double supply = Math.max(0.0d, company.amount(resource) - reserve(world, company, resource));
            if (supply <= market.minimumLot()) continue;
            ReferenceSettlement seller = world.settlements().get(company.homeSettlementId());
            List<ReferenceSettlement> buyers = world.settlements().values().stream()
                    .filter(buyer -> buyer.alive() && buyer.id() != seller.id())
                    .sorted(Comparator.comparingDouble((ReferenceSettlement buyer) -> economy.desiredImport(buyer, resource)).reversed()
                            .thenComparingInt(ReferenceSettlement::id)).toList();
            for (ReferenceSettlement buyer : buyers) {
                ContractKey key = new ContractKey(company.id(), buyer.id(), resource);
                if (existing.contains(key)) continue;
                ReferenceTradePath path = world.trade().shortestPath(seller.id(), buyer.id(), resource, world.settlements(), world.day());
                if (path == null) continue;
                double quantity = Math.min(supply * CONTRACT_SUPPLY_SHARE, economy.desiredImport(buyer, resource) / 3.0d);
                if (quantity <= market.minimumLot()) continue;
                double reference = economy.localValue(buyer, resource);
                double price = Math.max(reference * CONTRACT_PRICE_FLOOR,
                        Math.min(reference * CONTRACT_PRICE_CEILING, economy.localValue(seller, resource)));
                if (ESSENTIAL.contains(resource)) price = Math.min(price, economy.resourceRule(resource).referenceValue() * ESSENTIAL_PRICE_CAP_MULTIPLIER);
                int id = market.nextContractId();
                market.mutableContracts().put(id, new ReferenceContract(id, company.id(), buyer.id(), resource, quantity, price,
                        world.day(), world.day() + CONTRACT_DURATION_DAYS, path.nodes()));
                existing.add(key);
                supply -= quantity;
                break;
            }
        }
    }

    private List<ReferenceTradeRecord> execute(ReferenceMarketWorld world, Map<ReferenceRouteKey, Double> remaining,
                                                ReferenceMarketFinance finance) {
        List<ReferenceTradeRecord> records = new ArrayList<>();
        for (ReferenceContract contract : market.mutableContracts().values().stream()
                .sorted(Comparator.comparingInt(ReferenceContract::id)).toList()) {
            if (!contract.status().equals("active")) continue;
            ReferenceCompany seller = market.mutableCompanies().get(contract.sellerCompanyId());
            ReferenceSettlement buyer = world.settlements().get(contract.buyerSettlementId());
            if (seller == null || buyer == null || !buyer.alive()) {
                contract.status("breached");
                continue;
            }
            ReferenceTradePath path = world.trade().shortestPath(seller.homeSettlementId(), buyer.id(), contract.resource(),
                    world.settlements(), world.day());
            if (path == null) {
                contract.breachedQuantity(contract.breachedQuantity() + contract.dailyQuantity());
                continue;
            }
            double amount = Math.min(contract.dailyQuantity(), Math.min(Math.max(0.0d, seller.amount(contract.resource())
                    - reserve(world, seller, contract.resource())), capacity(path.edges(), remaining)));
            amount = Math.min(amount, economy.desiredImport(buyer, contract.resource()));
            if (amount <= market.minimumLot()) continue;
            ReferenceTradeRecord record = transfer(world, seller, buyer, contract.resource(), amount, contract.priceIndex(),
                    path.nodes(), path.edges(), remaining, "contract", finance);
            if (record == null) contract.breachedQuantity(contract.breachedQuantity() + contract.dailyQuantity());
            else {
                contract.delivered(contract.delivered() + record.delivered());
                records.add(record);
            }
        }
        return records;
    }

    private List<ReferenceTradeRecord> clearSpot(ReferenceMarketWorld world, Map<ReferenceRouteKey, Double> remaining,
                                                  ReferenceMarketFinance finance) {
        List<ReferenceTradeRecord> records = new ArrayList<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            List<ReferenceSettlement> buyers = world.settlements().values().stream()
                    .filter(buyer -> buyer.alive() && economy.desiredImport(buyer, resource) > market.minimumLot())
                    .sorted(Comparator.comparingInt((ReferenceSettlement buyer) -> ESSENTIAL.contains(resource) ? 0 : 1)
                            .thenComparing(Comparator.comparingDouble((ReferenceSettlement buyer) -> economy.desiredImport(buyer, resource))
                                    .reversed()).thenComparingInt(ReferenceSettlement::id)).toList();
            List<ReferenceCompany> sellers = market.mutableCompanies().values().stream()
                    .filter(company -> company.status().equals("operating") && company.amount(resource) > reserve(world, company, resource))
                    .toList();
            if (buyers.isEmpty() || sellers.isEmpty()) continue;
            for (int pass = 0; pass < MAXIMUM_MATCHES_PER_RESOURCE; pass++) {
                boolean progressed = false;
                for (ReferenceSettlement buyer : buyers) {
                    double need = economy.desiredImport(buyer, resource);
                    if (need <= market.minimumLot()) continue;
                    Offer offer = bestOffer(world, resource, buyer, sellers, remaining);
                    if (offer == null) continue;
                    ReferenceSettlement home = world.settlements().get(offer.seller().homeSettlementId());
                    double price = Math.max(0.01d, (economy.localValue(home, resource) + economy.localValue(buyer, resource)) * 0.5d);
                    if (ESSENTIAL.contains(resource)) price = Math.min(price, economy.resourceRule(resource).referenceValue() * ESSENTIAL_PRICE_CAP_MULTIPLIER);
                    double amount = Math.min(Math.max(market.minimumLot(), need * SPOT_LOT_FRACTION), need);
                    amount = Math.min(amount, offer.seller().amount(resource) - reserve(world, offer.seller(), resource));
                    amount = Math.min(amount, capacity(offer.path().edges(), remaining));
                    ReferenceTradeRecord record = transfer(world, offer.seller(), buyer, resource, amount, price, offer.path().nodes(),
                            offer.path().edges(), remaining, "spot", finance);
                    if (record != null) {
                        records.add(record);
                        progressed = true;
                    }
                }
                if (!progressed) break;
            }
        }
        return records;
    }

    private Offer bestOffer(ReferenceMarketWorld world, ReferenceResource resource, ReferenceSettlement buyer,
                            List<ReferenceCompany> sellers, Map<ReferenceRouteKey, Double> remaining) {
        Offer best = null;
        for (ReferenceCompany seller : sellers) {
            ReferenceTradePath path = seller.homeSettlementId() == buyer.id() ? new ReferenceTradePath(0.0d, List.of(buyer.id()), List.of())
                    : world.trade().shortestPath(seller.homeSettlementId(), buyer.id(), resource, world.settlements(), world.day());
            if (path == null || Math.min(capacity(path.edges(), remaining), seller.amount(resource) - reserve(world, seller, resource))
                    <= market.minimumLot()) continue;
            double score = economy.localValue(world.settlements().get(seller.homeSettlementId()), resource) + path.logisticsCost();
            if (best == null || score < best.score() || (score == best.score() && seller.id() < best.seller().id())) {
                best = new Offer(score, seller, path);
            }
        }
        return best;
    }

    private double reserve(ReferenceMarketWorld world, ReferenceCompany company, ReferenceResource resource) {
        if (company.sector() == ReferenceCompanySector.AGRICULTURE && resource == ReferenceResource.SEEDS) {
            double capacity = company.siteIds().stream().map(world.resourceSites()::get).filter(java.util.Objects::nonNull)
                    .mapToDouble(ReferenceResourceSite::capacity).sum();
            if (capacity <= 0.0d) capacity = Math.max(1.0d, company.siteIds().size());
            return capacity * 1.8d * 30.0d;
        }
        return company.output() == resource ? Math.max(0.0d, company.capacity() * 8.0d * 0.12d) : 0.0d;
    }

    private ReferenceTradeRecord transfer(ReferenceMarketWorld world, ReferenceCompany seller, ReferenceSettlement buyer,
                                          ReferenceResource resource, double amount, double price, List<Integer> nodes,
                                          List<ReferenceRoute> edges, Map<ReferenceRouteKey, Double> remaining, String purpose,
                                          ReferenceMarketFinance finance) {
        if (amount <= market.minimumLot()) return null;
        double payment = amount * price;
        if (buyer.cash() + 1.0e-9d < payment && ESSENTIAL.contains(resource)) {
            finance.borrowSettlement(world, buyer.id(), payment - buyer.cash(), purpose);
        }
        if (buyer.cash() + 1.0e-9d < payment) return null;
        seller.remove(resource, amount);
        buyer.cash(buyer.cash() - payment);
        seller.cash(seller.cash() + payment);
        market.mutablePublicInventory(buyer.id()).merge(resource, amount, Double::sum);
        buyer.add(resource, amount);
        for (ReferenceRoute edge : edges) remaining.put(edge.key(), remaining.get(edge.key()) - amount);
        return new ReferenceTradeRecord(world.day(), seller.homeSettlementId(), buyer.id(), resource, amount, amount, price, nodes);
    }

    private static double capacity(List<ReferenceRoute> edges, Map<ReferenceRouteKey, Double> remaining) {
        return edges.stream().mapToDouble(edge -> remaining.get(edge.key())).min().orElse(Double.POSITIVE_INFINITY);
    }

    private record ContractKey(int seller, int buyer, ReferenceResource resource) { }
    private record Offer(double score, ReferenceCompany seller, ReferenceTradePath path) { }
}
