package io.farfrontier.palemirror.frontier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Deterministic port of the reference supplied front: recon, coalition assembly, approach,
 * cordon, clearance, hold, restoration and return.  It never creates abstract personnel; every
 * member is a canonical guard whose physical death changes this same campaign on the next step.
 */
final class FrontierCampaignSimulation {
    private static final int COALITION_RADIUS_CELLS = 28;
    private static final int CORDON_DAYS = 1;
    private static final int CLEAR_DAYS = 2;
    private static final int SECTOR_CELLS = 4;

    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        for (FrontierCampaign campaign : state.campaigns().stream().sorted(Comparator.comparing(FrontierCampaign::id)).toList()) {
            advanceExisting(state, campaign, causationId, events);
        }
        if (state.day() >= FrontierBalance.CAMPAIGN_FIRST_ELIGIBLE_DAY
                && (state.day() - FrontierBalance.CAMPAIGN_FIRST_ELIGIBLE_DAY)
                % FrontierBalance.CAMPAIGN_PLANNING_INTERVAL_DAYS == 0) {
            for (FrontierHive hive : state.hives().stream().sorted(Comparator.comparing(FrontierHive::id)).toList()) {
                launchIfViable(state, hive, causationId, events);
            }
        }
        for (FrontierCampaign campaign : state.campaignRegistry().failBelowLivingParticipants(state, FrontierCampaign.MINIMUM_PARTICIPANTS)) {
            events.add(state.event(FrontierEvent.Type.CAMPAIGN_RESOLVED, campaign.id(), causationId));
        }
        state.campaignRegistry().compact(state.day());
    }

    private void advanceExisting(FrontierWorldState state, FrontierCampaign campaign, String causationId,
                                 List<FrontierEvent> events) {
        if (campaign.terminal()) return;
        FrontierSettlement leader = state.settlement(campaign.leaderSettlementId()).orElseThrow();
        FrontierHive hive = state.hive(campaign.targetHiveId()).orElseThrow();
        if (state.campaignRegistry().livingParticipants(state, campaign) < FrontierCampaign.MINIMUM_PARTICIPANTS) {
            if (campaign.fail(state.day())) events.add(state.event(FrontierEvent.Type.CAMPAIGN_RESOLVED, campaign.id(), causationId));
            return;
        }
        Supply supply = supply(state, leader.center(), hive.anchor());
        if (campaign.reconcileSupply(supply.riskPermille(), supply.readinessPermille())) {
            events.add(state.event(FrontierEvent.Type.CAMPAIGN_STATE_CHANGED, campaign.id(), causationId));
        }
        if (hive.state() != FrontierHive.State.ACTIVE && campaign.phase() != FrontierCampaign.Phase.WITHDRAW) {
            transition(campaign, campaign.beginWithdraw(), causationId, events, state);
        }
        switch (campaign.phase()) {
            case RECON -> { campaign.phaseDay(); transition(campaign, campaign.advance(FrontierCampaign.Phase.RECON,
                    FrontierCampaign.Phase.ASSEMBLE), causationId, events, state); }
            case ASSEMBLE -> { campaign.phaseDay(); transition(campaign, campaign.advance(FrontierCampaign.Phase.ASSEMBLE,
                    FrontierCampaign.Phase.ESTABLISH), causationId, events, state); }
            case ESTABLISH -> {
                if (!supply.ready()) transition(campaign, campaign.beginWithdraw(), causationId, events, state);
                else transition(campaign, campaign.advanceEstablish(leader.center(), hive.anchor()), causationId, events, state);
            }
            case CORDON -> {
                if (!supply.ready()) transition(campaign, campaign.beginWithdraw(), causationId, events, state);
                else {
                    campaign.phaseDay();
                    if (campaign.phaseDays() >= CORDON_DAYS) transition(campaign, campaign.advance(FrontierCampaign.Phase.CORDON,
                            FrontierCampaign.Phase.CLEAR), causationId, events, state);
                }
            }
            case CLEAR -> clear(state, campaign, hive, supply, causationId, events);
            case HOLD -> hold(campaign, supply, causationId, events, state);
            case RESTORE -> restore(state, campaign, hive, supply, causationId, events);
            case WITHDRAW -> {
                transition(campaign, campaign.advanceWithdraw(hive.anchor(), leader.center(), state.day()), causationId, events, state);
                if (campaign.terminal()) events.add(state.event(FrontierEvent.Type.CAMPAIGN_RESOLVED, campaign.id(), causationId));
            }
            case COMPLETE, FAILED -> { }
        }
    }

    private void clear(FrontierWorldState state, FrontierCampaign campaign, FrontierHive hive, Supply supply,
                       String causationId, List<FrontierEvent> events) {
        if (!supply.ready()) {
            transition(campaign, campaign.beginWithdraw(), causationId, events, state);
            return;
        }
        state.ecology().scorch(hive.anchor().x(), hive.anchor().z());
        campaign.phaseDay();
        FrontierHiveOrgan target = state.hiveOrgan(campaign.targetOrganId()).orElseThrow();
        if (target.state() != FrontierHiveOrgan.State.ALIVE || campaign.phaseDays() >= CLEAR_DAYS) {
            if (target.state() == FrontierHiveOrgan.State.ALIVE) {
                events.addAll(FrontierHiveDestruction.destroy(state, target, "frontier:campaign:" + campaign.id(), campaign.id()));
            }
            transition(campaign, campaign.advance(FrontierCampaign.Phase.CLEAR, FrontierCampaign.Phase.HOLD), causationId, events, state);
        } else {
            events.add(state.event(FrontierEvent.Type.CAMPAIGN_STATE_CHANGED, campaign.id(), causationId));
        }
    }

    private void hold(FrontierCampaign campaign, Supply supply, String causationId, List<FrontierEvent> events,
                      FrontierWorldState state) {
        if (!supply.ready()) transition(campaign, campaign.beginWithdraw(), causationId, events, state);
        else {
            campaign.phaseDay();
            if (campaign.phaseDays() >= FrontierBalance.CAMPAIGN_HOLD_DAYS) transition(campaign, campaign.advance(FrontierCampaign.Phase.HOLD,
                    FrontierCampaign.Phase.RESTORE), causationId, events, state);
        }
    }

    private void restore(FrontierWorldState state, FrontierCampaign campaign, FrontierHive hive, Supply supply,
                         String causationId, List<FrontierEvent> events) {
        if (!supply.ready()) transition(campaign, campaign.beginWithdraw(), causationId, events, state);
        else {
            state.ecology().addDetritus(hive.anchor().x(), hive.anchor().z(), 8);
            campaign.phaseDay();
            if (campaign.phaseDays() >= FrontierBalance.CAMPAIGN_RESTORE_DAYS) transition(campaign, campaign.beginWithdraw(), causationId, events, state);
        }
    }

    private void launchIfViable(FrontierWorldState state, FrontierHive hive, String causationId, List<FrontierEvent> events) {
        if (hive.state() != FrontierHive.State.ACTIVE || state.campaigns().stream().anyMatch(value -> !value.terminal()
                && value.targetHiveId().equals(hive.id()))) return;
        FrontierHiveOrgan core = state.hiveOrgans().stream().filter(value -> value.hiveId().equals(hive.id()))
                .filter(value -> value.kind() == FrontierHiveOrganKind.CORE && value.state() == FrontierHiveOrgan.State.ALIVE)
                .min(Comparator.comparing(FrontierHiveOrgan::id)).orElse(null);
        if (core == null) return;
        List<FrontierSettlement> coalition = state.settlements().stream().filter(value -> eligible(state, value, hive.anchor()))
                .sorted(Comparator.comparingInt((FrontierSettlement value) -> distance(value.center(), hive.anchor()))
                        .thenComparing(FrontierSettlement::id)).limit(3).toList();
        if (coalition.size() < 2) return;
        Supply initialSupply = supply(state, coalition.getFirst().center(), hive.anchor());
        if (!initialSupply.ready()) return;
        List<String> participants = new ArrayList<>();
        int perContributor = FrontierBalance.campaignPersonnel(state.profile());
        for (FrontierSettlement settlement : coalition) {
            List<String> guards = settlement.residentIds().stream().map(state::resident).flatMap(java.util.Optional::stream)
                    .filter(FrontierResident::alive).filter(value -> value.role() == FrontierResidentRole.GUARD)
                    .filter(value -> !state.residentAssignedToFieldOperation(value.id()) && !state.campaignRegistry().assigned(value.id()))
                    .sorted(Comparator.comparing(FrontierResident::id)).limit(perContributor).map(FrontierResident::id).toList();
            if (guards.size() != perContributor) return;
            participants.addAll(guards);
        }
        if (!fund(coalition, state.profile())) return;
        FrontierSettlement leader = coalition.getFirst();
        FrontierCampaign campaign = new FrontierCampaign(FrontierCampaign.idFor(leader.id(), hive.id(), state.day()), leader.id(), hive.id(),
                core.id(), FrontierCampaign.Kind.HIVE_CLEARANCE, coalition.stream().map(FrontierSettlement::id).toList(), participants,
                state.day(), FrontierCampaign.travelDays(leader.center(), hive.anchor()), leader.center());
        // The launch decision already computed this exact corridor.  Publishing zero readiness
        // until tomorrow would make a just-authorised front look unsupplied and can mislead a
        // player into thinking that the canonical decision and its label disagree.
        campaign.reconcileSupply(initialSupply.riskPermille(), initialSupply.readinessPermille());
        state.putCampaign(campaign);
        events.add(state.event(FrontierEvent.Type.CAMPAIGN_LAUNCHED, campaign.id(), causationId));
    }

    private static boolean eligible(FrontierWorldState state, FrontierSettlement settlement, FrontierPoint target) {
        return state.alivePopulation(settlement.id()) > 0 && settlement.civicState() != FrontierCivicState.EMERGENCY
                && settlement.civicState() != FrontierCivicState.SIEGE && distance(settlement.center(), target) <= COALITION_RADIUS_CELLS
                && settlement.stock(FrontierResource.FOOD) >= FrontierBalance.campaignFood(state.profile())
                && settlement.stock(FrontierResource.MEDICINE) >= FrontierBalance.campaignMedicine(state.profile())
                && settlement.stock(FrontierResource.WEAPONS) >= FrontierBalance.campaignWeapons(state.profile())
                && settlement.stock(FrontierResource.AMMO) >= FrontierBalance.campaignAmmo(state.profile());
    }

    private static boolean fund(List<FrontierSettlement> coalition, FrontierProfile profile) {
        if (coalition.stream().anyMatch(value -> value.stock(FrontierResource.FOOD) < FrontierBalance.campaignFood(profile)
                || value.stock(FrontierResource.MEDICINE) < FrontierBalance.campaignMedicine(profile)
                || value.stock(FrontierResource.WEAPONS) < FrontierBalance.campaignWeapons(profile)
                || value.stock(FrontierResource.AMMO) < FrontierBalance.campaignAmmo(profile))) return false;
        for (FrontierSettlement settlement : coalition) {
            settlement.removeStock(FrontierResource.FOOD, FrontierBalance.campaignFood(profile));
            settlement.removeStock(FrontierResource.MEDICINE, FrontierBalance.campaignMedicine(profile));
            settlement.removeStock(FrontierResource.WEAPONS, FrontierBalance.campaignWeapons(profile));
            settlement.removeStock(FrontierResource.AMMO, FrontierBalance.campaignAmmo(profile));
        }
        return true;
    }

    private static Supply supply(FrontierWorldState state, FrontierPoint origin, FrontierPoint target) {
        int width = (state.profile().widthCells() + SECTOR_CELLS - 1) / SECTOR_CELLS;
        int height = (state.profile().heightCells() + SECTOR_CELLS - 1) / SECTOR_CELLS;
        Sector source = Sector.of(origin);
        Sector destination = Sector.of(target);
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::cost).thenComparing(value -> value.sector().z())
                .thenComparing(value -> value.sector().x()));
        Map<Sector, Integer> best = new HashMap<>();
        queue.add(new Node(source, 0));
        best.put(source, 0);
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            if (current.cost() != best.getOrDefault(current.sector(), Integer.MAX_VALUE)) continue;
            if (current.sector().equals(destination)) return Supply.connected(current.cost());
            for (Sector next : current.sector().neighbours(width, height)) {
                int tissue = sectorTissue(state, next);
                if (!next.equals(destination) && tissue >= 800) continue;
                int cost = current.cost() + 25 + sectorScar(state, next) / 100 + tissue / 60;
                if (cost < best.getOrDefault(next, Integer.MAX_VALUE)) {
                    best.put(next, cost);
                    queue.add(new Node(next, cost));
                }
            }
        }
        return Supply.disconnected();
    }

    private static int sectorTissue(FrontierWorldState state, Sector sector) {
        return state.hiveTissue().stream().filter(value -> Sector.of(value.position()).equals(sector))
                .mapToInt(FrontierHiveTissueCell::strength).max().orElse(0);
    }

    private static int sectorScar(FrontierWorldState state, Sector sector) {
        long total = 0;
        int count = 0;
        for (int z = sector.z() * SECTOR_CELLS; z < Math.min(state.profile().heightCells(), (sector.z() + 1) * SECTOR_CELLS); z++) {
            for (int x = sector.x() * SECTOR_CELLS; x < Math.min(state.profile().widthCells(), (sector.x() + 1) * SECTOR_CELLS); x++) {
                total += state.ecology().cell(x, z).scar();
                count++;
            }
        }
        return count == 0 ? 1_000 : Math.toIntExact(total / count);
    }

    private static void transition(FrontierCampaign campaign, boolean changed, String causationId, List<FrontierEvent> events,
                                   FrontierWorldState state) {
        if (changed) events.add(state.event(FrontierEvent.Type.CAMPAIGN_STATE_CHANGED, campaign.id(), causationId));
    }
    private static int distance(FrontierPoint left, FrontierPoint right) {
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }

    private record Sector(int x, int z) {
        static Sector of(FrontierPoint point) { return new Sector(point.x() / SECTOR_CELLS, point.z() / SECTOR_CELLS); }
        List<Sector> neighbours(int width, int height) {
            return List.of(new Sector(x - 1, z), new Sector(x, z - 1), new Sector(x, z + 1), new Sector(x + 1, z)).stream()
                    .filter(value -> value.x() >= 0 && value.z() >= 0 && value.x() < width && value.z() < height).toList();
        }
    }
    private record Node(Sector sector, int cost) { }
    private record Supply(int riskPermille, int readinessPermille) {
        static Supply connected(int cost) {
            int risk = Math.min(1_000, cost * 3);
            return new Supply(risk, Math.max(0, 1_000 - risk));
        }
        static Supply disconnected() { return new Supply(1_000, 0); }
        boolean ready() { return readinessPermille >= FrontierBalance.CAMPAIGN_MINIMUM_READINESS_PERMILLE; }
    }
}
