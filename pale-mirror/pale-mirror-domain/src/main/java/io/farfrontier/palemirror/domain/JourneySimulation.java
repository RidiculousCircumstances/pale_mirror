package io.farfrontier.palemirror.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic off-screen travel and full-risk casualty simulation. */
public final class JourneySimulation {
    private final DomainEventFactory events;

    JourneySimulation(DomainEventFactory events) { this.events = events; }

    public List<DomainEvent> reconcile(WorldState state) {
        List<DomainEvent> produced = new ArrayList<>();
        state.journeys().stream().sorted(Comparator.comparing(WorldJourney::id)).forEach(journey -> {
            if (journey.terminal() || journey.state() == JourneyState.BLOCKED) return;
            PopulationGroup group = state.populationGroup(journey.subjectGroupId()).orElse(null);
            if (group == null || group.size() == 0) {
                journey.lose("The travelling population no longer exists");
                produced.add(event(state, DomainEventType.JOURNEY_LOST, journey.destinationSiteId(), journey.id()));
                return;
            }
            int losses = losses(journey, group, state.simulationStep());
            applyLosses(group, losses);
            if (losses > 0) {
                journey.recordLosses(losses);
                produced.add(event(state, DomainEventType.JOURNEY_CASUALTIES, group.communityId(), journey.id()));
            }
            if (group.size() == 0) {
                journey.lose("The travelling population was lost en route");
                produced.add(event(state, DomainEventType.JOURNEY_LOST, group.communityId(), journey.id()));
            } else if (journey.advanceAbstractStep() && journey.state() == JourneyState.ARRIVED) {
                produced.add(event(state, DomainEventType.JOURNEY_ARRIVED, journey.destinationSiteId(), journey.id()));
            }
        });
        produced.forEach(state::addEvent);
        return List.copyOf(produced);
    }

    private static int losses(WorldJourney journey, PopulationGroup group, long step) {
        JourneyRiskPolicy policy = journey.riskPolicy();
        if (policy.maximumLossPerStep() == 0) return 0;
        int guards = group.cohorts().getOrDefault(SettlementCohort.GUARDS, 0);
        int mitigation = Math.min(policy.guardMitigationBasisPoints(), guards * 100);
        int exposure = Math.max(0, policy.exposureBasisPoints() - mitigation);
        if (exposure == 0 || roll(journey.riskSeed(), journey.id(), step, "casualty") >= exposure) return 0;
        return Math.min(group.size(), 1 + roll(journey.riskSeed(), journey.id(), step, "loss_count")
                % policy.maximumLossPerStep());
    }

    private static void applyLosses(PopulationGroup group, int losses) {
        int remaining = losses;
        SettlementCohort[] order = {SettlementCohort.CIVILIANS, SettlementCohort.WORKERS,
                SettlementCohort.SPECIALISTS, SettlementCohort.GUARDS, SettlementCohort.CHILDREN};
        for (SettlementCohort cohort : order) {
            int available = group.cohorts().getOrDefault(cohort, 0);
            int amount = Math.min(available, remaining);
            if (amount > 0) group.lose(cohort, amount);
            remaining -= amount;
            if (remaining == 0) return;
        }
    }

    static int roll(long seed, String id, long step, String purpose) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (seed + ":" + id + ":" + step + ":" + purpose).getBytes(StandardCharsets.UTF_8));
            int value = java.nio.ByteBuffer.wrap(bytes).getInt();
            return Math.floorMod(value, 10_000);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private DomainEvent event(WorldState state, DomainEventType type, WorldObjectId subject, String causation) {
        return events.create(state, type, subject, causation);
    }
}
