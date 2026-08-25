package io.farfrontier.palemirror.frontier.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable source-compatible public read model for the active V2 reference profile.
 *
 * <p>This is deliberately narrower than persistence: it mirrors Python
 * {@code World.snapshot()} for parity and future presentation, and refuses a
 * state whose detailed public projection has not been ported yet. It never
 * owns or mutates simulation state.</p>
 */
public final class ReferenceV2PublicSnapshot {
    private ReferenceV2PublicSnapshot() { }

    public static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("public snapshot requires V2");
        if (required.profile().discretePeople()) throw new IllegalStateException("graybox resident snapshot is not ported");
        if (!required.operations().active().isEmpty()) throw new IllegalStateException("active operation snapshot is not ported");
        if (!required.field().posts().isEmpty() || !required.field().campaigns().isEmpty() || !required.field().engagements().isEmpty()) {
            throw new IllegalStateException("field-state snapshot is not ported");
        }
        if (!required.infection().swarms().isEmpty()) throw new IllegalStateException("bioform snapshot is not ported");

        ReferenceV2State v2 = required.v2();
        LinkedHashMap<String, Object> result = object();
        result.put("day", required.day());
        result.put("profile", required.profile().id());
        result.put("alive", (int) required.settlements().values().stream().filter(ReferenceSettlement::alive).count());
        result.put("settlements", required.settlements().values().stream().sorted(Comparator.comparingInt(ReferenceSettlement::id))
                .map(settlement -> settlement(settlement, required.economy())).toList());
        result.put("residents", List.of());
        result.put("infected_fraction", required.infection().infectedFraction());
        result.put("swarms", 0);
        result.put("nests", required.infection().organs().values().stream().sorted(Comparator.comparingInt(ReferenceHiveOrgan::id))
                .map(ReferenceV2PublicSnapshot::organ).toList());
        result.put("hive", hive(required.infection()));
        result.put("economy", economy(required.microeconomy()));
        result.put("companies", required.microeconomy().companies().values().stream().sorted(Comparator.comparingInt(ReferenceCompany::id))
                .map(ReferenceV2PublicSnapshot::company).toList());
        result.put("contracts", required.microeconomy().contracts().values().stream().sorted(Comparator.comparingInt(ReferenceContract::id))
                .map(ReferenceV2PublicSnapshot::contract).toList());
        result.put("resource_sites", required.resourceSites().values().stream().sorted(Comparator.comparingInt(ReferenceResourceSite::id))
                .map(ReferenceV2PublicSnapshot::site).toList());
        result.put("active_operations", 0);
        result.put("operations", List.of());
        result.put("field", field());
        result.put("strategy", null);
        result.put("v2", v2(v2));
        result.put("recent_events", tail(required.events(), 12));
        return freeze(result);
    }

    public static String canonicalJson(ReferenceWorld world) { return canonicalJson(capture(world)); }

    public static String canonicalJson(Object value) {
        StringBuilder result = new StringBuilder();
        appendJson(result, value);
        return result.toString();
    }

    public static String sha256(ReferenceWorld world) { return sha256(capture(world)); }

    static String sha256(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Map<String, Object> settlement(ReferenceSettlement item, ReferenceEconomyEngine economy) {
        return object("name", item.name(), "alive", item.alive(), "population", rounded(item.population(), 1), "cash", rounded(item.cash(), 1),
                "integrity", rounded(item.integrity(), 1), "threat", rounded(item.threat(), 3), "illness_burden", rounded(item.illnessBurden(), 3),
                "specialization", item.specialization(), "food_days", rounded(economy.coverageDays(item, ReferenceResource.FOOD), 2),
                "food", rounded(item.amount(ReferenceResource.FOOD), 1), "ore", rounded(item.amount(ReferenceResource.ORE), 1),
                "tools", rounded(item.amount(ReferenceResource.TOOLS), 1), "weapons", rounded(item.amount(ReferenceResource.WEAPONS), 1),
                "ammo", rounded(item.amount(ReferenceResource.AMMO), 1), "mobilized_personnel", rounded(item.mobilizedPersonnel(), 1),
                "wounded_personnel", rounded(item.woundedPersonnel(), 1));
    }

    private static Map<String, Object> organ(ReferenceHiveOrgan item) {
        LinkedHashMap<String, Object> mutations = object();
        item.mutations().forEach((mutation, level) -> mutations.put(mutation.name().toLowerCase(Locale.ROOT), level));
        return object("id", item.id(), "x", item.x(), "y", item.y(), "kind", item.kind().name().toLowerCase(Locale.ROOT),
                "biomass", rounded(item.biomass(), 2), "samples", rounded(item.samples(), 2), "vitality", rounded(item.vitality(), 1),
                "feral", item.feral(), "mutations", mutations, "colony_id", item.colonyId(), "role", item.role());
    }

    private static Map<String, Object> hive(ReferenceInfectionModel infection) {
        return object("genome", infection.genome(), "harvested_biomass", rounded(infection.harvestedBiomass(), 2),
                "harvested_genetic_material", rounded(infection.harvestedGeneticMaterial(), 2),
                "organic_mass", rounded(infection.ecosystem().totalOrganic(), 2), "scar", rounded(infection.ecosystem().totalScar(), 2),
                "feral_fraction", rounded(infection.feralFraction(), 3));
    }

    private static Map<String, Object> economy(ReferenceMarketEconomy microeconomy) {
        Map<String, Object> summary = microeconomy.summary();
        boolean performingCredit = microeconomy.credits().values().stream()
                .anyMatch(credit -> credit.status().equals("performing"));
        return object("season", summary.get("season"), "companies", summary.get("companies"),
                "active_contracts", summary.get("active_contracts"), "credit", performingCredit ? summary.get("credit") : 0,
                "construction", summary.get("construction"));
    }

    private static Map<String, Object> company(ReferenceCompany item) {
        List<String> inventory = new ArrayList<>();
        for (ReferenceResource resource : ReferenceResource.values()) {
            double amount = item.amount(resource);
            if (amount > .01d) inventory.add(resource.name().toLowerCase(Locale.ROOT) + ":" + fixed(amount, 1));
        }
        return object("id", item.id(), "name", item.name(), "sector", item.sector().name().toLowerCase(Locale.ROOT),
                "home_settlement_id", item.homeSettlementId(), "cash", rounded(item.cash(), 2), "debt", rounded(item.debt(), 2),
                "assets", rounded(item.assets(), 2), "capacity", rounded(item.capacity(), 3), "employees", rounded(item.employees(), 2),
                "employee_ids", item.employeeIds().isEmpty() ? "—" : item.employeeIds().stream().sorted()
                        .collect(java.util.stream.Collectors.joining(", ")),
                "wage_offer", rounded(item.wageOffer(), 3), "last_profit", rounded(item.lastProfit(), 2), "owner_kind", item.ownerKind(),
                "status", item.status(), "financial_state", item.v2State(), "sites", item.siteIds().stream().sorted()
                        .map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")),
                "inventory", String.join(", ", inventory));
    }

    private static String contract(ReferenceContract item) {
        return item.resource().name().toLowerCase(Locale.ROOT) + " " + item.sellerCompanyId() + "->" + item.buyerSettlementId()
                + " " + fixed(item.dailyQuantity(), 1) + "/d @ " + fixed(item.priceIndex(), 2) + " (" + item.status() + ")";
    }

    private static Map<String, Object> site(ReferenceResourceSite item) {
        return object("id", item.id(), "kind", item.kind().name().toLowerCase(Locale.ROOT), "x", item.x(), "y", item.y(),
                "owner_id", item.ownerId(), "operator_company_id", item.operatorCompanyId(), "quality", rounded(item.quality(), 3),
                "capacity", rounded(item.capacity(), 3), "condition", rounded(item.condition(), 3), "contamination", rounded(item.contamination(), 3),
                "substrate", rounded(item.substrate(), 2), "stock", rounded(item.amount(item.resource()), 2),
                "haul_capacity", rounded(item.haulCapacity(), 2));
    }

    private static Map<String, Object> field() {
        return object("summary", object("posts", 0, "campaigns", 0, "engagements", 0, "links", 0),
                "posts", List.of(), "campaigns", List.of(), "engagements", List.of());
    }

    private static Map<String, Object> v2(ReferenceV2State state) {
        LinkedHashMap<String, Object> civics = object();
        state.civics().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> civics.put(String.valueOf(entry.getKey()), civic(entry.getValue())));
        LinkedHashMap<String, Object> doctrines = object();
        state.doctrines().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> doctrines.put(String.valueOf(entry.getKey()), doctrine(entry.getValue())));
        LinkedHashMap<String, Object> lifecycle = object();
        state.hiveLifecycle().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> lifecycle.put(entry.getKey(), entry.getValue().id()));
        return object("summary", v2Summary(state),
                "sectors", state.sectors().values().stream().sorted(Comparator.comparingInt(ReferenceV2OperationalSector::y)
                        .thenComparingInt(ReferenceV2OperationalSector::x)).map(ReferenceV2PublicSnapshot::sector).toList(),
                "sector_control", state.sectorControl().values().stream().sorted(Comparator.comparing(ReferenceV2SectorControl::sectorKey))
                        .map(ReferenceV2PublicSnapshot::control).toList(),
                "front_campaigns", state.frontCampaigns().values().stream().sorted(Comparator.comparingInt(ReferenceFrontCampaign::id))
                        .map(ReferenceV2PublicSnapshot::frontCampaign).toList(),
                "supply_lines", state.supplyLines().values().stream().sorted(Comparator.comparingInt(ReferenceSupplyLineStatus::campaignId))
                        .map(ReferenceV2PublicSnapshot::supplyLine).toList(),
                "sector_engagements", tail(state.sectorEngagements(), 100).stream().map(ReferenceV2PublicSnapshot::sectorEngagement).toList(),
                "civics", civics, "emergency_regimes", state.emergencyRegimes().values().stream().sorted(Comparator.comparingInt(ReferenceEmergencyRegime::settlementId))
                        .map(ReferenceV2PublicSnapshot::emergency).toList(), "doctrines", doctrines,
                "charters", state.charters().values().stream().sorted(Comparator.comparingInt(ReferenceCoalitionCharter::id))
                        .map(ReferenceV2PublicSnapshot::charter).toList(), "chrysalises", state.chrysalises().values().stream()
                        .sorted(Comparator.comparingInt(ReferenceNeuralChrysalis::organId)).map(ReferenceV2PublicSnapshot::chrysalis).toList(),
                "hive_lifecycle", lifecycle, "procurements", state.procurements().values().stream().sorted(Comparator.comparingInt(ReferenceProcurementOrder::id))
                        .map(ReferenceV2PublicSnapshot::procurement).toList(), "compensation", state.compensation().values().stream()
                        .sorted(Comparator.comparingInt(ReferenceCompensationClaim::id)).map(ReferenceV2PublicSnapshot::compensation).toList(),
                "decisions", tail(state.decisionHistory(), 80).stream().map(ReferenceV2PublicSnapshot::decision).toList());
    }

    private static Map<String, Object> v2Summary(ReferenceV2State state) {
        return object("sectors", state.sectors().size(), "front_campaigns", (int) state.frontCampaigns().values().stream()
                .filter(campaign -> !campaign.phase().terminal()).count(), "held_sectors", (int) state.sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HUMAN).count(), "hive_sectors", (int) state.sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HIVE).count(), "chrysalises", state.chrysalises().size(),
                "charters", (int) state.charters().values().stream().filter(charter -> charter.status().equals("active")).count(),
                "procurement", state.procurements().size(), "compensation_pending", (int) state.compensation().values().stream()
                        .filter(claim -> claim.status().equals("pending")).count(), "emergency_regimes", state.emergencyRegimes().size(),
                "civic_site_projects", state.civicSiteProjects().size(), "civic_states", state.civics().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + ":" + entry.getValue().state().id()).collect(java.util.stream.Collectors.joining(", ")));
    }

    private static Map<String, Object> sector(ReferenceV2OperationalSector item) {
        return object("sector", item.key(), "organic", rounded(item.organicMass(), 2), "moisture", rounded(item.moisture(), 3),
                "scar", rounded(item.scar(), 3), "infection", rounded(item.infection(), 3), "spores", rounded(item.sporeLoad(), 3),
                "human_access", rounded(item.humanAccess(), 3), "hive_influence", rounded(item.hiveInfluence(), 3),
                "infrastructure", rounded(item.infrastructureValue(), 2), "routes", item.routeKeys().isEmpty() ? "—" : item.routeKeys().stream()
                        .map(key -> key.lowerSettlementId() + "-" + key.upperSettlementId()).collect(java.util.stream.Collectors.joining(", ")));
    }

    private static Map<String, Object> control(ReferenceV2SectorControl item) {
        return object("sector", item.sectorKey(), "control", item.state().id(), "cordon", rounded(item.cordonStrength(), 3),
                "garrison", rounded(item.garrison(), 1), "supplied", item.supplied(), "cleared_day", item.lastClearedDay() > -10_000 ? item.lastClearedDay() : "—",
                "held_days", item.heldDays(), "reason", item.reason());
    }

    private static Map<String, Object> frontCampaign(ReferenceFrontCampaign item) {
        return object("id", item.id(), "kind", item.kind().id(), "leader", item.leaderId(), "contributors", joinInts(item.contributors()),
                "target_sector", item.targetSector(), "phase", item.phase().id(), "personnel", rounded(item.personnel(), 1),
                "composition", "—", "field_campaign", nullable(item.fieldCampaignId()), "post", nullable(item.postId()), "hold_days", item.holdDays(),
                "risk", rounded(item.risk(), 3), "reason", item.reason(), "status_reason", item.statusReason(),
                "terminal_outcome", item.terminalOutcome() == null ? "—" : item.terminalOutcome().id());
    }

    private static Map<String, Object> supplyLine(ReferenceSupplyLineStatus item) {
        return object("campaign", item.campaignId(), "source", item.sourceSector(), "target", item.targetSector(),
                "sectors", item.sectors().isEmpty() ? "—" : String.join(" → ", item.sectors()), "connected", item.connected(),
                "risk", rounded(item.risk(), 3), "readiness", rounded(item.readiness(), 3), "reason", item.reason());
    }

    private static Map<String, Object> sectorEngagement(ReferenceSectorEngagement item) {
        return object("day", item.day(), "sector", item.sectorKey(), "kind", item.kind(), "attacker", item.attacker(), "defender", item.defender(),
                "power", rounded(item.power(), 1), "cordon_delta", rounded(item.cordonChange(), 3), "infection_delta", rounded(item.infectionChange(), 3),
                "personnel_loss", rounded(item.personnelLoss(), 2), "outcome", item.outcome());
    }

    private static Map<String, Object> civic(ReferenceCivicLedger item) {
        return object("state", item.state().id(), "reserve_days", rounded(item.foodReserveDays(), 2), "legitimacy", rounded(item.legitimacy(), 3),
                "quarantine_until", item.quarantineUntil(), "war_budget", rounded(item.warBudget(), 2), "ration", rounded(item.rationFraction(), 3), "reason", item.reason());
    }

    private static String doctrine(ReferenceSettlementDoctrine item) {
        return "caution=" + fixed(item.caution(), 2) + "; solidarity=" + fixed(item.solidarity(), 2) + "; commerce="
                + fixed(item.commercialDependence(), 2) + "; militancy=" + fixed(item.militancy(), 2) + "; legitimacy=" + fixed(item.legitimacy(), 2);
    }

    private static Map<String, Object> emergency(ReferenceEmergencyRegime item) {
        return object("settlement", item.settlementId(), "state", item.state().id(), "activated", item.activatedDay(),
                "war_budget", rounded(item.warBudget(), 2), "quarantine_until", item.quarantineUntil(), "status", item.status());
    }

    private static Map<String, Object> charter(ReferenceCoalitionCharter item) {
        return object("id", item.id(), "leader", item.leaderId(), "members", joinInts(item.members()), "target", item.target(), "status", item.status(),
                "expires", item.expiresDay(), "contributions", formattedMap(item.contribution(), 1), "compensation", formattedMap(item.compensationDue(), 1),
                "reason", item.reason());
    }

    private static Map<String, Object> chrysalis(ReferenceNeuralChrysalis item) {
        return object("organ", item.organId(), "sector", item.sectorKey(), "remaining", item.daysRemaining(),
                "biomass", rounded(item.biomassCommitted(), 2), "status", item.status());
    }

    private static Map<String, Object> procurement(ReferenceProcurementOrder item) {
        return object("id", item.id(), "settlement", item.settlementId(), "resource", item.resource().name().toLowerCase(Locale.ROOT),
                "quantity", rounded(item.quantity(), 2), "fulfilled", rounded(item.fulfilled(), 2), "status", item.status());
    }

    private static Map<String, Object> compensation(ReferenceCompensationClaim item) {
        return object("id", item.id(), "settlement", item.settlementId(), "company", item.companyId(), "amount", rounded(item.amount(), 2),
                "due", item.dueDay(), "status", item.status(), "reason", item.reason());
    }

    private static Map<String, Object> decision(ReferenceV2DecisionReceipt item) {
        return object("day", item.day(), "agent", "settlement:" + item.settlementId(), "action", item.action(), "risk", rounded(item.risk(), 3),
                "reason", item.reason(), "blockers", item.blockers());
    }

    private static String nullable(Integer value) { return value == null ? "—" : String.valueOf(value); }
    private static String joinInts(List<Integer> values) { return values.isEmpty() ? "—" : values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")); }
    private static String formattedMap(Map<Integer, Double> values, int scale) {
        return values.isEmpty() ? "—" : values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + fixed(entry.getValue(), scale)).collect(java.util.stream.Collectors.joining(", "));
    }
    /** Python {@code round(float, digits)} and f-string formatting inspect the binary64 value, not its shortest decimal text. */
    private static double rounded(double value, int scale) { return new BigDecimal(value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue(); }
    private static String fixed(double value, int scale) { return new BigDecimal(value).setScale(scale, RoundingMode.HALF_EVEN).toPlainString(); }
    private static <T> List<T> tail(List<T> values, int count) { return List.copyOf(values.subList(Math.max(0, values.size() - count), values.size())); }
    private static LinkedHashMap<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return result;
    }

    private static Map<String, Object> freeze(Map<String, Object> value) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(key, frozen(item)));
        return Collections.unmodifiableMap(result);
    }

    private static Object frozen(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("snapshot map key is not a string");
                result.put(key, frozen(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(ReferenceV2PublicSnapshot::frozen).toList());
        return value;
    }

    private static void appendJson(StringBuilder target, Object value) {
        if (value == null) { target.append("null"); return; }
        if (value instanceof String string) { appendString(target, string); return; }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) { target.append(value); return; }
        if (value instanceof Double number) { target.append(pythonFloat(number)); return; }
        if (value instanceof Map<?, ?> map) {
            target.append('{'); boolean first = true;
            List<String> keys = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("snapshot map key is not a string");
                keys.add(key);
            }
            Collections.sort(keys);
            for (String key : keys) {
                if (!first) target.append(','); first = false; appendString(target, key); target.append(':'); appendJson(target, map.get(key));
            }
            target.append('}'); return;
        }
        if (value instanceof List<?> list) {
            target.append('['); for (int index = 0; index < list.size(); index++) { if (index > 0) target.append(','); appendJson(target, list.get(index)); } target.append(']'); return;
        }
        throw new IllegalArgumentException("unsupported canonical JSON value: " + value.getClass().getName());
    }

    private static void appendString(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            switch (item) {
                case '"' -> target.append("\\\""); case '\\' -> target.append("\\\\"); case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f"); case '\n' -> target.append("\\n"); case '\r' -> target.append("\\r"); case '\t' -> target.append("\\t");
                default -> { if (item < 0x20 || item > 0x7f) target.append(String.format(Locale.ROOT, "\\u%04x", (int) item)); else target.append(item); }
            }
        }
        target.append('"');
    }

    /** Exact CPython 3.11 {@code repr(float)} spelling used by {@code json.dumps}. */
    static String pythonFloat(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("canonical JSON rejects non-finite double");
        String raw = Double.toString(value);
        int exponentMarker = raw.indexOf('E');
        if (exponentMarker < 0) return raw;
        boolean negative = raw.charAt(0) == '-';
        String mantissa = negative ? raw.substring(1, exponentMarker) : raw.substring(0, exponentMarker);
        int exponent = Integer.parseInt(raw.substring(exponentMarker + 1));
        String digits = mantissa.replace(".", "");
        if (exponent >= -4 && exponent < 16) {
            int decimal = exponent + 1;
            String fixed;
            if (decimal <= 0) fixed = trimFraction("0." + "0".repeat(-decimal) + digits);
            else if (decimal >= digits.length()) fixed = digits + "0".repeat(decimal - digits.length()) + ".0";
            else fixed = trimFraction(digits.substring(0, decimal) + "." + digits.substring(decimal));
            return negative ? "-" + fixed : fixed;
        }
        while (digits.length() > 1 && digits.endsWith("0")) digits = digits.substring(0, digits.length() - 1);
        String result = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return (negative ? "-" : "") + result + "e" + (exponent >= 0 ? "+" : "-")
                + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
    }

    private static String trimFraction(String value) {
        int decimal = value.indexOf('.');
        int end = value.length();
        while (end > decimal + 1 && value.charAt(end - 1) == '0') end--;
        return value.substring(0, end);
    }
}
