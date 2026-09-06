package io.farfrontier.palemirror.frontier.reference;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Source-order company capacity growth, surveys and licensed-site construction. */
final class ReferenceMarketExpansion {
    private static final int INVESTMENT_START_DAY = 91;
    private static final int COOLDOWN_DAYS = 24;
    private static final double INVESTMENT_MINIMUM_SCORE = 0.65d;
    private static final double SURVEY_COST = 18.0d;
    private static final double CONSTRUCTION_CASH = 80.0d;
    private static final int CONSTRUCTION_DAYS = 10;
    private static final double CAPACITY_GAIN = 0.55d;
    private static final double MINIMUM_QUALITY = 0.62d;
    private static final double REPORT_CONFIDENCE = 0.72d;
    private static final double HAUL_CAPACITY_BASE = 300.0d;
    private static final Map<ReferenceResource, Double> CONSTRUCTION_MATERIALS = Map.of(
            ReferenceResource.TIMBER, 16.0d, ReferenceResource.ORE, 12.0d, ReferenceResource.TOOLS, 10.0d);

    private final ReferenceMarketEconomy market;
    private final ReferenceEconomyEngine economy;
    private final ReferenceMarketFinance finance;

    ReferenceMarketExpansion(ReferenceMarketEconomy market, ReferenceMarketFinance finance) {
        this.market = market;
        economy = market.economy();
        this.finance = finance;
    }

    void completeProjects(ReferenceMarketWorld world) {
        for (ReferenceConstructionProject project : market.mutableProjects().values()) {
            if (!project.status().equals("building")) continue;
            project.daysRemaining(project.daysRemaining() - 1);
            if (project.daysRemaining() > 0) continue;
            ReferenceExplorationReport report = market.mutableReports().get(project.reportId());
            ReferenceCompany company = market.mutableCompanies().get(project.companyId());
            ReferenceSettlement host = world.settlements().get(report.settlementId());
            int siteId = world.resourceSites().keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            ReferenceResourceSite site = new ReferenceResourceSite(siteId, report.kind(), report.x(), report.y(), report.quality(),
                    economy.humanAmount(CAPACITY_GAIN), host.id());
            site.operatorCompanyId(company.id());
            site.haulCapacity(economy.humanAmount(HAUL_CAPACITY_BASE));
            world.addResourceSite(site);
            company.siteIds().add(site.id());
            company.assets(company.assets() + economy.humanAmount(CONSTRUCTION_CASH));
            market.issueLicence(host.id(), company, site.id(), world.day());
            report.status("complete");
            project.status("complete");
            world.event("D" + world.day() + ": " + company.name() + " opened " + site.kind().name().toLowerCase()
                    + " site " + site.id() + " under " + host.name() + " licence");
        }
    }

    void plan(ReferenceMarketWorld world) {
        for (ReferenceCompany company : market.mutableCompanies().values().stream().sorted(Comparator.comparingInt(ReferenceCompany::id)).toList()) {
            if (!company.status().equals("operating") || world.day() < INVESTMENT_START_DAY
                    || world.day() - company.lastInvestmentDay() < COOLDOWN_DAYS || company.lastProfit() < INVESTMENT_MINIMUM_SCORE) continue;
            if (!company.siteIds().isEmpty()) surveyAndBuild(world, company);
            else expandFacility(world, company);
        }
    }

    void licenseExistingSite(ReferenceMarketWorld world, ReferenceResourceSite site, int settlementId) {
        ReferenceCompanySector sector = ReferenceCompanySector.forSite(site.kind());
        ReferenceCompany company = market.mutableCompanies().values().stream().sorted(Comparator.comparingInt(ReferenceCompany::id))
                .filter(candidate -> candidate.homeSettlementId() == settlementId && candidate.sector() == sector
                        && candidate.status().equals("operating"))
                .findFirst().orElseGet(() -> market.newExpansionCompany(world, sector, settlementId, 0.0d, "reclaimed-" + site.id()));
        site.ownerId(settlementId);
        site.operatorCompanyId(company.id());
        company.siteIds().add(site.id());
        company.assets(company.assets() + site.capacity() * site.quality() * 70.0d);
        market.issueLicence(settlementId, company, site.id(), world.day());
    }

