package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;

import java.util.ArrayList;
import java.util.List;

/** Pure command/reducer boundary for post-effect physical evidence. */
public final class FrontierWorldPhysicalObservationProcess {
    private FrontierWorldPhysicalObservationProcess() { }

    static CommandPlan plan(FrontierWorldState state, PhysicalDeltaObserved observed, long submittedAt) {
        FrontierWorldState after;
        try { after = state.recordPhysicalDelta(observed.delta()); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(FrontierExecutionSubjects.PHYSICAL_EXECUTOR, observed));
        appendMealRouteWakeups(after, List.of(observed.delta()), submittedAt, events);
        appendActorMovementRouteWakeups(after, List.of(observed.delta()), submittedAt, events);
        appendProductionFacilityFailures(after, List.of(observed.delta()), events);
        if (isKnownRouteLoss(observed.delta()) && !hasActiveAffectedOperation(after, observed.delta())) {
            for (Settlement settlement : after.bootstrap().settlements()) {
                if (!after.routeTopology().supplyPassable(after.bootstrap(), settlement.id())) {
                    var reconsideration = StrategicObjectiveProcess.routeReconsideration(settlement.id(), observed.delta().position(), "loss", Math.addExact(submittedAt, 1L));
                    events.add(new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(reconsideration)));
                }
            }
        }
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     PhysicalDeltaObserved observed) {
        if (!subject.equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR)) throw new IllegalArgumentException("physical delta lacks trusted executor subject");
        return state.recordPhysicalDelta(observed.delta());
    }

    static CommandPlan plan(FrontierWorldState state, PhysicalDeltasObserved observed, long submittedAt) {
        FrontierWorldState after;
        try { after = FrontierWorldPhysicalDeltaSupport.recordAll(state, observed.deltas()); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        List<ProposedEvent> events = new ArrayList<>();
        events.add(new ProposedEvent(FrontierExecutionSubjects.PHYSICAL_EXECUTOR, observed));
        appendMealRouteWakeups(after, observed.deltas(), submittedAt, events);
        appendActorMovementRouteWakeups(after, observed.deltas(), submittedAt, events);
        appendProductionFacilityFailures(after, observed.deltas(), events);
        java.util.Optional<PhysicalDelta> genericRouteLoss = observed.deltas().stream()
                .filter(FrontierWorldPhysicalObservationProcess::isKnownRouteLoss)
                .filter(delta -> !hasActiveAffectedOperation(after, delta)).findFirst();
        if (genericRouteLoss.isPresent()) {
            for (Settlement settlement : after.bootstrap().settlements()) {
                if (!after.routeTopology().supplyPassable(after.bootstrap(), settlement.id())) {
                    PhysicalDelta cause = genericRouteLoss.orElseThrow();
                    var reconsideration = StrategicObjectiveProcess.routeReconsideration(settlement.id(), cause.position(), "loss", Math.addExact(submittedAt, 1L));
                    events.add(new ProposedEvent(settlement.id(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(reconsideration)));
                }
            }
        }
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                     PhysicalDeltasObserved observed) {
        if (!subject.equals(FrontierExecutionSubjects.PHYSICAL_EXECUTOR)) throw new IllegalArgumentException("physical delta lacks trusted executor subject");
        return FrontierWorldPhysicalDeltaSupport.recordAll(state, observed.deltas());
    }

    private static boolean isKnownRouteLoss(PhysicalDelta delta) {
        return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS && delta.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent()
                && delta.semanticPart().filter(part -> part == GrayboxSemanticPart.ROUTE_SURFACE || part == GrayboxSemanticPart.ROUTE_FOUNDATION).isPresent();
    }

    private static void appendMealRouteWakeups(FrontierWorldState state, List<PhysicalDelta> deltas,
                                                long submittedAt, List<ProposedEvent> events) {
        state.humanPopulation().meals().values().stream()
                .filter(meal -> meal.coldTravel().isPresent())
                .filter(meal -> deltas.stream().anyMatch(delta ->
                        ResidentMealProcess.travelIntersects(meal, delta.position())))
                .sorted(java.util.Comparator.comparing(ResidentMeal::residentId))
                .forEach(meal -> {
                    events.add(new ProposedEvent(meal.residentId(),
                            new ResidentMealColdStep(meal.residentId(), meal.phase(), submittedAt, meal.executionId())));
                    events.add(new ProposedEvent(meal.residentId(),
                            new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled(
                                    ResidentMealProcess.progress(meal, Math.addExact(meal.startedAtTick(), 1L)).id(),
                                    ResidentMealProcess.progress(meal, Math.max(Math.addExact(submittedAt, 1L),
                                            Math.addExact(meal.startedAtTick(), 1L))))));
                });
    }

    private static void appendActorMovementRouteWakeups(FrontierWorldState state, List<PhysicalDelta> deltas,
                                                         long submittedAt, List<ProposedEvent> events) {
        state.actorMovements().values().stream()
                .filter(movement -> movement.coldTravel().isPresent())
                .filter(movement -> deltas.stream().anyMatch(delta ->
                        ActorMovementProcess.travelIntersects(movement, delta.position())))
                .sorted(java.util.Comparator.comparing(movement -> movement.order().actorId()))
                .forEach(movement -> {
                    var actor = movement.order().actorId();
                    events.add(new ProposedEvent(actor,
                            new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced(
                                    actor, movement.order().goalRevision(), submittedAt, movement.executionId())));
                    events.add(new ProposedEvent(actor,
                            new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled(
                                    ActorMovementProcess.progress(movement, Math.addExact(movement.issuedAtTick(), 1L)).id(),
                                    ActorMovementProcess.progress(movement, Math.max(Math.addExact(submittedAt, 1L),
                                            Math.addExact(movement.issuedAtTick(), 1L))))));
                });
    }

    /**
     * A moving operation retains the narrowest available causal source for a
     * route loss.  Letting every settlement with an overlapping long-haul
     * corridor start an immediate generic patrol duplicates work, obscures the
     * actual convoy cause and can saturate the bounded scheduler.  Its terminal
     * failure emits the exact operation-backed reconsideration instead.
     */
    private static boolean hasActiveAffectedOperation(FrontierWorldState state, PhysicalDelta delta) {
        return state.operations().values().stream()
                .filter(operation -> operation.stage() == OperationStage.ASSEMBLING || operation.stage() == OperationStage.EN_ROUTE
                        || operation.stage() == OperationStage.RETURNING || operation.stage() == OperationStage.ARRIVED)
                .anyMatch(operation -> FrontierRouteNetwork.containsOperationSurfaceCell(operation.route(), delta.position()));
    }

    /** One physical observation may damage several graybox cells, but each named workshop job is blocked once. */
    private static void appendProductionFacilityFailures(FrontierWorldState after, List<PhysicalDelta> deltas, List<ProposedEvent> events) {
        deltas.stream().filter(delta -> delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS)
                .map(PhysicalDelta::semanticTarget).flatMap(java.util.Optional::stream)
                .filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.SETTLEMENT_STRUCTURE).map(PhysicalDeltaSemanticTarget::subjectId)
                .distinct().sorted().filter(structure -> after.structureConditions().get(structure) != StructureCondition.INTACT)
                .forEach(structure -> events.addAll(ProductionProcess.planFacilityUnavailable(after, structure)));
    }

    static CommandPlan planResourceDeposit(FrontierWorldState state, ResourceDeposited deposited) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) deposited.item().custody();
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, "resource deposit has an unknown container"));
        }
        try { reduceResourceDeposit(state, container.ownerId(), deposited); }
        catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), deposited)));
    }

    static FrontierWorldState reduceResourceDeposit(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                     ResourceDeposited deposited) {
        InventoryCustody.ContainerSlot slot = (InventoryCustody.ContainerSlot) deposited.item().custody();
        ContainerRecord container = state.inventory().containers().get(slot.containerId());
        if (container == null || !subject.equals(container.ownerId())) {
            throw new IllegalArgumentException("resource deposit lacks the owned container subject");
        }
        if (!deposited.item().economicOwnerId().equals(container.ownerId())) {
            throw new IllegalArgumentException("resource deposit claim does not belong to its receiving container owner");
        }
        ContainerSurface surface = state.inventory().surfaces().get(slot.containerId());
        if (surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("resource deposit needs an active owned container surface");
        }
        if (!state.containerSlotAvailable(slot)) {
            throw new IllegalArgumentException("resource deposit targets an occupied canonical slot");
        }
        return state.withInventory(state.inventory().store(deposited.item()));
    }

    static CommandPlan planFungibleLayout(FrontierWorldState state, FungibleStackLayoutObserved observed) {
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(observed.accountId());
        if (account == null) return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY,
                "fungible layout observation has an unknown custody account"));
        SubjectId owner = owner(state, account);
        try { reduceFungibleLayout(state, owner, observed); }
        catch (IllegalArgumentException invalid) { return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage())); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner, observed)));
    }

    static FrontierWorldState reduceFungibleLayout(FrontierWorldState state, SubjectId subject, FungibleStackLayoutObserved observed) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources(); CustodyAccount account = ledger.accounts().get(observed.accountId());
        if (account == null || !subject.equals(owner(state, account))) throw new IllegalArgumentException("fungible layout observation has no owning subject");
        if (account.custody() instanceof ResourceCustody.Actor) {
            throw new IllegalArgumentException("actor-hand layout requires its exact work/physical-effect owner");
        }
        if (account.custody() instanceof ResourceCustody.Container container
                && ReferenceContainerCustody.isReferenceContainer(state, container.containerId())) {
            PhysicalCustodyLease lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container.containerId()));
            if (!ReferenceContainerCustody.hasOperationalCustody(state, container.containerId())
                    || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED || lease.authorityEpoch() != observed.authorityEpoch()) {
                throw new IllegalArgumentException("fungible layout requires the current acquired reference custody epoch");
            }
        }
        List<PhysicalStackBinding> bindings = FungiblePhysicalObservation.bind(ledger, account.id(), observed.authorityEpoch(), observed.stacks());
        return ProductionResourceCustody.bind(state, account.id(), observed.authorityEpoch(), bindings);
    }

    static CommandPlan planFungibleHandoff(FrontierWorldState state, FungibleResourceHandoffObserved observed) {
        CustodyAccount source = state.inventory().fungibleResources().accounts().get(observed.sourceAccountId());
        if (source == null) return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY,
                "fungible handoff has an unknown source account"));
        SubjectId owner = owner(state, source);
        try { reduceFungibleHandoff(state, owner, observed); }
        catch (IllegalArgumentException invalid) { return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage())); }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(owner, observed)));
    }

    static CommandPlan planFungibleStockDeparture(FrontierWorldState state, FungibleStockDepartureObserved observed,
                                                  long now) {
        try {
            reduceFungibleStockDeparture(state, observed.economicOwnerId(), observed);
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        List<ProposedEvent> events = new java.util.ArrayList<>();
        events.add(new ProposedEvent(observed.economicOwnerId(), observed));
        for (SubjectId claimId : observed.forfeitedClaimIds().stream().sorted().toList()) {
            ClaimAllocation claim = state.inventory().fungibleResources().claims().get(claimId);
            ResidentMeal meal = state.humanPopulation().meals().get(claim.claimantId());
            events.add(new ProposedEvent(meal.residentId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled(
                    ResidentMealProcess.progress(meal, meal.startedAtTick() + 1L).id())));
            events.add(ResidentActivityProcess.wakeAfterMeal(meal.residentId(), now));
        }
        events.add(playerStockWake(observed.economicOwnerId(), observed.interactionId(), now));
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    static FrontierWorldState reduceFungibleStockDeparture(FrontierWorldState state, SubjectId subject,
                                                          FungibleStockDepartureObserved observed) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        CustodyAccount source = ledger.accounts().get(observed.sourceAccountId());
        ContainerRecord container = state.inventory().containers().get(observed.containerId());
        ContainerSurface surface = state.inventory().surfaces().get(observed.containerId());
        if (source == null || !(source.custody() instanceof ResourceCustody.Container actual)
                || !actual.containerId().equals(observed.containerId()) || container == null
                || surface == null || ReferenceContainerCustody.blocksCanonicalUse(state, observed.containerId())
                || !container.ownerId().equals(subject) || !subject.equals(observed.economicOwnerId())
                || observed.departedLots().keySet().stream().anyMatch(id -> {
                    ResourceLot lot = ledger.lots().get(id);
                    return lot == null || !lot.economicOwnerId().equals(subject);
                })) {
            throw new IllegalArgumentException("stock departure lacks its declared current owner and container");
        }
        if (ReferenceContainerCustody.isReferenceContainer(state, observed.containerId())) {
            PhysicalCustodyLease lease = state.replicaCustody().custodyByScope()
                    .get(ReferenceContainerCustody.scopeId(observed.containerId()));
            if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                    || !ReferenceContainerCustody.hasOperationalCustody(state, observed.containerId())
                    || lease.authorityEpoch() != observed.authorityEpoch()) {
                throw new IllegalArgumentException("stock departure lacks its current physical custody epoch");
            }
        }
        if (!observed.equals(FungibleDepotPlayerEdit.classify(ledger, observed.sourceAccountId(),
                observed.containerId(), observed.economicOwnerId(), observed.authorityEpoch(),
                observed.playerId(), observed.interactionId(), observed.remaining())))
            throw new IllegalArgumentException("stock departure differs from its exact witnessed edit classification");
        HumanPopulation population = state.humanPopulation();
        var executions = state.actorExecutions();
        for (SubjectId claimId : observed.forfeitedClaimIds().stream().sorted().toList()) {
            ClaimAllocation claim = ledger.claims().get(claimId);
            ResidentMeal meal = claim == null ? null : population.meals().get(claim.claimantId());
            if (claim == null || claim.purpose() != ClaimPurpose.RESIDENT_MEAL || meal == null
                    || !meal.claimId().equals(claimId) || !meal.sourceAccountId().equals(observed.sourceAccountId())
                    || !meal.portion().lotQuantities().equals(claim.lotQuantities())
                    || ledger.accounts().containsKey(meal.actorAccountId()))
                throw new IllegalArgumentException("stock exit cannot retire a foreign or physically held meal claim");
            population = population.abandonMealSource(meal);
            executions = executions.finish(meal.executionId());
        }
        FungibleResourceLedger cleared = observed.forfeitedClaimIds().isEmpty()
                ? ledger : ledger.releaseClaims(observed.forfeitedClaimIds());
        FungibleResourceLedger departed = cleared.departObserved(observed.sourceAccountId(),
                observed.authorityEpoch(), observed.departedLots(), observed.remaining());
        FrontierWorldState next = state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(departed)).humanPopulation(population)
                .actorExecutions(executions));
        for (SubjectId claimId : observed.forfeitedClaimIds().stream().sorted().toList()) {
            ClaimAllocation claim = ledger.claims().get(claimId);
            next = ResidentActivityProcess.retargetHotResident(next, claim.claimantId(),
                    Math.max(1L, state.humanPopulation().meals().get(claim.claimantId()).startedAtTick()));
        }
        return next;
    }

    static CommandPlan planFungibleStockContribution(FrontierWorldState state,
                                                     FungibleStockContributionObserved observed, long now) {
        try {
            reduceFungibleStockContribution(state, observed.contribution().economicOwnerId(), observed);
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        return new CommandPlan.Accepted(List.of(new ProposedEvent(observed.contribution().economicOwnerId(), observed),
                playerStockWake(observed.contribution().economicOwnerId(), observed.interactionId(), now)));
    }

    private static ProposedEvent playerStockWake(SubjectId settlementId, java.util.UUID interactionId,
                                                 long now) {
        return new ProposedEvent(settlementId, new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                StrategicObjectiveProcess.playerStockReconsideration(settlementId, interactionId,
                        Math.addExact(now, 1L))));
    }

    static FrontierWorldState reduceFungibleStockContribution(FrontierWorldState state, SubjectId subject,
                                                              FungibleStockContributionObserved observed) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources();
        CustodyAccount account = ledger.accounts().get(observed.accountId());
        ContainerRecord container = state.inventory().containers().get(observed.containerId());
        ContainerSurface surface = state.inventory().surfaces().get(observed.containerId());
        if (account != null && !(account.custody() instanceof ResourceCustody.Container custody
                && custody.containerId().equals(observed.containerId())) || container == null
                || surface == null || ReferenceContainerCustody.blocksCanonicalUse(state, observed.containerId())
                || !container.ownerId().equals(subject) || !subject.equals(observed.contribution().economicOwnerId())
                || !FrontierWorldState.depotId(subject).equals(observed.containerId())) {
            throw new IllegalArgumentException("stock contribution lacks its declared settlement depot");
        }
        PhysicalCustodyLease lease = state.replicaCustody().custodyByScope()
                .get(ReferenceContainerCustody.scopeId(observed.containerId()));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED
                || !ReferenceContainerCustody.hasOperationalCustody(state, observed.containerId())
                || lease.authorityEpoch() != observed.authorityEpoch()) {
            throw new IllegalArgumentException("stock contribution lacks current physical custody authority");
        }
        FungibleResourceLedger contributed = ledger.contributeObserved(observed.accountId(),
                observed.authorityEpoch(), observed.contribution(), observed.observed());
        return state.withInventory(state.inventory().withFungibleResources(contributed));
    }

    static FrontierWorldState reduceFungibleHandoff(FrontierWorldState state, SubjectId subject, FungibleResourceHandoffObserved observed) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources(); CustodyAccount source = ledger.accounts().get(observed.sourceAccountId());
        if (source == null || !subject.equals(owner(state, source))) throw new IllegalArgumentException("fungible handoff has no owning subject");
        if (source.custody() instanceof ResourceCustody.Actor
                || observed.destinationAccount().custody() instanceof ResourceCustody.Actor) {
            throw new IllegalArgumentException("actor-hand transfer requires its exact work/physical-effect owner");
        }
        if (!observed.claimQuantities().isEmpty() && observed.forfeitedClaimIds().isEmpty()) {
            throw new IllegalArgumentException("physical handoff cannot move a live claimed allocation without retiring its owner");
        }
        if (!observed.forfeitedClaimIds().isEmpty()) return FungibleClaimForfeitureStateSupport.apply(state, observed);
        CustodyAccount existing = ledger.accounts().get(observed.destinationAccount().id());
        FungibleResourceLedger transferred;
        if (existing == null) {
            transferred = ledger.transferObservedToNewAccount(observed.sourceAccountId(), observed.destinationAccount(), observed.sourceEpoch(),
                    observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(), observed.remainingSource(), observed.destinationBindings());
        } else {
            if (!expectedDestination(existing, observed).equals(observed.destinationAccount())) {
                throw new IllegalArgumentException("fungible handoff has a forged existing destination balance");
            }
            transferred = ledger.transferObservedToExistingAccount(observed.sourceAccountId(), existing.id(), observed.sourceEpoch(),
                    observed.destinationEpoch(), observed.lotQuantities(), observed.claimQuantities(), observed.remainingSource(), observed.destinationBindings());
        }
        if (!observed.playerSaveFence().isEmpty()) {
            transferred = transferred.fenceUnresolvedPlayerSave(observed.destinationAccount().id(), observed.playerSaveFence());
        }
        return state.withInventory(state.inventory().withFungibleResources(transferred));
    }

    static CommandPlan planFungibleBindingRelease(FrontierWorldState state, FungibleStackBindingsReleased released, long now) {
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(released.accountId());
        if (account == null) return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY,
                "fungible binding release has an unknown custody account"));
        SubjectId owner = owner(state, account);
        var events = new ArrayList<ProposedEvent>();
        FrontierWorldState after;
        try {
            after = state;
            PhysicalCustodyLease containerLease = null;
            if (account.custody() instanceof ResourceCustody.Container container) {
                containerLease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container.containerId()));
                if (containerLease == null || !containerLease.objectId().equals(container.containerId())
                        || !containerLease.providerId().equals(ReferenceContainerCustody.PROVIDER_ID)
                        || containerLease.authorityEpoch() != released.authorityEpoch()) {
                    throw new IllegalArgumentException("fungible container release requires its exact reference custody epoch");
                }
                if (containerLease.status() == PhysicalCustodyLeaseStatus.ACQUIRED) {
                    var checkpoint = new PhysicalReplicaCustodyPayloads.CustodyCheckpointed(containerLease.scopeId(),
                            containerLease.authorityEpoch(), containerLease.expectedCanonicalRevision(), containerLease.expectedReplicaRevision());
                    after = after.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(after.replicaCustody().checkpoint(
                            checkpoint.scopeId(), checkpoint.expectedEpoch(), checkpoint.expectedCanonicalRevision(), checkpoint.expectedReplicaRevision())));
                    events.add(new ProposedEvent(checkpoint.scopeId(), checkpoint));
                }
            }
            after = reduceFungibleBindingRelease(after, owner, released);
            events.add(new ProposedEvent(owner, released));
            if (containerLease != null) {
                // One canonical transaction closes both layout and reference scope. Publishing
                // between these facts strands COLD input behind a live scope if its chunk loads.
                var closed = new PhysicalReplicaCustodyPayloads.CustodyReleased(containerLease.scopeId(),
                        containerLease.authorityEpoch(), containerLease.expectedCanonicalRevision(), containerLease.expectedReplicaRevision());
                after = ReferenceContainerCustody.release(after, closed);
                events.add(new ProposedEvent(closed.scopeId(), closed));
            }
        } catch (IllegalArgumentException invalid) {
            return new CommandPlan.Rejected(new CommandRejection(RejectionCode.REJECTED_BY_POLICY, invalid.getMessage()));
        }
        events.addAll(ProductionProcess.resumeReleasedEffects(state, after, now));
        return new CommandPlan.Accepted(List.copyOf(events));
    }

    static FrontierWorldState reduceFungibleBindingRelease(FrontierWorldState state, SubjectId subject, FungibleStackBindingsReleased released) {
        FungibleResourceLedger ledger = state.inventory().fungibleResources(); CustodyAccount account = ledger.accounts().get(released.accountId());
        if (account == null || !subject.equals(owner(state, account))) throw new IllegalArgumentException("fungible binding release has no owning subject");
        if (account.custody() instanceof ResourceCustody.Actor) {
            throw new IllegalArgumentException("actor-hand release requires its exact scene/work owner");
        }
        return ProductionResourceCustody.release(state, released.accountId(), released.authorityEpoch());
    }

    private static CustodyAccount expectedDestination(CustodyAccount current, FungibleResourceHandoffObserved observed) {
        java.util.Map<SubjectId, Integer> lots = new java.util.HashMap<>(current.lotQuantities());
        observed.lotQuantities().forEach((id, quantity) -> lots.merge(id, quantity, Integer::sum));
        java.util.Map<SubjectId, Integer> claims = new java.util.HashMap<>(current.claimQuantities());
        observed.claimQuantities().forEach((id, quantity) -> claims.merge(id, quantity, Integer::sum));
        return new CustodyAccount(current.id(), current.custody(), lots, claims);
    }

    private static SubjectId owner(FrontierWorldState state, CustodyAccount account) {
        if (account.custody() instanceof ResourceCustody.Container container) {
            ContainerRecord record = state.inventory().containers().get(container.containerId());
            if (record == null) throw new IllegalArgumentException("fungible custody account has no known container owner");
            return record.ownerId();
        }
        return account.lotQuantities().keySet().stream().map(id -> state.inventory().fungibleResources().lots().get(id))
                .map(ResourceLot::economicOwnerId).distinct().reduce((left, right) -> {
                    throw new IllegalArgumentException("fungible physical account has mixed economic owners");
                }).orElseThrow(() -> new IllegalArgumentException("fungible custody account has no resource owner"));
    }
}
