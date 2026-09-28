package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exact one-bread self-care owner. Not activated until work-yield and HOT receipts exist. */
public final class ResidentMealProcess {
    public static final String PROGRESS = "frontier.resident.meal.progress";
    private ResidentMealProcess() { }

    public static ScheduledAction progress(ResidentMeal meal, long dueAt) {
        if (dueAt <= meal.startedAtTick()) throw new IllegalArgumentException("meal progress precedes start");
        return new ScheduledAction(new ScheduleId("schedule:resident-meal-"
                + meal.residentId().value().substring("resident:".length()) + "-" + meal.startedAtTick()),
                new SimInstant(dueAt), 12, meal.residentId(), PROGRESS, 1);
    }

    /** One sparse due action; unavailable HOT custody or route retains the same activity. */
    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        ResidentMeal meal = state.humanPopulation().meals().get(action.subject());
        if (meal == null || !action.equals(progress(meal, action.dueAt().ticks())))
            throw new IllegalArgumentException("meal progress lacks its retained exact activity");
        long nextDue = Math.addExact(action.dueAt().ticks(), 20L);
        Optional<ResidentMealColdStep> step = planColdStep(state, meal.residentId(), action.dueAt().ticks());
        if (step.isEmpty()) return List.of(new ProposedEvent(meal.residentId(),
                new ScheduleEffect.Rescheduled(action.id(), progress(meal, nextDue))));
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(meal.residentId(), step.orElseThrow()));
        events.add(new ProposedEvent(meal.residentId(), meal.phase() == ResidentMeal.Phase.RETURN
                ? new ScheduleEffect.Consumed(action.id())
                : new ScheduleEffect.Rescheduled(action.id(), progress(meal, nextDue))));
        return List.copyOf(events);
    }

    public static Optional<ResidentMealStarted> selectSourceAtYield(FrontierWorldState state,
                                                                 SubjectId residentId, long now) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorLocations().get(residentId).condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().migration(residentId) != null
                || ResidentActivityCoordinator.assess(state, residentId, now).kind()
                    != ResidentActivityChoice.Kind.EAT) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        if (assignment.active() && assignment.kind() != HumanAssignmentKind.BAKING
                && assignment.kind() != HumanAssignmentKind.FIELD_HARVEST) return Optional.empty();
        if (state.humanPopulation().nutrition(residentId)
                .accrueThrough(now, state.bootstrap().ruleset().residentLife()).hungerDeficit() < 1) return Optional.empty();
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        if (ReferenceContainerCustody.blocksCanonicalUse(state, depot)) return Optional.empty();
        var selected = FungibleResourceCustodySupport.selectAtContainer(state, depot,
                resident.settlementId(), ResidentMeal.BREAD_KIND, 1).orElse(null);
        if (selected == null || selected.lotQuantities().size() != 1) return Optional.empty();
        String suffix = residentId.value().substring("resident:".length());
        SubjectId claimId = new SubjectId("claim:resident-meal-" + suffix + "-" + now);
        SubjectId actorAccount = new SubjectId("custody:resident-meal-" + suffix);
        ResidentMeal meal = new ResidentMeal(residentId, resident.settlementId(), depot,
                selected.accountId(), actorAccount, selected.firstLotId(), claimId,
                assignment.ownerId(), ResidentMeal.Phase.MOVE, now, Optional.empty());
        ResidentMealStarted started = new ResidentMealStarted(meal);
        // A broken ownership/binding invariant must remain visible. Ordinary absence was
        // handled above; silently mapping every reducer failure to "no food" hid bugs.
        reduceStarted(state, residentId, started);
        return Optional.of(started);
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject,
                                                   ResidentMealStarted started) {
        ResidentMeal meal = started.meal();
        ResidentProfile resident = state.humanPopulation().resident(meal.residentId());
        if (!subject.equals(meal.residentId()) || resident == null
                || !resident.settlementId().equals(meal.settlementId())
                || state.humanPopulation().meals().containsKey(subject)
                || state.humanPopulation().migration(subject) != null
                || state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE
                || !HumanAssignmentProjection.compile(state).assignment(subject).ownerId()
                    .equals(meal.retainedWorkOwner())
                || ResidentActivityCoordinator.assess(state, subject, meal.startedAtTick()).kind()
                    != ResidentActivityChoice.Kind.EAT
                || meal.phase() != ResidentMeal.Phase.MOVE
                || meal.waitReason().isPresent()
                || state.humanPopulation().nutrition(subject).accrueThrough(meal.startedAtTick(),
                        state.bootstrap().ruleset().residentLife()).hungerDeficit() < 1)
            throw new IllegalArgumentException("meal start requires one hungry safely yielded living resident");
        if (ReferenceContainerCustody.blocksCanonicalUse(state, meal.depotId()))
            throw new IllegalArgumentException("meal source depot is unavailable");
        var selected = FungibleResourceCustodySupport.selectAtContainer(state, meal.depotId(),
                resident.settlementId(), ResidentMeal.BREAD_KIND, 1).orElseThrow(
                () -> new IllegalArgumentException("meal source lacks one unclaimed bread unit"));
        if (!selected.accountId().equals(meal.sourceAccountId())
                || !selected.lotQuantities().equals(Map.of(meal.lotId(), 1))
                || state.inventory().fungibleResources().accounts().containsKey(meal.actorAccountId()))
            throw new IllegalArgumentException("meal start cannot replace its selected source or actor hand");
        ClaimAllocation claim = new ClaimAllocation(meal.claimId(), meal.residentId(),
                meal.settlementId(), ResidentMeal.BREAD_KIND, 1,
                Map.of(meal.lotId(), 1), ClaimPurpose.RESIDENT_MEAL);
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.sourceAccountId())).toList();
        if (bindings.isEmpty()) ledger = ledger.reserve(claim, meal.sourceAccountId());
        else {
            long epoch = bindings.getFirst().authorityEpoch();
            if (!ReferenceContainerCustody.hasOperationalCustody(state, meal.depotId())
                    || bindings.stream().anyMatch(binding -> binding.authorityEpoch() != epoch))
                throw new IllegalArgumentException("meal source has no current physical custody");
            ledger = ledger.reserveBound(claim, meal.sourceAccountId(), epoch);
        }
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger))
                .humanPopulation(state.humanPopulation().withMeal(meal)));
    }

    /** The semantic depot goal is shared by COLD route planning and the future HOT executor. */
    public static MovementOrder movementOrder(FrontierWorldState state, ResidentMeal meal) {
        return new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L, List.of(serviceSurface(state, meal)),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }

    public static ActorContainerItemOrder takeOrder(FrontierWorldState state, ResidentMeal meal) {
        if (meal.phase() != ResidentMeal.Phase.TAKE)
            throw new IllegalArgumentException("meal bread take requires the current take phase");
        return new ActorContainerItemOrder(meal.residentId(), meal.residentId(),
                ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(meal.sourceAccountId(),
                        new ResourceCustody.Container(meal.depotId()), meal.actorAccountId(),
                        new ResourceCustody.Actor(meal.residentId()), Optional.of(meal.claimId()),
                        ResidentMeal.BREAD_KIND, Map.of(meal.lotId(), 1)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(meal.depotId()),
                serviceSurface(state, meal), ActorContainerItemOrder.Hand.MAIN,
                FrontierWireTags.tag(meal.phase()), 1L);
    }

    /** Plans at most one known COLD edge or stage; no route cursor becomes canonical state. */
    public static Optional<ResidentMealColdStep> planColdStep(FrontierWorldState state,
                                                              SubjectId residentId, long now) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        if (meal == null || now < meal.startedAtTick()) return Optional.empty();
        ActorLocation actor = state.actorLocations().get(residentId);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !FrontierSceneAdmission.available(state, List.of(residentId))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(residentId)))
            return Optional.empty();
        if ((meal.phase() == ResidentMeal.Phase.TAKE
                && ReferenceContainerCustody.hasLiveCustody(state, meal.depotId()))
                || (meal.phase() == ResidentMeal.Phase.CONSUME
                && state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(meal.actorAccountId()))))
            return Optional.empty();
        if (meal.phase() != ResidentMeal.Phase.MOVE)
            return Optional.of(new ResidentMealColdStep(residentId, meal.phase(), now));
        try {
            List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
            return Optional.of(route.size() > 1
                    ? new ResidentMealColdStep(residentId, meal.phase(), now, Optional.of(route.get(1)))
                    : new ResidentMealColdStep(residentId, meal.phase(), now));
        } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.empty();
        }
    }

    private static SurfaceAnchor serviceSurface(FrontierWorldState state, ResidentMeal meal) {
        Settlement settlement = state.bootstrap().settlements().stream()
                .filter(value -> value.id().equals(meal.settlementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("meal has no current settlement"));
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("meal has no current depot"));
        return SettlementDepotServicePort.forDepot(depot).serviceSurface();
    }

    /** Reducer for a witnessed COLD-only stage; no HOT source or hand may be spent here. */
    public static FrontierWorldState reduceColdStep(FrontierWorldState state, SubjectId subject,
                                                     ResidentMealColdStep step) {
        ResidentMeal meal = state.humanPopulation().meals().get(step.residentId());
        if (!subject.equals(step.residentId()) || meal == null || meal.phase() != step.expectedPhase()
                || step.atTick() < meal.startedAtTick())
            throw new IllegalArgumentException("cold meal step has no exact retained predecessor");
        ActorLocation actor = state.actorLocations().get(subject);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !FrontierSceneAdmission.available(state, List.of(subject))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(subject)))
            throw new IllegalArgumentException("cold meal step competes with a loaded or dead resident body");
        return switch (meal.phase()) {
            case MOVE -> {
                ResidentMealColdStep expected = planColdStep(state, subject, step.atTick())
                        .orElseThrow(() -> new IllegalArgumentException("meal has no current COLD route step"));
                if (!step.equals(expected))
                    throw new IllegalArgumentException("meal COLD movement differs from its current known route");
                if (step.nextSurface().isPresent()) {
                    Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
                    actors.put(subject, actor.withBody(BodyPosition.above(step.nextSurface().orElseThrow())));
                    yield state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
                }
                if (!actor.supportingSurface().equals(serviceSurface(state, meal)))
                    throw new IllegalArgumentException("meal arrival lacks its exact depot station body");
                yield state.withHumanPopulation(state.humanPopulation().advanceMeal(meal,
                        meal.advance(ResidentMeal.Phase.TAKE)));
            }
            case TAKE -> {
                ExactInventory inventory = ActorItemCustody.transferCold(state, takeOrder(state, meal));
                yield state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                        .humanPopulation(state.humanPopulation().advanceMeal(meal,
                                meal.advance(ResidentMeal.Phase.CONSUME))));
            }
            case CONSUME -> {
                if (!actor.supportingSurface().equals(serviceSurface(state, meal)))
                    throw new IllegalArgumentException("cold meal consumption lacks its retained station body");
                FungibleResourceLedger ledger = state.inventory().fungibleResources()
                        .destroy(meal.actorAccountId(), Map.of(meal.lotId(), 1),
                                Map.of(meal.claimId(), 1));
                HumanPopulation people = state.humanPopulation().consumeResidentBread(subject, step.atTick(),
                        state.bootstrap().ruleset().residentLife())
                        .advanceMeal(meal, meal.advance(ResidentMeal.Phase.RETURN));
                yield state.withChanges(FrontierWorldStateUpdate.begin()
                        .inventory(state.inventory().withFungibleResources(ledger)).humanPopulation(people));
            }
            case RETURN -> state.withHumanPopulation(state.humanPopulation().completeMeal(meal));
        };
    }
}
