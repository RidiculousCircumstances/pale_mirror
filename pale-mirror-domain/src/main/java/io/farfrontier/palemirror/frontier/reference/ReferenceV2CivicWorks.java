package io.farfrontier.palemirror.frontier.reference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Source V2 autonomous civilian site-work pass. */
final class ReferenceV2CivicWorks {
    private ReferenceV2CivicWorks() { }

    static void advance(ReferenceWorld world, List<ReferenceCivicSiteProject> projects) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        List<ReferenceCivicSiteProject> pending = new ArrayList<>();
        boolean changed = false;
        for (ReferenceCivicSiteProject project : projects) {
            project.daysRemaining(project.daysRemaining() - 1);
            if (project.daysRemaining() > 0) {
                pending.add(project);
                continue;
            }
            ReferenceSettlement settlement = required.settlements().get(project.settlementId());
            ReferenceResourceSite site = required.resourceSites().get(project.siteId());
            if (settlement == null || site == null || !settlement.alive()) continue;
            if (project.action().equals("claim") && site.ownerId() == null) {
                required.microeconomy().licenseExistingSite(required.marketWorld(), site, settlement.id());
                site.claimedDay(required.day());
                changed = true;
                required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " reclaimed "
                        + site.kind().name().toLowerCase(java.util.Locale.ROOT) + " site " + site.id());
            }
        }
        projects.clear();
        projects.addAll(pending);
        if (changed) required.refreshPrimaryCapacity();
    }

    static void maintain(ReferenceWorld world, Map<Integer, ReferenceSettlementDoctrine> doctrines,
                         Map<Integer, ReferenceV2HumanPerception> perceptions,
                         List<ReferenceCivicSiteProject> projects, Map<Integer, Integer> lastWorkDay) {
        ReferenceWorld required = Objects.requireNonNull(world, "world");
        for (ReferenceSettlement settlement : required.settlements().values().stream()
                .sorted(Comparator.comparingInt(ReferenceSettlement::id)).toList()) {
            if (!settlement.alive() || required.day() - lastWorkDay.getOrDefault(settlement.id(), -10_000)
                    < ReferenceV2Rules.CIVIC_PROJECT_COOLDOWN_DAYS) continue;
            Work decision = decide(required, settlement.id(), doctrines, perceptions, projects);
            if (decision == null) continue;
            if (decision.action().equals("claim")) {
                if (!required.economy().canStartClaim(settlement)) continue;
                required.economy().payClaimMaterials(settlement);
                projects.add(new ReferenceCivicSiteProject(settlement.id(), decision.site().id(), decision.action(),
                        ReferenceV2Rules.CIVIC_CLAIM_DAYS));
                lastWorkDay.put(settlement.id(), required.day());
                required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " began reclaiming "
                        + decision.site().kind().name().toLowerCase(java.util.Locale.ROOT) + " site " + decision.site().id());
                continue;
            }
            if (!required.economy().siteProject(settlement, decision.site(), decision.action())) continue;
            applyEcology(required, decision);
            lastWorkDay.put(settlement.id(), required.day());
            required.refreshPrimaryCapacity();
            required.marketWorld().event("D" + required.day() + ": " + settlement.name() + " " + decision.action() + "d "
                    + decision.site().kind().name().toLowerCase(java.util.Locale.ROOT) + " site " + decision.site().id());
        }
    }

    static Work decide(ReferenceWorld world, int settlementId, Map<Integer, ReferenceSettlementDoctrine> doctrines,
                       Map<Integer, ReferenceV2HumanPerception> perceptions, List<ReferenceCivicSiteProject> projects) {
        ReferenceSettlement settlement = require(world.settlements(), settlementId, "settlement");
        ReferenceSettlementDoctrine doctrine = require(doctrines, settlementId, "settlement doctrine");
        ReferenceV2HumanPerception perception = require(perceptions, settlementId, "human perception");
        Set<String> knownSectors = new HashSet<>();
        for (ReferenceV2Belief belief : perception.known(world.day())) knownSectors.add(belief.sectorKey());
        Set<Integer> activeSites = new HashSet<>();
        for (ReferenceCivicSiteProject project : projects) activeSites.add(project.siteId());
        List<Candidate> candidates = new ArrayList<>();
        for (ReferenceResourceSite site : world.resourceSites().values().stream().sorted(Comparator.comparingInt(ReferenceResourceSite::id)).toList()) {
            if (activeSites.contains(site.id())) continue;
            boolean owned = Objects.equals(site.ownerId(), settlementId);
            boolean observed = owned || knownSectors.contains(world.v2().sectorKeyAt(site.x(), site.y()));
            if (!observed) continue;
            if (owned) addOwnedCandidates(world, settlement, doctrine, site, candidates);
            else if (site.ownerId() == null && site.distanceTo(settlement.x(), settlement.y()) <= ReferenceV2Rules.CIVIC_CLAIM_DISTANCE) {
                candidates.add(new Candidate(site.capacity() * site.quality() * (1.0d - site.contamination())
                        * (0.8d + doctrine.commercialDependence()), "claim", site));
            }
        }
        return candidates.stream().max(Comparator.comparingDouble(Candidate::score)
                .thenComparing(Comparator.comparingInt((Candidate item) -> item.site().id()).reversed())
                .thenComparing(Candidate::action)).map(item -> new Work(item.action(), item.site())).orElse(null);
    }

    private static void addOwnedCandidates(ReferenceWorld world, ReferenceSettlement settlement, ReferenceSettlementDoctrine doctrine,
                                           ReferenceResourceSite site, List<Candidate> candidates) {
        if (site.contamination() > 0.03d) {
            candidates.add(new Candidate(site.contamination() * site.capacity() * (13.0d + doctrine.caution() * 10.0d), "cleanse", site));
            if (site.contamination() > 0.55d) candidates.add(new Candidate(site.contamination() * site.capacity()
                    * (9.0d + doctrine.militancy() * 10.0d), "scorch", site));
        }
        if (site.condition() < 0.95d) candidates.add(new Candidate((1.0d - site.condition()) * site.capacity()
                * (6.0d + doctrine.commercialDependence() * 5.0d), "repair", site));
        ReferenceEcosystemCell ecology = world.infection().ecosystem().cell(site.x(), site.y());
        double output = world.infection().ecosystem().humanOutputFactor(site.kind().name().toLowerCase(java.util.Locale.ROOT), site.x(), site.y());
        if (ecology.scar() >= 0.18d && site.contamination() <= 0.08d && output < 0.85d) candidates.add(new Candidate(
                ecology.scar() * site.capacity() * (8.0d + doctrine.solidarity() * 6.0d), "restore", site));
        candidates.add(new Candidate(world.economy().localValue(settlement, site.resource()) * site.quality()
                * (0.8d + doctrine.commercialDependence()), "upgrade", site));
    }

    private static void applyEcology(ReferenceWorld world, Work decision) {
        ReferenceResourceSite site = decision.site();
        switch (decision.action()) {
            case "restore" -> world.infection().ecosystem().restore(site.x(), site.y(), 1.0d);
            case "scorch" -> {
                world.infection().ecosystem().scorch(site.x(), site.y(), 1.0d);
                world.infection().recordDamage("scorch", 0.62d);
            }
            case "cleanse" -> world.infection().recordDamage("cleanse",
                    world.infection().suppressArea(site.x(), site.y(), 1.4d, 0.12d));
            default -> { }
        }
    }

    record Work(String action, ReferenceResourceSite site) { }
    private record Candidate(double score, String action, ReferenceResourceSite site) { }

    private static <K, V> V require(Map<K, V> source, K key, String description) {
        V result = source.get(key);
        if (result == null) throw new IllegalStateException(description + " is absent: " + key);
        return result;
    }
}
