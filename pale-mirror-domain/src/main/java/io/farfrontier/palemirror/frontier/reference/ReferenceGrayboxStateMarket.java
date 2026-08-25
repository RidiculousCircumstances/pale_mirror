package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict all-or-nothing reader for MarketEconomy's private economic ledgers. */
final class ReferenceGrayboxStateMarket {
    private ReferenceGrayboxStateMarket() { }

    static State read(Object encoded, ReferenceSimulationProfile profile, Map<Integer, ReferenceSettlement> settlements,
                      Map<Integer, ReferenceResourceSite> sites, Object siteLookup, int day) {
        Map<String, Object> attributes = ReferenceGrayboxStateReader.typed(encoded, "simulation.microeconomy.MarketEconomy", "attributes");
        ReferenceGrayboxStateReader.exactKeys(attributes, "market attributes", "_last_synced", "_next_company_id", "_next_contract_id", "_next_credit_id",
                "_next_licence_id", "_next_project_id", "_next_report_id", "_site_lookup", "companies", "contracts", "credits", "economy", "history",
                "households", "licences", "projects", "public_inventory", "reports");
        requireEconomy(attributes.get("economy"), profile);
        if (day == 0) {
            if (!ReferenceGrayboxStateReader.mapEntries(attributes.get("_site_lookup"), "market initial site lookup").isEmpty()) {
                throw new IllegalArgumentException("initial market site lookup must be empty");
            }
        } else if (!ReferenceV2PublicSnapshot.canonicalJson(siteLookup).equals(ReferenceV2PublicSnapshot.canonicalJson(attributes.get("_site_lookup")))) {
            throw new IllegalArgumentException("market site lookup differs from resource-site owner");
        }
        LinkedHashMap<Integer, ReferenceCompany> companies = companies(attributes.get("companies"), settlements, sites);
        LinkedHashMap<Integer, ReferenceHouseholdLedger> households = households(attributes.get("households"), settlements);
        LinkedHashMap<Integer, ReferenceContract> contracts = contracts(attributes.get("contracts"), companies, settlements);
        LinkedHashMap<Integer, ReferenceCreditPosition> credits = credits(attributes.get("credits"), companies, settlements);
        LinkedHashMap<Integer, ReferenceLicence> licences = licences(attributes.get("licences"), companies, settlements, sites);
        LinkedHashMap<Integer, ReferenceExplorationReport> reports = reports(attributes.get("reports"), companies, settlements);
        LinkedHashMap<Integer, ReferenceConstructionProject> projects = projects(attributes.get("projects"), companies, reports);
        LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> publicInventory = inventories(attributes.get("public_inventory"), "market public inventory", settlements);
        LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> lastSynced = inventories(attributes.get("_last_synced"), "market last synced", settlements);
        return new State(companies, households, contracts, credits, licences, reports, projects, publicInventory, lastSynced, history(attributes.get("history")),
                next(attributes.get("_next_company_id"), companies, "company"), next(attributes.get("_next_licence_id"), licences, "licence"),
                next(attributes.get("_next_contract_id"), contracts, "contract"), next(attributes.get("_next_credit_id"), credits, "credit"),
                next(attributes.get("_next_report_id"), reports, "report"), next(attributes.get("_next_project_id"), projects, "project"));
    }

    private static void requireEconomy(Object encoded, ReferenceSimulationProfile profile) {
        Map<String, Object> attributes = ReferenceGrayboxStateReader.typed(encoded, "simulation.economy.EconomyEngine", "attributes");
        ReferenceGrayboxStateReader.exactKeys(attributes, "market economy", "profile");
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(attributes.get("profile"), "simulation.profiles.SimulationProfile", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "market economy profile", "name", "person_scale", "discrete_people", "minimum_surviving_settlement");
        String id = ReferenceGrayboxStateReader.enumValue(fields.get("name"), "simulation.profiles.SimulationProfileName", "market profile name");
        if (!profile.id().equals(id) || profile.personScale() != number(fields.get("person_scale"), "market person scale")
                || profile.discretePeople() != ReferenceGrayboxStateReader.bool(fields.get("discrete_people"), "market discrete people")
                || profile.minimumSurvivingSettlement() != ReferenceGrayboxStateReader.integer(fields.get("minimum_surviving_settlement"), "market survival minimum")) {
            throw new IllegalArgumentException("market economy profile differs from world profile");
        }
    }