    private void expandFacility(ReferenceMarketWorld world, ReferenceCompany company) {
        double cash = economy.humanAmount(CONSTRUCTION_CASH);
        if (company.cash() < cash) finance.borrowCompany(world, company, cash - company.cash(), "capacity expansion");
        if (company.cash() < cash) return;
        company.capacity(company.capacity() + economy.humanAmount(CAPACITY_GAIN));
        company.cash(company.cash() - cash);
        company.assets(company.assets() + cash);
        company.lastInvestmentDay(world.day());
        world.event("D" + world.day() + ": " + company.name() + " expanded " + company.sector().name().toLowerCase() + " capacity");
    }

    private void surveyAndBuild(ReferenceMarketWorld world, ReferenceCompany company) {
        List<ReferenceSettlement> candidates = world.settlements().values().stream().filter(ReferenceSettlement::alive).toList();
        double cashNeed = economy.humanAmount(SURVEY_COST + CONSTRUCTION_CASH);
        if (company.cash() < cashNeed) finance.borrowCompany(world, company, cashNeed - company.cash(), "licensed site construction");
        ReferenceSettlement home = world.settlements().get(company.homeSettlementId());
        if (candidates.isEmpty() || company.cash() < cashNeed || CONSTRUCTION_MATERIALS.entrySet().stream()
                .anyMatch(entry -> home.amount(entry.getKey()) < economy.humanAmount(entry.getValue()))) return;
        ReferenceResource resource = company.output();
        ReferenceSettlement host = candidates.stream().max(Comparator.comparingDouble((ReferenceSettlement candidate) -> economy.localValue(candidate, resource))
                .thenComparing(Comparator.comparingInt(ReferenceSettlement::id).reversed())).orElseThrow();
        ReferenceSiteKind kind = siteKind(company.sector());
        ReferenceSiteSurveyor.ReferenceSitePosition position;
        try {
            position = world.siteSurveyor().place(host, kind);
        } catch (IllegalStateException unavailable) {
            return;
        }
        double quality = Math.max(MINIMUM_QUALITY, world.siteSurveyor().quality(kind, position.x(), position.y()));
        int reportId = market.nextReportId();
        market.mutableReports().put(reportId, new ReferenceExplorationReport(reportId, company.id(), host.id(), kind, position.x(), position.y(),
                quality, REPORT_CONFIDENCE, world.day(), "licensed"));
        company.cash(company.cash() - cashNeed);
        for (Map.Entry<ReferenceResource, Double> entry : CONSTRUCTION_MATERIALS.entrySet()) {
            market.withdraw(company.homeSettlementId(), entry.getKey(), economy.humanAmount(entry.getValue()));
        }
        int projectId = market.nextProjectId();
        market.mutableProjects().put(projectId, new ReferenceConstructionProject(projectId, company.id(), reportId, CONSTRUCTION_DAYS));
        company.lastInvestmentDay(world.day());
        world.event("D" + world.day() + ": " + company.name() + " surveyed " + kind.name().toLowerCase()
                + " potential near " + host.name());
    }

    private static ReferenceSiteKind siteKind(ReferenceCompanySector sector) {
        return switch (sector) {
            case AGRICULTURE -> ReferenceSiteKind.FARM;
            case MINING -> ReferenceSiteKind.MINE;
            case FORESTRY -> ReferenceSiteKind.FOREST;
            case ENERGY -> ReferenceSiteKind.POWER;
            default -> throw new IllegalArgumentException("facility sector has no site kind: " + sector);
        };
    }
}
