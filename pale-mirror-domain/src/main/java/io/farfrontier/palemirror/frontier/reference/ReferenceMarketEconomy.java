package io.farfrontier.palemirror.frontier.reference;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Source port of Python {@code MarketEconomy}; it owns company custody and
 * delegates market, finance and expansion phases to narrow domain helpers.
 */
public final class ReferenceMarketEconomy {
    private static final double WORKERS_SHARE = 0.55d;
    private static final double OWNERS_SHARE = 0.05d;
    private static final double DEPENDENTS_SHARE = 0.40d;
    private static final double INITIAL_CASH_SHARE = 0.58d;
    private static final double MUNICIPAL_CASH_SHARE = 0.20d;
    private static final double INITIAL_INVENTORY_SHARE = 0.82d;
    private static final double SITE_ASSET_PER_CAPACITY = 70.0d;
    private static final double FACILITY_ASSET_PER_CAPACITY = 115.0d;
    private static final double LICENCE_RENT_PER_CAPACITY = 0.11d;
    private static final double MINIMUM_WORKERS = 3.0d;
    private static final double WORKERS_PER_CAPACITY = 42.0d;
    private static final double SEASON_SPRING_FARM_YIELD = 0.62d;
    private static final double SEASON_SPRING_SEEDS_PER_CAPACITY = 1.8d;
    private static final double SEASON_SUMMER_FARM_YIELD = 1.08d;
    private static final double SEASON_AUTUMN_FARM_YIELD = 1.72d;
    private static final double SEASON_AUTUMN_SEED_OUTPUT_FRACTION = 0.08d;
    private static final double SEASON_WINTER_FARM_YIELD = 0.24d;
    private static final double ILLNESS_PRODUCTIVITY_PENALTY = 0.25d;
    private static final double HAUL_LOSS_PER_INFECTION = 0.16d;

    private final ReferenceEconomyEngine economy;
    private final LinkedHashMap<Integer, ReferenceCompany> companies = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceHouseholdLedger> households = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceContract> contracts = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceCreditPosition> credits = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceLicence> licences = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceExplorationReport> reports = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceConstructionProject> projects = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> publicInventory = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> lastSynced = new LinkedHashMap<>();
    private final List<ReferenceMarketHistoryEntry> history = new ArrayList<>();
    private int nextCompanyId = 1;
    private int nextLicenceId = 1;
    private int nextContractId = 1;
    private int nextCreditId = 1;
    private int nextReportId = 1;
    private int nextProjectId = 1;

