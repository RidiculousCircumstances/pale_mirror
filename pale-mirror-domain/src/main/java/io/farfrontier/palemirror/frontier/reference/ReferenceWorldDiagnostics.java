package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded reporting-only history owned by one reference world.
 *
 * <p>No canonical rule reads these rows. Keeping them separate from world
 * construction prevents diagnostic retention from obscuring source state
 * ownership.</p>
 */
final class ReferenceWorldDiagnostics {
    private static final int HISTORY_DAYS = 365;

    private final List<ReferenceDailyWorldHistory> history = new ArrayList<>();
    private final LinkedHashMap<Integer, List<ReferenceDailySettlementHistory>> settlementHistory = new LinkedHashMap<>();
    private final List<ReferenceCombatReceipt> combatHistory = new ArrayList<>();
    private final List<ReferenceContainmentReceipt> containmentHistory = new ArrayList<>();

    List<ReferenceDailyWorldHistory> history() { return List.copyOf(history); }

    Map<Integer, List<ReferenceDailySettlementHistory>> settlementHistory() {
        LinkedHashMap<Integer, List<ReferenceDailySettlementHistory>> result = new LinkedHashMap<>();
        settlementHistory.forEach((settlementId, rows) -> result.put(settlementId, List.copyOf(rows)));
        return Collections.unmodifiableMap(result);
    }

    List<ReferenceCombatReceipt> combatHistory() { return List.copyOf(combatHistory); }
    List<ReferenceContainmentReceipt> containmentHistory() { return List.copyOf(containmentHistory); }

    void restoreState(
            List<ReferenceDailyWorldHistory> restoredHistory,
            Map<Integer, List<ReferenceDailySettlementHistory>> restoredSettlementHistory,
            List<ReferenceCombatReceipt> restoredCombatHistory,
            List<ReferenceContainmentReceipt> restoredContainmentHistory
    ) {
        List<ReferenceDailyWorldHistory> historyRequired = List.copyOf(Objects.requireNonNull(restoredHistory, "restoredHistory"));
        Map<Integer, List<ReferenceDailySettlementHistory>> settlementsRequired = Map.copyOf(Objects.requireNonNull(restoredSettlementHistory, "restoredSettlementHistory"));
        List<ReferenceCombatReceipt> combatRequired = List.copyOf(Objects.requireNonNull(restoredCombatHistory, "restoredCombatHistory"));
        List<ReferenceContainmentReceipt> containmentRequired = List.copyOf(Objects.requireNonNull(restoredContainmentHistory, "restoredContainmentHistory"));
        if (historyRequired.size() > HISTORY_DAYS || combatRequired.size() > HISTORY_DAYS || containmentRequired.size() > HISTORY_DAYS) {
            throw new IllegalArgumentException("diagnostic history exceeds retention");
        }
        for (Map.Entry<Integer, List<ReferenceDailySettlementHistory>> entry : settlementsRequired.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().size() > HISTORY_DAYS) {
                throw new IllegalArgumentException("settlement diagnostic history exceeds retention");
            }
        }
        history.clear(); history.addAll(historyRequired);
        settlementHistory.clear(); settlementsRequired.forEach((id, rows) -> settlementHistory.put(id, new ArrayList<>(rows)));
        combatHistory.clear(); combatHistory.addAll(combatRequired);
        containmentHistory.clear(); containmentHistory.addAll(containmentRequired);
    }

    void recordCombat(ReferenceCombatReceipt receipt) {
        combatHistory.add(Objects.requireNonNull(receipt, "receipt"));
        trim(combatHistory);
    }

    void recordContainment(ReferenceContainmentReceipt receipt) {
        containmentHistory.add(Objects.requireNonNull(receipt, "receipt"));
        trim(containmentHistory);
    }

    void recordHistory(ReferenceWorld world) {
        world.infection().recordEconomySnapshot(world.day(), HISTORY_DAYS);
        List<ReferenceSettlement> alive = world.marketWorld().settlements().values().stream().filter(ReferenceSettlement::alive).toList();
        double population = 0.0d;
        double cash = 0.0d;
        double privateCash = 0.0d;
        for (ReferenceSettlement settlement : alive) {
            population += settlement.population();
            cash += settlement.cash();
            ReferenceHouseholdLedger household = world.microeconomy().households().get(settlement.id());
            privateCash += household == null ? 0.0d : household.cash();
        }
        double bioforms = 0.0d;
        for (ReferenceSwarm swarm : world.infection().swarms()) {
            double composition = 0.0d;
            for (double count : swarm.composition().values()) composition += count;
            bioforms += composition;
        }
        double hiveBiomass = 0.0d;
        for (ReferenceHiveOrgan organ : world.infection().organs().values()) hiveBiomass += organ.biomass();
        int fieldCampaigns = (int) world.field().campaigns().values().stream().filter(campaign -> campaign.phase() != ReferenceCampaignPhase.COMPLETE
                && campaign.phase() != ReferenceCampaignPhase.FAILED).count();
        int v2Chrysalises = world.v2Enabled() ? world.v2().chrysalises().size() : 0;
        int v2Emergencies = world.v2Enabled() ? (int) world.v2().civics().values().stream().filter(civic -> civic.state() == ReferenceCivicState.EMERGENCY
                || civic.state() == ReferenceCivicState.SIEGE).count() : 0;
        history.add(new ReferenceDailyWorldHistory(world.day(), alive.size(), population, cash, privateCash,
                world.infection().infectedFraction(), world.infection().swarms().size(), bioforms, world.infection().organs().size(), hiveBiomass,
                world.infection().harvestedBiomass(),
                world.infection().ecosystem().totalOrganic(), world.infection().ecosystem().totalScar(), world.infection().feralFraction(),
                world.operations().active().size(), world.field().activePosts().size(), fieldCampaigns, world.field().engagements().size(), 0, 0,
                world.marketWorld().resourceSites().size(), (int) world.marketWorld().resourceSites().values().stream()
                        .filter(site -> site.contamination() >= .18d).count(), world.trade().recentVolume(30, world.day()), v2Chrysalises, v2Emergencies));
        trim(history);
        for (ReferenceSettlement settlement : world.marketWorld().settlements().values()) {
            EnumMap<ReferenceResource, ReferenceDailySettlementHistory.ReferenceResourceHistory> resources = new EnumMap<>(ReferenceResource.class);
            for (ReferenceResource resource : ReferenceResource.values()) {
                resources.put(resource, new ReferenceDailySettlementHistory.ReferenceResourceHistory(settlement.amount(resource),
                        world.economy().targetStock(settlement, resource), world.economy().localValue(settlement, resource),
                        settlement.dailyProduction().getOrDefault(resource, 0.0d), settlement.dailyConsumption().getOrDefault(resource, 0.0d)));
            }
            List<ReferenceDailySettlementHistory> rows = settlementHistory.computeIfAbsent(settlement.id(), ignored -> new ArrayList<>());
            rows.add(new ReferenceDailySettlementHistory(world.day(), settlement.alive(), settlement.population(), settlement.cash(), settlement.integrity(),
                    settlement.threat(), settlement.illnessBurden(), settlement.medicineFulfillment(), settlement.woundedPersonnel(), resources));
            trim(rows);
        }
    }

    private static <T> void trim(List<T> rows) {
        while (rows.size() > HISTORY_DAYS) rows.removeFirst();
    }
}
