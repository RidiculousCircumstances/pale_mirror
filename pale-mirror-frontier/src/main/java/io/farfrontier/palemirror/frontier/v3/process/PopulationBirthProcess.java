package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bounded demographic growth: one physical, exact-food permit at a time per settlement.
 * A birth exists only after the owned stack is durably observed consumed.
 */
public final class PopulationBirthProcess {
    public static final String BREAD = "minecraft:bread";
    private static final long REVIEW_INTERVAL = 24_000L;
    private static final long COMPLETION_DELAY = 200L;

    private PopulationBirthProcess() { }

    public static ScheduledAction review(SubjectId settlementId, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resident-birth-review-" + suffix(settlementId) + "-" + ordinal), new SimInstant(dueAt), 0,
                settlementId, "frontier.population.birth.review", 1);
    }

    public static List<ProposedEvent> planReview(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(schedule(review(settlement.id(), nextOrdinal(action), action.dueAt().ticks() + REVIEW_INTERVAL)));
        if (hasActiveJob(state, settlement.id()) || !hasHousing(state, settlement.id())) return List.copyOf(events);
        Optional<ExactItemStack> food = food(state, settlement.id());
        if (food.isEmpty()) return List.copyOf(events);
        Household household = household(state, settlement.id());
        if (household == null) return List.copyOf(events);
        int ordinal = nextOrdinal(action);
        int placementOrdinal = Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())).count());
        ResidentBirthJob job = job(state.bootstrap().bounds(), settlement, household, food.orElseThrow(), ordinal, placementOrdinal, action.dueAt().ticks() + COMPLETION_DELAY);
        PhysicalIntent intent = new PhysicalIntent(job.consumptionIntentId(), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, job.id(), List.of(job.id(), job.foodItemId()), fixed(job.position()), 0,
                PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        events.add(new ProposedEvent(settlement.id(), new ResidentBirthStarted(job)));
        events.add(new ProposedEvent(settlement.id(), new PhysicalIntentPrepared(intent)));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition, long now) {
        ResidentBirthJob job = jobForIntent(state, intent);
        ProposedEvent physical = new ProposedEvent(job.settlementId(), transition);
        if (transition.status() == PhysicalIntentStatus.CONFIRMED) return List.of(physical, schedule(complete(job, now + COMPLETION_DELAY)));
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            return List.of(physical, new ProposedEvent(job.settlementId(), new ResidentBirthCancelled(job.id())));
        }
        return List.of(physical);
    }

    public static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        ResidentBirthJob job = state.humanPopulation().birthJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("resident birth completion has no active permit: " + action.subject().value());
        PhysicalIntent consumption = state.physicalIntents().get(job.consumptionIntentId());
        if (consumption == null || consumption.status() != PhysicalIntentStatus.CONFIRMED) {
            throw new IllegalStateException("resident birth completion has no confirmed food receipt");
        }
        return List.of(new ProposedEvent(job.settlementId(), new ResidentBorn(job.resident(), job.position())));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResidentBirthStarted started) {
        ResidentBirthJob job = started.job();
        if (!subject.equals(job.settlementId()) || !hasHousing(state, job.settlementId()) || !food(state, job.settlementId()).map(item -> item.id().equals(job.foodItemId())).orElse(false)) {
            throw new IllegalArgumentException("resident birth start lacks owned active food and housing");
        }
        return state.startResidentBirth(job);
    }

    public static FrontierWorldState reduceCancelled(FrontierWorldState state, SubjectId subject, ResidentBirthCancelled cancelled) {
        ResidentBirthJob job = state.humanPopulation().birthJobs().get(cancelled.jobId());
        if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("resident birth cancellation lacks its settlement permit");
        PhysicalIntent intent = state.physicalIntents().get(job.consumptionIntentId());
        if (intent == null || intent.status() != PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("resident birth cancellation requires unknown physical food result");
        }
        return state.cancelResidentBirth(job.id());
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        ResidentBirthJob job = jobForIntent(state, intent);
        if (!subject.equals(job.settlementId()) || !intent.subjectIds().equals(List.of(job.id(), job.foodItemId())) || !food(state, job.settlementId())
                .map(item -> item.id().equals(job.foodItemId())).orElse(false)) {
            throw new IllegalArgumentException("resident birth food intent does not bind an active owned stack");
        }
        return state.preparePhysicalIntent(intent);
    }

    public static FrontierWorldState reduceBorn(FrontierWorldState state, SubjectId subject, ResidentBorn birth) {
        if (!subject.equals(birth.resident().settlementId())) throw new IllegalArgumentException("resident birth lacks its settlement owner");
        ResidentBirthJob job = state.humanPopulation().birthJobs().values().stream()
                .filter(value -> value.resident().equals(birth.resident()) && value.position().equals(birth.position())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("resident birth has no active exact permit"));
        PhysicalIntent intent = state.physicalIntents().get(job.consumptionIntentId());
        if (intent == null || intent.status() != PhysicalIntentStatus.CONFIRMED) throw new IllegalArgumentException("resident birth food has not been physically confirmed");
        return state.completeResidentBirth(job);
    }

    public static ResidentBirthJob jobForIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) throw new IllegalArgumentException("resident birth has invalid physical intent kind");
        ResidentBirthJob job = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
        if (job == null || !job.consumptionIntentId().equals(intent.id())) throw new IllegalArgumentException("resident birth food intent has no active permit");
        return job;
    }

    private static boolean hasActiveJob(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().birthJobs().values().stream().anyMatch(job -> job.settlementId().equals(settlementId));
    }

    private static boolean hasHousing(FrontierWorldState state, SubjectId settlementId) {
        return SettlementFacilityCapability.livingResidents(state, settlementId) < SettlementFacilityCapability.housingCapacity(state, settlementId);
    }

    private static Optional<ExactItemStack> food(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(item -> BREAD.equals(item.itemKind())
                && item.count() >= 1 && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                && state.inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.ACTIVE).findFirst();
    }

    private static Household household(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().households().values().stream().filter(value -> value.settlementId().equals(settlementId))
                .min(Comparator.comparingLong((Household value) -> state.humanPopulation().residents().values().stream()
                        .filter(resident -> resident.householdId().equals(value.id())).count()).thenComparing(Household::id)).orElse(null);
    }

    private static ResidentBirthJob job(WorldBounds bounds, Settlement settlement, Household household, ExactItemStack food, int ordinal, int placementOrdinal, long birthTick) {
        String suffix = suffix(settlement.id()) + "-" + ordinal;
        ResidentProfile resident = new ResidentProfile(new SubjectId("resident:" + suffix(settlement.id()) + "-born-" + ordinal), household.id(), settlement.id(), ResidentRole.FARMER,
                birthTick, HumanPopulation.birthSkills(ordinal));
        return new ResidentBirthJob(new SubjectId("job:resident-birth-" + suffix), settlement.id(), household.id(), food.id(),
                new PhysicalIntentId("intent:resident-birth-food-" + suffix), resident,
                FrontierSettlementActorSlots.slot(bounds, settlement, placementOrdinal));
    }

    private static int nextOrdinal(ScheduledAction action) {
        String prefix = "schedule:resident-birth-review-" + suffix(action.subject()) + "-";
        if (!action.id().value().startsWith(prefix)) throw new IllegalStateException("resident birth review has invalid stable identity");
        return Math.addExact(Integer.parseInt(action.id().value().substring(prefix.length())), 1);
    }

    private static ScheduledAction complete(ResidentBirthJob job, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resident-birth-complete-" + job.id().value().substring("job:".length())), new SimInstant(dueAt), 0,
                job.id(), "frontier.population.birth.complete", 1);
    }

    private static ProposedEvent schedule(ScheduledAction action) { return new ProposedEvent(action.subject(), new ScheduleEffect.Created(action)); }
    private static FixedPosition fixed(BlockPosition position) { return new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())); }
    private static String suffix(SubjectId settlementId) { return settlementId.value().substring("settlement:".length()); }
}
