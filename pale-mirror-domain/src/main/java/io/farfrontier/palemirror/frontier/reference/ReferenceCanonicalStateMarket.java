package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Source-shaped owner mapper for Python {@code MarketEconomy}.
 */
final class ReferenceCanonicalStateMarket {
    private ReferenceCanonicalStateMarket() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        ReferenceMarketEconomy market = required.microeconomy();
        return typed("simulation.microeconomy.MarketEconomy", "attributes", object(
                "_last_synced", inventories(market.mutableLastSynced()),
                "_next_company_id", nextId(market.mutableCompanies()),
                "_next_contract_id", nextId(market.mutableContracts()),
                "_next_credit_id", nextId(market.mutableCredits()),
                "_next_licence_id", nextId(market.mutableLicences()),
                "_next_project_id", nextId(market.mutableProjects()),
                "_next_report_id", nextId(market.mutableReports()),
                "_site_lookup", required.day() == 0 ? map(List.of()) : ReferenceCanonicalStateResourceSites.capture(required),
                "companies", companies(market.mutableCompanies()),
                "contracts", contracts(market.mutableContracts()),
                "credits", credits(market.mutableCredits()),
                "economy", economy(market.economy()),
                "history", sequence("list", market.history().stream().map(ReferenceCanonicalStateMarket::history).toList()),
                "households", households(market.mutableHouseholds()),
                "licences", licences(market.mutableLicences()),
                "projects", projects(market.mutableProjects()),
                "public_inventory", publicInventories(market, required.marketWorld().settlements().keySet()),
                "reports", reports(market.mutableReports())));
    }

    private static Map<String, Object> economy(ReferenceEconomyEngine economy) {
        ReferenceSimulationProfile profile = economy.profile();
        return typed("simulation.economy.EconomyEngine", "attributes", object("profile",
                typed("simulation.profiles.SimulationProfile", "fields", object(
                        "name", enumValue("simulation.profiles.SimulationProfileName", profile.id()),
                        "person_scale", (double) profile.personScale(),
                        "discrete_people", profile.discretePeople(),
                        "minimum_surviving_settlement", profile.minimumSurvivingSettlement()))));
    }

    private static Map<String, Object> companies(Map<Integer, ReferenceCompany> companies) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceCompany> entry : companies.entrySet()) pairs.add(pair(entry.getKey(), company(entry.getValue())));
        return map(pairs);
    }

    private static Map<String, Object> company(ReferenceCompany value) {
        return typed("simulation.microeconomy.Company", "fields", object(
                "id", value.id(), "name", value.name(), "sector", sector(value.sector()),
                "home_settlement_id", value.homeSettlementId(), "cash", value.cash(), "capacity", value.capacity(),
                "site_ids", set(value.siteIds()), "inventory", inventory(value.inventory()), "employees", value.employees(),
                "employee_ids", set(value.employeeIds()), "wage_offer", value.wageOffer(), "debt", value.debt(),
                "assets", value.assets(), "owner_kind", value.ownerKind(), "status", value.status(), "v2_state", value.v2State(),
                "receiver_until", value.receiverUntil(), "last_investment_day", value.lastInvestmentDay(),
                "last_profit", value.lastProfit(), "wage_bill", value.wageBill()));
    }

    private static Map<String, Object> households(Map<Integer, ReferenceHouseholdLedger> households) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceHouseholdLedger> entry : households.entrySet()) {
            ReferenceHouseholdLedger value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.HouseholdLedger", "fields", object(
                    "workers", value.workers(), "owners", value.owners(), "dependents", value.dependents(), "cash", value.cash(),
                    "wage_income", value.wageIncome(), "dividend_income", value.dividendIncome(), "food_coverage", value.foodCoverage()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> contracts(Map<Integer, ReferenceContract> contracts) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceContract> entry : contracts.entrySet()) {
            ReferenceContract value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.Contract", "fields", object(
                    "id", value.id(), "seller_company_id", value.sellerCompanyId(), "buyer_settlement_id", value.buyerSettlementId(),
                    "resource", resource(value.resource()), "daily_quantity", value.dailyQuantity(), "price_index", value.priceIndex(),
                    "start_day", value.startDay(), "end_day", value.endDay(), "route", sequence("tuple", value.route()), "status", value.status(),
                    "delivered", value.delivered(), "breached_quantity", value.breachedQuantity()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> credits(Map<Integer, ReferenceCreditPosition> credits) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceCreditPosition> entry : credits.entrySet()) {
            ReferenceCreditPosition value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.CreditPosition", "fields", object(
                    "id", value.id(), "borrower_kind", value.borrowerKind(), "borrower_id", value.borrowerId(),
                    "principal", value.principal(), "rate_per_day", value.ratePerDay(), "purpose", value.purpose(),
                    "issued_day", value.issuedDay(), "status", value.status()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> licences(Map<Integer, ReferenceLicence> licences) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceLicence> entry : licences.entrySet()) {
            ReferenceLicence value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.Licence", "fields", object(
                    "id", value.id(), "settlement_id", value.settlementId(), "company_id", value.companyId(), "sector", sector(value.sector()),
                    "site_id", value.siteId(), "rent_per_day", value.rentPerDay(), "issued_day", value.issuedDay(), "status", value.status()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> reports(Map<Integer, ReferenceExplorationReport> reports) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceExplorationReport> entry : reports.entrySet()) {
            ReferenceExplorationReport value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.ExplorationReport", "fields", object(
                    "id", value.id(), "company_id", value.companyId(), "settlement_id", value.settlementId(), "kind", siteKind(value.kind()),
                    "x", value.x(), "y", value.y(), "quality", value.quality(), "confidence", value.confidence(),
                    "discovered_day", value.discoveredDay(), "status", value.status()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> projects(Map<Integer, ReferenceConstructionProject> projects) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ReferenceConstructionProject> entry : projects.entrySet()) {
            ReferenceConstructionProject value = entry.getValue();
            pairs.add(pair(entry.getKey(), typed("simulation.microeconomy.ConstructionProject", "fields", object(
                    "id", value.id(), "company_id", value.companyId(), "report_id", value.reportId(),
                    "days_remaining", value.daysRemaining(), "status", value.status()))));
        }
        return map(pairs);
    }

    private static Map<String, Object> inventories(Map<Integer, ? extends Map<ReferenceResource, Double>> values) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Map.Entry<Integer, ? extends Map<ReferenceResource, Double>> entry : values.entrySet()) pairs.add(pair(entry.getKey(), inventory(entry.getValue())));
        return map(pairs);
    }

    private static int nextId(Map<Integer, ?> values) {
        return values.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private static Map<String, Object> publicInventories(ReferenceMarketEconomy market, Set<Integer> settlementIds) {
        List<List<Object>> pairs = new ArrayList<>();
        for (Integer settlementId : settlementIds) pairs.add(pair(settlementId, inventory(market.mutablePublicInventory(settlementId))));
        return map(pairs);
    }

    private static Map<String, Object> inventory(Map<ReferenceResource, Double> inventory) {
        List<List<Object>> pairs = new ArrayList<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            Double value = inventory.get(resource);
            if (value == null) throw new IllegalStateException("market inventory lacks resource " + resource);
            pairs.add(pair(resource(resource), value));
        }
        return map(pairs);
    }

    private static Map<String, Object> history(ReferenceMarketHistoryEntry value) {
        return mapping("day", value.day(), "season", value.season(), "companies", value.companies(), "contracts", value.activeContracts(),
                "credit", pythonCredit(value),
                "employment", value.employment(), "projects", value.projects());
    }

    /** {@code round(sum(...), 2)} retains Python's integer zero for an empty generator. */
    private static Number pythonCredit(ReferenceMarketHistoryEntry value) {
        if (value.creditWasEmptySum()) return Integer.valueOf(0);
        return Double.valueOf(value.credit());
    }

    private static Map<String, Object> sector(ReferenceCompanySector value) { return enumValue("simulation.microeconomy.CompanySector", value.name().toLowerCase(java.util.Locale.ROOT)); }
    private static Map<String, Object> resource(ReferenceResource value) { return enumValue("simulation.economy.Resource", value.name().toLowerCase(java.util.Locale.ROOT)); }
    private static Map<String, Object> siteKind(ReferenceSiteKind value) { return enumValue("simulation.sites.SiteKind", value.name().toLowerCase(java.util.Locale.ROOT)); }
    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> values) { return object("$type", type, fieldName, values); }
    private static Map<String, Object> sequence(String kind, List<?> items) {
        return object("$sequence", kind, "items", Collections.unmodifiableList(new ArrayList<>(items)));
    }

    private static Map<String, Object> set(Set<?> values) {
        List<Object> items = new ArrayList<>(values);
        items.sort(Comparator.comparing(ReferenceV2PublicSnapshot::canonicalJson));
        return sequence("set", items);
    }

    private static Map<String, Object> mapping(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("mapping entries must be pairs");
        List<List<Object>> pairs = new ArrayList<>();
        for (int index = 0; index < entries.length; index += 2) pairs.add(pair(entries[index], entries[index + 1]));
        return map(pairs);
    }

    private static Map<String, Object> map(List<List<Object>> pairs) {
        List<List<Object>> ordered = new ArrayList<>(pairs);
        ordered.sort(Comparator.comparing(pair -> ReferenceV2PublicSnapshot.canonicalJson(pair.getFirst())));
        return object("$map", List.copyOf(ordered));
    }

    private static List<Object> pair(Object key, Object value) {
        ArrayList<Object> result = new ArrayList<>(2);
        result.add(key);
        result.add(value);
        return Collections.unmodifiableList(result);
    }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
