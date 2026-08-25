package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source V2 settlement mind, coalition proposal and frontier authorisation pass. */
final class ReferenceV2HumanPlanner {
    private ReferenceV2HumanPlanner() { }

    static void plan(ReferenceV2State state, ReferenceWorld world) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(world, "world");
        // The state helper already advances matured projects before selecting
        // new work, precisely matching the two Python calls.
        state.maintainCivicSites(world);
        if ((world.day() - 1) % ReferenceV2Rules.SETTLEMENT_PLANNING_INTERVAL != 0) return;

        for (ReferenceSettlement settlement : orderedSettlements(world)) {
            if (!settlement.alive()) continue;
            ReferenceCivicLedger civic = required(state.civics(), settlement.id(), "settlement civic");
            ReferenceSettlementDoctrine doctrine = required(state.doctrines(), settlement.id(), "settlement doctrine");
            ReferenceV2HumanPerception perception = required(state.humanPerceptions(), settlement.id(), "settlement perception");
            Decision decision = decide(settlement, civic, doctrine, perception, world.day());
            state.recordDecision(new ReferenceV2DecisionReceipt(world.day(), settlement.id(), decision.action(),
                    roundThree(decision.risk()), decision.reason(), formatBlockers(decision.blockers())));
            if (decision.action().equals("quarantine_and_contain") && civic.quarantineUntil() >= world.day()) {
                for (ReferenceRoute route : world.trade().routes()) {
                    if (settlement.id() == route.a() || settlement.id() == route.b()) {
                        route.quarantineUntil(Math.max(route.quarantineUntil(), civic.quarantineUntil()));
                    }
                }
            }
            if (!decision.action().equals("interrupt_chrysalis")) continue;
            ReferenceV2Belief target = perception.known(world.day()).stream().filter(ReferenceV2Belief::chrysalis).findFirst().orElse(null);
            if (target == null || state.charters().values().stream().anyMatch(item -> item.target().equals(target.sectorKey())
                    && (item.status().equals("offered") || item.status().equals("active")))) continue;
            Proposal proposal = propose(world, settlement.id(), target, doctrine);
            if (proposal == null) continue;
            List<Integer> accepted = new ArrayList<>();
            for (int member : proposal.members()) {
                ReferenceSettlementDoctrine memberDoctrine = required(state.doctrines(), member, "coalition member doctrine");
                if (member == settlement.id() || Math.min(doctrine.solidarity(), memberDoctrine.solidarity())
                        >= ReferenceV2Rules.COALITION_TRUST_TO_SIGN) accepted.add(member);
            }
            if (accepted.size() < 2) continue;
            LinkedHashMap<Integer, Double> contributions = new LinkedHashMap<>();
            LinkedHashMap<Integer, Double> reserves = new LinkedHashMap<>();
            LinkedHashMap<Integer, Double> compensation = new LinkedHashMap<>();
            for (int member : accepted) {
                double contribution = required(world.settlements(), member, "coalition member").population()
                        * ReferenceV2Rules.COALITION_CONTRIBUTION_POPULATION_FRACTION;
                contributions.put(member, contribution);
                reserves.put(member, contribution * ReferenceV2Rules.COALITION_RESERVE_CONTRIBUTION_FRACTION);
                if (member != settlement.id()) compensation.put(member, contribution * ReferenceV2Rules.COALITION_COMPENSATION_PER_PERSON);
            }
            ReferenceCoalitionCharter charter = new ReferenceCoalitionCharter(state.nextCharterIdAndIncrement(), settlement.id(), accepted,
                    target.sectorKey(), world.day(), world.day() + ReferenceV2Rules.COALITION_DEFAULT_DAYS,
                    contributions, reserves, compensation);
            charter.status("active");
            charter.reason(proposal.reason());
            state.mutableCharters().put(charter.id(), charter);
            world.marketWorld().event("D" + world.day() + ": cities " + accepted.stream().map(String::valueOf)
                    .reduce((first, second) -> first + "," + second).orElseThrow() + " signed chrysalis charter " + charter.id());
        }
        planFrontiers(state, world);
    }

    private static Decision decide(ReferenceSettlement settlement, ReferenceCivicLedger civic,
                                   ReferenceSettlementDoctrine doctrine, ReferenceV2HumanPerception perception, int day) {
        List<ReferenceV2Belief> known = perception.known(day);
        double maximum = known.stream().mapToDouble(item -> item.infection() + item.hiveInfluence() * .5d).max().orElse(0.0d);
        boolean chrysalis = known.stream().anyMatch(item -> item.chrysalis() && item.confidence() >= .60d);
        List<String> blockers = new ArrayList<>();
        if (civic.legitimacy() < ReferenceV2Rules.MINIMUM_LEGITIMACY + .08d) blockers.add("civic legitimacy exhausted");
        if (settlement.foodFulfillment() < .80d) blockers.add("food emergency");
        if (civic.state() == ReferenceCivicState.SIEGE) return new Decision("hold", Math.min(1.0d, settlement.threat() + .25d),
                "hold walls and ration critical stock", blockers);
        if (chrysalis) return new Decision("interrupt_chrysalis", Math.min(1.0d, maximum),
                "visible neural chrysalis is a time-critical target", blockers);
        if (maximum >= ReferenceV2Rules.EMERGENCY_THREAT) return new Decision("quarantine_and_contain", maximum,
                "confirmed biological threat threatens accessible territory", blockers);
        if (maximum >= ReferenceV2Rules.WATCH_THREAT) return new Decision("recon_and_guard", maximum,
                "uncertain frontier threat requires observation", blockers);
        return new Decision("recover", Math.max(0.0d, settlement.threat() * .4d), "restore reserves and civilian production", blockers);
    }

    private static Proposal propose(ReferenceWorld world, int leaderId, ReferenceV2Belief target,
                                    ReferenceSettlementDoctrine doctrine) {
        ReferenceSettlement leader = world.settlements().get(leaderId);
        if (leader == null || !leader.alive() || target.confidence() < .60d) return null;
        double populationMinimum = ReferenceV2Rules.COALITION_CANDIDATE_MIN_POPULATION;
        double armoryMinimum = ReferenceV2Rules.COALITION_CANDIDATE_MIN_ARMORY;
        if (world.profile().discretePeople()) {
            populationMinimum = Math.max(2.0d, Math.floor(populationMinimum / world.profile().personScale() + .5d));
            armoryMinimum /= world.profile().personScale();
        }
        final double requiredPopulation = populationMinimum;
        final double requiredArmory = armoryMinimum;
        List<ReferenceSettlement> candidates = orderedSettlements(world).stream().filter(item -> item.alive() && item.id() != leaderId
                        && item.population() >= requiredPopulation && item.facilities().armory() >= requiredArmory
                        && Math.hypot(item.x() - leader.x(), item.y() - leader.y()) <= ReferenceV2Rules.COALITION_CANDIDATE_RADIUS)
                .sorted(Comparator.<ReferenceSettlement>comparingDouble(ReferenceSettlement::population)
                        .reversed().thenComparingInt(ReferenceSettlement::id)).toList();
        List<Integer> members = new ArrayList<>();
        members.add(leaderId);
        for (ReferenceSettlement candidate : candidates) {
            if (members.size() >= ReferenceV2Rules.COALITION_MAX_CONTRIBUTORS) break;
            members.add(candidate.id());
        }
        if (members.size() < 2) return null;
        double risk = Math.min(1.0d, (target.infection() + target.hiveInfluence())
                / Math.max(.45d, doctrine.militancy() + doctrine.casualtyTolerance()));
        if (risk > ReferenceV2Rules.COALITION_RISK_LIMIT) return null;
        members.sort(Integer::compareTo);
        return new Proposal(target.sectorKey(), List.copyOf(members), risk, "shared frontier target has credible coalition");
    }

    private static void planFrontiers(ReferenceV2State state, ReferenceWorld world) {
        for (ReferenceSettlement settlement : orderedSettlements(world)) {
            if (!settlement.alive() || required(state.civics(), settlement.id(), "frontier civic").state() == ReferenceCivicState.SIEGE
                    || state.frontierCooldownUntil().getOrDefault(settlement.id(), 0) > world.day()) continue;
            long active = state.frontCampaigns().values().stream().filter(item -> item.leaderId() == settlement.id() && !item.phase().terminal()).count();
            if (active >= ReferenceV2Rules.FRONTIER_MAXIMUM_CAMPAIGNS_PER_SETTLEMENT) continue;
            double minimumGarrison = minimumGarrison(world.profile());
            double available = Math.max(0.0d, settlement.population() - settlement.mobilizedPersonnel());
            double committed = Math.max(minimumGarrison, settlement.population() * ReferenceV2Rules.FRONTIER_GARRISON_POPULATION_FRACTION);
            if (available < committed || settlement.amount(ReferenceResource.AMMO)
                    < committed * ReferenceV2Rules.FRONTIER_CLEAR_AMMO_PER_PERSON * 2.0d) continue;
            Candidate target = selectFrontierTarget(state, settlement, world.day());
            if (target == null) continue;
            List<Integer> contributors = contributors(state, settlement.id(), target.belief().sectorKey());
            LinkedHashMap<Integer, Double> personnel = reservePersonnel(world, contributors, minimumGarrison);
            if (personnel.values().stream().mapToDouble(Double::doubleValue).sum() < minimumGarrison) {
                rollbackContinuousMobilisation(world, personnel);
                continue;
            }
            LinkedHashMap<Integer, List<String>> residents = new LinkedHashMap<>();
            LinkedHashMap<Integer, Map<ReferenceHumanUnitKind, Double>> composition = new LinkedHashMap<>();
            if (world.profile().discretePeople() && !deployNamedPeople(world, state.nextFrontCampaignId(), personnel, residents, composition)) {
                rollbackNamedPeople(world, residents);
                continue;
            }
            if (personnel.values().stream().mapToDouble(Double::doubleValue).sum() < minimumGarrison) {
                rollbackNamedPeople(world, residents);
                rollbackContinuousMobilisation(world, personnel);
                continue;
            }
            int campaignId = state.nextFrontCampaignIdAndIncrement();
            ReferenceFrontCampaign campaign = new ReferenceFrontCampaign(campaignId, ReferenceFrontCampaignKind.SECTOR_CLEARANCE,
                    settlement.id(), new ArrayList<>(personnel.keySet()), target.belief().sectorKey(), world.day(), ReferenceFrontPhase.RECON,
                    personnel, residents, composition, "confirmed tissue threatens a valued, reachable frontier sector");
            state.mutableFrontCampaigns().put(campaign.id(), campaign);
            world.marketWorld().event("D" + world.day() + ": " + settlement.name() + " authorised frontier campaign " + campaign.id()
                    + " for sector " + target.belief().sectorKey());
        }
    }

    private static Candidate selectFrontierTarget(ReferenceV2State state, ReferenceSettlement settlement, int day) {
        Candidate result = null;
        for (ReferenceV2Belief belief : required(state.humanPerceptions(), settlement.id(), "frontier perception").known(day)) {
            if (belief.confidence() < .55d || belief.infection() < ReferenceV2Rules.FRONTIER_MINIMUM_KNOWN_INFECTION) continue;
            ReferenceV2SectorControl control = required(state.sectorControl(), belief.sectorKey(), "frontier control");
            double pressure = switch (control.state()) {
                case HIVE -> 1.25d;
                case CONTESTED -> 1.0d;
                case HUMAN, SCARRED, ABANDONED -> .45d;
            };
            ReferenceV2OperationalSector sector = required(state.sectors(), belief.sectorKey(), "frontier sector");
            double distance = Math.hypot((sector.x() + .5d) * ReferenceV2Rules.SECTOR_SIZE - settlement.x(),
                    (sector.y() + .5d) * ReferenceV2Rules.SECTOR_SIZE - settlement.y());
            double score = (belief.infrastructureValue() * .16d + belief.hiveInfluence() + belief.infection()) * pressure - distance * .035d;
            Candidate candidate = new Candidate(score, belief);
            if (result == null || candidate.score() > result.score()
                    || candidate.score() == result.score() && candidate.belief().sectorKey().compareTo(result.belief().sectorKey()) > 0) result = candidate;
        }
        return result;
    }

    private static List<Integer> contributors(ReferenceV2State state, int leaderId, String target) {
        for (ReferenceCoalitionCharter charter : state.charters().values()) {
            if (charter.status().equals("active") && charter.target().equals(target) && charter.members().contains(leaderId)) {
                return charter.members();
            }
        }
        return List.of(leaderId);
    }

    private static LinkedHashMap<Integer, Double> reservePersonnel(ReferenceWorld world, List<Integer> contributors, double minimumGarrison) {
        LinkedHashSet<Integer> distinct = new LinkedHashSet<>(contributors.stream().sorted().toList());
        LinkedHashMap<Integer, Double> result = new LinkedHashMap<>();
        for (int contributorId : distinct) {
            ReferenceSettlement contributor = required(world.settlements(), contributorId, "frontier contributor");
            double free = Math.max(0.0d, contributor.population() - contributor.mobilizedPersonnel());
            double share = Math.min(free, Math.max(minimumGarrison / contributors.size(),
                    contributor.population() * ReferenceV2Rules.FRONTIER_GARRISON_POPULATION_FRACTION));
            if (share <= 0.0d) continue;
            result.put(contributorId, share);
            if (!contributor.discretePeople()) contributor.mobilizedPersonnel(contributor.mobilizedPersonnel() + share);
        }
        return result;
    }

    private static boolean deployNamedPeople(ReferenceWorld world, int campaignId, LinkedHashMap<Integer, Double> personnel,
                                             Map<Integer, List<String>> residents,
                                             Map<Integer, Map<ReferenceHumanUnitKind, Double>> composition) {
        try {
            for (Map.Entry<Integer, Double> entry : personnel.entrySet()) {
                ReferenceSettlement contributor = required(world.settlements(), entry.getKey(), "named frontier contributor");
                Map<String, Integer> requested = ReferenceFormations.integerComposition(entry.getValue().intValue(), ReferenceV2Rules.FRONTIER_ROLE_MIX);
                Map<String, List<String>> assigned = contributor.deployPeople(1_000_000 + campaignId, requested);
                List<String> residentIds = new ArrayList<>();
                LinkedHashMap<ReferenceHumanUnitKind, Double> roles = new LinkedHashMap<>();
                for (String role : assigned.keySet().stream().sorted().toList()) {
                    List<String> ids = assigned.get(role);
                    residentIds.addAll(ids);
                    roles.put(ReferenceHumanUnitKind.valueOf(role.toUpperCase(java.util.Locale.ROOT)), (double) ids.size());
                }
                residents.put(entry.getKey(), List.copyOf(residentIds));
                composition.put(entry.getKey(), roles);
                personnel.put(entry.getKey(), (double) residentIds.size());
            }
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }

    private static void rollbackNamedPeople(ReferenceWorld world, Map<Integer, List<String>> residents) {
        for (Map.Entry<Integer, List<String>> entry : residents.entrySet()) {
            required(world.settlements(), entry.getKey(), "deployed frontier contributor").returnPeopleHome(entry.getValue());
        }
    }

    private static void rollbackContinuousMobilisation(ReferenceWorld world, Map<Integer, Double> personnel) {
        for (Map.Entry<Integer, Double> entry : personnel.entrySet()) {
            ReferenceSettlement settlement = required(world.settlements(), entry.getKey(), "continuous frontier contributor");
            if (!settlement.discretePeople()) settlement.mobilizedPersonnel(Math.max(0.0d, settlement.mobilizedPersonnel() - entry.getValue()));
        }
    }

    private static double minimumGarrison(ReferenceSimulationProfile profile) {
        return profile.discretePeople() ? Math.max(3.0d, Math.floor(ReferenceV2Rules.FRONTIER_MINIMUM_GARRISON / profile.personScale() + .5d))
                : ReferenceV2Rules.FRONTIER_MINIMUM_GARRISON;
    }

    private static List<ReferenceSettlement> orderedSettlements(ReferenceWorld world) {
        return world.settlements().values().stream().sorted(Comparator.comparingInt(ReferenceSettlement::id)).toList();
    }

    private static double roundThree(double value) { return Math.rint(value * 1_000.0d) / 1_000.0d; }
    private static String formatBlockers(List<String> blockers) { return blockers.isEmpty() ? "—" : String.join(", ", blockers); }
    private static <K, V> V required(Map<K, V> values, K key, String description) {
        V value = values.get(key);
        if (value == null) throw new IllegalStateException(description + " is absent: " + key);
        return value;
    }

    private record Decision(String action, double risk, String reason, List<String> blockers) { }
    private record Proposal(String targetSector, List<Integer> members, double risk, String reason) { }
    private record Candidate(double score, ReferenceV2Belief belief) { }
}