    public ReferenceMarketEconomy(ReferenceEconomyEngine economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    public Map<Integer, ReferenceCompany> companies() { return Map.copyOf(companies); }
    public Map<Integer, ReferenceHouseholdLedger> households() { return Map.copyOf(households); }
    public Map<Integer, ReferenceContract> contracts() { return Map.copyOf(contracts); }
    public Map<Integer, ReferenceCreditPosition> credits() { return Map.copyOf(credits); }
    public Map<Integer, ReferenceLicence> licences() { return Map.copyOf(licences); }
    public Map<Integer, ReferenceExplorationReport> reports() { return Map.copyOf(reports); }
    public Map<Integer, ReferenceConstructionProject> projects() { return Map.copyOf(projects); }
    public Map<ReferenceResource, Double> publicInventory(int settlementId) { return Map.copyOf(publicInventory.get(settlementId)); }
    public List<ReferenceMarketHistoryEntry> history() { return List.copyOf(history); }

    public Map<String, Object> summary() {
        return Map.of("season", history.isEmpty() ? "spring" : history.getLast().season(), "companies", companies.size(),
                "active_contracts", (int) contracts.values().stream().filter(contract -> contract.status().equals("active")).count(),
                "credit", round2(credits.values().stream().filter(credit -> credit.status().equals("performing"))
                        .mapToDouble(ReferenceCreditPosition::principal).sum()),
                "construction", (int) projects.values().stream().filter(project -> project.status().equals("building")).count());
    }

    public double minimumWorkers() {
        return economy.profile().discretePeople() ? 1.0d : MINIMUM_WORKERS;
    }

    public double minimumLot() {
        return economy.humanAmount(0.2d);
    }

    /** Execute the complete Python daily order after bootstrap. */
    public List<ReferenceTradeRecord> runDay(ReferenceMarketWorld world) {
        return new ReferenceMarketDay(this).run(world);
    }

    /** Make a reclaimed physical site a licensed private operation. */
    public void licenseExistingSite(ReferenceMarketWorld world, ReferenceResourceSite site, int settlementId) {
        new ReferenceMarketDay(this).licenseExistingSite(world, site, settlementId);
    }

    /** Repair individual employment after later same-day casualty custody. */
    public void reconcileIndividualEmployment(ReferenceMarketWorld world) {
        if (!economy.profile().discretePeople()) return;
        for (ReferenceCompany company : companies.values()) {
            ReferenceSettlement home = world.settlements().get(company.homeSettlementId());
            if (home == null || home.residents() == null) {
                throw new IllegalStateException("company " + company.id() + " has no individual home ledger");
            }
            company.employeeIds().removeIf(residentId -> {
                ReferenceResident resident = home.residents().resident(residentId);
                return resident == null || !resident.economicClass().equals("worker") || resident.employerCompanyId() == null
                        || resident.employerCompanyId() != company.id();
            });
            company.employees(company.employeeIds().size());
        }
    }

    public String season(int day) {
        int index = ((Math.max(1, day) - 1) % 120) / 30;
        return switch (index) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "autumn";
            case 3 -> "winter";
            default -> throw new IllegalStateException("calendar index " + index);
        };
    }

    /** Convert initial municipal capacity into firms exactly once. */
    public void bootstrap(ReferenceMarketWorld world) {
        Objects.requireNonNull(world, "world");
        if (!companies.isEmpty()) return;
        Map<Integer, Double> companyBudgets = new LinkedHashMap<>();
        for (ReferenceSettlement settlement : sortedSettlements(world)) {
            double totalCash = settlement.cash();
            companyBudgets.put(settlement.id(), totalCash * INITIAL_CASH_SHARE);
            households.put(settlement.id(), householdFor(settlement, totalCash));
            settlement.cash(totalCash * MUNICIPAL_CASH_SHARE);
            publicInventory.put(settlement.id(), emptyStock());
        }
        for (ReferenceResourceSite site : sortedSites(world)) {
            if (site.ownerId() == null) continue;
            ReferenceCompany company = newCompany(world, ReferenceCompanySector.forSite(site.kind()), site.ownerId(), 0.0d,
                    site.kind().name().toLowerCase() + "-" + site.id());
            company.siteIds().add(site.id());
            company.assets(site.capacity() * site.quality() * SITE_ASSET_PER_CAPACITY);
            site.operatorCompanyId(company.id());
            issueLicence(site.ownerId(), company, site.id(), 0);
        }
        for (ReferenceSettlement settlement : sortedSettlements(world)) {
            addFacilityCompany(world, settlement, ReferenceCompanySector.WORKSHOP, settlement.facilities().workshop(), "workshop");
            addFacilityCompany(world, settlement, ReferenceCompanySector.MEDICINE, settlement.facilities().clinic(), "clinic");
            addFacilityCompany(world, settlement, ReferenceCompanySector.ARMORY, settlement.facilities().armory(), "armory");
        }
        for (ReferenceSettlement settlement : sortedSettlements(world)) {
            var local = companies.values().stream().filter(company -> company.homeSettlementId() == settlement.id()).toList();
            double budget = companyBudgets.get(settlement.id());
            if (!local.isEmpty() && budget > 0.0d) {
                double each = budget / local.size();
                for (ReferenceCompany company : local) company.cash(each);
            }
            for (ReferenceResource resource : ReferenceResource.values()) {
                double total = settlement.amount(resource);
                var matching = local.stream().filter(company -> company.output() == resource).toList();
                double privateAmount = total * INITIAL_INVENTORY_SHARE;
                if (matching.isEmpty()) {
                    publicInventory.get(settlement.id()).merge(resource, privateAmount, Double::sum);
                } else {
                    double share = privateAmount / matching.size();
                    for (ReferenceCompany company : matching) company.add(resource, share);
                }
                publicInventory.get(settlement.id()).merge(resource, total - privateAmount, Double::sum);
            }
        }
        syncCompatibility(world);
    }

