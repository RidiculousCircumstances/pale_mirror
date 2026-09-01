package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded, event-driven infection exposure for exact residents.
 *
 * <p>Only canonical infection cells intersecting current settlement semantic geometry can
 * expose residents. Minecraft is not sampled as a second disease simulation, and the process
 * scans one settlement at a time on a persisted cadence.</p>
 */
public final class HumanHealthProcess {
    private HumanHealthProcess() { }

    /** One assessment driven by an existing owner reconsideration, without creating another global pulse. */
    public static List<ProposedEvent> assess(FrontierWorldState state, Settlement settlement, long now) {
        List<ProposedEvent> events = new ArrayList<>();
        boolean contaminated = localExposure(state, settlement);
        ResidentProfile subject = nextResident(state, settlement.id(), contaminated, now);
        HumanPopulation populationAfter = state.humanPopulation();
        if (subject != null) {
            ResidentHealth current = populationAfter.health(subject.id());
            ResidentHealthStatus next = nextStatus(state, current, contaminated, now);
            events.add(new ProposedEvent(settlement.id(), new ResidentHealthTransition(subject.id(), next, now)));
            populationAfter = populationAfter.transitionHealth(subject.id(), next, now);
        }
        boolean quarantineNeeded = contaminated || populationAfter.activeCases(settlement.id()) > 0;
        SettlementQuarantine policy = populationAfter.quarantine(settlement.id());
        SettlementQuarantineStatus target = quarantineNeeded ? SettlementQuarantineStatus.QUARANTINED : SettlementQuarantineStatus.NORMAL;
        if (policy.status() != target) events.add(new ProposedEvent(settlement.id(), new SettlementQuarantineTransition(settlement.id(), target, now)));
        return List.copyOf(events);
    }

    public static FrontierWorldState reduceResidentTransition(FrontierWorldState state, SubjectId subject, long eventTick, ResidentHealthTransition transition) {
        if (transition.atTick() != eventTick) throw new IllegalArgumentException("resident health transition must use its event instant");
        ResidentProfile resident = state.humanPopulation().resident(transition.residentId());
        if (resident == null || !resident.settlementId().equals(subject)) throw new IllegalArgumentException("resident health transition has a foreign settlement owner");
        ActorLocation actor = state.actorLocations().get(transition.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("dead or unknown resident cannot change disease state");
        return state.withHumanPopulation(state.humanPopulation().transitionHealth(transition.residentId(), transition.status(), transition.atTick()));
    }

    public static FrontierWorldState reduceQuarantineTransition(FrontierWorldState state, SubjectId subject, long eventTick, SettlementQuarantineTransition transition) {
        if (transition.atTick() != eventTick) throw new IllegalArgumentException("settlement quarantine transition must use its event instant");
        if (!subject.equals(transition.settlementId())) throw new IllegalArgumentException("settlement quarantine transition has a foreign event owner");
        FrontierWorldStateSupport.settlement(state.bootstrap(), subject);
        boolean contaminated = localExposure(state, FrontierWorldStateSupport.settlement(state.bootstrap(), subject));
        boolean required = contaminated || state.humanPopulation().activeCases(subject) > 0;
        if (transition.status() == SettlementQuarantineStatus.QUARANTINED && !required) {
            throw new IllegalArgumentException("settlement quarantine requires known contamination or active residents");
        }
        if (transition.status() == SettlementQuarantineStatus.NORMAL && required) {
            throw new IllegalArgumentException("settlement quarantine cannot lift while risk remains");
        }
        return state.withHumanPopulation(state.humanPopulation().transitionQuarantine(subject, transition.status(), transition.atTick()));
    }

    /** Read-only semantic contact query shared by scheduling, validation and player projection. */
    public static boolean localExposure(FrontierWorldState state, Settlement settlement) {
        return FrontierGrayboxPlan.settlementHasInfectionContact(state, settlement);
    }

    private static ResidentProfile nextResident(FrontierWorldState state, SubjectId settlementId, boolean contaminated, long now) {
        return state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .filter(resident -> state.humanPopulation().medicalOperations().values().stream()
                        .noneMatch(operation -> operation.active() && operation.patientId().equals(resident.id())))
                .filter(resident -> eligible(state, state.humanPopulation().health(resident.id()), contaminated, now))
                .sorted(Comparator.comparing((ResidentProfile resident) -> priority(state, state.humanPopulation().health(resident.id()), contaminated, now))
                        .thenComparing(ResidentProfile::id))
                .findFirst().orElse(null);
    }

    private static boolean eligible(FrontierWorldState state, ResidentHealth health, boolean contaminated, long now) {
        return nextStatus(state, health, contaminated, now) != null;
    }

    private static ResidentHealthStatus nextStatus(FrontierWorldState state, ResidentHealth health, boolean contaminated, long now) {
        long elapsed = Math.subtractExact(now, health.sinceTick());
        long delay = state.bootstrap().ruleset().cadence().humanHealthProgressionDelay();
        if (contaminated) {
            if (health.status() == ResidentHealthStatus.EXPOSED && elapsed >= delay) return ResidentHealthStatus.INFECTED;
            if (health.status() == ResidentHealthStatus.HEALTHY || health.status() == ResidentHealthStatus.RECOVERING) return ResidentHealthStatus.EXPOSED;
            return null;
        }
        if (health.status() == ResidentHealthStatus.EXPOSED) return ResidentHealthStatus.HEALTHY;
        if (health.status() == ResidentHealthStatus.INFECTED && elapsed >= delay) return ResidentHealthStatus.RECOVERING;
        if (health.status() == ResidentHealthStatus.RECOVERING && elapsed >= delay) return ResidentHealthStatus.HEALTHY;
        return null;
    }

    private static int priority(FrontierWorldState state, ResidentHealth health, boolean contaminated, long now) {
        if (contaminated && health.status() == ResidentHealthStatus.EXPOSED && Math.subtractExact(now, health.sinceTick())
                >= state.bootstrap().ruleset().cadence().humanHealthProgressionDelay()) return 0;
        return 1;
    }

}
