package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Source-shaped canonical owner mapper for Python's complete {@code V2State}. */
final class ReferenceCanonicalStateV2 {
    private ReferenceCanonicalStateV2() { }

    static Map<String, Object> capture(ReferenceWorld world) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        if (!required.v2Enabled()) throw new IllegalStateException("canonical state requires V2-enabled reference world");
        ReferenceV2State state = required.v2();
        return typed("simulation.v2.V2State", "attributes", object(
                "_next_charter_id", state.nextCharterId(), "_next_claim_id", state.nextClaimId(),
                "_next_front_campaign_id", state.nextFrontCampaignId(), "_next_procurement_id", state.nextProcurementId(),
                "charters", charterMap(state.charters()), "chrysalises", chrysalisMap(state.chrysalises()),
                "civic_site_projects", sequence("list", state.civicSiteProjects().stream().map(ReferenceCanonicalStateV2::project).toList()),
                "civics", civicMap(state.civics()), "compensation", compensationMap(state.compensation()),
                "decision_history", sequence("list", state.decisionHistory().stream().map(ReferenceCanonicalStateV2::decision).toList()),
                "doctrines", doctrineMap(state.doctrines()), "emergency_regimes", regimeMap(state.emergencyRegimes()),
                "front_campaigns", campaignMap(state.frontCampaigns()), "frontier_cooldown_until", integerMap(state.frontierCooldownUntil()),
                "hive_lifecycle", lifecycleMap(state.hiveLifecycle()), "hive_perception", hivePerception(state.hivePerception()),
                "human_perceptions", humanPerceptionMap(state.humanPerceptions()), "last_civic_work_day", integerMap(state.lastCivicWorkDay()),
                "procurements", procurementMap(state.procurements()), "profile", profile(state.profile()),
                "ration_plans", rationMap(state.rationPlans()), "reserve_policies", reserveMap(state.reservePolicies()),
                "rng", random(state.rng()), "route_insurance", insuranceMap(state.routeInsurance()),
                "sector_control", controlMap(state.sectorControl()),
                "sector_engagements", sequence("list", state.sectorEngagements().stream().map(ReferenceCanonicalStateV2::engagement).toList()),
                "sectors", sectorMap(state.sectors()), "supply_lines", supplyMap(state.supplyLines())));
    }

    private static Map<String, Object> profile(ReferenceSimulationProfile value) {
        return typed("simulation.profiles.SimulationProfile", "fields", object(
                "name", enumValue("simulation.profiles.SimulationProfileName", value.id()), "person_scale", (double) value.personScale(),
                "discrete_people", value.discretePeople(), "minimum_surviving_settlement", value.minimumSurvivingSettlement()));
    }

    private static Map<String, Object> random(PythonRandom value) {
        long[] words = value.state().words();
        List<Long> state = new ArrayList<>(words.length);
        for (long word : words) state.add(word);
        return object("$random_mt19937", object("version", 3, "state", List.copyOf(state), "gaussian_cache", null));
    }

    private static Map<String, Object> sectorMap(Map<String, ReferenceV2OperationalSector> values) {
        return map(values, ReferenceCanonicalStateV2::sector);
    }

    private static Map<String, Object> sector(ReferenceV2OperationalSector value) {
        return typed("simulation.v2.OperationalSector", "fields", object(
                "x", value.x(), "y", value.y(), "cells", positions(value.cells()), "organic_mass", value.organicMass(),
                "moisture", value.moisture(), "scar", value.scar(), "infection", value.infection(), "spore_load", value.sporeLoad(),
                "human_access", value.humanAccess(), "hive_influence", value.hiveInfluence(),
                "infrastructure_value", value.infrastructureValue(), "route_keys", routeKeys(value.routeKeys())));
    }

    private static Map<String, Object> controlMap(Map<String, ReferenceV2SectorControl> values) {
        return map(values, ReferenceCanonicalStateV2::control);
    }

    private static Map<String, Object> control(ReferenceV2SectorControl value) {
        return typed("simulation.v2.SectorControl", "fields", object(
                "sector_key", value.sectorKey(), "state", enumValue("simulation.v2.SectorControlState", value.state().id()),
                "cordon_strength", value.cordonStrength(), "garrison", value.garrison(), "supplied", value.supplied(),
                "last_cleared_day", value.lastClearedDay(), "last_changed_day", value.lastChangedDay(), "held_days", value.heldDays(),
                "reason", value.reason()));
    }

    private static Map<String, Object> humanPerceptionMap(Map<Integer, ReferenceV2HumanPerception> values) {
        return map(values, ReferenceCanonicalStateV2::humanPerception);
    }

    private static Map<String, Object> humanPerception(ReferenceV2HumanPerception value) {
        return typed("simulation.v2.HumanPerception", "fields", object("settlement_id", value.settlementId(), "beliefs", beliefMap(value.beliefs())));
    }

    private static Map<String, Object> hivePerception(ReferenceV2HivePerception value) {
        return typed("simulation.v2.HivePerception", "fields", object("beliefs", beliefMap(value.beliefs())));
    }

    private static Map<String, Object> beliefMap(Map<String, ReferenceV2Belief> values) { return map(values, ReferenceCanonicalStateV2::belief); }

    private static Map<String, Object> belief(ReferenceV2Belief value) {
        return typed("simulation.v2.Belief", "fields", object(
                "sector_key", value.sectorKey(), "observed_day", value.observedDay(), "confidence", value.confidence(),
                // Python's Belief annotates source as str; ObservationSource is
                // planner vocabulary, not a stored dataclass enum here.
                "source", value.source().id(), "infection", value.infection(),
                "organic_mass", value.organicMass(), "hive_influence", value.hiveInfluence(),
                "infrastructure_value", value.infrastructureValue(), "chrysalis", value.chrysalis()));
    }

    private static Map<String, Object> doctrineMap(Map<Integer, ReferenceSettlementDoctrine> values) { return map(values, ReferenceCanonicalStateV2::doctrine); }
    private static Map<String, Object> doctrine(ReferenceSettlementDoctrine value) {
        return typed("simulation.v2.SettlementDoctrine", "fields", object(
                "caution", value.caution(), "solidarity", value.solidarity(), "commercial_dependence", value.commercialDependence(),
                "militancy", value.militancy(), "casualty_tolerance", value.casualtyTolerance(),
                "quarantine_willingness", value.quarantineWillingness(), "legitimacy", value.legitimacy()));
    }

    private static Map<String, Object> civicMap(Map<Integer, ReferenceCivicLedger> values) { return map(values, ReferenceCanonicalStateV2::civic); }
    private static Map<String, Object> civic(ReferenceCivicLedger value) {
        return typed("simulation.v2.CivicLedger", "fields", object(
                "state", enumValue("simulation.v2.CivicState", value.state().id()), "entered_day", value.enteredDay(),
                "food_reserve_days", value.foodReserveDays(), "legitimacy", value.legitimacy(), "quarantine_until", value.quarantineUntil(),
                "war_budget", value.warBudget(), "ration_fraction", value.rationFraction(), "reason", value.reason()));
    }

    private static Map<String, Object> reserveMap(Map<Integer, ReferenceV2ReservePolicy> values) { return map(values, ReferenceCanonicalStateV2::reserve); }
    private static Map<String, Object> reserve(ReferenceV2ReservePolicy value) {
        return typed("simulation.v2.ReservePolicy", "fields", object("settlement_id", value.settlementId(), "food_days", value.foodDays(),
                "medicine_days", value.medicineDays(), "ammo_target", value.ammoTarget(), "active", value.active()));
    }

    private static Map<String, Object> rationMap(Map<Integer, ReferenceV2RationPlan> values) { return map(values, ReferenceCanonicalStateV2::ration); }
    private static Map<String, Object> ration(ReferenceV2RationPlan value) {
        return typed("simulation.v2.RationPlan", "fields", object("settlement_id", value.settlementId(), "fraction", value.fraction(),
                "issued_day", value.issuedDay(), "reason", value.reason()));
    }

    private static Map<String, Object> regimeMap(Map<Integer, ReferenceEmergencyRegime> values) { return map(values, ReferenceCanonicalStateV2::regime); }
    private static Map<String, Object> regime(ReferenceEmergencyRegime value) {
        return typed("simulation.v2.EmergencyRegime", "fields", object("settlement_id", value.settlementId(),
                "state", enumValue("simulation.v2.CivicState", value.state().id()), "activated_day", value.activatedDay(),
                "war_budget", value.warBudget(), "quarantine_until", value.quarantineUntil(), "status", value.status()));
    }

    private static Map<String, Object> charterMap(Map<Integer, ReferenceCoalitionCharter> values) { return map(values, ReferenceCanonicalStateV2::charter); }
    private static Map<String, Object> charter(ReferenceCoalitionCharter value) {
        return typed("simulation.v2.CoalitionCharter", "fields", object(
                "id", value.id(), "leader_id", value.leaderId(), "members", sequence("tuple", value.members()), "target", value.target(),
                "opened_day", value.openedDay(), "expires_day", value.expiresDay(), "contribution", doubleMap(value.contribution()),
                "reserve_commitment", doubleMap(value.reserveCommitment()), "compensation_due", doubleMap(value.compensationDue()),
                "status", value.status(), "reason", value.reason()));
    }

    private static Map<String, Object> procurementMap(Map<Integer, ReferenceProcurementOrder> values) { return map(values, ReferenceCanonicalStateV2::procurement); }
    private static Map<String, Object> procurement(ReferenceProcurementOrder value) {
        return typed("simulation.v2.ProcurementOrder", "fields", object("id", value.id(), "settlement_id", value.settlementId(),
                "resource", resource(value.resource()), "quantity", value.quantity(), "max_price", value.maxPrice(), "issued_day", value.issuedDay(),
                "fulfilled", value.fulfilled(), "status", value.status()));
    }

    private static Map<String, Object> compensationMap(Map<Integer, ReferenceCompensationClaim> values) { return map(values, ReferenceCanonicalStateV2::claim); }
    private static Map<String, Object> claim(ReferenceCompensationClaim value) {
        return typed("simulation.v2.CompensationClaim", "fields", object("id", value.id(), "settlement_id", value.settlementId(),
                "company_id", value.companyId(), "amount", value.amount(), "due_day", value.dueDay(), "reason", value.reason(), "status", value.status()));
    }

    private static Map<String, Object> insuranceMap(Map<ReferenceRouteKey, ReferenceRouteInsurance> values) { return map(values, ReferenceCanonicalStateV2::insurance); }
    private static Map<String, Object> insurance(ReferenceRouteInsurance value) {
        return typed("simulation.v2.RouteInsurance", "fields", object("route_key", routeKey(value.routeKey()), "underwriter_id", value.underwriterId(),
                "premium", value.premium(), "coverage", value.coverage(), "expires_day", value.expiresDay(), "status", value.status()));
    }

    private static Map<String, Object> project(ReferenceCivicSiteProject value) {
        return typed("simulation.v2.CivicSiteProject", "fields", object("settlement_id", value.settlementId(), "site_id", value.siteId(),
                "action", value.action(), "days_remaining", value.daysRemaining()));
    }

    private static Map<String, Object> chrysalisMap(Map<Integer, ReferenceNeuralChrysalis> values) { return map(values, ReferenceCanonicalStateV2::chrysalis); }
    private static Map<String, Object> chrysalis(ReferenceNeuralChrysalis value) {
        return typed("simulation.v2.NeuralChrysalis", "fields", object("organ_id", value.organId(), "sector_key", value.sectorKey(),
                "started_day", value.startedDay(), "days_remaining", value.daysRemaining(), "biomass_committed", value.biomassCommitted(), "status", value.status()));
    }

    private static Map<String, Object> lifecycleMap(Map<String, ReferenceHiveLifecycle> values) {
        return map(values, value -> enumValue("simulation.v2.HiveLifecycle", value.id()));
    }

    private static Map<String, Object> campaignMap(Map<Integer, ReferenceFrontCampaign> values) { return map(values, ReferenceCanonicalStateV2::campaign); }
    private static Map<String, Object> campaign(ReferenceFrontCampaign value) {
        return typed("simulation.v2.FrontCampaign", "fields", object(
                "id", value.id(), "kind", enumValue("simulation.v2.FrontCampaignKind", value.kind().id()), "leader_id", value.leaderId(),
                "contributors", sequence("tuple", value.contributors()), "target_sector", value.targetSector(), "created_day", value.createdDay(),
                "phase", enumValue("simulation.v2.FrontPhase", value.phase().id()), "personnel_by_settlement", doubleMap(value.personnelBySettlement()),
                "resident_ids_by_settlement", residentMap(value.residentIdsBySettlement()), "unit_composition_by_settlement", compositionMap(value.unitCompositionBySettlement()),
                "field_campaign_id", value.fieldCampaignId(), "post_id", value.postId(), "hold_days", value.holdDays(), "reason", value.reason(),
                "risk", value.risk(), "status_reason", value.statusReason(),
                "terminal_outcome", value.terminalOutcome() == null ? null : enumValue("simulation.v2.FrontPhase", value.terminalOutcome().id())));
    }

    private static Map<String, Object> supplyMap(Map<Integer, ReferenceSupplyLineStatus> values) { return map(values, ReferenceCanonicalStateV2::supply); }
    private static Map<String, Object> supply(ReferenceSupplyLineStatus value) {
        return typed("simulation.v2.SupplyLineStatus", "fields", object("campaign_id", value.campaignId(), "source_sector", value.sourceSector(),
                "target_sector", value.targetSector(), "sectors", sequence("tuple", value.sectors()), "connected", value.connected(),
                "risk", value.risk(), "readiness", value.readiness(), "reason", value.reason()));
    }

    private static Map<String, Object> engagement(ReferenceSectorEngagement value) {
        return typed("simulation.v2.SectorEngagement", "fields", object("day", value.day(), "sector_key", value.sectorKey(), "kind", value.kind(),
                "attacker", value.attacker(), "defender", value.defender(), "power", value.power(), "cordon_change", value.cordonChange(),
                "infection_change", value.infectionChange(), "personnel_loss", value.personnelLoss(), "outcome", value.outcome()));
    }

    private static Map<String, Object> decision(ReferenceV2DecisionReceipt value) {
        return map(List.of(pair("action", value.action()), pair("agent", "settlement:" + value.settlementId()), pair("blockers", value.blockers()),
                pair("day", value.day()), pair("reason", value.reason()), pair("risk", value.risk())));
    }

    private static Map<String, Object> positions(List<ReferenceGridPosition> values) {
        return sequence("tuple", values.stream().map(value -> sequence("tuple", List.of(value.x(), value.y()))).toList());
    }

    private static Map<String, Object> routeKeys(List<ReferenceRouteKey> values) {
        return sequence("tuple", values.stream().map(ReferenceCanonicalStateV2::routeKey).toList());
    }

    private static Map<String, Object> routeKey(ReferenceRouteKey value) { return sequence("tuple", List.of(value.lowerSettlementId(), value.upperSettlementId())); }

    private static Map<String, Object> integerMap(Map<Integer, Integer> values) { return map(values, value -> value); }
    private static Map<String, Object> doubleMap(Map<Integer, Double> values) { return map(values, value -> value); }

    private static Map<String, Object> residentMap(Map<Integer, List<String>> values) {
        return map(values, value -> sequence("tuple", value));
    }

    private static Map<String, Object> compositionMap(Map<Integer, Map<ReferenceHumanUnitKind, Double>> values) {
        return map(values, roles -> {
            List<List<Object>> pairs = new ArrayList<>();
            roles.forEach((kind, amount) -> pairs.add(pair(enumValue("simulation.formations.HumanUnitKind", kind.id()), amount)));
            return map(pairs);
        });
    }

    private static Map<String, Object> resource(ReferenceResource value) {
        return enumValue("simulation.economy.Resource", value.name().toLowerCase(Locale.ROOT));
    }

    private static <K, V> Map<String, Object> map(Map<K, V> values, java.util.function.Function<V, Object> encoder) {
        List<List<Object>> pairs = new ArrayList<>();
        values.forEach((key, value) -> pairs.add(pair(key(key), encoder.apply(value))));
        return map(pairs);
    }

    private static Object key(Object value) {
        if (value instanceof ReferenceRouteKey routeKey) return routeKey(routeKey);
        return value;
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

    private static Map<String, Object> enumValue(String type, String value) { return object("$enum", type, "value", value); }
    private static Map<String, Object> typed(String type, String fieldName, Map<String, Object> fields) { return object("$type", type, fieldName, fields); }
    private static Map<String, Object> sequence(String kind, List<?> items) { return object("$sequence", kind, "items", List.copyOf(items)); }

    private static Map<String, Object> object(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("object entries must be pairs");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put((String) entries[index], entries[index + 1]);
        return Collections.unmodifiableMap(result);
    }
}
