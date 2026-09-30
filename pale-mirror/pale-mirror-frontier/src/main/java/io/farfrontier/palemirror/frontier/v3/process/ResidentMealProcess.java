package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exact one-bread source, custody and consumption owner; activity hand-off belongs elsewhere. */
public final class ResidentMealProcess {
    public static final String PROGRESS = "frontier.resident.meal.progress";
    private static final long COLD_TICKS_PER_EDGE = 20L;
    private ResidentMealProcess() { }

    /** Read-only COLD projection; never grants a physical lease or consumes a meal effect. */
    public static BodyPosition bodyAt(FrontierWorldState state, SubjectId residentId, long atTick) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        if (meal != null && meal.coldTravel().isPresent()) {
            TimedKnownRoute travel = meal.coldTravel().orElseThrow();
            // A due but uncommitted arrival cannot be observed as an interaction.
            // The last pre-arrival support is the maximal safe HOT handoff.
            long safeTick = Math.min(atTick, travel.arrivalTick() - 1L);
            int safeIndex = travel.indexAt(safeTick);
            int barrier = firstKnownBarrier(state, travel);
            if (barrier >= 0) safeIndex = Math.min(safeIndex, barrier - 1);
            return BodyPosition.above(travel.route().get(safeIndex));
        }
        ActorLocation actor = state.actorLocations().get(residentId);
        if (actor == null) throw new IllegalArgumentException("resident has no canonical body");
        return actor.body();
    }

    public static ScheduledAction progress(ResidentMeal meal, long dueAt) {
        if (dueAt <= meal.startedAtTick()) throw new IllegalArgumentException("meal progress precedes start");
        return new ScheduledAction(new ScheduleId("schedule:resident-meal-"
                + meal.residentId().value().substring("resident:".length()) + "-" + meal.startedAtTick()),
                new SimInstant(dueAt), 12, meal.residentId(), PROGRESS, 1);
    }

    /** HOT or an unresolved physical hand owns the meal; no COLD retry is useful. */
    public static boolean held(FrontierWorldState state, ScheduledAction action) {
        if (!PROGRESS.equals(action.kind())) return false;
        ResidentMeal meal = state.humanPopulation().meals().get(action.subject());
        if (meal == null) return false;
        if (meal.pendingPhysicalStep().isPresent()
                || !FrontierSceneAdmission.available(state, List.of(meal.residentId()))
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.retainsMemberCustody(meal.residentId())))
            return true;
        return switch (meal.phase()) {
            case TAKE -> ReferenceContainerCustody.hasLiveCustody(state, meal.depotId());
            case CONSUME -> state.inventory().fungibleResources().bindings().values().stream()
                    .anyMatch(binding -> binding.accountId().equals(meal.actorAccountId()));
            case MOVE, RETURN -> planColdStep(state, meal.residentId(), action.dueAt().ticks()).isEmpty();
        };
    }

    /** One sparse due action; unavailable HOT custody or route retains the same activity. */
    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action) {
        return planProgress(state, action, action.dueAt().ticks());
    }

    public static List<ProposedEvent> planProgress(FrontierWorldState state, ScheduledAction action, long currentTick) {
        ResidentMeal meal = state.humanPopulation().meals().get(action.subject());
        if (meal == null || !action.equals(progress(meal, action.dueAt().ticks())))
            throw new IllegalArgumentException("meal progress lacks its retained exact activity");
        ActorLocation body = state.actorLocations().get(meal.residentId());
        if (body == null || body.condition().status() != ActorLifeStatus.ALIVE)
            return List.of(new ProposedEvent(meal.residentId(), new ScheduleEffect.Consumed(action.id())));
        long now = Math.max(action.dueAt().ticks(), currentTick);
        Optional<ResidentMealColdStep> step = planColdStep(state, meal.residentId(), now);
        // A changing availability boundary is rechecked by the engine's held
        // predicate; keep the same due owner instead of adding 200 ticks of
        // artificial latency after the service becomes free.
        if (step.isEmpty()) return List.of(new ProposedEvent(meal.residentId(),
                new ScheduleEffect.Rescheduled(action.id(), progress(meal,
                        Math.addExact(now, 1L)))));
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(meal.residentId(), step.orElseThrow()));
        long nextDue = nextColdDue(state, meal, now);
        boolean returnComplete = meal.phase() == ResidentMeal.Phase.RETURN
                && meal.coldTravel().isEmpty() && step.orElseThrow().nextSurface().isEmpty()
                && body.supportingSurface().equals(meal.clearingSurface());
        boolean consumed = meal.phase() == ResidentMeal.Phase.CONSUME;
        if (consumed) {
            events.add(ResidentNeedProcess.requeueAfterConfirmedBread(state, meal.residentId(), now));
            var movement = ActorMovementProcess.afterMeal(meal, now);
            events.add(new ProposedEvent(meal.residentId(), new ScheduleEffect.Created(
                    ActorMovementProcess.progress(movement, Math.addExact(now, 1L)))));
        }
        if (returnComplete)
            events.add(ResidentActivityProcess.wakeAfterMeal(meal.residentId(), now));
        events.add(new ProposedEvent(meal.residentId(), returnComplete || consumed
                ? new ScheduleEffect.Cancelled(action.id())
                : new ScheduleEffect.Rescheduled(action.id(), progress(meal, nextDue))));
        return List.copyOf(events);
    }

    public static Optional<ResidentMealStarted> selectSourceAtYield(FrontierWorldState state,
                                                                 SubjectId residentId, long now) {
        ResidentProfile resident = state.humanPopulation().resident(residentId);
        if (resident == null || state.humanPopulation().meals().containsKey(residentId)
                || state.actorMovements().containsKey(residentId)
                || state.actorLocations().get(residentId).condition().status() != ActorLifeStatus.ALIVE
                || state.humanPopulation().migration(residentId) != null
                || ResidentActivityCoordinator.assess(state, residentId, now).kind()
                    != ResidentActivityChoice.Kind.EAT) return Optional.empty();
        HumanAssignment assignment = HumanAssignmentProjection.compile(state).assignment(residentId);
        if (state.humanPopulation().nutrition(residentId)
                .accrueThrough(now, state.bootstrap().ruleset().residentLife(),
                        resident.characteristics().effectiveMetabolismPermille(now)).hungerDeficit() < 1) return Optional.empty();
        ResidentMealOpportunity.Source source = ResidentMealOpportunity.find(state, residentId).orElse(null);
        if (source == null) return Optional.empty();
        var selected = source.bread();
        String suffix = residentId.value().substring("resident:".length());
        SubjectId claimId = new SubjectId("claim:resident-meal-" + suffix + "-" + now);
        SubjectId actorAccount = new SubjectId("custody:resident-meal-" + suffix);
        ResidentMeal meal = new ResidentMeal(residentId, resident.settlementId(), source.depotId(), source.clearingSurface(),
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
                || !ServiceAccessCoordinator.depotMayStartMeal(state, meal.depotId(), subject)
                || !ServiceAccessCoordinator.mealClearingSurface(state, subject).equals(Optional.of(meal.clearingSurface()))
                || state.humanPopulation().migration(subject) != null
                || state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE
                || !HumanAssignmentProjection.compile(state).assignment(subject).ownerId()
                    .equals(meal.retainedWorkOwner())
                || ResidentActivityCoordinator.assess(state, subject, meal.startedAtTick()).kind()
                    != ResidentActivityChoice.Kind.EAT
                || meal.phase() != ResidentMeal.Phase.MOVE
                || meal.waitReason().isPresent()
                || state.humanPopulation().nutrition(subject).accrueThrough(meal.startedAtTick(),
                        state.bootstrap().ruleset().residentLife(),
                        resident.characteristics().effectiveMetabolismPermille(meal.startedAtTick())).hungerDeficit() < 1)
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
                serviceSurface(state, meal), ActorContainerItemOrder.Hand.OFF,
                FrontierWireTags.tag(meal.phase()), 1L);
    }

    public static FrontierWorldState reduceHotArrived(FrontierWorldState state, SubjectId subject,
                                                      ResidentMealHotArrived arrived) {
        ResidentMeal meal = state.humanPopulation().meals().get(arrived.residentId());
        AmbientActorLease lease = state.ambientLeases().get(arrived.residentId());
        if (!subject.equals(arrived.residentId()) || meal == null || meal.phase() != ResidentMeal.Phase.MOVE
                || meal.pendingPhysicalStep().isPresent() || lease == null
                || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.MEAL
                || lease.revision() != arrived.ambientRevision()
                || !lease.goalBody().equals(arrived.observedBody())
                || !serviceSurface(state, meal).standingBody().equals(arrived.observedBody())
                || !ServiceAccessCoordinator.depotAvailableForMeal(state, meal.depotId(), subject))
            throw new IllegalArgumentException("HOT meal arrival lacks its exact retained resident and service station");
        ActorLocation actor = state.actorLocations().get(subject);
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actor.withBody(arrived.observedBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .humanPopulation(state.humanPopulation().advanceMeal(meal, meal.advance(ResidentMeal.Phase.TAKE))));
    }

    public static FrontierWorldState reduceHotPrepared(FrontierWorldState state, SubjectId subject,
                                                       ResidentMealHotEffectPrepared prepared) {
        ResidentMeal meal = hotMeal(state, subject, prepared.residentId(), prepared.step().ambientRevision());
        ResidentMealPhysicalStep step = prepared.step();
        if (meal.phase() != step.phase() || meal.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("HOT meal effect cannot prepare a foreign or second stage");
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        if (step.phase() == ResidentMeal.Phase.TAKE) {
            if (!ReferenceContainerCustody.hasOperationalCustody(state, meal.depotId()))
                throw new IllegalArgumentException("meal take has no current physical depot authority");
            List<MaterialSourceSelection.Slice> slices = MaterialSourceSelection.select(ledger, takeOrder(state, meal));
            if (slices.size() != 1 || !(slices.getFirst().address() instanceof PhysicalStackAddress.ContainerSlot slot)
                    || !slot.slot().containerId().equals(meal.depotId())
                    || slot.slot().slot() != step.sourceSlot()
                    || slices.getFirst().before() != step.sourceCount()
                    || slices.getFirst().epoch() != step.sourceEpoch()
                    || step.destinationEpoch() != step.ambientRevision())
                throw new IllegalArgumentException("meal take fence differs from its claimed current chest binding");
        } else {
            List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                    .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList();
            if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != step.sourceEpoch()
                    || step.destinationEpoch() != 0 || !bindings.getFirst().lotQuantities().equals(Map.of(meal.lotId(), 1))
                    || !(bindings.getFirst().address() instanceof PhysicalStackAddress.ActorHand hand)
                    || !hand.actorId().equals(subject)
                    || !hand.entityId().equals(SceneLease.deterministicEntityId(state.bootstrap().worldId(), subject)))
                throw new IllegalArgumentException("meal consumption fence lacks its exact bound HOT hand");
        }
        return state.withHumanPopulation(state.humanPopulation().advanceMeal(meal, meal.prepare(step)));
    }

    public static FrontierWorldState reduceHotObserved(FrontierWorldState state, SubjectId subject,
                                                       ResidentMealHotEffectObserved observed, long atTick) {
        ResidentMeal meal = hotMeal(state, subject, observed.residentId(), observed.ambientRevision());
        ResidentMealPhysicalStep step = meal.pendingPhysicalStep().orElseThrow(
                () -> new IllegalArgumentException("HOT meal effect lacks its durable pre-effect fence"));
        if (step.phase() != observed.phase() || !observed.observedBody().equals(serviceSurface(state, meal).standingBody()))
            throw new IllegalArgumentException("HOT meal receipt differs from its exact phase or resident body");
        if (step.phase() == ResidentMeal.Phase.TAKE) {
            PhysicalStackAddress.ActorHand hand = new PhysicalStackAddress.ActorHand(subject,
                    SceneLease.deterministicEntityId(state.bootstrap().worldId(), subject));
            if (observed.destination().size() != 1
                    || !observed.destination().getFirst().address().equals(hand)
                    || !observed.destination().getFirst().itemKind().equals(ResidentMeal.BREAD_KIND)
                    || observed.destination().getFirst().quantity() != 1)
                throw new IllegalArgumentException("observed meal bread did not enter the exact resident hand");
            ExactInventory inventory = ActorItemCustody.transferObserved(state, takeOrder(state, meal),
                    step.sourceEpoch(), step.destinationEpoch(), observed.remainingSource(), observed.destination());
            return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                    .humanPopulation(state.humanPopulation().advanceMeal(meal, meal.advance(ResidentMeal.Phase.CONSUME))));
        }
        if (!observed.remainingSource().isEmpty() || !observed.destination().isEmpty())
            throw new IllegalArgumentException("observed meal consumption retained a physical bread stack");
        FungibleResourceLedger ledger = state.inventory().fungibleResources().destroyObserved(
                meal.actorAccountId(), step.sourceEpoch(), Map.of(meal.lotId(), 1),
                Map.of(meal.claimId(), 1), List.of());
        HumanPopulation people = state.humanPopulation().consumeResidentBread(subject, atTick,
                        state.bootstrap().ruleset().residentLife())
                .completeMeal(meal);
        Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement> movements =
                new LinkedHashMap<>(state.actorMovements());
        if (movements.putIfAbsent(subject, ActorMovementProcess.afterMeal(meal, atTick)) != null)
            throw new IllegalArgumentException("resident meal competes with an existing movement order");
        FrontierWorldState next = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger)).humanPopulation(people)
                .actorMovements(movements));
        return ResidentActivityProcess.retargetHotResident(next, subject, atTick);
    }

    /** A witnessed consumption and both affected resident clocks form one command transaction. */
    public static List<ProposedEvent> planHotObserved(FrontierWorldState state,
                                                       ResidentMealHotEffectObserved observed, long atTick) {
        ResidentMeal meal = state.humanPopulation().meals().get(observed.residentId());
        reduceHotObserved(state, observed.residentId(), observed, atTick);
        if (observed.phase() != ResidentMeal.Phase.CONSUME)
            return List.of(new ProposedEvent(observed.residentId(), observed));
        var movement = ActorMovementProcess.afterMeal(meal, atTick);
        return List.of(new ProposedEvent(observed.residentId(), observed),
                ResidentNeedProcess.requeueAfterConfirmedBread(state, observed.residentId(), atTick),
                new ProposedEvent(observed.residentId(), new ScheduleEffect.Cancelled(
                        progress(meal, Math.addExact(meal.startedAtTick(), 1L)).id())),
                new ProposedEvent(observed.residentId(), new ScheduleEffect.Created(
                        ActorMovementProcess.progress(movement, Math.addExact(atTick, 1L)))));
    }

    private static ResidentMeal hotMeal(FrontierWorldState state, SubjectId subject,
                                       SubjectId residentId, long ambientRevision) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        AmbientActorLease lease = state.ambientLeases().get(residentId);
        ActorLocation actor = state.actorLocations().get(subject);
        if (!subject.equals(residentId) || meal == null || lease == null
                || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.MEAL
                || lease.revision() != ambientRevision
                || !lease.goalBody().equals((meal.phase() == ResidentMeal.Phase.RETURN
                        ? meal.clearingSurface() : serviceSurface(state, meal)).standingBody())
                || actor == null || !validHotMealBody(state, meal, lease, actor.body()))
            throw new IllegalArgumentException("HOT meal step lacks its exact resident, body or lease");
        return meal;
    }

    private static boolean validHotMealBody(FrontierWorldState state, ResidentMeal meal,
                                            AmbientActorLease lease, BodyPosition body) {
        if (body.equals(serviceSurface(state, meal).standingBody())) return true;
        if (meal.phase() != ResidentMeal.Phase.RETURN) return false;
        // COLD may have advanced the return route before this HOT lease was admitted.
        // The exact admitted handoff is then an authoritative checkpoint inside the
        // depot throat; a later witnessed exit checkpoints a cleared body instead.
        return body.equals(lease.handoffBody()) || port(state, meal).accessBoundary().cleared(body);
    }

    private static SettlementDepotServicePort port(FrontierWorldState state, ResidentMeal meal) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        return SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow());
    }

    /** Checkpoint only the witnessed exit; neither eating nor the return journey ends here. */
    public static FrontierWorldState reduceHotAccessCleared(FrontierWorldState state, SubjectId subject,
                                                             ResidentMealHotAccessCleared cleared) {
        ResidentMeal meal = hotMeal(state, subject, cleared.residentId(), cleared.ambientRevision());
        if (meal.phase() != ResidentMeal.Phase.RETURN || meal.pendingPhysicalStep().isPresent()
                || !ServiceAccessCoordinator.witnessedMealExit(state, meal, cleared.observedBody()))
            throw new IllegalArgumentException("HOT meal access clearance lacks a witnessed route exit");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actors.get(subject).withBody(cleared.observedBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
    }

    public static FrontierWorldState reduceHotHandMaterialized(FrontierWorldState state, SubjectId subject,
                                                               ResidentMealHotHandMaterialized observed) {
        ResidentMeal meal = hotMeal(state, subject, observed.residentId(), observed.ambientRevision());
        if (meal.phase() != ResidentMeal.Phase.CONSUME || meal.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("meal hand projection requires retained consume stage");
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        if (ledger.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(meal.actorAccountId())))
            throw new IllegalArgumentException("meal hand already has a current HOT binding");
        requireMealHand(state, meal, observed.observedHand());
        return state.withInventory(state.inventory().withFungibleResources(ledger.rebind(meal.actorAccountId(),
                observed.ambientRevision(), FungiblePhysicalObservation.bind(ledger, meal.actorAccountId(),
                        observed.ambientRevision(), List.of(observed.observedHand())))));
    }

    public static FrontierWorldState reduceHotHandReleased(FrontierWorldState state, SubjectId subject,
                                                           ResidentMealHotHandReleased released) {
        ResidentMeal meal = hotMeal(state, subject, released.residentId(), released.ambientRevision());
        if (meal.phase() != ResidentMeal.Phase.CONSUME || meal.pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("meal hand release requires retained consume stage");
        requireMealHand(state, meal, released.observedHand());
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        List<PhysicalStackBinding> bindings = ledger.bindings().values().stream()
                .filter(binding -> binding.accountId().equals(meal.actorAccountId())).toList();
        if (bindings.size() != 1 || !bindings.getFirst().address().equals(released.observedHand().address())
                || bindings.getFirst().authorityEpoch() != released.ambientRevision()
                || !bindings.getFirst().lotQuantities().equals(Map.of(meal.lotId(), 1)))
            throw new IllegalArgumentException("meal hand release differs from exact HOT custody");
        return state.withInventory(state.inventory().withFungibleResources(
                ledger.releaseBindings(meal.actorAccountId(), released.ambientRevision())));
    }

    public static FrontierWorldState reduceHotReturned(FrontierWorldState state, SubjectId subject,
                                                       ResidentMealHotReturned returned) {
        ResidentMeal meal = hotMeal(state, subject, returned.residentId(), returned.ambientRevision());
        if (meal.phase() != ResidentMeal.Phase.RETURN || meal.pendingPhysicalStep().isPresent()
                || !ServiceAccessCoordinator.cleared(state, meal, returned.observedBody()))
            throw new IllegalArgumentException("HOT meal cannot return before one confirmed consumption");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actors.get(subject).withBody(returned.observedBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .humanPopulation(state.humanPopulation().completeMeal(meal)));
    }

    /** The physical clearance receipt, activity wake and old progress retirement are atomic. */
    public static List<ProposedEvent> planHotReturned(FrontierWorldState state,
                                                       ResidentMealHotReturned returned, long atTick) {
        ResidentMeal meal = state.humanPopulation().meals().get(returned.residentId());
        if (meal == null) throw new IllegalArgumentException("HOT meal clearance has no retained meal");
        ResidentActivityProcess.reduceMealReturned(state, returned.residentId(), returned, atTick);
        return List.of(new ProposedEvent(returned.residentId(), returned),
                ResidentActivityProcess.wakeAfterMeal(returned.residentId(), atTick),
                new ProposedEvent(returned.residentId(), new ScheduleEffect.Cancelled(
                        progress(meal, Math.max(atTick, meal.startedAtTick() + 1L)).id())));
    }

    private static void requireMealHand(FrontierWorldState state, ResidentMeal meal,
                                        FungiblePhysicalObservation.Stack observed) {
        if (!(observed.address() instanceof PhysicalStackAddress.ActorHand hand)
                || !hand.actorId().equals(meal.residentId())
                || !hand.entityId().equals(SceneLease.deterministicEntityId(state.bootstrap().worldId(), meal.residentId()))
                || !observed.itemKind().equals(ResidentMeal.BREAD_KIND) || observed.quantity() != 1)
            throw new IllegalArgumentException("meal hand observation does not name one exact resident bread");
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(meal.actorAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(meal.residentId()))
                || !account.lotQuantities().equals(Map.of(meal.lotId(), 1))
                || !account.claimQuantities().equals(Map.of(meal.claimId(), 1)))
            throw new IllegalArgumentException("meal hand observation has no exact canonical bread and claim");
    }

    /** Plans at most one known COLD edge or stage; no route cursor becomes canonical state. */
    public static Optional<ResidentMealColdStep> planColdStep(FrontierWorldState state,
                                                              SubjectId residentId, long now) {
        ResidentMeal meal = state.humanPopulation().meals().get(residentId);
        if (meal == null || now < meal.startedAtTick()) return Optional.empty();
        if (meal.pendingPhysicalStep().isPresent()) return Optional.empty();
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
        if (meal.phase() == ResidentMeal.Phase.RETURN) {
            if (meal.coldTravel().isPresent()) return arrivedTravelStep(state, meal, now);
            try {
                ResidentMealKnownNavigation.returnPath(state, meal);
                return Optional.of(new ResidentMealColdStep(residentId, meal.phase(), now));
            } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                return Optional.empty();
            }
        }
        if (meal.phase() != ResidentMeal.Phase.MOVE)
            return Optional.of(new ResidentMealColdStep(residentId, meal.phase(), now));
        if (meal.coldTravel().isPresent()) return arrivedTravelStep(state, meal, now);
        try {
            List<SurfaceAnchor> route = ResidentMealKnownNavigation.path(state, meal);
            if (route.size() == 1 && !actor.supportingSurface().equals(serviceSurface(state, meal)))
                return Optional.empty();
            return Optional.of(new ResidentMealColdStep(residentId, meal.phase(), now));
        } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return Optional.empty();
        }
    }

    private static Optional<ResidentMealColdStep> arrivedTravelStep(FrontierWorldState state, ResidentMeal meal, long now) {
        TimedKnownRoute travel = meal.coldTravel().orElseThrow();
        if (firstKnownBarrier(state, travel) >= 0)
            return Optional.of(new ResidentMealColdStep(meal.residentId(), meal.phase(), now));
        if (!travel.arrivedBy(now)) return Optional.empty();
        if (meal.phase() == ResidentMeal.Phase.MOVE
                && travel.route().getLast().equals(serviceSurface(state, meal))
                && !ServiceAccessCoordinator.depotAvailableForMeal(state, meal.depotId(), meal.residentId()))
            return Optional.of(new ResidentMealColdStep(meal.residentId(), meal.phase(), now));
        return Optional.of(new ResidentMealColdStep(meal.residentId(), meal.phase(), now,
                Optional.of(travel.route().getLast())));
    }

    private static int firstKnownBarrier(FrontierWorldState state, TimedKnownRoute travel) {
        for (int index = 1; index < travel.route().size(); index++) {
            SurfaceAnchor surface = travel.route().get(index);
            BlockPosition support = surface.support();
            if (state.physicalDeltas().containsKey(support)
                    || state.physicalDeltas().containsKey(support.offset(0, 1, 0))
                    || state.physicalDeltas().containsKey(support.offset(0, 2, 0))) return index;
        }
        return -1;
    }

    /** A newly observed change behind the as-of body must not rewind the resident. */
    private static BodyPosition interruptionBodyAt(ResidentMeal meal, long atTick) {
        TimedKnownRoute travel = meal.coldTravel().orElseThrow();
        int index = travel.indexAt(Math.min(atTick, travel.arrivalTick() - 1L));
        return BodyPosition.above(travel.route().get(index));
    }

    /** Exact route-owner match for a newly witnessed physical cell change. */
    public static boolean travelIntersects(ResidentMeal meal, BlockPosition cell) {
        if (meal.coldTravel().isEmpty()) return false;
        List<SurfaceAnchor> route = meal.coldTravel().orElseThrow().route();
        for (int index = 1; index < route.size(); index++) {
            SurfaceAnchor surface = route.get(index);
            BlockPosition support = surface.support();
            if (cell.equals(support) || cell.equals(support.offset(0, 1, 0))
                    || cell.equals(support.offset(0, 2, 0))) return true;
        }
        return false;
    }

    private static long nextColdDue(FrontierWorldState state, ResidentMeal meal, long now) {
        if (meal.phase() != ResidentMeal.Phase.MOVE && meal.phase() != ResidentMeal.Phase.RETURN)
            return Math.addExact(now, COLD_TICKS_PER_EDGE);
        if (meal.coldTravel().isPresent()) {
            TimedKnownRoute travel = meal.coldTravel().orElseThrow();
            return firstKnownBarrier(state, travel) >= 0 ? Math.addExact(now, 1L)
                    : Math.max(Math.addExact(now, 1L), travel.arrivalTick());
        }
        List<SurfaceAnchor> route = routeToBoundary(state, meal);
        if (route.size() <= 1) return Math.addExact(now, COLD_TICKS_PER_EDGE);
        return timedRoute(state, meal, route, now).arrivalTick();
    }

    private static TimedKnownRoute timedRoute(FrontierWorldState state, ResidentMeal meal,
                                             List<SurfaceAnchor> route, long now) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L, List.of(route.getLast()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        AmbientActorLease previous = state.ambientLeases().get(meal.residentId());
        long epoch = previous == null ? 1L : Math.addExact(previous.revision(), 1L);
        return new TimedKnownRoute(order, route, now, COLD_TICKS_PER_EDGE, epoch);
    }

    /** Travel never coalesces across the single physical service entrance or its exit. */
    private static List<SurfaceAnchor> routeToBoundary(FrontierWorldState state, ResidentMeal meal) {
        List<SurfaceAnchor> route = meal.phase() == ResidentMeal.Phase.MOVE
                ? ResidentMealKnownNavigation.path(state, meal)
                : ResidentMealKnownNavigation.returnPath(state, meal);
        ServiceAccessBoundary boundary = port(state, meal).accessBoundary();
        if (meal.phase() == ResidentMeal.Phase.MOVE) {
            for (int index = 1; index < route.size() - 1; index++)
                if (index > 1 && boundary.occupied(route.get(index).standingBody()))
                    return List.copyOf(route.subList(0, index));
        } else if (boundary.occupied(route.getFirst().standingBody())) {
            for (int index = 1; index < route.size() - 1; index++)
                if (boundary.cleared(route.get(index).standingBody()))
                    return List.copyOf(route.subList(0, index + 1));
        }
        return route;
    }

    public static SurfaceAnchor serviceSurface(FrontierWorldState state, ResidentMeal meal) {
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
                if (meal.coldTravel().isEmpty()) {
                    List<SurfaceAnchor> route = routeToBoundary(state, meal);
                    if (route.size() > 1) {
                        TimedKnownRoute travel = timedRoute(state, meal, route, step.atTick());
                        yield state.withHumanPopulation(state.humanPopulation().advanceMeal(meal,
                                meal.withColdTravel(travel)));
                    }
                }
                if (meal.coldTravel().isPresent() && step.nextSurface().isEmpty()) {
                    Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
                    actors.put(subject, actor.withBody(interruptionBodyAt(meal, step.atTick())));
                    yield state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                            .humanPopulation(state.humanPopulation().advanceMeal(meal,
                                    meal.withoutColdTravel())));
                }
                if (step.nextSurface().isPresent()) {
                    Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
                    actors.put(subject, actor.withBody(BodyPosition.above(step.nextSurface().orElseThrow())));
                    ResidentMeal arrived = meal.withoutColdTravel();
                    yield state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                            .humanPopulation(state.humanPopulation().advanceMeal(meal, arrived)));
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
                        .completeMeal(meal);
                Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement> movements =
                        new LinkedHashMap<>(state.actorMovements());
                if (movements.putIfAbsent(subject, ActorMovementProcess.afterMeal(meal, step.atTick())) != null)
                    throw new IllegalArgumentException("resident meal competes with an existing movement order");
                yield state.withChanges(FrontierWorldStateUpdate.begin()
                        .inventory(state.inventory().withFungibleResources(ledger)).humanPopulation(people)
                        .actorMovements(movements));
            }
            case RETURN -> {
                ResidentMealColdStep expected = planColdStep(state, subject, step.atTick())
                        .orElseThrow(() -> new IllegalArgumentException("meal has no current COLD clearing step"));
                if (!step.equals(expected))
                    throw new IllegalArgumentException("meal COLD clearance differs from its known route");
                if (meal.coldTravel().isEmpty()) {
                    List<SurfaceAnchor> route = routeToBoundary(state, meal);
                    if (route.size() > 1) {
                        TimedKnownRoute travel = timedRoute(state, meal, route, step.atTick());
                        yield state.withHumanPopulation(state.humanPopulation().advanceMeal(meal,
                                meal.withColdTravel(travel)));
                    }
                }
                if (meal.coldTravel().isPresent() && step.nextSurface().isEmpty()) {
                    Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
                    actors.put(subject, actor.withBody(interruptionBodyAt(meal, step.atTick())));
                    yield state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                            .humanPopulation(state.humanPopulation().advanceMeal(meal,
                                    meal.withoutColdTravel())));
                }
                if (step.nextSurface().isPresent()) {
                    Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
                    actors.put(subject, actor.withBody(BodyPosition.above(step.nextSurface().orElseThrow())));
                    ResidentMeal arrived = meal.withoutColdTravel();
                    yield state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                            .humanPopulation(state.humanPopulation().advanceMeal(meal, arrived)));
                }
                if (!ServiceAccessCoordinator.cleared(state, meal, actor.body()))
                    throw new IllegalArgumentException("meal cannot release an occupied service throat");
                yield state.withHumanPopulation(state.humanPopulation().completeMeal(meal));
            }
        };
    }
}