    /** Rebuild the legacy settlement warehouse/capacity projection from owned ledgers. */
    public void syncCompatibility(ReferenceMarketWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            EnumMap<ReferenceResource, Double> totals = new EnumMap<>(publicInventory.computeIfAbsent(settlement.id(), ignored -> emptyStock()));
            for (ReferenceCompany company : companies.values()) {
                if (company.homeSettlementId() != settlement.id()) continue;
                for (ReferenceResource resource : ReferenceResource.values()) totals.merge(resource, company.amount(resource), Double::sum);
            }
            settlement.replaceStock(totals);
            lastSynced.put(settlement.id(), new EnumMap<>(totals));
            for (ReferenceSiteKind kind : ReferenceSiteKind.values()) settlement.primaryCapacity(kind.name().toLowerCase(), 0.0d);
            for (ReferenceResourceSite site : world.resourceSites().values()) {
                if (Objects.equals(site.ownerId(), settlement.id()) && site.operatorCompanyId() != null) {
                    settlement.primaryCapacity(site.kind().name().toLowerCase(), settlement.primaryCapacity(site.kind().name().toLowerCase())
                            + site.capacity() * site.quality() * site.condition());
                }
            }
            settlement.facilities().workshop(sumCapacity(settlement.id(), ReferenceCompanySector.WORKSHOP));
            settlement.facilities().clinic(sumCapacity(settlement.id(), ReferenceCompanySector.MEDICINE));
            settlement.facilities().armory(sumCapacity(settlement.id(), ReferenceCompanySector.ARMORY));
        }
    }

    /** Reconcile legacy/physical withdrawals before production without double-spending a private inventory. */
    public void captureExternalWarehouseChanges(ReferenceMarketWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            EnumMap<ReferenceResource, Double> expected = lastSynced.get(settlement.id());
            if (expected == null) continue;
            for (ReferenceResource resource : ReferenceResource.values()) {
                double delta = settlement.amount(resource) - expected.get(resource);
                if (delta < -1.0e-9d) withdraw(settlement.id(), resource, -delta);
                else if (delta > 1.0e-9d) publicInventory.get(settlement.id()).merge(resource, delta, Double::sum);
            }
        }
        syncCompatibility(world);
    }

    /** Labour, previous-day haul and production phases, in Python's exact daily order. */
    public void runLabourHaulProduction(ReferenceMarketWorld world) {
        captureExternalWarehouseChanges(world);
        for (ReferenceSettlement settlement : world.settlements().values()) economy.resetDailyFlows(settlement);
        assignLabour(world);
        haulSiteOutputs(world);
        produce(world);
        syncCompatibility(world);
    }

    void assignLabour(ReferenceMarketWorld world) {
        for (ReferenceSettlement settlement : world.settlements().values()) {
            ReferenceHouseholdLedger household = households.get(settlement.id());
            List<ReferenceCompany> candidates = companies.values().stream()
                    .filter(company -> company.homeSettlementId() == settlement.id() && company.status().equals("operating"))
                    .sorted(Comparator.comparingDouble((ReferenceCompany company) -> company.wageOffer()).reversed()
                            .thenComparingInt(ReferenceCompany::id)).toList();
            if (settlement.residents() != null) {
                List<String> usable = new ArrayList<>(settlement.residents().workerIds());
                Map<Integer, List<String>> assignments = new LinkedHashMap<>();
                for (ReferenceCompany company : candidates) {
                    double desired = Math.max(minimumWorkers(), company.capacity() * WORKERS_PER_CAPACITY);
                    if (!company.siteIds().isEmpty()) desired += minimumWorkers() * company.siteIds().size();
                    int count = Math.min(usable.size(), (int) (desired + 0.5d));
                    List<String> selected = List.copyOf(usable.subList(0, count));
                    usable.subList(0, count).clear();
                    assignments.put(company.id(), selected);
                    company.employeeIds().clear();
                    company.employeeIds().addAll(selected);
                    company.employees(selected.size());
                    payWage(company, household);
                }
                settlement.residents().replaceEmployment(assignments);
                continue;
            }
            double usable = Math.max(0.0d, household.workers() - settlement.mobilizedPersonnel() - settlement.woundedPersonnel());
            for (ReferenceCompany company : candidates) {
                double desired = Math.max(minimumWorkers(), company.capacity() * WORKERS_PER_CAPACITY);
                if (!company.siteIds().isEmpty()) desired += company.siteIds().size() * minimumWorkers();
                company.employees(Math.min(usable, desired));
                company.employeeIds().clear();
                usable -= company.employees();
                payWage(company, household);
            }
        }
    }

    void haulSiteOutputs(ReferenceMarketWorld world) {
        for (ReferenceResourceSite site : sortedSites(world)) {
            if (site.operatorCompanyId() == null || site.ownerId() == null) continue;
            ReferenceCompany company = companies.get(site.operatorCompanyId());
            ReferenceSettlement owner = world.settlements().get(site.ownerId());
            if (company == null || owner == null || !owner.alive()) continue;
            double loss = Math.min(0.85d, Math.max(0.0d, world.siteHaulInfection(site.id())) * HAUL_LOSS_PER_INFECTION);
            double shipped = site.remove(site.resource(), site.haulCapacity());
            company.add(site.resource(), shipped * (1.0d - loss));
        }
    }

    void produce(ReferenceMarketWorld world) {
        for (ReferenceCompany company : companies.values().stream().sorted(Comparator.comparingInt(ReferenceCompany::id)).toList()) {
            if (!company.status().equals("operating")) continue;
            ReferenceSettlement home = world.settlements().get(company.homeSettlementId());
            double labour = labourFactor(company) * Math.max(0.0d, 1.0d - home.illnessBurden() * ILLNESS_PRODUCTIVITY_PENALTY);
            if (company.siteIds().isEmpty()) produceFacility(world, company, labour);
            else produceSites(world, company, home, labour);
        }
    }

    double withdraw(int settlementId, ReferenceResource resource, double quantity) {
        double remaining = Math.max(0.0d, quantity);
        EnumMap<ReferenceResource, Double> reserve = publicInventory.computeIfAbsent(settlementId, ignored -> emptyStock());
        double taken = Math.min(reserve.get(resource), remaining);
        reserve.put(resource, reserve.get(resource) - taken);
        remaining -= taken;
        List<ReferenceCompany> local = companies.values().stream().filter(company -> company.homeSettlementId() == settlementId)
                .sorted(Comparator.comparingDouble((ReferenceCompany company) -> company.amount(resource)).reversed()
                        .thenComparingInt(ReferenceCompany::id)).toList();
        for (ReferenceCompany company : local) {
            if (remaining <= 1.0e-9d) break;
            double part = company.remove(resource, remaining);
            remaining -= part;
        }
        return quantity - remaining;
    }

    private void payWage(ReferenceCompany company, ReferenceHouseholdLedger household) {
        company.wageBill(company.employees() * company.wageOffer());
        company.cash(company.cash() - company.wageBill());
        household.cash(household.cash() + company.wageBill());
        household.wageIncome(household.wageIncome() + company.wageBill());
        company.wageOffer(company.lastProfit() < 0.0d ? Math.max(0.08d, company.wageOffer() * 0.985d)
                : Math.min(2.5d, company.wageOffer() * 1.006d));
    }

    private double labourFactor(ReferenceCompany company) {
        double demand = Math.max(1.0d, company.capacity() * WORKERS_PER_CAPACITY + company.siteIds().size() * minimumWorkers());
        return Math.min(1.0d, company.employees() / demand);
    }

    private void produceSites(ReferenceMarketWorld world, ReferenceCompany company, ReferenceSettlement home, double labour) {
        for (int siteId : company.siteIds().stream().sorted().toList()) {
            ReferenceResourceSite site = world.resourceSites().get(siteId);
            if (site == null || !site.operational() || !Objects.equals(site.operatorCompanyId(), company.id())) continue;
            SiteProductionSpec spec = siteSpec(site.kind());
            double planned = spec.yield() * site.capacity() * site.quality() * site.condition() * world.siteOutputFactor(site.id())
                    * labour * (site.kind() == ReferenceSiteKind.FARM ? farmYield(world.day()) : 1.0d);
            if (site.kind() == ReferenceSiteKind.FARM && seedPerCapacity(world.day()) > 0.0d) {
                double seedNeed = seedPerCapacity(world.day()) * site.capacity();
                double seedUsed = withdraw(company.homeSettlementId(), ReferenceResource.SEEDS, seedNeed);
                planned *= seedNeed > 0.0d ? seedUsed / seedNeed : 1.0d;
            }
            double toolsNeed = planned * spec.toolsPerOutput();
            double energyNeed = planned * spec.energyPerOutput();
            double tools = withdraw(company.homeSettlementId(), ReferenceResource.TOOLS, toolsNeed);
            double energy = withdraw(company.homeSettlementId(), ReferenceResource.ENERGY, energyNeed);
            double factor = Math.min(1.0d, Math.min(toolsNeed == 0.0d ? 1.0d : tools / toolsNeed,
                    energyNeed == 0.0d ? 1.0d : energy / energyNeed));
            double output = planned * factor;
            if (site.kind() == ReferenceSiteKind.FARM) {
                double seeds = output * seedOutputFraction(world.day());
                company.add(ReferenceResource.SEEDS, seeds);
                site.add(ReferenceResource.FOOD, output - seeds);
                home.recordProduction(ReferenceResource.SEEDS, seeds);
                home.recordProduction(ReferenceResource.FOOD, output - seeds);
            } else {
                site.add(site.resource(), output);
                home.recordProduction(site.resource(), output);
            }
            world.recordHumanExtraction(site.id(), output);
        }
    }

    private void produceFacility(ReferenceMarketWorld world, ReferenceCompany company, double labour) {
        ReferenceSettlement settlement = world.settlements().get(company.homeSettlementId());
        if (company.sector() == ReferenceCompanySector.WORKSHOP) {
            produceFacilityOutput(company, settlement, ReferenceResource.TOOLS, 9.0d * company.capacity() * labour,
                    inputs(ReferenceResource.ORE, 1.2d, ReferenceResource.ENERGY, 0.65d));
        } else if (company.sector() == ReferenceCompanySector.MEDICINE) {
            produceFacilityOutput(company, settlement, ReferenceResource.MEDICINE, 12.0d * company.capacity() * labour,
                    inputs(ReferenceResource.FOOD, 0.4d, ReferenceResource.ENERGY, 0.25d));
        } else if (company.sector() == ReferenceCompanySector.ARMORY) {
            produceFacilityOutput(company, settlement, ReferenceResource.WEAPONS, 2.2d * company.capacity() * labour,
                    inputs(ReferenceResource.ORE, 2.5d, ReferenceResource.TOOLS, 0.2d, ReferenceResource.ENERGY, 1.1d));
            produceFacilityOutput(company, settlement, ReferenceResource.AMMO, 12.0d * company.capacity() * labour,
                    inputs(ReferenceResource.ORE, 0.22d, ReferenceResource.ENERGY, 0.1d));
        }
    }

    private void produceFacilityOutput(ReferenceCompany company, ReferenceSettlement settlement, ReferenceResource output,
                                       double planned, Map<ReferenceResource, Double> inputs) {
        double factor = 1.0d;
        for (Map.Entry<ReferenceResource, Double> entry : inputs.entrySet()) {
            double required = planned * entry.getValue();
            double received = withdraw(company.homeSettlementId(), entry.getKey(), required);
            factor = Math.min(factor, required == 0.0d ? 1.0d : received / required);
        }
        double amount = planned * factor;
        company.add(output, amount);
        settlement.recordProduction(output, amount);
    }

    /**
     * Preserve Python recipe declaration order: withdrawals mutate shared local
     * inventories, so an enum's natural order would change a later input's
     * availability and therefore the deterministic market outcome.
     */
    private static Map<ReferenceResource, Double> inputs(Object... values) {
        LinkedHashMap<ReferenceResource, Double> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((ReferenceResource) values[index], (Double) values[index + 1]);
        return result;
    }

    private static SiteProductionSpec siteSpec(ReferenceSiteKind kind) {
        return switch (kind) {
            case FARM -> new SiteProductionSpec(400.0d, 0.002d, 0.006d);
            case MINE -> new SiteProductionSpec(34.0d, 0.045d, 0.28d);
            case FOREST -> new SiteProductionSpec(38.0d, 0.025d, 0.1d);
            case POWER -> new SiteProductionSpec(50.0d, 0.0d, 0.0d);
        };
    }

    private static double farmYield(int day) {
        return switch (((Math.max(1, day) - 1) % 120) / 30) {
            case 0 -> SEASON_SPRING_FARM_YIELD;
            case 1 -> SEASON_SUMMER_FARM_YIELD;
            case 2 -> SEASON_AUTUMN_FARM_YIELD;
            case 3 -> SEASON_WINTER_FARM_YIELD;
            default -> throw new IllegalStateException("calendar index");
        };
    }

    private static double seedPerCapacity(int day) {
        return ((Math.max(1, day) - 1) % 120) / 30 == 0 ? SEASON_SPRING_SEEDS_PER_CAPACITY : 0.0d;
    }

    private static double seedOutputFraction(int day) {
        return ((Math.max(1, day) - 1) % 120) / 30 == 2 ? SEASON_AUTUMN_SEED_OUTPUT_FRACTION : 0.0d;
    }

    private ReferenceHouseholdLedger householdFor(ReferenceSettlement settlement, double totalCash) {
        if (settlement.residents() == null) {
            return new ReferenceHouseholdLedger(settlement.population() * WORKERS_SHARE, settlement.population() * OWNERS_SHARE,
                    settlement.population() * DEPENDENTS_SHARE, totalCash * (1.0d - INITIAL_CASH_SHARE - MUNICIPAL_CASH_SHARE));
        }
        double workers = settlement.residents().livingIds().stream().map(settlement.residents()::resident)
                .filter(resident -> resident.economicClass().equals("worker")).count();
        double owners = settlement.residents().livingIds().stream().map(settlement.residents()::resident)
                .filter(resident -> resident.economicClass().equals("owner")).count();
        double dependents = settlement.residents().livingIds().stream().map(settlement.residents()::resident)
                .filter(resident -> resident.economicClass().equals("dependent")).count();
        return new ReferenceHouseholdLedger(workers, owners, dependents,
                totalCash * (1.0d - INITIAL_CASH_SHARE - MUNICIPAL_CASH_SHARE));
    }

    private void addFacilityCompany(ReferenceMarketWorld world, ReferenceSettlement settlement, ReferenceCompanySector sector,
                                    double capacity, String suffix) {
        ReferenceCompany company = newCompany(world, sector, settlement.id(), capacity, suffix);
        company.assets(capacity * FACILITY_ASSET_PER_CAPACITY);
    }

    private ReferenceCompany newCompany(ReferenceMarketWorld world, ReferenceCompanySector sector, int settlementId,
                                        double capacity, String suffix) {
        ReferenceSettlement home = world.settlements().get(settlementId);
        ReferenceCompany company = new ReferenceCompany(nextCompanyId, home.name() + " " + suffix + " Co.", sector, settlementId, 0.0d, capacity);
        companies.put(company.id(), company);
        nextCompanyId++;
        return company;
    }

    void issueLicence(int settlementId, ReferenceCompany company, Integer siteId, int day) {
        licences.put(nextLicenceId, new ReferenceLicence(nextLicenceId, settlementId, company.id(), company.sector(), siteId,
                LICENCE_RENT_PER_CAPACITY, day));
        nextLicenceId++;
    }

    private double sumCapacity(int settlementId, ReferenceCompanySector sector) {
        return companies.values().stream().filter(company -> company.homeSettlementId() == settlementId && company.sector() == sector)
                .mapToDouble(ReferenceCompany::capacity).sum();
    }

    static EnumMap<ReferenceResource, Double> emptyStock() {
        EnumMap<ReferenceResource, Double> result = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) result.put(resource, 0.0d);
        return result;
    }

    private static java.util.List<ReferenceSettlement> sortedSettlements(ReferenceMarketWorld world) {
        return world.settlements().values().stream().sorted(java.util.Comparator.comparingInt(ReferenceSettlement::id)).toList();
    }

    private static java.util.List<ReferenceResourceSite> sortedSites(ReferenceMarketWorld world) {
        return world.resourceSites().values().stream().sorted(java.util.Comparator.comparingInt(ReferenceResourceSite::id)).toList();
    }

    private record SiteProductionSpec(double yield, double toolsPerOutput, double energyPerOutput) { }

    ReferenceEconomyEngine economy() { return economy; }
    LinkedHashMap<Integer, ReferenceCompany> mutableCompanies() { return companies; }
    LinkedHashMap<Integer, ReferenceHouseholdLedger> mutableHouseholds() { return households; }
    LinkedHashMap<Integer, ReferenceContract> mutableContracts() { return contracts; }
    LinkedHashMap<Integer, ReferenceCreditPosition> mutableCredits() { return credits; }
    LinkedHashMap<Integer, ReferenceLicence> mutableLicences() { return licences; }
    LinkedHashMap<Integer, ReferenceExplorationReport> mutableReports() { return reports; }
    LinkedHashMap<Integer, ReferenceConstructionProject> mutableProjects() { return projects; }
    LinkedHashMap<Integer, EnumMap<ReferenceResource, Double>> mutableLastSynced() { return lastSynced; }
    EnumMap<ReferenceResource, Double> mutablePublicInventory(int settlementId) {
        return publicInventory.computeIfAbsent(settlementId, ignored -> emptyStock());
    }
    int nextContractId() { return nextContractId++; }
    int nextCreditId() { return nextCreditId++; }
    int nextReportId() { return nextReportId++; }
    int nextProjectId() { return nextProjectId++; }
    ReferenceCompany newExpansionCompany(ReferenceMarketWorld world, ReferenceCompanySector sector, int settlementId,
                                         double capacity, String suffix) {
        return newCompany(world, sector, settlementId, capacity, suffix);
    }
    void recordHistory(int day) {
        boolean creditWasEmptySum = credits.values().stream().noneMatch(credit -> credit.status().equals("performing"));
        history.add(new ReferenceMarketHistoryEntry(day, season(day), companies.size(),
                (int) contracts.values().stream().filter(contract -> contract.status().equals("active")).count(),
                round2(credits.values().stream().filter(credit -> credit.status().equals("performing"))
                        .mapToDouble(ReferenceCreditPosition::principal).sum()),
                creditWasEmptySum,
                round2(companies.values().stream().mapToDouble(ReferenceCompany::employees).sum()),
                (int) projects.values().stream().filter(project -> project.status().equals("building")).count()));
    }
    private static double round2(double value) { return Math.rint(value * 100.0d) / 100.0d; }
}
