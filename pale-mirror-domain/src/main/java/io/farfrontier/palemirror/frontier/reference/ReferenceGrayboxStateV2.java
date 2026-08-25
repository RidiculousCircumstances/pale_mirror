package io.farfrontier.palemirror.frontier.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict all-or-nothing hydration of Python's complete V2 state owner. */
final class ReferenceGrayboxStateV2 {
    private ReferenceGrayboxStateV2() { }

    static State read(Object encoded) {
        Map<String, Object> fields = ReferenceGrayboxStateReader.typed(encoded, "simulation.v2.V2State", "attributes");
        ReferenceGrayboxStateReader.exactKeys(fields, "V2 state", "_next_charter_id", "_next_claim_id", "_next_front_campaign_id",
                "_next_procurement_id", "charters", "chrysalises", "civic_site_projects", "civics", "compensation", "decision_history",
                "doctrines", "emergency_regimes", "front_campaigns", "frontier_cooldown_until", "hive_lifecycle", "hive_perception",
                "human_perceptions", "last_civic_work_day", "procurements", "profile", "ration_plans", "reserve_policies", "rng",
                "route_insurance", "sector_control", "sector_engagements", "sectors", "supply_lines");
        ReferenceSimulationProfile profile = ReferenceGrayboxStateV2Support.profile(fields.get("profile"));
        LinkedHashMap<String, ReferenceV2OperationalSector> sectors = ReferenceGrayboxStateV2Territory.sectors(fields.get("sectors"));
        LinkedHashMap<String, ReferenceV2SectorControl> control = ReferenceGrayboxStateV2Territory.control(fields.get("sector_control"), sectors);
        LinkedHashMap<Integer, ReferenceV2HumanPerception> perceptions = ReferenceGrayboxStateV2Territory.humanPerceptions(fields.get("human_perceptions"));
        ReferenceV2HivePerception hivePerception = ReferenceGrayboxStateV2Territory.hivePerception(fields.get("hive_perception"));
        LinkedHashMap<Integer, ReferenceSettlementDoctrine> doctrines = ReferenceGrayboxStateV2Territory.doctrines(fields.get("doctrines"));
        LinkedHashMap<Integer, ReferenceCivicLedger> civics = ReferenceGrayboxStateV2Territory.civics(fields.get("civics"));
        LinkedHashMap<Integer, ReferenceV2ReservePolicy> reserves = ReferenceGrayboxStateV2Territory.reservePolicies(fields.get("reserve_policies"));
        LinkedHashMap<Integer, ReferenceV2RationPlan> rations = ReferenceGrayboxStateV2Territory.rationPlans(fields.get("ration_plans"));
        LinkedHashMap<Integer, ReferenceEmergencyRegime> regimes = ReferenceGrayboxStateV2Territory.emergencyRegimes(fields.get("emergency_regimes"));
        LinkedHashMap<Integer, ReferenceCoalitionCharter> charters = ReferenceGrayboxStateV2Frontier.charters(fields.get("charters"));
        LinkedHashMap<ReferenceRouteKey, ReferenceRouteInsurance> insurance = ReferenceGrayboxStateV2Frontier.routeInsurance(fields.get("route_insurance"));
        LinkedHashMap<Integer, ReferenceProcurementOrder> procurements = ReferenceGrayboxStateV2Frontier.procurements(fields.get("procurements"));
        LinkedHashMap<Integer, ReferenceCompensationClaim> compensation = ReferenceGrayboxStateV2Frontier.compensation(fields.get("compensation"));
        List<ReferenceCivicSiteProject> projects = ReferenceGrayboxStateV2Frontier.civicProjects(fields.get("civic_site_projects"));
        LinkedHashMap<Integer, ReferenceNeuralChrysalis> chrysalises = ReferenceGrayboxStateV2Frontier.chrysalises(fields.get("chrysalises"));
        LinkedHashMap<String, ReferenceHiveLifecycle> lifecycle = ReferenceGrayboxStateV2Territory.hiveLifecycle(fields.get("hive_lifecycle"));
        LinkedHashMap<Integer, ReferenceFrontCampaign> campaigns = ReferenceGrayboxStateV2Frontier.campaigns(fields.get("front_campaigns"));
        LinkedHashMap<Integer, ReferenceSupplyLineStatus> supply = ReferenceGrayboxStateV2Frontier.supplyLines(fields.get("supply_lines"));
        List<ReferenceSectorEngagement> engagements = ReferenceGrayboxStateV2Frontier.engagements(fields.get("sector_engagements"));
        List<ReferenceV2DecisionReceipt> decisions = ReferenceGrayboxStateV2Frontier.decisions(fields.get("decision_history"));
        int nextCharter = ReferenceGrayboxStateReader.integer(fields.get("_next_charter_id"), "V2 next charter id");
        int nextClaim = ReferenceGrayboxStateReader.integer(fields.get("_next_claim_id"), "V2 next claim id");
        int nextFrontCampaign = ReferenceGrayboxStateReader.integer(fields.get("_next_front_campaign_id"), "V2 next campaign id");
        int nextProcurement = ReferenceGrayboxStateReader.integer(fields.get("_next_procurement_id"), "V2 next procurement id");
        ReferenceGrayboxStateV2Support.sequence(nextCharter, charters.keySet(), "V2 charter");
        ReferenceGrayboxStateV2Support.sequence(nextClaim, compensation.keySet(), "V2 claim");
        ReferenceGrayboxStateV2Support.sequence(nextFrontCampaign, campaigns.keySet(), "V2 campaign");
        ReferenceGrayboxStateV2Support.sequence(nextProcurement, procurements.keySet(), "V2 procurement");
        State result = new State(profile, ReferenceGrayboxStateReader.randomState(fields.get("rng"), "V2 RNG"), sectors, control, perceptions,
                hivePerception, doctrines, civics, reserves, rations, regimes, charters, insurance, procurements, compensation, projects,
                ReferenceGrayboxStateV2Support.integers(fields.get("last_civic_work_day"), "V2 civic work days"), chrysalises, lifecycle,
                campaigns, supply, engagements, ReferenceGrayboxStateV2Support.integers(fields.get("frontier_cooldown_until"), "V2 frontier cooldown"),
                decisions, nextProcurement, nextClaim, nextCharter, nextFrontCampaign);
        result.validateLocalGraph();
        return result;
    }