    private static LinkedHashMap<Integer, ReferenceCompany> companies(Object encoded, Map<Integer, ReferenceSettlement> settlements,
                                                                         Map<Integer, ReferenceResourceSite> sites) {
        LinkedHashMap<Integer, ReferenceCompany> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market companies")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "company key");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.Company", "company", "id", "name", "sector",
                    "home_settlement_id", "cash", "capacity", "site_ids", "inventory", "employees", "employee_ids", "wage_offer",
                    "debt", "assets", "owner_kind", "status", "v2_state", "receiver_until", "last_investment_day", "last_profit", "wage_bill");
            int home = ReferenceGrayboxStateReader.integer(f.get("home_settlement_id"), "company home");
            ReferenceSettlement settlement = settlements.get(home);
            if (settlement == null) throw new IllegalArgumentException("company home is unknown");
            ReferenceCompany company = new ReferenceCompany(ReferenceGrayboxStateReader.integer(f.get("id"), "company id"), ReferenceGrayboxStateReader.string(f.get("name"), "company name"),
                    sector(f.get("sector"), "company sector"), home, number(f.get("cash"), "company cash"), number(f.get("capacity"), "company capacity"));
            if (company.id() != id || result.putIfAbsent(id, company) != null) throw new IllegalArgumentException("duplicate company");
            Set<Integer> siteIds = integers(f.get("site_ids"), "set", "company sites");
            for (int siteId : siteIds) if (!sites.containsKey(siteId)) throw new IllegalArgumentException("company site is unknown");
            company.siteIds().addAll(siteIds); companyInventory(f.get("inventory"), "company inventory").forEach(company::add);
            Set<String> employees = strings(f.get("employee_ids"), "set", "company employees");
            for (String residentId : employees) if (settlement.residents() == null || settlement.residents().resident(residentId) == null) throw new IllegalArgumentException("company employee is unknown");
            company.employeeIds().addAll(employees); company.employees(number(f.get("employees"), "company employees count"));
            if (company.employees() != company.employeeIds().size()) throw new IllegalArgumentException("company employee count differs from identity set");
            company.wageOffer(number(f.get("wage_offer"), "company wage")); company.debt(number(f.get("debt"), "company debt")); company.assets(number(f.get("assets"), "company assets"));
            company.ownerKind(ReferenceGrayboxStateReader.string(f.get("owner_kind"), "company owner kind")); company.status(ReferenceGrayboxStateReader.string(f.get("status"), "company status"));
            company.v2State(ReferenceGrayboxStateReader.string(f.get("v2_state"), "company V2 state")); company.receiverUntil(ReferenceGrayboxStateReader.integer(f.get("receiver_until"), "company receiver"));
            company.lastInvestmentDay(ReferenceGrayboxStateReader.integer(f.get("last_investment_day"), "company investment day"));
            company.lastProfit(number(f.get("last_profit"), "company profit")); company.wageBill(number(f.get("wage_bill"), "company wage bill"));
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceHouseholdLedger> households(Object encoded, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, ReferenceHouseholdLedger> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market households")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "household key"); if (!settlements.containsKey(id)) throw new IllegalArgumentException("household settlement is unknown");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.HouseholdLedger", "household", "workers", "owners", "dependents", "cash", "wage_income", "dividend_income", "food_coverage");
            ReferenceHouseholdLedger household = new ReferenceHouseholdLedger(number(f.get("workers"), "household workers"),
                    number(f.get("owners"), "household owners"), number(f.get("dependents"), "household dependents"), number(f.get("cash"), "household cash"));
            household.wageIncome(number(f.get("wage_income"), "household wages")); household.dividendIncome(number(f.get("dividend_income"), "household dividends")); household.foodCoverage(number(f.get("food_coverage"), "household food"));
            if (result.putIfAbsent(id, household) != null) throw new IllegalArgumentException("duplicate household");
        }
        if (!result.keySet().equals(settlements.keySet())) throw new IllegalArgumentException("market households do not cover settlements"); return result;
    }

    private static LinkedHashMap<Integer, ReferenceContract> contracts(Object encoded, Map<Integer, ReferenceCompany> companies, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, ReferenceContract> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market contracts")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "contract key");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.Contract", "contract", "id", "seller_company_id",
                    "buyer_settlement_id", "resource", "daily_quantity", "price_index", "start_day", "end_day", "route", "status", "delivered", "breached_quantity");
            int seller = ReferenceGrayboxStateReader.integer(f.get("seller_company_id"), "contract seller"), buyer = ReferenceGrayboxStateReader.integer(f.get("buyer_settlement_id"), "contract buyer");
            if (!companies.containsKey(seller) || !settlements.containsKey(buyer)) throw new IllegalArgumentException("contract owner is unknown");
            ReferenceContract contract = new ReferenceContract(ReferenceGrayboxStateReader.integer(f.get("id"), "contract id"), seller, buyer,
                    resource(f.get("resource"), "contract resource"), number(f.get("daily_quantity"), "contract quantity"), number(f.get("price_index"), "contract price"),
                    ReferenceGrayboxStateReader.integer(f.get("start_day"), "contract start"), ReferenceGrayboxStateReader.integer(f.get("end_day"), "contract end"), integerList(f.get("route"), "tuple", "contract route"));
            contract.status(ReferenceGrayboxStateReader.string(f.get("status"), "contract status"));
            contract.delivered(number(f.get("delivered"), "contract delivered")); contract.breachedQuantity(number(f.get("breached_quantity"), "contract breached"));
            if (contract.id() != id || result.putIfAbsent(id, contract) != null) throw new IllegalArgumentException("duplicate contract");
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceCreditPosition> credits(Object encoded, Map<Integer, ReferenceCompany> companies, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, ReferenceCreditPosition> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market credits")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "credit key");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.CreditPosition", "credit", "id", "borrower_kind", "borrower_id", "principal", "rate_per_day", "purpose", "issued_day", "status");
            String kind = ReferenceGrayboxStateReader.string(f.get("borrower_kind"), "credit borrower kind"); int borrower = ReferenceGrayboxStateReader.integer(f.get("borrower_id"), "credit borrower");
            if (!(kind.equals("company") && companies.containsKey(borrower)) && !(kind.equals("settlement") && settlements.containsKey(borrower))) throw new IllegalArgumentException("credit borrower is unknown");
            ReferenceCreditPosition credit = new ReferenceCreditPosition(ReferenceGrayboxStateReader.integer(f.get("id"), "credit id"), kind, borrower,
                    number(f.get("principal"), "credit principal"), number(f.get("rate_per_day"), "credit rate"),
                    ReferenceGrayboxStateReader.string(f.get("purpose"), "credit purpose"), ReferenceGrayboxStateReader.integer(f.get("issued_day"), "credit issued"));
            credit.status(ReferenceGrayboxStateReader.string(f.get("status"), "credit status")); if (credit.id() != id || result.putIfAbsent(id, credit) != null) throw new IllegalArgumentException("duplicate credit");
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceLicence> licences(Object encoded, Map<Integer, ReferenceCompany> companies, Map<Integer, ReferenceSettlement> settlements, Map<Integer, ReferenceResourceSite> sites) {
        LinkedHashMap<Integer, ReferenceLicence> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market licences")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "licence key");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.Licence", "licence", "id", "settlement_id", "company_id", "sector", "site_id", "rent_per_day", "issued_day", "status");
            int settlement = ReferenceGrayboxStateReader.integer(f.get("settlement_id"), "licence settlement");
            int company = ReferenceGrayboxStateReader.integer(f.get("company_id"), "licence company"); Integer site = ReferenceGrayboxStateReader.nullableInteger(f.get("site_id"), "licence site");
            if (!settlements.containsKey(settlement) || !companies.containsKey(company) || (site != null && !sites.containsKey(site))) throw new IllegalArgumentException("licence reference is unknown");
            ReferenceLicence licence = new ReferenceLicence(ReferenceGrayboxStateReader.integer(f.get("id"), "licence id"), settlement, company,
                    sector(f.get("sector"), "licence sector"), site, number(f.get("rent_per_day"), "licence rent"), ReferenceGrayboxStateReader.integer(f.get("issued_day"), "licence issued"));
            licence.status(ReferenceGrayboxStateReader.string(f.get("status"), "licence status")); if (licence.id() != id || result.putIfAbsent(id, licence) != null) throw new IllegalArgumentException("duplicate licence");
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceExplorationReport> reports(Object encoded, Map<Integer, ReferenceCompany> companies, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, ReferenceExplorationReport> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market reports")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "report key");
            Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.ExplorationReport", "report", "id", "company_id", "settlement_id", "kind", "x", "y", "quality", "confidence", "discovered_day", "status");
            int company = ReferenceGrayboxStateReader.integer(f.get("company_id"), "report company"); int settlement = ReferenceGrayboxStateReader.integer(f.get("settlement_id"), "report settlement");
            if (!companies.containsKey(company) || !settlements.containsKey(settlement)) throw new IllegalArgumentException("report owner is unknown");
            ReferenceExplorationReport report = new ReferenceExplorationReport(ReferenceGrayboxStateReader.integer(f.get("id"), "report id"), company, settlement,
                    siteKind(f.get("kind"), "report kind"), ReferenceGrayboxStateReader.integer(f.get("x"), "report x"), ReferenceGrayboxStateReader.integer(f.get("y"), "report y"),
                    number(f.get("quality"), "report quality"), number(f.get("confidence"), "report confidence"),
                    ReferenceGrayboxStateReader.integer(f.get("discovered_day"), "report day"), ReferenceGrayboxStateReader.string(f.get("status"), "report status"));
            if (report.id() != id || result.putIfAbsent(id, report) != null) throw new IllegalArgumentException("duplicate report");
        }
        return result;
    }

    private static LinkedHashMap<Integer, ReferenceConstructionProject> projects(Object encoded, Map<Integer, ReferenceCompany> companies, Map<Integer, ReferenceExplorationReport> reports) {
        LinkedHashMap<Integer, ReferenceConstructionProject> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "market projects")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "project key"); Map<String, Object> f = typed(entry.value(), "simulation.microeconomy.ConstructionProject", "project", "id", "company_id", "report_id", "days_remaining", "status");
            int company = ReferenceGrayboxStateReader.integer(f.get("company_id"), "project company"); int report = ReferenceGrayboxStateReader.integer(f.get("report_id"), "project report");
            if (!companies.containsKey(company) || !reports.containsKey(report)) throw new IllegalArgumentException("project owner is unknown");
            ReferenceConstructionProject project = new ReferenceConstructionProject(ReferenceGrayboxStateReader.integer(f.get("id"), "project id"), company, report,
                    ReferenceGrayboxStateReader.integer(f.get("days_remaining"), "project days")); project.status(ReferenceGrayboxStateReader.string(f.get("status"), "project status"));
            if (project.id() != id || result.putIfAbsent(id, project) != null) throw new IllegalArgumentException("duplicate project");
        }
        return result;
    }

    private static LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> inventories(Object encoded, String label, Map<Integer, ReferenceSettlement> settlements) {
        LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), label + " settlement"); if (!settlements.containsKey(settlement)) throw new IllegalArgumentException(label + " settlement is unknown");
            if (result.putIfAbsent(settlement, companyInventory(entry.value(), label)) != null) throw new IllegalArgumentException("duplicate " + label + " settlement");
        }
        if (!result.keySet().equals(settlements.keySet())) throw new IllegalArgumentException(label + " does not cover settlements"); return result;
    }

    private static List<ReferenceMarketHistoryEntry> history(Object encoded) {
        List<ReferenceMarketHistoryEntry> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "market history")) {
            Map<String, Object> f = ReferenceGrayboxStateReader.stringMap(item, "market history row"); ReferenceGrayboxStateReader.exactKeys(f, "market history row", "day", "season", "companies", "contracts", "credit", "employment", "projects");
            Object credit = f.get("credit"); boolean empty = credit instanceof Integer integer && integer == 0;
            result.add(new ReferenceMarketHistoryEntry(ReferenceGrayboxStateReader.integer(f.get("day"), "market history day"),
                    ReferenceGrayboxStateReader.string(f.get("season"), "market season"), ReferenceGrayboxStateReader.integer(f.get("companies"), "market history companies"),
                    ReferenceGrayboxStateReader.integer(f.get("contracts"), "market history contracts"), empty ? 0.0d : number(credit, "market history credit"), empty,
                    number(f.get("employment"), "market history employment"), ReferenceGrayboxStateReader.integer(f.get("projects"), "market history projects")));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> typed(Object encoded, String type, String label, String... keys) {
        Map<String, Object> f = ReferenceGrayboxStateReader.typed(encoded, type, "fields"); ReferenceGrayboxStateReader.exactKeys(f, label, keys); return f;
    }
    private static int next(Object encoded, Map<Integer, ?> values, String label) {
        int value = ReferenceGrayboxStateReader.integer(encoded, "next " + label + " id");
        if (value != values.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1) throw new IllegalArgumentException("next " + label + " id is stale"); return value;
    }
    private static double number(Object value, String label) { return ReferenceGrayboxStateReader.number(value, label); }
    private static ReferenceCompanySector sector(Object value, String label) { return ReferenceCompanySector.valueOf(ReferenceGrayboxStateReader.enumValue(value, "simulation.microeconomy.CompanySector", label).toUpperCase(Locale.ROOT)); }
    private static ReferenceResource resource(Object value, String label) { return ReferenceResource.valueOf(ReferenceGrayboxStateReader.enumValue(value, "simulation.economy.Resource", label).toUpperCase(Locale.ROOT)); }
    private static ReferenceSiteKind siteKind(Object value, String label) { return ReferenceSiteKind.valueOf(ReferenceGrayboxStateReader.enumValue(value, "simulation.sites.SiteKind", label).toUpperCase(Locale.ROOT)); }
    private static EnumMap<ReferenceResource, Double> companyInventory(Object encoded, String label) {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            ReferenceResource resource = resource(entry.key(), label + " resource"); double value = number(entry.value(), label + " amount");
            if (value < 0.0d || result.putIfAbsent(resource, value) != null) throw new IllegalArgumentException(label + " is invalid");
        }
        if (result.size() != ReferenceResource.values().length) throw new IllegalArgumentException(label + " lacks a resource"); return result;
    }
    private static Set<Integer> integers(Object encoded, String kind, String label) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, kind, label)) if (!result.add(ReferenceGrayboxStateReader.integer(value, label + " item"))) throw new IllegalArgumentException(label + " has duplicate"); return result;
    }
    private static Set<String> strings(Object encoded, String kind, String label) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : ReferenceGrayboxStateReader.sequence(encoded, kind, label)) if (!result.add(ReferenceGrayboxStateReader.string(value, label + " item"))) throw new IllegalArgumentException(label + " has duplicate"); return result;
    }
    private static List<Integer> integerList(Object encoded, String kind, String label) {
        List<Integer> result = new ArrayList<>(); for (Object value : ReferenceGrayboxStateReader.sequence(encoded, kind, label)) result.add(ReferenceGrayboxStateReader.integer(value, label + " item")); return List.copyOf(result);
    }

    record State(LinkedHashMap<Integer, ReferenceCompany> companies,
                 LinkedHashMap<Integer, ReferenceHouseholdLedger> households,
                 LinkedHashMap<Integer, ReferenceContract> contracts,
                 LinkedHashMap<Integer, ReferenceCreditPosition> credits,
                 LinkedHashMap<Integer, ReferenceLicence> licences,
                 LinkedHashMap<Integer, ReferenceExplorationReport> reports,
                 LinkedHashMap<Integer, ReferenceConstructionProject> projects,
                 LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> publicInventory,
                 LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> lastSynced, List<ReferenceMarketHistoryEntry> history,
                 int nextCompany, int nextLicence, int nextContract, int nextCredit, int nextReport, int nextProject) {
        void applyTo(ReferenceMarketEconomy market, Set<Integer> settlementIds) {
            market.mutableCompanies().clear(); market.mutableCompanies().putAll(companies); market.mutableHouseholds().clear(); market.mutableHouseholds().putAll(households);
            market.mutableContracts().clear(); market.mutableContracts().putAll(contracts); market.mutableCredits().clear(); market.mutableCredits().putAll(credits);
            market.mutableLicences().clear(); market.mutableLicences().putAll(licences); market.mutableReports().clear(); market.mutableReports().putAll(reports);
            market.mutableProjects().clear(); market.mutableProjects().putAll(projects); market.mutableLastSynced().clear(); market.mutableLastSynced().putAll(lastSynced);
            for (Integer settlementId : settlementIds) { EnumMap<ReferenceResource, Double> target = market.mutablePublicInventory(settlementId); target.clear(); target.putAll(publicInventory.get(settlementId)); }
            market.restoreCounters(nextCompany, nextLicence, nextContract, nextCredit, nextReport, nextProject); market.restoreHistory(history);
        }
    }
}
