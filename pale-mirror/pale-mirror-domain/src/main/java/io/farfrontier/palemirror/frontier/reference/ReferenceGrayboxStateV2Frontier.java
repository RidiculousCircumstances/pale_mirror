package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict V2 agreement, work, hive and frontier-record readers. */
final class ReferenceGrayboxStateV2Frontier {
    private ReferenceGrayboxStateV2Frontier() { }

    static LinkedHashMap<Integer, ReferenceCoalitionCharter> charters(Object encoded) {
        LinkedHashMap<Integer, ReferenceCoalitionCharter> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 charters")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 charter key");
            ReferenceCoalitionCharter value = charter(entry.value());
            if (id != value.id() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 charter is invalid");
        }
        return result;
    }

    static LinkedHashMap<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance(Object encoded) {
        LinkedHashMap<ReferenceRouteKey, ReferenceRouteInsurance> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 route insurance")) {
            ReferenceRouteKey key = ReferenceGrayboxStateV2Support.routeKey(entry.key(), "V2 insurance key");
            ReferenceRouteInsurance value = insurance(entry.value());
            if (!key.equals(value.routeKey()) || result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("V2 route insurance is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceProcurementOrder> procurements(Object encoded) {
        LinkedHashMap<Integer, ReferenceProcurementOrder> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 procurements")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 procurement key");
            ReferenceProcurementOrder value = procurement(entry.value());
            if (id != value.id() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 procurement is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceCompensationClaim> compensation(Object encoded) {
        LinkedHashMap<Integer, ReferenceCompensationClaim> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 compensation")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 compensation key");
            ReferenceCompensationClaim value = claim(entry.value());
            if (id != value.id() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 compensation is invalid");
        }
        return result;
    }

    static List<ReferenceCivicSiteProject> civicProjects(Object encoded) {
        List<ReferenceCivicSiteProject> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "V2 civic projects")) result.add(project(item));
        return List.copyOf(result);
    }

    static LinkedHashMap<Integer, ReferenceNeuralChrysalis> chrysalises(Object encoded) {
        LinkedHashMap<Integer, ReferenceNeuralChrysalis> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 chrysalises")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 chrysalis key");
            ReferenceNeuralChrysalis value = chrysalis(entry.value());
            if (id != value.organId() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 chrysalis is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceFrontCampaign> campaigns(Object encoded) {
        LinkedHashMap<Integer, ReferenceFrontCampaign> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 front campaigns")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 campaign key");
            ReferenceFrontCampaign value = campaign(entry.value());
            if (id != value.id() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 front campaign is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceSupplyLineStatus> supplyLines(Object encoded) {
        LinkedHashMap<Integer, ReferenceSupplyLineStatus> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 supply lines")) {
            int id = ReferenceGrayboxStateReader.integer(entry.key(), "V2 supply line key");
            ReferenceSupplyLineStatus value = supplyLine(entry.value());
            if (id != value.campaignId() || id < 1 || result.putIfAbsent(id, value) != null) throw new IllegalArgumentException("V2 supply line is invalid");
        }
        return result;
    }

    static List<ReferenceSectorEngagement> engagements(Object encoded) {
        List<ReferenceSectorEngagement> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "V2 sector engagements")) result.add(engagement(item));
        if (result.size() > 128) throw new IllegalArgumentException("V2 sector engagement history is unbounded");
        return List.copyOf(result);
    }

    static List<ReferenceV2DecisionReceipt> decisions(Object encoded) {
        List<ReferenceV2DecisionReceipt> result = new ArrayList<>();
        for (Object item : ReferenceGrayboxStateReader.sequence(encoded, "list", "V2 decisions")) result.add(decision(item));
        return List.copyOf(result);
    }

    private static ReferenceCoalitionCharter charter(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.CoalitionCharter", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 charter", "id", "leader_id", "members", "target", "opened_day", "expires_day",
                "contribution", "reserve_commitment", "compensation_due", "status", "reason");
        ReferenceCoalitionCharter result = new ReferenceCoalitionCharter(ReferenceGrayboxStateReader.integer(fields.get("id"), "V2 charter id"),
                ReferenceGrayboxStateReader.integer(fields.get("leader_id"), "V2 charter leader"), ReferenceGrayboxStateV2Support.integerTuple(fields.get("members"), "V2 charter members"),
                ReferenceGrayboxStateReader.string(fields.get("target"), "V2 charter target"), ReferenceGrayboxStateReader.integer(fields.get("opened_day"), "V2 charter opened day"),
                ReferenceGrayboxStateReader.integer(fields.get("expires_day"), "V2 charter expiry day"), ReferenceGrayboxStateV2Support.doubles(fields.get("contribution"), "V2 charter contribution"),
                ReferenceGrayboxStateV2Support.doubles(fields.get("reserve_commitment"), "V2 charter reserve commitment"),
                ReferenceGrayboxStateV2Support.doubles(fields.get("compensation_due"), "V2 charter compensation due"));
        result.status(ReferenceGrayboxStateReader.string(fields.get("status"), "V2 charter status"));
        result.reason(ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 charter reason"));
        return result;
    }

    private static ReferenceRouteInsurance insurance(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.RouteInsurance", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 route insurance", "route_key", "underwriter_id", "premium", "coverage", "expires_day", "status");
        return new ReferenceRouteInsurance(ReferenceGrayboxStateV2Support.routeKey(fields.get("route_key"), "V2 insurance route"),
                ReferenceGrayboxStateReader.integer(fields.get("underwriter_id"), "V2 insurance underwriter"),
                ReferenceGrayboxStateV2Support.number(fields.get("premium"), "V2 insurance premium"), ReferenceGrayboxStateV2Support.number(fields.get("coverage"), "V2 insurance coverage"),
                ReferenceGrayboxStateReader.integer(fields.get("expires_day"), "V2 insurance expiry"), ReferenceGrayboxStateReader.string(fields.get("status"), "V2 insurance status"));
    }

    private static ReferenceProcurementOrder procurement(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.ProcurementOrder", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 procurement", "id", "settlement_id", "resource", "quantity", "max_price", "issued_day", "fulfilled", "status");
        return new ReferenceProcurementOrder(ReferenceGrayboxStateReader.integer(fields.get("id"), "V2 procurement id"),
                ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 procurement settlement"), ReferenceGrayboxStateV2Support.resource(fields.get("resource"), "V2 procurement resource"),
                ReferenceGrayboxStateV2Support.number(fields.get("quantity"), "V2 procurement quantity"), ReferenceGrayboxStateV2Support.number(fields.get("max_price"), "V2 procurement price"),
                ReferenceGrayboxStateReader.integer(fields.get("issued_day"), "V2 procurement day"), ReferenceGrayboxStateV2Support.number(fields.get("fulfilled"), "V2 procurement fulfilled"),
                ReferenceGrayboxStateReader.string(fields.get("status"), "V2 procurement status"));
    }

    private static ReferenceCompensationClaim claim(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.CompensationClaim", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 compensation claim", "id", "settlement_id", "company_id", "amount", "due_day", "reason", "status");
        ReferenceCompensationClaim result = new ReferenceCompensationClaim(ReferenceGrayboxStateReader.integer(fields.get("id"), "V2 claim id"),
                ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 claim settlement"), ReferenceGrayboxStateReader.integer(fields.get("company_id"), "V2 claim company"),
                ReferenceGrayboxStateV2Support.number(fields.get("amount"), "V2 claim amount"), ReferenceGrayboxStateReader.integer(fields.get("due_day"), "V2 claim due day"),
                ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 claim reason"));
        result.status(ReferenceGrayboxStateReader.string(fields.get("status"), "V2 claim status"));
        return result;
    }

    private static ReferenceCivicSiteProject project(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.CivicSiteProject", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 civic project", "settlement_id", "site_id", "action", "days_remaining");
        return new ReferenceCivicSiteProject(ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 project settlement"),
                ReferenceGrayboxStateReader.integer(fields.get("site_id"), "V2 project site"), ReferenceGrayboxStateReader.string(fields.get("action"), "V2 project action"),
                ReferenceGrayboxStateReader.integer(fields.get("days_remaining"), "V2 project days"));
    }

    private static ReferenceNeuralChrysalis chrysalis(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.NeuralChrysalis", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 chrysalis", "organ_id", "sector_key", "started_day", "days_remaining", "biomass_committed", "status");
        ReferenceNeuralChrysalis result = new ReferenceNeuralChrysalis(ReferenceGrayboxStateReader.integer(fields.get("organ_id"), "V2 chrysalis organ"),
                ReferenceGrayboxStateReader.string(fields.get("sector_key"), "V2 chrysalis sector"), ReferenceGrayboxStateReader.integer(fields.get("started_day"), "V2 chrysalis start day"),
                ReferenceGrayboxStateReader.integer(fields.get("days_remaining"), "V2 chrysalis days"), ReferenceGrayboxStateV2Support.number(fields.get("biomass_committed"), "V2 chrysalis biomass"));
        result.status(ReferenceGrayboxStateReader.string(fields.get("status"), "V2 chrysalis status"));
        return result;
    }

    private static ReferenceFrontCampaign campaign(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.FrontCampaign", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 front campaign", "id", "kind", "leader_id", "contributors", "target_sector", "created_day", "phase",
                "personnel_by_settlement", "resident_ids_by_settlement", "unit_composition_by_settlement", "field_campaign_id", "post_id", "hold_days", "reason",
                "risk", "status_reason", "terminal_outcome");
        ReferenceFrontCampaign result = new ReferenceFrontCampaign(ReferenceGrayboxStateReader.integer(fields.get("id"), "V2 campaign id"),
                ReferenceGrayboxStateV2Support.enumById(fields.get("kind"), "simulation.v2.FrontCampaignKind", ReferenceFrontCampaignKind.values(), ReferenceFrontCampaignKind::id, "V2 campaign kind"),
                ReferenceGrayboxStateReader.integer(fields.get("leader_id"), "V2 campaign leader"), ReferenceGrayboxStateV2Support.integerTuple(fields.get("contributors"), "V2 campaign contributors"),
                ReferenceGrayboxStateReader.string(fields.get("target_sector"), "V2 campaign target"), ReferenceGrayboxStateReader.integer(fields.get("created_day"), "V2 campaign day"),
                ReferenceGrayboxStateV2Support.enumById(fields.get("phase"), "simulation.v2.FrontPhase", ReferenceFrontPhase.values(), ReferenceFrontPhase::id, "V2 campaign phase"),
                ReferenceGrayboxStateV2Support.doubles(fields.get("personnel_by_settlement"), "V2 campaign personnel"),
                ReferenceGrayboxStateV2Support.residents(fields.get("resident_ids_by_settlement"), "V2 campaign residents"),
                ReferenceGrayboxStateV2Support.composition(fields.get("unit_composition_by_settlement"), "V2 campaign composition"),
                ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 campaign reason"));
        result.fieldCampaignId(ReferenceGrayboxStateReader.nullableInteger(fields.get("field_campaign_id"), "V2 campaign field campaign"));
        result.postId(ReferenceGrayboxStateReader.nullableInteger(fields.get("post_id"), "V2 campaign post"));
        result.holdDays(ReferenceGrayboxStateReader.integer(fields.get("hold_days"), "V2 campaign hold days"));
        result.risk(ReferenceGrayboxStateV2Support.number(fields.get("risk"), "V2 campaign risk"));
        result.statusReason(ReferenceGrayboxStateReader.string(fields.get("status_reason"), "V2 campaign status reason"));
        if (fields.get("terminal_outcome") != null) result.terminalOutcome(ReferenceGrayboxStateV2Support.enumById(fields.get("terminal_outcome"),
                "simulation.v2.FrontPhase", ReferenceFrontPhase.values(), ReferenceFrontPhase::id, "V2 campaign terminal outcome"));
        return result;
    }

    private static ReferenceSupplyLineStatus supplyLine(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.SupplyLineStatus", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 supply line", "campaign_id", "source_sector", "target_sector", "sectors", "connected", "risk", "readiness", "reason");
        return new ReferenceSupplyLineStatus(ReferenceGrayboxStateReader.integer(fields.get("campaign_id"), "V2 supply campaign"),
                ReferenceGrayboxStateReader.string(fields.get("source_sector"), "V2 supply source"), ReferenceGrayboxStateReader.string(fields.get("target_sector"), "V2 supply target"),
                ReferenceGrayboxStateV2Support.strings(fields.get("sectors"), "V2 supply sectors"), ReferenceGrayboxStateReader.bool(fields.get("connected"), "V2 supply connected"),
                ReferenceGrayboxStateV2Support.number(fields.get("risk"), "V2 supply risk"), ReferenceGrayboxStateV2Support.number(fields.get("readiness"), "V2 supply readiness"),
                ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 supply reason"));
    }

    private static ReferenceSectorEngagement engagement(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.SectorEngagement", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 sector engagement", "day", "sector_key", "kind", "attacker", "defender", "power", "cordon_change",
                "infection_change", "personnel_loss", "outcome");
        return new ReferenceSectorEngagement(ReferenceGrayboxStateReader.integer(fields.get("day"), "V2 engagement day"),
                ReferenceGrayboxStateReader.string(fields.get("sector_key"), "V2 engagement sector"), ReferenceGrayboxStateReader.string(fields.get("kind"), "V2 engagement kind"),
                ReferenceGrayboxStateReader.string(fields.get("attacker"), "V2 engagement attacker"), ReferenceGrayboxStateReader.string(fields.get("defender"), "V2 engagement defender"),
                ReferenceGrayboxStateV2Support.number(fields.get("power"), "V2 engagement power"), ReferenceGrayboxStateV2Support.number(fields.get("cordon_change"), "V2 engagement cordon"),
                ReferenceGrayboxStateV2Support.number(fields.get("infection_change"), "V2 engagement infection"),
                ReferenceGrayboxStateV2Support.number(fields.get("personnel_loss"), "V2 engagement personnel"), ReferenceGrayboxStateReader.string(fields.get("outcome"), "V2 engagement outcome"));
    }

    private static ReferenceV2DecisionReceipt decision(Object encoded) {
        Map<String, Object> values = ReferenceGrayboxStateReader.stringMap(encoded, "V2 decision");
        ReferenceGrayboxStateReader.exactKeys(values, "V2 decision", "action", "agent", "blockers", "day", "reason", "risk");
        String agent = ReferenceGrayboxStateReader.string(values.get("agent"), "V2 decision agent");
        if (!agent.startsWith("settlement:")) throw new IllegalArgumentException("V2 decision agent is invalid");
        int settlement;
        try { settlement = Integer.parseInt(agent.substring("settlement:".length())); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("V2 decision agent is invalid", error); }
        return new ReferenceV2DecisionReceipt(ReferenceGrayboxStateReader.integer(values.get("day"), "V2 decision day"), settlement,
                ReferenceGrayboxStateReader.string(values.get("action"), "V2 decision action"), ReferenceGrayboxStateV2Support.number(values.get("risk"), "V2 decision risk"),
                ReferenceGrayboxStateReader.string(values.get("reason"), "V2 decision reason"), ReferenceGrayboxStateReader.string(values.get("blockers"), "V2 decision blockers"));
    }
}