    record State(
            ReferenceSimulationProfile profile,
            PythonRandom.State rng,
            Map<String, ReferenceV2OperationalSector> sectors,
            Map<String, ReferenceV2SectorControl> sectorControl,
            Map<Integer, ReferenceV2HumanPerception> humanPerceptions,
            ReferenceV2HivePerception hivePerception,
            Map<Integer, ReferenceSettlementDoctrine> doctrines,
            Map<Integer, ReferenceCivicLedger> civics,
            Map<Integer, ReferenceV2ReservePolicy> reservePolicies,
            Map<Integer, ReferenceV2RationPlan> rationPlans,
            Map<Integer, ReferenceEmergencyRegime> emergencyRegimes,
            Map<Integer, ReferenceCoalitionCharter> charters,
            Map<ReferenceRouteKey, ReferenceRouteInsurance> routeInsurance,
            Map<Integer, ReferenceProcurementOrder> procurements,
            Map<Integer, ReferenceCompensationClaim> compensation,
            List<ReferenceCivicSiteProject> civicSiteProjects,
            Map<Integer, Integer> lastCivicWorkDay,
            Map<Integer, ReferenceNeuralChrysalis> chrysalises,
            Map<String, ReferenceHiveLifecycle> hiveLifecycle,
            Map<Integer, ReferenceFrontCampaign> frontCampaigns,
            Map<Integer, ReferenceSupplyLineStatus> supplyLines,
            List<ReferenceSectorEngagement> sectorEngagements,
            Map<Integer, Integer> frontierCooldownUntil,
            List<ReferenceV2DecisionReceipt> decisionHistory,
            int nextProcurementId,
            int nextClaimId,
            int nextCharterId,
            int nextFrontCampaignId
    ) {
        State {
            if (profile != ReferenceSimulationProfile.GRAYBOX_1_40) throw new IllegalArgumentException("V2 state has an unsupported profile");
            sectors = immutable(sectors); sectorControl = immutable(sectorControl); humanPerceptions = immutable(humanPerceptions);
            doctrines = immutable(doctrines); civics = immutable(civics); reservePolicies = immutable(reservePolicies); rationPlans = immutable(rationPlans);
            emergencyRegimes = immutable(emergencyRegimes); charters = immutable(charters); routeInsurance = immutable(routeInsurance);
            procurements = immutable(procurements); compensation = immutable(compensation); civicSiteProjects = List.copyOf(civicSiteProjects);
            lastCivicWorkDay = immutable(lastCivicWorkDay); chrysalises = immutable(chrysalises); hiveLifecycle = immutable(hiveLifecycle);
            frontCampaigns = immutable(frontCampaigns); supplyLines = immutable(supplyLines); sectorEngagements = List.copyOf(sectorEngagements);
            frontierCooldownUntil = immutable(frontierCooldownUntil); decisionHistory = List.copyOf(decisionHistory);
        }

        void applyTo(ReferenceV2State target) {
            if (target.profile() != profile) throw new IllegalArgumentException("V2 target profile differs from state");
            validateTargetGeometry(target);
            replace(target.mutableSectors(), sectors); replace(target.mutableSectorControl(), sectorControl);
            replace(target.mutableHumanPerceptions(), humanPerceptions);
            target.mutableHivePerception().mutableBeliefs().clear();
            target.mutableHivePerception().mutableBeliefs().putAll(hivePerception.beliefs());
            replace(target.mutableDoctrines(), doctrines); replace(target.mutableCivics(), civics); replace(target.mutableReservePolicies(), reservePolicies);
            replace(target.mutableRationPlans(), rationPlans); replace(target.mutableEmergencyRegimes(), emergencyRegimes); replace(target.mutableCharters(), charters);
            replace(target.mutableRouteInsurance(), routeInsurance); replace(target.mutableProcurements(), procurements); replace(target.mutableCompensation(), compensation);
            replace(target.mutableLastCivicWorkDay(), lastCivicWorkDay); replace(target.mutableChrysalises(), chrysalises); replace(target.mutableHiveLifecycle(), hiveLifecycle);
            replace(target.mutableFrontCampaigns(), frontCampaigns); replace(target.mutableSupplyLines(), supplyLines); replace(target.mutableFrontierCooldownUntil(), frontierCooldownUntil);
            replace(target.mutableCivicSiteProjects(), civicSiteProjects); replace(target.mutableSectorEngagements(), sectorEngagements); replace(target.mutableDecisionHistory(), decisionHistory);
            target.rng().restore(rng);
            target.restoreNextIds(nextProcurementId, nextClaimId, nextCharterId, nextFrontCampaignId);
        }

        private void validateLocalGraph() {
            if (!sectors.keySet().equals(sectorControl.keySet())) throw new IllegalArgumentException("V2 sector/control graph differs");
            for (ReferenceV2HumanPerception perception : humanPerceptions.values()) validateBeliefs(perception.beliefs());
            validateBeliefs(hivePerception.beliefs());
            for (ReferenceNeuralChrysalis chrysalis : chrysalises.values()) requireSector(chrysalis.sectorKey(), "V2 chrysalis sector");
            for (ReferenceCoalitionCharter charter : charters.values()) requireSector(charter.target(), "V2 charter target");
            for (ReferenceFrontCampaign campaign : frontCampaigns.values()) validateCampaign(campaign);
            for (ReferenceSupplyLineStatus supply : supplyLines.values()) {
                if (!frontCampaigns.containsKey(supply.campaignId())) throw new IllegalArgumentException("V2 supply line has no campaign");
                requireSector(supply.sourceSector(), "V2 supply source"); requireSector(supply.targetSector(), "V2 supply target");
                for (String sector : supply.sectors()) requireSector(sector, "V2 supply path");
            }
            for (ReferenceSectorEngagement engagement : sectorEngagements) requireSector(engagement.sectorKey(), "V2 engagement sector");
        }

        private void validateCampaign(ReferenceFrontCampaign campaign) {
            requireSector(campaign.targetSector(), "V2 campaign target");
            Set<Integer> personnel = campaign.personnelBySettlement().keySet();
            if (!personnel.equals(campaign.residentIdsBySettlement().keySet()) || !personnel.equals(campaign.unitCompositionBySettlement().keySet())) {
                throw new IllegalArgumentException("V2 campaign custody lanes differ");
            }
            for (int settlement : personnel) {
                List<String> residents = campaign.residentIdsBySettlement().get(settlement);
                if (campaign.personnelBySettlement().get(settlement) != residents.size()) throw new IllegalArgumentException("V2 campaign personnel differs from people");
                double roles = campaign.unitCompositionBySettlement().get(settlement).values().stream().mapToDouble(Double::doubleValue).sum();
                if (roles != residents.size()) throw new IllegalArgumentException("V2 campaign role composition differs from people");
            }
        }

        private void validateBeliefs(Map<String, ReferenceV2Belief> beliefs) {
            for (ReferenceV2Belief belief : beliefs.values()) requireSector(belief.sectorKey(), "V2 belief sector");
        }

        private void requireSector(String sector, String label) {
            if (!sectors.containsKey(sector)) throw new IllegalArgumentException(label + " is absent: " + sector);
        }

        private void validateTargetGeometry(ReferenceV2State target) {
            if (!target.sectors().keySet().equals(sectors.keySet()) || !target.sectorControl().keySet().equals(sectorControl.keySet())) {
                throw new IllegalArgumentException("V2 state does not match target grid");
            }
            for (Map.Entry<String, ReferenceV2OperationalSector> entry : sectors.entrySet()) {
                ReferenceV2OperationalSector expected = target.sectors().get(entry.getKey());
                ReferenceV2OperationalSector actual = entry.getValue();
                if (expected.x() != actual.x() || expected.y() != actual.y() || !expected.cells().equals(actual.cells())) {
                    throw new IllegalArgumentException("V2 sector geometry does not match target grid");
                }
            }
            Set<Integer> settlements = target.doctrines().keySet();
            if (!settlements.equals(doctrines.keySet()) || !settlements.equals(civics.keySet()) || !settlements.equals(reservePolicies.keySet())
                    || !settlements.equals(rationPlans.keySet()) || !settlements.equals(humanPerceptions.keySet())) {
                throw new IllegalArgumentException("V2 civic state does not match target settlements");
            }
        }

        private static <K, V> Map<K, V> immutable(Map<K, V> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        private static <T> void replace(Map<?, T> target, Map<?, T> values) {
            @SuppressWarnings("unchecked") Map<Object, T> mutable = (Map<Object, T>) target;
            mutable.clear();
            for (Map.Entry<?, T> entry : values.entrySet()) mutable.put(entry.getKey(), entry.getValue());
        }

        private static <T> void replace(List<T> target, List<T> values) {
            target.clear();
            target.addAll(values);
        }
    }
}
