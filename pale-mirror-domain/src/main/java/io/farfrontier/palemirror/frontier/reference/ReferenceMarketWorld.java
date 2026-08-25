package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical market-facing slice of the later complete reference world.
 *
 * <p>It owns insertion order, day, physical sites and event receipts. Ecology,
 * V2 rationing and site placement enter only when their source owners are
 * ported; no NeoForge type is allowed across this boundary.</p>
 */
public final class ReferenceMarketWorld {
    private final LinkedHashMap<Integer, ReferenceSettlement> settlements = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ReferenceResourceSite> resourceSites = new LinkedHashMap<>();
    private final Map<Integer, Double> siteOutputFactors = new LinkedHashMap<>();
    private final Map<Integer, Double> siteHaulInfections = new LinkedHashMap<>();
    private final Map<Integer, Double> extractedBySite = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private final ReferenceTradeNetwork trade;
    private ReferenceSiteSurveyor siteSurveyor = ReferenceSiteSurveyor.unavailable();
    private ReferenceRationAuthority rationAuthority;
    private int day;

    public ReferenceMarketWorld(ReferenceTradeNetwork trade) {
        this.trade = Objects.requireNonNull(trade, "trade");
    }

    public int day() { return day; }
    public void day(int value) { day = value; }
    public ReferenceTradeNetwork trade() { return trade; }
    public ReferenceSiteSurveyor siteSurveyor() { return siteSurveyor; }
    public void siteSurveyor(ReferenceSiteSurveyor value) { siteSurveyor = Objects.requireNonNull(value, "siteSurveyor"); }
    public ReferenceRationAuthority rationAuthority() { return rationAuthority; }
    public void rationAuthority(ReferenceRationAuthority value) { rationAuthority = value; }
    public Map<Integer, ReferenceSettlement> settlements() { return settlements; }
    public Map<Integer, ReferenceResourceSite> resourceSites() { return resourceSites; }
    public List<String> events() { return List.copyOf(events); }
    public void event(String value) { events.add(Objects.requireNonNull(value, "value")); }
    /** Read-only ecology projection; the later ecology owner refreshes this before market production. */
    public double siteOutputFactor(int siteId) { return siteOutputFactors.getOrDefault(siteId, 1.0d); }
    public void siteOutputFactor(int siteId, double value) { siteOutputFactors.put(siteId, value); }
    /** Read-only infection projection for the physical haul between site and its owner. */
    public double siteHaulInfection(int siteId) { return siteHaulInfections.getOrDefault(siteId, 0.0d); }
    public void siteHaulInfection(int siteId, double value) { siteHaulInfections.put(siteId, value); }
    public void recordHumanExtraction(int siteId, double amount) {
        if (amount > 0.0d) extractedBySite.merge(siteId, amount, Double::sum);
    }
    public double extractedAtSite(int siteId) { return extractedBySite.getOrDefault(siteId, 0.0d); }
    /**
     * Transfers this market phase's physical harvest back to the ecology owner.
     * The pending values are not a second stock ledger and must be drained once
     * by the source-order world engine before the next market day.
     */
    Map<Integer, Double> drainHumanExtractions() {
        Map<Integer, Double> result = Map.copyOf(extractedBySite);
        extractedBySite.clear();
        return result;
    }

    public void addSettlement(ReferenceSettlement settlement) {
        ReferenceSettlement required = Objects.requireNonNull(settlement, "settlement");
        if (settlements.putIfAbsent(required.id(), required) != null) throw new IllegalArgumentException("duplicate settlement " + required.id());
    }

    public void addResourceSite(ReferenceResourceSite site) {
        ReferenceResourceSite required = Objects.requireNonNull(site, "site");
        if (resourceSites.putIfAbsent(required.id(), required) != null) throw new IllegalArgumentException("duplicate site " + required.id());
    }
}
