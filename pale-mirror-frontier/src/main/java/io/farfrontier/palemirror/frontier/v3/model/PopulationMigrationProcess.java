package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bounded background relocation for exact people displaced by a real housing-capacity loss.
 *
 * <p>A person advances one immutable corridor waypoint only while no HOT/scene executor owns
 * them. Thus COLD time keeps moving with zero players, while player-demanded bodies never race a
 * canonical position write. A blocked route or destination remains a durable journey state and
 * is reconsidered by the same recurring review.</p>
 */
final class PopulationMigrationProcess {
    private static final SubjectId OWNER = new SubjectId("system:population");
    private static final long REVIEW_INTERVAL = 1_200L;
    private static final long STEP_INTERVAL = 20L;
    private static final int MAX_ACTIVE_JOURNEYS = 24;
    private static final int MAX_JOURNEYS_PER_ORIGIN = 2;
    private static final int MAX_NEW_JOURNEYS_PER_REVIEW = 12;

    private PopulationMigrationProcess() { }

    static ScheduledAction review(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resident-migration-review-" + ordinal), new SimInstant(dueAt), 0,
                OWNER, "frontier.population.migration.review", 1);
    }

    static List<ProposedEvent> planReview(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value());
        List<ProposedEvent> events = new ArrayList<>();
        events.add(schedule(review(ordinal + 1, Math.addExact(action.dueAt().ticks(), REVIEW_INTERVAL))));
        FrontierWorldState working = state;
        for (ResidentMigrationJourney journey : orderedJourneys(state, ordinal)) {
            if (journey.status() == ResidentMigrationStatus.BLOCKED && HumanPopulationStateSupport.migrationBlockReason(working, journey) == null) {
                events.add(new ProposedEvent(journey.originSettlementId(), new ResidentMigrationResumed(journey.residentId())));
                events.add(schedule(progress(journey.residentId(), Math.addExact(action.dueAt().ticks(), STEP_INTERVAL))));
                working = HumanPopulationStateSupport.resumeMigration(working, new ResidentMigrationResumed(journey.residentId()));
            }
        }
        int capacity = Math.min(MAX_NEW_JOURNEYS_PER_REVIEW, MAX_ACTIVE_JOURNEYS - working.humanPopulation().migrations().size());
        for (int round = 0, started = 0; started < capacity && round < MAX_JOURNEYS_PER_ORIGIN; round++) {
            boolean admitted = false;
            for (Settlement source : orderedSettlements(working, ordinal)) {
                if (started >= capacity || journeysFrom(working, source.id()) >= MAX_JOURNEYS_PER_ORIGIN) continue;
                Optional<Candidate> candidate = candidate(working, source);
                if (candidate.isEmpty()) continue;
                Candidate value = candidate.orElseThrow();
                events.add(new ProposedEvent(value.origin().id(), new ResidentMigrationStarted(value.journey())));
                events.add(schedule(progress(value.journey().residentId(), Math.addExact(action.dueAt().ticks(), STEP_INTERVAL))));
                working = HumanPopulationStateSupport.startMigration(working, value.journey());
                started++; admitted = true;
            }
            if (!admitted) break;
        }
        return List.copyOf(events);
    }

    static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(action.subject());
        if (journey == null || !progress(action.subject()).id().equals(action.id())) return List.of();
        if (journey.status() == ResidentMigrationStatus.BLOCKED) return List.of();
        ResidentMigrationBlockReason reason = HumanPopulationStateSupport.migrationBlockReason(state, journey);
        if (reason != null) return List.of(new ProposedEvent(journey.originSettlementId(), new ResidentMigrationBlocked(journey.residentId(), reason)));
        if (!coldAvailable(state, journey.residentId())) {
            return List.of(schedule(progress(journey.residentId(), Math.addExact(action.dueAt().ticks(), STEP_INTERVAL))));
        }
        if (journey.arriving()) {
            Settlement destination = FrontierWorldStateSupport.settlement(state.bootstrap(), journey.destinationSettlementId());
            int ordinal = SettlementFacilityCapability.livingResidents(state, destination.id());
            BlockPosition handoff = FrontierSettlementActorSlots.slot(state.bootstrap().bounds(), destination, ordinal);
            return List.of(new ProposedEvent(destination.id(), new ResidentMigrated(journey.residentId(), journey.destinationHouseholdId(), destination.id(), handoff)));
        }
        return List.of(new ProposedEvent(journey.originSettlementId(), new ResidentMigrationAdvanced(journey.residentId(), journey.nextRouteIndex())),
                schedule(progress(journey.residentId(), Math.addExact(action.dueAt().ticks(), STEP_INTERVAL))));
    }

    private static Optional<Candidate> candidate(FrontierWorldState state, Settlement source) {
        if (!displaced(state, source) || state.humanPopulation().quarantined(source.id())) return Optional.empty();
        Optional<Settlement> destination = state.bootstrap().settlements().stream().filter(value -> !value.id().equals(source.id()))
                .filter(value -> !state.humanPopulation().quarantined(value.id())).filter(value -> freeBeds(state, value) > 0)
                .filter(value -> corridorPassable(state, source, value)).sorted(Comparator.comparingInt((Settlement value) -> freeBeds(state, value)).reversed()
                        .thenComparing(Settlement::id)).findFirst();
        if (destination.isEmpty()) return Optional.empty();
        Optional<ResidentProfile> resident = state.humanPopulation().residents().values().stream().filter(value -> value.settlementId().equals(source.id()))
                .filter(value -> !state.humanPopulation().migrations().containsKey(value.id()))
                .filter(value -> state.actorLocations().get(value.id()).condition().status() == ActorLifeStatus.ALIVE).filter(value -> coldAvailable(state, value.id()))
                .sorted(Comparator.comparing(ResidentProfile::id)).findFirst();
        if (resident.isEmpty()) return Optional.empty();
        Settlement target = destination.orElseThrow(); ResidentProfile person = resident.orElseThrow();
        Household household = state.humanPopulation().households().values().stream().filter(value -> value.settlementId().equals(target.id()))
                .min(Comparator.comparingLong((Household value) -> state.humanPopulation().residents().values().stream().filter(personInHome -> personInHome.householdId().equals(value.id())).count())
                        .thenComparing(Household::id)).orElseThrow();
        return Optional.of(new Candidate(source, journey(state, person, source, household, target)));
    }

    private static ResidentMigrationJourney journey(FrontierWorldState state, ResidentProfile resident, Settlement source, Household household, Settlement destination) {
        List<BlockPosition> route = new ArrayList<>(); route.add(state.actorLocations().get(resident.id()).position());
        append(route, state.routeTopology().supplyWaypoints(state.bootstrap(), source.id()));
        List<BlockPosition> reverseDestination = new ArrayList<>(state.routeTopology().supplyWaypoints(state.bootstrap(), destination.id()));
        java.util.Collections.reverse(reverseDestination); append(route, reverseDestination);
        return new ResidentMigrationJourney(resident.id(), source.id(), household.id(), destination.id(), route, 0,
                ResidentMigrationStatus.EN_ROUTE, Optional.empty());
    }

    private static void append(List<BlockPosition> route, List<BlockPosition> suffix) {
        for (BlockPosition destination : suffix) appendSegment(route, destination);
    }
    private static void appendSegment(List<BlockPosition> route, BlockPosition destination) {
        BlockPosition from = route.getLast();
        if (from.y() != destination.y()) throw new IllegalArgumentException("migration route cannot change vertical datum");
        if (from.x() != destination.x() && from.z() != destination.z()) appendAxis(route, new BlockPosition(destination.x(), from.y(), from.z()));
        appendAxis(route, destination);
    }
    private static void appendAxis(List<BlockPosition> route, BlockPosition destination) {
        BlockPosition from = route.getLast(); int stepX = Integer.compare(destination.x(), from.x()), stepZ = Integer.compare(destination.z(), from.z());
        for (int x = from.x() + stepX, z = from.z() + stepZ; x != destination.x() + stepX || z != destination.z() + stepZ; x += stepX, z += stepZ) {
            route.add(new BlockPosition(x, from.y(), z));
            if (route.size() > ResidentMigrationJourney.MAX_WAYPOINTS) throw new IllegalArgumentException("migration route exceeds bounded frontier profile");
        }
    }

    private static boolean displaced(FrontierWorldState state, Settlement settlement) { return overflow(state, settlement) > 0; }
    private static int overflow(FrontierWorldState state, Settlement settlement) {
        return Math.max(0, SettlementFacilityCapability.livingResidents(state, settlement.id()) - SettlementFacilityCapability.housingCapacity(state, settlement.id()));
    }
    private static int freeBeds(FrontierWorldState state, Settlement settlement) {
        return Math.max(0, Math.toIntExact(SettlementFacilityCapability.housingCapacity(state, settlement.id())
                - SettlementFacilityCapability.livingResidents(state, settlement.id()) - state.humanPopulation().inboundHousingReservations(settlement.id())));
    }
    private static boolean corridorPassable(FrontierWorldState state, Settlement source, Settlement destination) {
        List<BlockPosition> route = new ArrayList<>(state.routeTopology().supplyWaypoints(state.bootstrap(), source.id()));
        List<BlockPosition> reverse = new ArrayList<>(state.routeTopology().supplyWaypoints(state.bootstrap(), destination.id()));
        java.util.Collections.reverse(reverse); append(route, reverse);
        return FrontierRouteNetwork.isPassable(state.bootstrap(), route, state.physicalDeltas());
    }
    private static boolean coldAvailable(FrontierWorldState state, SubjectId residentId) {
        return FrontierSceneAdmission.available(state, List.of(residentId)) && state.operations().values().stream().noneMatch(operation ->
                FrontierWorldStateSupport.retainsParticipantClaim(state, operation) && operation.participantIds().contains(residentId));
    }
    private static long journeysFrom(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().migrations().values().stream().filter(journey -> journey.originSettlementId().equals(settlementId)).count();
    }
    private static List<ResidentMigrationJourney> orderedJourneys(FrontierWorldState state, int ordinal) {
        List<ResidentMigrationJourney> journeys = new ArrayList<>(state.humanPopulation().migrations().values());
        journeys.sort(Comparator.comparingInt((ResidentMigrationJourney journey) -> settlementOrder(state, journey.originSettlementId(), ordinal))
                .thenComparing(ResidentMigrationJourney::residentId));
        return List.copyOf(journeys);
    }
    private static List<Settlement> orderedSettlements(FrontierWorldState state, int ordinal) {
        List<Settlement> values = new ArrayList<>(state.bootstrap().settlements());
        values.sort(Comparator.comparingInt((Settlement settlement) -> settlementOrder(state, settlement.id(), ordinal))
                .thenComparing(Settlement::id));
        return List.copyOf(values);
    }
    private static int settlementOrder(FrontierWorldState state, SubjectId settlementId, int ordinal) {
        List<Settlement> settlements = state.bootstrap().settlements(); int index = 0;
        while (index < settlements.size() && !settlements.get(index).id().equals(settlementId)) index++;
        if (index == settlements.size()) throw new IllegalArgumentException("migration references unknown settlement");
        return Math.floorMod(index - Math.floorMod(ordinal, settlements.size()), settlements.size());
    }
    private static ScheduledAction progress(SubjectId residentId, long dueAt) {
        return new ScheduledAction(progress(residentId).id(), new SimInstant(dueAt), 0, residentId, "frontier.population.migration.progress", 1);
    }
    private static ScheduledAction progress(SubjectId residentId) {
        return new ScheduledAction(new ScheduleId("schedule:resident-migration-progress-" + residentId.value().substring("resident:".length())),
                SimInstant.ZERO, 0, residentId, "frontier.population.migration.progress", 1);
    }
    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private record Candidate(Settlement origin, ResidentMigrationJourney journey) { }
}
