package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Source execution of Python's V2 territorial control and frontier campaign state. */
final class ReferenceV2Frontier {
    private static final int MAXIMUM_SECTOR_ENGAGEMENT_RECEIPTS = 128;

    private ReferenceV2Frontier() { }

    static void updateSectorControl(ReferenceV2State state, ReferenceWorld world) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(world, "world");
        Map<String, Double> garrison = new LinkedHashMap<>();
        Map<String, Boolean> supplied = new LinkedHashMap<>();
        for (String key : state.mutableSectors().keySet()) {
            garrison.put(key, 0.0d);
            supplied.put(key, false);
        }
        for (ReferenceFieldPost post : world.field().activePosts()) {
            String key = state.sectorKeyAt(post.x(), post.y());
            garrison.merge(key, post.garrison(), Double::sum);
            supplied.put(key, post.status() == ReferenceFieldPostStatus.ACTIVE);
        }
        for (ReferenceFrontCampaign campaign : state.mutableFrontCampaigns().values()) {
            if (campaign.phase() == ReferenceFrontPhase.WITHDRAW || campaign.phase().terminal()) continue;
            garrison.merge(campaign.targetSector(), campaign.personnel(), Double::sum);
            supplied.put(campaign.targetSector(), state.mutableSupplyLines().getOrDefault(campaign.id(),
                    disconnected(campaign.id(), "", campaign.targetSector(), "no supply record")).connected());
        }
        for (Map.Entry<String, ReferenceV2OperationalSector> entry : state.mutableSectors().entrySet()) {
            String key = entry.getKey();
            ReferenceV2OperationalSector sector = entry.getValue();
            ReferenceV2SectorControl control = requiredControl(state, key);
            double people = garrison.get(key);
            boolean hasSupply = supplied.get(key);
            control.garrison(people);
            control.supplied(hasSupply);
            if (people > 0.0d && hasSupply) {
                control.cordonStrength(Math.min(1.0d, control.cordonStrength()
                        + people * ReferenceV2Rules.FRONTIER_CORDON_STRENGTH_PER_GARRISON));
            } else {
                control.cordonStrength(Math.max(0.0d, control.cordonStrength() - ReferenceV2Rules.FRONTIER_CORDON_DECAY));
            }
            if (people >= minimumGarrison(state.profile(), ReferenceV2Rules.FRONTIER_MINIMUM_GARRISON) && hasSupply
                    && sector.infection() <= ReferenceV2Rules.FRONTIER_HUMAN_CONTROL_INFECTION) {
                control.heldDays(control.heldDays() + 1);
                changeControl(world, control, ReferenceSectorControlState.HUMAN, "held by supplied frontier force");
            } else if (sector.infection() >= ReferenceV2Rules.FRONTIER_HIVE_CONTROL_INFECTION) {
                control.heldDays(0);
                changeControl(world, control, ReferenceSectorControlState.HIVE, "continuous biological tissue");
            } else if (sector.scar() >= ReferenceV2Rules.FRONTIER_SCARRED_THRESHOLD
                    && sector.infection() <= ReferenceV2Rules.FRONTIER_HUMAN_CONTROL_INFECTION) {
                control.heldDays(0);
                changeControl(world, control, ReferenceSectorControlState.SCARRED, "cleared but ecologically damaged");
            } else if (sector.humanAccess() <= ReferenceV2Rules.FRONTIER_ABANDONED_ACCESS
                    && sector.infection() <= ReferenceV2Rules.FRONTIER_HUMAN_CONTROL_INFECTION) {
                control.heldDays(0);
                changeControl(world, control, ReferenceSectorControlState.ABANDONED, "outside sustainable human reach");
            } else {
                control.heldDays(0);
                changeControl(world, control, ReferenceSectorControlState.CONTESTED, "neither side can hold the frontier");
            }
        }
    }

    static void advance(ReferenceV2State state, ReferenceWorld world) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(world, "world");
        state.mutableSupplyLines().clear();
        for (ReferenceFrontCampaign campaign : state.mutableFrontCampaigns().values()) {
            // Source V2 retains terminal campaigns as historical domain state.
            // They no longer receive supply or execution, but planners and
            // read models can still inspect their terminal outcome.
            if (campaign.phase().terminal()) continue;
            ReferenceSupplyLineStatus line = supplyLine(state, world, campaign);
            state.mutableSupplyLines().put(campaign.id(), line);
            campaign.risk(line.risk());
            switch (campaign.phase()) {
                case RECON -> transition(campaign, ReferenceFrontPhase.ASSEMBLE, "scouts confirmed approach sector");
                case ASSEMBLE -> transition(campaign, ReferenceFrontPhase.ESTABLISH, "force assembled with role mix");
                case ESTABLISH -> {
                    if (!line.connected() || line.readiness() < ReferenceV2Rules.FRONTIER_MINIMUM_SUPPLY_READINESS) {
                        release(state, world, campaign, ReferenceFrontPhase.FAILED, "cannot establish a supplied post");
                    } else {
                        transition(campaign, ReferenceFrontPhase.CORDON, "temporary cordon established");
                    }
                }
                case CORDON -> {
                    if (!line.connected()) {
                        release(state, world, campaign, ReferenceFrontPhase.WITHDRAW, "supply corridor cut before clearance");
                    } else if (requiredSector(state, campaign.targetSector()).infection() <= ReferenceV2Rules.FRONTIER_CLEAR_TARGET_INFECTION) {
                        transition(campaign, ReferenceFrontPhase.HOLD, "sector already below clearance threshold");
                    } else {
                        campaign.phase(ReferenceFrontPhase.CLEAR);
                    }
                }
                case CLEAR -> {
                    if (!line.connected()) {
                        release(state, world, campaign, ReferenceFrontPhase.WITHDRAW, "assault force isolated");
                    } else {
                        clearSector(state, world, campaign);
                        if (requiredSector(state, campaign.targetSector()).infection() <= ReferenceV2Rules.FRONTIER_CLEAR_TARGET_INFECTION) {
                            transition(campaign, ReferenceFrontPhase.HOLD, "clearance threshold reached");
                        }
                    }
                }
                case HOLD -> {
                    if (!line.connected() || line.readiness() < ReferenceV2Rules.FRONTIER_MINIMUM_SUPPLY_READINESS) {
                        release(state, world, campaign, ReferenceFrontPhase.WITHDRAW, "cannot sustain cordon");
                    } else {
                        campaign.holdDays(campaign.holdDays() + 1);
                        if (campaign.holdDays() >= ReferenceV2Rules.FRONTIER_HOLD_DAYS) {
                            transition(campaign, ReferenceFrontPhase.RESTORE, "sector held long enough for restoration");
                        }
                    }
                }
                case RESTORE -> {
                    for (ReferenceGridPosition cell : requiredSector(state, campaign.targetSector()).cells()) {
                        ReferenceEcosystemCell ecosystem = world.infection().ecosystem().cell(cell.x(), cell.y());
                        ecosystem.scar(Math.max(0.0d, ecosystem.scar() - .02d));
                    }
                    campaign.holdDays(campaign.holdDays() + 1);
                    if (campaign.holdDays() >= ReferenceV2Rules.FRONTIER_HOLD_DAYS + ReferenceV2Rules.FRONTIER_RESTORE_DAYS) {
                        release(state, world, campaign, ReferenceFrontPhase.COMPLETE, "cordon handed to routine patrols");
                    }
                }
                case WITHDRAW -> release(state, world, campaign, ReferenceFrontPhase.COMPLETE,
                        campaign.statusReason().isEmpty() ? "force returned" : campaign.statusReason());
                case COMPLETE, FAILED -> throw new IllegalStateException("terminal campaign was not pruned: " + campaign.id());
            }
        }
        recontaminateUnheld(state, world);
        state.refreshTerritory(world);
        updateSectorControl(state, world);
        applyHiveIsolation(state, world);
    }

    static boolean resolveAttack(ReferenceV2State state, ReferenceWorld world, ReferenceSwarm swarm) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(swarm, "swarm");
        String key = state.sectorKeyAt(swarm.targetX() == null ? swarm.x() : swarm.targetX(),
                swarm.targetY() == null ? swarm.y() : swarm.targetY());
        ReferenceV2SectorControl control = state.mutableSectorControl().get(key);
        if (control == null || control.cordonStrength() <= 0.0d && control.garrison() <= 0.0d
                && control.state() != ReferenceSectorControlState.HUMAN) return false;
        double raiders = swarm.composition().getOrDefault(ReferenceBioformKind.RAIDER, 0.0d);
        double breakers = swarm.composition().getOrDefault(ReferenceBioformKind.BREAKER, 0.0d);
        double cordonLoss = breakers * ReferenceV2Rules.FRONTIER_CORDON_BREAK_PER_BREAKER_POWER + swarm.power() * .0015d;
        control.cordonStrength(Math.max(0.0d, control.cordonStrength() - cordonLoss));
        double personnelLoss = Math.min(control.garrison(), (raiders * .10d + breakers * .04d)
                * ReferenceV2Rules.FRONTIER_HIVE_COUNTERATTACK_PERSONNEL_LOSS);
        for (ReferenceFrontCampaign campaign : state.mutableFrontCampaigns().values()) {
            if (!campaign.targetSector().equals(key) || campaign.personnel() <= 0.0d) continue;
            for (Map.Entry<Integer, Double> entry : List.copyOf(campaign.mutablePersonnelBySettlement().entrySet())) {
                int settlementId = entry.getKey();
                ReferenceSettlement settlement = world.settlements().get(settlementId);
                if (settlement == null) throw new IllegalStateException("front campaign references missing settlement: " + settlementId);
                double loss = personnelLoss * entry.getValue() / campaign.personnel();
                if (settlement.discretePeople()) {
                    List<String> ids = campaign.mutableResidentIdsBySettlement().getOrDefault(settlementId, List.of());
                    settlement.removeExposedPeople(ids, loss * .35d, "frontier_counterattack");
                    List<String> survivors = ids.stream().filter(id -> settlement.residents().resident(id) != null).toList();
                    campaign.mutableResidentIdsBySettlement().put(settlementId, new ArrayList<>(survivors));
                    campaign.mutablePersonnelBySettlement().put(settlementId, (double) survivors.size());
                    campaign.mutableUnitCompositionBySettlement().put(settlementId, composition(settlement, survivors));
                } else {
                    campaign.mutablePersonnelBySettlement().put(settlementId, Math.max(0.0d, entry.getValue() - loss));
                    settlement.mobilizedPersonnel(Math.max(0.0d, settlement.mobilizedPersonnel() - loss));
                    settlement.population(Math.max(0.0d, settlement.population() - loss * .35d));
                }
            }
        }
        ReferenceV2OperationalSector sector = requiredSector(state, key);
        for (ReferenceGridPosition cell : sector.cells()) {
            world.infection().level[cell.y()][cell.x()] = Math.min(1.0d,
                    world.infection().level[cell.y()][cell.x()] + ReferenceV2Rules.FRONTIER_HIVE_COUNTERATTACK_INFECTION);
        }
        String outcome = control.cordonStrength() <= .02d ? "cordon breached" : "cordon damaged";
        addReceipt(state, new ReferenceSectorEngagement(world.day(), key, "counterattack", "hive", "human cordon",
                swarm.power(), -cordonLoss, ReferenceV2Rules.FRONTIER_HIVE_COUNTERATTACK_INFECTION, personnelLoss, outcome));
        world.marketWorld().event("D" + world.day() + ": hive " + outcome + " in sector " + key
                + "; raiders=" + formatZero(raiders) + ", breakers=" + formatZero(breakers));
        return true;
    }

    static ReferenceSupplyLineStatus supplyLine(ReferenceV2State state, ReferenceWorld world, ReferenceFrontCampaign campaign) {
        ReferenceSettlement leader = world.settlements().get(campaign.leaderId());
        if (leader == null) throw new IllegalStateException("front campaign leader is absent: " + campaign.leaderId());
        String source = state.sectorKeyAt(leader.x(), leader.y());
        String target = campaign.targetSector();
        PriorityQueue<Path> queue = new PriorityQueue<>(Comparator.comparingDouble(Path::cost).thenComparing(Path::key)
                .thenComparing(item -> String.join("/", item.sectors())));
        Map<String, Double> best = new HashMap<>();
        queue.add(new Path(0.0d, source, List.of(source)));
        best.put(source, 0.0d);
        while (!queue.isEmpty()) {
            Path current = queue.remove();
            if (Double.compare(current.cost(), best.getOrDefault(current.key(), Double.POSITIVE_INFINITY)) != 0) continue;
            if (current.key().equals(target)) {
                double readiness = Math.max(0.0d, Math.min(1.0d, 1.0d - current.cost()
                        / Math.max(.01d, ReferenceV2Rules.FRONTIER_SUPPLY_PATH_MAX_COST)));
                return new ReferenceSupplyLineStatus(campaign.id(), source, target, current.sectors(), true,
                        Math.min(1.0d, current.cost() / 2.0d), readiness, "connected operational corridor");
            }
            for (String neighbor : neighbors(state, current.key())) {
                ReferenceV2OperationalSector sector = requiredSector(state, neighbor);
                ReferenceV2SectorControl control = requiredControl(state, neighbor);
                if (!neighbor.equals(target) && sector.infection() > ReferenceV2Rules.FRONTIER_SUPPLY_MAX_INFECTION) continue;
                double step = .08d + sector.infection() * .34d;
                if (control.state() == ReferenceSectorControlState.HIVE) step += ReferenceV2Rules.FRONTIER_SUPPLY_HIVE_SECTOR_COST;
                else if (control.state() == ReferenceSectorControlState.CONTESTED) step += ReferenceV2Rules.FRONTIER_SUPPLY_CONTESTED_SECTOR_COST;
                if (!sector.routeKeys().isEmpty()) step = Math.max(.02d, step - ReferenceV2Rules.FRONTIER_SUPPLY_ROUTE_SECTOR_BONUS);
                double nextCost = current.cost() + step;
                if (nextCost + 1.0e-9d < best.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    best.put(neighbor, nextCost);
                    List<String> path = new ArrayList<>(current.sectors());
                    path.add(neighbor);
                    queue.add(new Path(nextCost, neighbor, path));
                }
            }
        }
        return disconnected(campaign.id(), source, target, "no viable supply corridor");
    }

    private static void clearSector(ReferenceV2State state, ReferenceWorld world, ReferenceFrontCampaign campaign) {
        ReferenceSettlement leader = world.settlements().get(campaign.leaderId());
        double ammo = campaign.personnel() * ReferenceV2Rules.FRONTIER_CLEAR_AMMO_PER_PERSON;
        double food = campaign.personnel() * ReferenceV2Rules.FRONTIER_CLEAR_FOOD_PER_PERSON;
        if (leader == null || leader.amount(ReferenceResource.AMMO) < ammo || leader.amount(ReferenceResource.FOOD) < food) {
            transition(campaign, ReferenceFrontPhase.WITHDRAW, "ammunition or food reserve exhausted");
            return;
        }
        leader.remove(ReferenceResource.AMMO, ammo);
        leader.remove(ReferenceResource.FOOD, food);
        double strength = campaign.personnel() * (ReferenceV2Rules.FRONTIER_ROLE_MIX.get(ReferenceHumanUnitKind.ASSAULT)
                * ReferenceV2Rules.FRONTIER_CLEAR_INFECTION_PER_ASSAULT
                + ReferenceV2Rules.FRONTIER_ROLE_MIX.get(ReferenceHumanUnitKind.ENGINEER)
                * ReferenceV2Rules.FRONTIER_CLEAR_INFECTION_PER_ENGINEER);
        ReferenceV2OperationalSector sector = requiredSector(state, campaign.targetSector());
        ReferenceGridPosition center = sectorCenter(sector);
        double removed = world.infection().suppressArea(center.x(), center.y(), 3.0d, Math.min(.34d, strength), false);
        Iterator<ReferenceLatentColony> colonies = world.infection().latentColonies.iterator();
        while (colonies.hasNext()) {
            ReferenceLatentColony colony = colonies.next();
            if (state.sectorKeyAt(colony.x(), colony.y()).equals(campaign.targetSector())
                    && state.rng().random() <= ReferenceV2Rules.FRONTIER_CLEAR_SPORE_FRACTION) colonies.remove();
        }
        ReferenceV2SectorControl control = requiredControl(state, campaign.targetSector());
        control.lastClearedDay(world.day());
        addReceipt(state, new ReferenceSectorEngagement(world.day(), campaign.targetSector(), "clear", "human", "tissue",
                strength, 0.0d, -removed, 0.0d, "tissue suppressed"));
        campaign.statusReason("cleared " + String.format(java.util.Locale.ROOT, "%.2f", removed) + " tissue with supplied assault force");
    }

    private static void recontaminateUnheld(ReferenceV2State state, ReferenceWorld world) {
        if (world.infection().organs().isEmpty() && world.infection().latentColonies().isEmpty()) return;
        for (Map.Entry<String, ReferenceV2OperationalSector> entry : state.mutableSectors().entrySet()) {
            String key = entry.getKey();
            ReferenceV2OperationalSector sector = entry.getValue();
            ReferenceV2SectorControl control = requiredControl(state, key);
            if (world.day() - control.lastClearedDay() < ReferenceV2Rules.FRONTIER_HOLD_DAYS
                    || control.garrison() >= minimumGarrison(state.profile(), ReferenceV2Rules.FRONTIER_MINIMUM_GARRISON) && control.supplied()) continue;
            double adjacent = 0.0d;
            for (String neighbor : neighbors(state, key)) adjacent = Math.max(adjacent, requiredSector(state, neighbor).infection());
            if (Math.max(sector.sporeLoad() / 8.0d, adjacent) < ReferenceV2Rules.FRONTIER_RECONTAMINATION_INFECTION) continue;
            double delta = ReferenceV2Rules.FRONTIER_RECONTAMINATION_PER_DAY * Math.max(.35d, adjacent);
            for (ReferenceGridPosition cell : sector.cells()) {
                world.infection().level[cell.y()][cell.x()] = Math.min(1.0d, world.infection().level[cell.y()][cell.x()] + delta);
            }
            if (control.state() == ReferenceSectorControlState.HUMAN || control.state() == ReferenceSectorControlState.SCARRED) {
                changeControl(world, control, ReferenceSectorControlState.CONTESTED, "unheld clearance is being recolonised");
            }
        }
    }

    private static void applyHiveIsolation(ReferenceV2State state, ReferenceWorld world) {
        Map<String, List<ReferenceHiveOrgan>> organs = new LinkedHashMap<>();
        for (ReferenceHiveOrgan organ : world.infection().organs().values()) {
            organs.computeIfAbsent(state.sectorKeyAt(organ.x(), organ.y()), ignored -> new ArrayList<>()).add(organ);
        }
        for (Map.Entry<String, List<ReferenceHiveOrgan>> entry : organs.entrySet()) {
            ReferenceV2SectorControl control = requiredControl(state, entry.getKey());
            if (control.state() != ReferenceSectorControlState.HUMAN
                    && control.cordonStrength() < ReferenceV2Rules.FRONTIER_CORDON_INITIAL_STRENGTH) continue;
            boolean hasCore = entry.getValue().stream().anyMatch(item -> item.kind() == ReferenceOrganKind.CORE);
            for (ReferenceHiveOrgan organ : entry.getValue()) {
                if (organ.kind() == ReferenceOrganKind.DIGESTIVE_POOL) continue;
                organ.biomass(Math.max(0.0d, organ.biomass() * (1.0d - ReferenceV2Rules.FRONTIER_ISOLATED_ORGAN_BIOMASS_LOSS)));
                organ.vitality(Math.max(0.0d, organ.vitality() - ReferenceV2Rules.FRONTIER_ISOLATED_ORGAN_READINESS_LOSS * 100.0d));
                organ.feral(!hasCore);
            }
        }
    }

    private static void release(ReferenceV2State state, ReferenceWorld world, ReferenceFrontCampaign campaign,
                                ReferenceFrontPhase phase, String reason) {
        for (Map.Entry<Integer, Double> entry : campaign.mutablePersonnelBySettlement().entrySet()) {
            ReferenceSettlement settlement = world.settlements().get(entry.getKey());
            if (settlement == null) continue;
            if (settlement.discretePeople()) settlement.returnPeopleHome(campaign.mutableResidentIdsBySettlement().getOrDefault(entry.getKey(), List.of()));
            else settlement.mobilizedPersonnel(Math.max(0.0d, settlement.mobilizedPersonnel() - entry.getValue()));
        }
        if (campaign.terminalOutcome() == null && (phase == ReferenceFrontPhase.WITHDRAW || phase.terminal())) campaign.terminalOutcome(phase);
        campaign.mutablePersonnelBySettlement().clear();
        campaign.mutableResidentIdsBySettlement().clear();
        campaign.mutableUnitCompositionBySettlement().clear();
        transition(campaign, phase, reason);
        state.mutableFrontierCooldownUntil().merge(campaign.leaderId(), world.day() + ReferenceV2Rules.FRONTIER_CAMPAIGN_COOLDOWN_DAYS, Math::max);
        world.marketWorld().event("D" + world.day() + ": front campaign " + campaign.id() + " " + phase.id() + ": " + reason);
    }

    private static Map<ReferenceHumanUnitKind, Double> composition(ReferenceSettlement settlement, List<String> residentIds) {
        LinkedHashMap<ReferenceHumanUnitKind, Double> result = new LinkedHashMap<>();
        for (String id : residentIds) {
            ReferenceResident resident = settlement.residents().resident(id);
            if (resident == null) continue;
            String role = resident.deploymentRole() == null ? ReferenceHumanUnitKind.LINE.id() : resident.deploymentRole();
            result.merge(role(role), 1.0d, Double::sum);
        }
        return result;
    }

    private static ReferenceHumanUnitKind role(String id) {
        for (ReferenceHumanUnitKind item : ReferenceHumanUnitKind.values()) if (item.id().equals(id)) return item;
        throw new IllegalStateException("resident has unknown source deployment role: " + id);
    }

    private static void changeControl(ReferenceWorld world, ReferenceV2SectorControl control,
                                      ReferenceSectorControlState state, String reason) {
        if (control.state() == state && control.reason().equals(reason)) return;
        control.state(state);
        control.reason(reason);
        control.lastChangedDay(world.day());
        world.marketWorld().event("D" + world.day() + ": sector " + control.sectorKey() + " is " + state.id() + ": " + reason);
    }

    private static void transition(ReferenceFrontCampaign campaign, ReferenceFrontPhase phase, String reason) {
        campaign.phase(phase);
        campaign.statusReason(reason);
    }

    private static void addReceipt(ReferenceV2State state, ReferenceSectorEngagement receipt) {
        List<ReferenceSectorEngagement> receipts = state.mutableSectorEngagements();
        receipts.add(receipt);
        if (receipts.size() > MAXIMUM_SECTOR_ENGAGEMENT_RECEIPTS) receipts.removeFirst();
    }

    private static List<String> neighbors(ReferenceV2State state, String key) {
        ReferenceV2OperationalSector sector = requiredSector(state, key);
        List<String> result = new ArrayList<>(4);
        for (int[] delta : List.of(new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1})) {
            String candidate = (sector.x() + delta[0]) + ":" + (sector.y() + delta[1]);
            if (state.mutableSectors().containsKey(candidate)) result.add(candidate);
        }
        return result;
    }

    private static ReferenceGridPosition sectorCenter(ReferenceV2OperationalSector sector) {
        int minimumX = sector.cells().stream().mapToInt(ReferenceGridPosition::x).min().orElseThrow();
        int maximumX = sector.cells().stream().mapToInt(ReferenceGridPosition::x).max().orElseThrow();
        int minimumY = sector.cells().stream().mapToInt(ReferenceGridPosition::y).min().orElseThrow();
        int maximumY = sector.cells().stream().mapToInt(ReferenceGridPosition::y).max().orElseThrow();
        return new ReferenceGridPosition((minimumX + maximumX) / 2, (minimumY + maximumY) / 2);
    }

    private static double minimumGarrison(ReferenceSimulationProfile profile, double sourceMinimum) {
        return profile.discretePeople() ? Math.max(3.0d, Math.floor(sourceMinimum / profile.personScale() + .5d)) : sourceMinimum;
    }

    private static ReferenceSupplyLineStatus disconnected(int campaignId, String source, String target, String reason) {
        return new ReferenceSupplyLineStatus(campaignId, source, target, List.of(), false, 1.0d, 0.0d, reason);
    }

    private static ReferenceV2OperationalSector requiredSector(ReferenceV2State state, String key) {
        ReferenceV2OperationalSector result = state.mutableSectors().get(key);
        if (result == null) throw new IllegalStateException("frontier sector is absent: " + key);
        return result;
    }

    private static ReferenceV2SectorControl requiredControl(ReferenceV2State state, String key) {
        ReferenceV2SectorControl result = state.mutableSectorControl().get(key);
        if (result == null) throw new IllegalStateException("frontier control is absent: " + key);
        return result;
    }

    private static String formatZero(double value) { return String.format(java.util.Locale.ROOT, "%.0f", value); }

    private record Path(double cost, String key, List<String> sectors) { }
}
