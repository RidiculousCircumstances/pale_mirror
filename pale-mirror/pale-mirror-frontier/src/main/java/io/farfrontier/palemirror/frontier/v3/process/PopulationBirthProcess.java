package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

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
 * Bounded demographic growth: one canonical, exact-food commitment at a time per settlement.
 * The semantic food consequence is durable before its delayed resident admission; a serialized
 * depot replica is only a later projection of that current canonical balance.
 */
public final class PopulationBirthProcess {
    public static final String BREAD = "minecraft:bread";

    private PopulationBirthProcess() { }

    public static ScheduledAction review(SubjectId settlementId, int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:resident-birth-review-" + suffix(settlementId) + "-" + ordinal), new SimInstant(dueAt), 0,
                settlementId, "frontier.population.birth.review", 1);
    }

    public static List<ProposedEvent> planReview(FrontierWorldState state, ScheduledAction action) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), action.subject());
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(schedule(review(settlement.id(), nextOrdinal(action), action.dueAt().ticks() + state.bootstrap().ruleset().cadence().populationBirthReviewInterval())));
        if (hasActiveJob(state, settlement.id()) || !hasHousing(state, settlement.id())) return List.copyOf(events);
        // A new resident is discretionary while the settlement's exact ration
        // owner still has an unresolved cycle.  In particular, do not let a
        // birth permit retain the only COLD bread stack between a visible
        // shortage and its next ordinary provision review.
        if (!SettlementFoodPolicy.allowsPopulationGrowth(state, settlement.id())) return List.copyOf(events);
        // A current physical custodian fences its item scope, but historical
        // replica status never changes autonomous demographic eligibility.
        // COLD therefore commits the exact one-bread semantic consequence and
        // leaves any unloaded replica to catch up from canonical custody.
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return List.copyOf(events);
        if (ReferenceContainerCustody.isReferenceContainer(state, depot)
                && ReferenceContainerCustody.hasLiveCustody(state, depot)) return List.copyOf(events);
        Optional<ExactItemStack> food = food(state, settlement.id());
        if (food.isEmpty()) return List.copyOf(events);
        Household household = household(state, settlement.id());
        if (household == null) return List.copyOf(events);
        int ordinal = nextOrdinal(action);
        int placementOrdinal = Math.toIntExact(state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id())).count());
        ResidentBirthJob job = job(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                Math.toIntExact(SettlementFacilityCapability.housingCapacity(state, settlement.id())), household, food.orElseThrow(), ordinal, placementOrdinal,
                action.dueAt().ticks() + state.bootstrap().ruleset().cadence().populationBirthCompletionDelay(),
                state.bootstrap().ruleset().residentLife());
        events.add(new ProposedEvent(settlement.id(), new ResidentBirthStarted(job)));
        events.add(schedule(complete(job, action.dueAt().ticks() + state.bootstrap().ruleset().cadence().populationBirthCompletionDelay())));
        return List.copyOf(events);
    }

    public static List<ProposedEvent> planCompletion(FrontierWorldState state, ScheduledAction action) {
        ResidentBirthJob job = state.humanPopulation().birthJobs().get(action.subject());
        if (job == null) throw new IllegalStateException("resident birth completion has no active permit: " + action.subject().value());
        if (!complete(job, job.resident().birthTick()).equals(action))
            throw new IllegalArgumentException("resident birth completion lacks its exact retained schedule");
        return List.of(new ProposedEvent(job.settlementId(), new ResidentBorn(job.id(), job.resident(), job.position(),
                new io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity(job.resident().id(),
                        io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity.Kind.RESIDENT))),
                schedule(ResidentNeedProcess.review(job.resident().id(), ResidentNutrition.nourishedAtTick(job.resident().birthTick())
                        .nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                                job.resident().characteristics().effectiveMetabolismPermille(job.resident().birthTick())))),
                schedule(ResidentActivityProcess.review(job.resident().id(), Math.addExact(job.resident().birthTick(), 1L))));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, ResidentBirthStarted started) {
        ResidentBirthJob job = started.job();
        if (!subject.equals(job.settlementId()) || !hasHousing(state, job.settlementId())
                || !SettlementFoodPolicy.allowsPopulationGrowth(state, job.settlementId())
                || !food(state, job.settlementId()).map(item -> item.id().equals(job.foodItemId())).orElse(false)) {
            throw new IllegalArgumentException("resident birth start lacks unfenced canonical food and housing");
        }
        // This is one semantic event: retain the exact pending resident and
        // atomically commit exactly one ration.  No PhysicalIntent is created,
        // so an unvisited replica cannot fence the rest of its stack from COLD
        // provisioning or turn a player visit into demographic eligibility.
        return state.startResidentBirth(job).withInventory(state.inventory().consume(job.foodItemId(), 1));
    }

    public static FrontierWorldState reduceBorn(FrontierWorldState state, SubjectId subject, ResidentBorn birth) {
        if (!subject.equals(birth.resident().settlementId())) throw new IllegalArgumentException("resident birth lacks its settlement owner");
        ResidentBirthJob job = state.humanPopulation().birthJobs().get(birth.jobId());
        if (job == null || !job.settlementId().equals(subject)
                || !job.resident().equals(birth.resident()) || !job.position().equals(birth.position()))
            throw new IllegalArgumentException("resident birth has no active exact job and output");
        return state.completeResidentBirth(job);
    }

    private static boolean hasActiveJob(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().birthJobs().values().stream().anyMatch(job -> job.settlementId().equals(settlementId));
    }

    private static boolean hasHousing(FrontierWorldState state, SubjectId settlementId) {
        return SettlementFacilityCapability.livingResidents(state, settlementId) < SettlementFacilityCapability.housingCapacity(state, settlementId);
    }

    private static Optional<ExactItemStack> food(FrontierWorldState state, SubjectId settlementId) {
        SubjectId depot = FrontierWorldState.depotId(settlementId);
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)
                || ReferenceContainerCustody.hasLiveCustody(state, depot)) return Optional.empty();
        return state.inventory().items().values().stream().sorted(Comparator.comparing(ExactItemStack::id)).filter(item -> BREAD.equals(item.itemKind())
                && item.count() >= 1 && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                ).findFirst();
    }

    private static Household household(FrontierWorldState state, SubjectId settlementId) {
        return state.humanPopulation().households().values().stream().filter(value -> value.settlementId().equals(settlementId))
                .min(Comparator.comparingLong((Household value) -> state.humanPopulation().residents().values().stream()
                        .filter(resident -> resident.householdId().equals(value.id())).count()).thenComparing(Household::id)).orElse(null);
    }

    private static ResidentBirthJob job(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int housingBeds,
                                        Household household, ExactItemStack food, int ordinal, int placementOrdinal, long birthTick,
                                        FrontierRuleset.ResidentLife residentLife) {
        String suffix = suffix(settlement.id()) + "-" + ordinal;
        ResidentProfile resident = new ResidentProfile(new SubjectId("resident:" + suffix(settlement.id()) + "-born-" + ordinal), household.id(), settlement.id(), ResidentRole.FARMER,
                birthTick, HumanPopulation.birthSkills(ordinal))
                .withCharacteristics(ResidentCharacteristics.initial(residentLife,
                        new SubjectId("resident:" + suffix(settlement.id()) + "-born-" + ordinal)));
        return new ResidentBirthJob(new SubjectId("job:resident-birth-" + suffix), settlement.id(), household.id(), food.id(),
                new SubjectId("commitment:resident-birth-food-" + suffix), resident,
                FrontierSettlementActorSlots.residentSlot(bounds, terrain, settlement, housingBeds, placementOrdinal));
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
    private static String suffix(SubjectId settlementId) { return settlementId.value().substring("settlement:".length()); }
}
