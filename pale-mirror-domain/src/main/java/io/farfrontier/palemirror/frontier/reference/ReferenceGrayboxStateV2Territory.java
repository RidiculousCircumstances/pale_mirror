package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict V2 territorial, cognition and civic-record readers. */
final class ReferenceGrayboxStateV2Territory {
    private ReferenceGrayboxStateV2Territory() { }

    static LinkedHashMap<String, ReferenceV2OperationalSector> sectors(Object encoded) {
        LinkedHashMap<String, ReferenceV2OperationalSector> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 sectors")) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), "V2 sector key");
            ReferenceV2OperationalSector sector = sector(entry.value());
            if (!key.equals(sector.key()) || result.putIfAbsent(key, sector) != null) throw new IllegalArgumentException("V2 sector is invalid");
        }
        if (result.isEmpty()) throw new IllegalArgumentException("V2 sectors are empty");
        return result;
    }

    static LinkedHashMap<String, ReferenceV2SectorControl> control(Object encoded, Map<String, ReferenceV2OperationalSector> sectors) {
        LinkedHashMap<String, ReferenceV2SectorControl> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 sector control")) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), "V2 control key");
            ReferenceV2SectorControl value = control(entry.value());
            if (!key.equals(value.sectorKey()) || !sectors.containsKey(key) || result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("V2 sector control is invalid");
            }
        }
        if (!result.keySet().equals(sectors.keySet())) throw new IllegalArgumentException("V2 sector control is incomplete");
        return result;
    }

    static LinkedHashMap<Integer, ReferenceV2HumanPerception> humanPerceptions(Object encoded) {
        LinkedHashMap<Integer, ReferenceV2HumanPerception> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 human perceptions")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 perception settlement");
            ReferenceV2HumanPerception value = humanPerception(entry.value());
            if (settlement != value.settlementId() || result.putIfAbsent(settlement, value) != null) {
                throw new IllegalArgumentException("V2 human perception is invalid");
            }
        }
        return result;
    }

    static ReferenceV2HivePerception hivePerception(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.HivePerception", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 hive perception", "beliefs");
        ReferenceV2HivePerception result = new ReferenceV2HivePerception();
        result.mutableBeliefs().putAll(beliefs(fields.get("beliefs"), "V2 hive beliefs"));
        return result;
    }

    static LinkedHashMap<Integer, ReferenceSettlementDoctrine> doctrines(Object encoded) {
        LinkedHashMap<Integer, ReferenceSettlementDoctrine> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 doctrines")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 doctrine settlement");
            if (result.putIfAbsent(settlement, doctrine(entry.value())) != null) throw new IllegalArgumentException("V2 doctrine duplicates a settlement");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceCivicLedger> civics(Object encoded) {
        LinkedHashMap<Integer, ReferenceCivicLedger> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 civics")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 civic settlement");
            if (result.putIfAbsent(settlement, civic(entry.value())) != null) throw new IllegalArgumentException("V2 civic duplicates a settlement");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceV2ReservePolicy> reservePolicies(Object encoded) {
        LinkedHashMap<Integer, ReferenceV2ReservePolicy> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 reserve policies")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 reserve settlement");
            ReferenceV2ReservePolicy value = reservePolicy(entry.value());
            if (settlement != value.settlementId() || result.putIfAbsent(settlement, value) != null) throw new IllegalArgumentException("V2 reserve policy is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceV2RationPlan> rationPlans(Object encoded) {
        LinkedHashMap<Integer, ReferenceV2RationPlan> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 ration plans")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 ration settlement");
            ReferenceV2RationPlan value = rationPlan(entry.value());
            if (settlement != value.settlementId() || result.putIfAbsent(settlement, value) != null) throw new IllegalArgumentException("V2 ration plan is invalid");
        }
        return result;
    }

    static LinkedHashMap<Integer, ReferenceEmergencyRegime> emergencyRegimes(Object encoded) {
        LinkedHashMap<Integer, ReferenceEmergencyRegime> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 emergency regimes")) {
            int settlement = ReferenceGrayboxStateReader.integer(entry.key(), "V2 regime settlement");
            ReferenceEmergencyRegime value = emergencyRegime(entry.value());
            if (settlement != value.settlementId() || result.putIfAbsent(settlement, value) != null) throw new IllegalArgumentException("V2 emergency regime is invalid");
        }
        return result;
    }

    static LinkedHashMap<String, ReferenceHiveLifecycle> hiveLifecycle(Object encoded) {
        LinkedHashMap<String, ReferenceHiveLifecycle> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, "V2 hive lifecycle")) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), "V2 lifecycle key");
            ReferenceHiveLifecycle value = ReferenceGrayboxStateV2Support.enumById(entry.value(), "simulation.v2.HiveLifecycle",
                    ReferenceHiveLifecycle.values(), ReferenceHiveLifecycle::id, "V2 lifecycle value");
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("V2 hive lifecycle duplicates a component");
        }
        return result;
    }

    private static ReferenceV2OperationalSector sector(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.OperationalSector", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 sector", "x", "y", "cells", "organic_mass", "moisture", "scar", "infection",
                "spore_load", "human_access", "hive_influence", "infrastructure_value", "route_keys");
        int x = ReferenceGrayboxStateReader.integer(fields.get("x"), "V2 sector x");
        int y = ReferenceGrayboxStateReader.integer(fields.get("y"), "V2 sector y");
        if (x < 0 || y < 0) throw new IllegalArgumentException("V2 sector coordinates are invalid");
        ReferenceV2OperationalSector result = new ReferenceV2OperationalSector(x, y, ReferenceGrayboxStateV2Support.positions(fields.get("cells"), "V2 sector cells"));
        result.territory(ReferenceGrayboxStateV2Support.number(fields.get("organic_mass"), "V2 organic mass"),
                ReferenceGrayboxStateV2Support.number(fields.get("moisture"), "V2 moisture"), ReferenceGrayboxStateV2Support.number(fields.get("scar"), "V2 scar"),
                ReferenceGrayboxStateV2Support.number(fields.get("infection"), "V2 infection"), ReferenceGrayboxStateV2Support.number(fields.get("spore_load"), "V2 spores"),
                ReferenceGrayboxStateV2Support.number(fields.get("human_access"), "V2 human access"), ReferenceGrayboxStateV2Support.number(fields.get("hive_influence"), "V2 hive influence"),
                ReferenceGrayboxStateV2Support.number(fields.get("infrastructure_value"), "V2 infrastructure"), ReferenceGrayboxStateV2Support.routeKeys(fields.get("route_keys"), "V2 sector routes"));
        return result;
    }

    private static ReferenceV2SectorControl control(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.SectorControl", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 sector control", "sector_key", "state", "cordon_strength", "garrison", "supplied",
                "last_cleared_day", "last_changed_day", "held_days", "reason");
        ReferenceV2SectorControl result = new ReferenceV2SectorControl(ReferenceGrayboxStateReader.string(fields.get("sector_key"), "V2 control sector"));
        result.state(ReferenceGrayboxStateV2Support.enumById(fields.get("state"), "simulation.v2.SectorControlState", ReferenceSectorControlState.values(),
                ReferenceSectorControlState::id, "V2 control state"));
        result.cordonStrength(ReferenceGrayboxStateV2Support.number(fields.get("cordon_strength"), "V2 cordon strength"));
        result.garrison(ReferenceGrayboxStateV2Support.number(fields.get("garrison"), "V2 control garrison"));
        result.supplied(ReferenceGrayboxStateReader.bool(fields.get("supplied"), "V2 control supplied"));
        result.lastClearedDay(ReferenceGrayboxStateReader.integer(fields.get("last_cleared_day"), "V2 control cleared day"));
        result.lastChangedDay(ReferenceGrayboxStateReader.integer(fields.get("last_changed_day"), "V2 control changed day"));
        result.heldDays(ReferenceGrayboxStateReader.integer(fields.get("held_days"), "V2 control held days"));
        result.reason(ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 control reason"));
        return result;
    }

    private static ReferenceV2HumanPerception humanPerception(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.HumanPerception", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 human perception", "settlement_id", "beliefs");
        ReferenceV2HumanPerception result = new ReferenceV2HumanPerception(ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 perception settlement"));
        result.mutableBeliefs().putAll(beliefs(fields.get("beliefs"), "V2 human beliefs"));
        return result;
    }

    private static LinkedHashMap<String, ReferenceV2Belief> beliefs(Object encoded, String label) {
        LinkedHashMap<String, ReferenceV2Belief> result = new LinkedHashMap<>();
        for (ReferenceGrayboxStateReader.Entry entry : ReferenceGrayboxStateReader.mapEntries(encoded, label)) {
            String key = ReferenceGrayboxStateReader.string(entry.key(), label + " key");
            ReferenceV2Belief value = belief(entry.value());
            if (!key.equals(value.sectorKey()) || result.putIfAbsent(key, value) != null) throw new IllegalArgumentException(label + " is invalid");
        }
        return result;
    }

    private static ReferenceV2Belief belief(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.Belief", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 belief", "sector_key", "observed_day", "confidence", "source", "infection", "organic_mass",
                "hive_influence", "infrastructure_value", "chrysalis");
        return new ReferenceV2Belief(ReferenceGrayboxStateReader.string(fields.get("sector_key"), "V2 belief sector"),
                ReferenceGrayboxStateReader.integer(fields.get("observed_day"), "V2 belief day"), ReferenceGrayboxStateV2Support.number(fields.get("confidence"), "V2 belief confidence"),
                source(fields.get("source")), ReferenceGrayboxStateV2Support.number(fields.get("infection"), "V2 belief infection"),
                ReferenceGrayboxStateV2Support.number(fields.get("organic_mass"), "V2 belief organic mass"),
                ReferenceGrayboxStateV2Support.number(fields.get("hive_influence"), "V2 belief influence"),
                ReferenceGrayboxStateV2Support.number(fields.get("infrastructure_value"), "V2 belief infrastructure"),
                ReferenceGrayboxStateReader.bool(fields.get("chrysalis"), "V2 belief chrysalis"));
    }

    private static ReferenceSettlementDoctrine doctrine(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.SettlementDoctrine", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 doctrine", "caution", "solidarity", "commercial_dependence", "militancy", "casualty_tolerance",
                "quarantine_willingness", "legitimacy");
        ReferenceSettlementDoctrine result = new ReferenceSettlementDoctrine(ReferenceGrayboxStateV2Support.number(fields.get("caution"), "V2 caution"),
                ReferenceGrayboxStateV2Support.number(fields.get("solidarity"), "V2 solidarity"), ReferenceGrayboxStateV2Support.number(fields.get("commercial_dependence"), "V2 commercial dependence"),
                ReferenceGrayboxStateV2Support.number(fields.get("militancy"), "V2 militancy"), ReferenceGrayboxStateV2Support.number(fields.get("casualty_tolerance"), "V2 casualty tolerance"),
                ReferenceGrayboxStateV2Support.number(fields.get("quarantine_willingness"), "V2 quarantine willingness"));
        result.legitimacy(ReferenceGrayboxStateV2Support.number(fields.get("legitimacy"), "V2 doctrine legitimacy"));
        return result;
    }

    private static ReferenceCivicLedger civic(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.CivicLedger", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 civic", "state", "entered_day", "food_reserve_days", "legitimacy", "quarantine_until",
                "war_budget", "ration_fraction", "reason");
        ReferenceCivicLedger result = new ReferenceCivicLedger(ReferenceGrayboxStateV2Support.number(fields.get("legitimacy"), "V2 civic legitimacy"));
        result.state(ReferenceGrayboxStateV2Support.enumById(fields.get("state"), "simulation.v2.CivicState", ReferenceCivicState.values(), ReferenceCivicState::id, "V2 civic state"));
        result.enteredDay(ReferenceGrayboxStateReader.integer(fields.get("entered_day"), "V2 civic entered day"));
        result.foodReserveDays(ReferenceGrayboxStateV2Support.number(fields.get("food_reserve_days"), "V2 food reserve"));
        result.quarantineUntil(ReferenceGrayboxStateReader.integer(fields.get("quarantine_until"), "V2 quarantine until"));
        result.warBudget(ReferenceGrayboxStateV2Support.number(fields.get("war_budget"), "V2 war budget"));
        result.rationFraction(ReferenceGrayboxStateV2Support.number(fields.get("ration_fraction"), "V2 ration fraction"));
        result.reason(ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 civic reason"));
        return result;
    }

    private static ReferenceV2ReservePolicy reservePolicy(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.ReservePolicy", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 reserve policy", "settlement_id", "food_days", "medicine_days", "ammo_target", "active");
        ReferenceV2ReservePolicy result = new ReferenceV2ReservePolicy(ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 reserve settlement"),
                ReferenceGrayboxStateV2Support.number(fields.get("food_days"), "V2 reserve food days"),
                ReferenceGrayboxStateV2Support.number(fields.get("medicine_days"), "V2 reserve medicine days"),
                ReferenceGrayboxStateV2Support.number(fields.get("ammo_target"), "V2 reserve ammo target"));
        result.active(ReferenceGrayboxStateReader.bool(fields.get("active"), "V2 reserve active"));
        return result;
    }

    private static ReferenceV2RationPlan rationPlan(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.RationPlan", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 ration plan", "settlement_id", "fraction", "issued_day", "reason");
        ReferenceV2RationPlan result = new ReferenceV2RationPlan(ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 ration settlement"));
        result.fraction(ReferenceGrayboxStateV2Support.number(fields.get("fraction"), "V2 ration fraction"));
        result.issuedDay(ReferenceGrayboxStateReader.integer(fields.get("issued_day"), "V2 ration issued day"));
        result.reason(ReferenceGrayboxStateReader.string(fields.get("reason"), "V2 ration reason"));
        return result;
    }

    private static ReferenceEmergencyRegime emergencyRegime(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.EmergencyRegime", "fields");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 emergency regime", "settlement_id", "state", "activated_day", "war_budget", "quarantine_until", "status");
        ReferenceEmergencyRegime result = new ReferenceEmergencyRegime(ReferenceGrayboxStateReader.integer(fields.get("settlement_id"), "V2 regime settlement"),
                ReferenceGrayboxStateV2Support.enumById(fields.get("state"), "simulation.v2.CivicState", ReferenceCivicState.values(), ReferenceCivicState::id, "V2 regime state"),
                ReferenceGrayboxStateReader.integer(fields.get("activated_day"), "V2 regime activated day"),
                ReferenceGrayboxStateV2Support.number(fields.get("war_budget"), "V2 regime war budget"),
                ReferenceGrayboxStateReader.integer(fields.get("quarantine_until"), "V2 regime quarantine until"));
        result.status(ReferenceGrayboxStateReader.string(fields.get("status"), "V2 regime status"));
        return result;
    }

    private static ReferenceObservationSource source(Object encoded) {
        String value = ReferenceGrayboxStateReader.string(encoded, "V2 belief source");
        for (ReferenceObservationSource item : ReferenceObservationSource.values()) if (item.id().equals(value)) return item;
        throw new IllegalArgumentException("V2 belief source is unsupported");
    }
}
