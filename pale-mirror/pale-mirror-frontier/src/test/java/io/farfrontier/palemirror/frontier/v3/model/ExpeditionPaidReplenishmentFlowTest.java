package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Post-arrival unit fixture. Only the subsequent purchase, clearance and meal are registered outcomes;
 * the initial placement is not native evidence or a claim that the outward trip was exercised here. */
class ExpeditionPaidReplenishmentFlowTest {
    @Test void retainedPaidPurchaseSurvivesRecoveryAndClearsTheSupplierBeforePortableEating() throws Exception {
        purchaseFlow(Scenario.REGULAR);
    }
    @Test void pausedPaidPurchaseReleasesServiceAndResumesExactPromiseAfterRecovery() throws Exception {
        purchaseFlow(Scenario.INTERRUPTED);
    }
    @Test void reservedSupplyEnvelopePreservesUnusedFundsAcrossPurchaseCancellationAndActualReceipt() throws Exception {
        purchaseFlow(Scenario.FUNDED);
    }
    @Test void playerTakingFoodDuringPaidApproachCancelsTheExactMotionAndRefundsWithoutRewinding() throws Exception {
        purchaseFlow(Scenario.WITHDRAWN);
    }
    @Test void unusableFoodAccessDoesNotHoldTheCaravanButUnknownAccessRemainsUnresolvedWithoutNewSearches() throws Exception {
        purchaseFlow(Scenario.BLOCKED_ACCESS);
    }
    @Test void accessBlockedAfterPaidReservationCancelsOnlyTheUnappliedPurchaseAndReselectsUsableAccess() throws Exception {
        purchaseFlow(Scenario.RETAINED_BLOCKED);
    }
    private enum Scenario { REGULAR, INTERRUPTED, FUNDED, WITHDRAWN, BLOCKED_ACCESS, RETAINED_BLOCKED }
    private void purchaseFlow(Scenario scenario) throws Exception {
        boolean interrupt=scenario==Scenario.INTERRUPTED;
        boolean preFunded=scenario==Scenario.FUNDED || scenario==Scenario.WITHDRAWN || scenario==Scenario.RETAINED_BLOCKED;
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:paid-visited-supplies"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(base);
        TransportMission mission = null;
        for (int turn = 0; turn < 2048; turn++) {
            var state = engine.canonicalState().state();
            mission = state.shipments().missions().values().stream().filter(m -> m.stage() == TransportMission.Stage.OUTBOUND).findFirst().orElse(null);
            if (mission != null) break;
            advance(engine, 1);
        }
        assertNotNull(mission);
        var state = engine.canonicalState().state(); var group = state.unitGroups().groups().get(mission.groupId());
        long tick = engine.checkpoint().instant().ticks(); var recipient = group.members().getFirst().actorId();
        var positions = new LinkedHashMap<>(state.actorLocations());
        for (var member : group.members()) positions.put(member.actorId(), positions.get(member.actorId()).withBody(
                member.actorId().equals(recipient) ? mission.receiver().station().standingBody() : mission.destinationRendezvous().standingBody()));
        var groups = state.unitGroups().replace(group, new UnitGroup(group.id(), group.mission(), group.members(), group.formation(),
                UnitGroup.Phase.AT_GOAL, group.revision() + 1, 1, Optional.empty()));
        var movements = new LinkedHashMap<>(state.actorMovements()); group.members().forEach(m -> movements.remove(m.actorId()));
        var resources = state.inventory().fungibleResources(); var accounts = new LinkedHashMap<>(resources.accounts());
        for (var account : resources.accounts().values()) if (account.custody().equals(new ResourceCustody.Actor(recipient))
                && account.claimQuantities().isEmpty()) accounts.put(account.id(), new CustodyAccount(account.id(),
                        new ResourceCustody.WorldCarrier(UUID.nameUUIDFromBytes(account.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        account.lotQuantities(), Map.of()));
        resources = new FungibleResourceLedger(resources.lots(), resources.claims(), accounts, resources.bindings());
        var stock = new ResourceLot(new SubjectId("lot:visited-supplier-stock"), mission.receiver().settlementId(), FoodCatalog.BREAD,
                256, "fixture:finite-foreign-supplier-stock", List.of());
        var staging = new SubjectId("custody:visited-supplier-stock");
        resources = resources.issue(stock, new CustodyAccount(staging, new ResourceCustody.Container(mission.receiver().containerId()),
                Map.of(stock.id(), stock.quantity()), Map.of()));
        var supplierAccount = ReferenceContainerCustody.scopeId(mission.receiver().containerId());
        resources = resources.accounts().containsKey(supplierAccount)
                ? resources.transfer(staging, supplierAccount, Map.of(stock.id(), stock.quantity()), Map.of())
                : resources.transferToNewAccount(staging, new CustodyAccount(supplierAccount,
                        new ResourceCustody.Container(mission.receiver().containerId()), Map.of(stock.id(), stock.quantity()), Map.of()));
        var population = state.humanPopulation(); var nutrition = new LinkedHashMap<>(population.nutrition());
        nutrition.put(recipient, new ResidentNutrition(ResidentNutritionStatus.HUNGRY, state.bootstrap().ruleset().residentLife().eatBelowUnits() - 1, tick, 0));
        var precondition = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions).actorMovements(movements).unitGroups(groups)
                .inventory(state.inventory().withFungibleResources(resources)).humanPopulation(new HumanPopulation(population.households(), population.residents(), population.birthJobs(),
                    population.health(), population.quarantines(), population.migrations(), population.provisions(), nutrition,
                    population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations())));
        // The ordinary goods workflow has first access. A refill cannot deadlock its
        // own courier's unloading, and terminal courier work must release UAE before reuse.
        var arrivingConfiguration = new FrontierEngineConfiguration<>(base.worldId(), precondition, new SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), engine.checkpoint().schedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        engine = FrontierEngines.createCanonicalStateAccess(arrivingConfiguration);
        var shipmentIds = mission.shipmentIds(); boolean unloaded = false;
        for (int turn = 0; turn < 2048; turn++) {
            var current = engine.canonicalState().state();
            if (shipmentIds.stream().allMatch(id -> current.shipments().shipments().get(id).terminal())) { unloaded = true; break; }
            advance(engine, 1);
        }
        assertTrue(unloaded); precondition = engine.canonicalState().state(); tick = engine.checkpoint().instant().ticks();
        var retainedMission = precondition.shipments().missions().get(mission.id());
        if(preFunded) {
            // Isolated supplier-arrival precondition with an already reserved supply envelope.
            // Real pre-departure funding is exercised by ExpeditionSupplyBudgetFlowTest.
            var envelope=new FinancialBudget(new SubjectId("budget:retained-supply-envelope"),retainedMission.organization().sponsor().id(),
                    FinancialBudget.OwnerKind.TRANSPORT_MISSION,retainedMission.id(),FixedScalar.whole(20));
            var funded=new TransportMission(retainedMission.id(),retainedMission.groupId(),retainedMission.shipmentIds(),retainedMission.sender(),retainedMission.receiver(),retainedMission.organization(),
                    retainedMission.homeRendezvous(),retainedMission.destinationRendezvous(),retainedMission.stage(),retainedMission.revision(),Optional.of(envelope.id()),retainedMission.supplies(),
                    retainedMission.transportAssetId(),retainedMission.replenishment(),retainedMission.replenishmentPurchase());
            var missions=new LinkedHashMap<>(precondition.shipments().missions());missions.put(funded.id(),funded);
            precondition=precondition.withChanges(FrontierWorldStateUpdate.begin().shipments(new ShipmentState(precondition.shipments().shipments(),missions,precondition.shipments().accumulation()))
                    .inventory(precondition.inventory().withEconomics(precondition.inventory().economics().reserveBudget(envelope))));
            retainedMission=funded;
        } else assertTrue(retainedMission.financialBudgetId().isEmpty(), "provisioned departure must not hold unrelated cash");
        if (scenario==Scenario.BLOCKED_ACCESS) { inaccessibleFood(precondition,retainedMission,tick); return; }
        var transfer = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.select(precondition, retainedMission, tick).orElseThrow();
        assertEquals(mission.receiver().containerId(), transfer.containerId());
        assertFalse(transfer.sourceEconomicOwnerId().equals(mission.sender().settlementId()));
        var held = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.start(precondition, mission.id(), transfer, tick);
        FrontierReferenceClosure.validateTransition(precondition, held, List.of());
        var heldMission = held.shipments().missions().get(mission.id()); var purchase = heldMission.replenishmentPurchase().orElseThrow();
        var remainingBudget=held.inventory().economics().budgets().get(heldMission.financialBudgetId().orElseThrow()).remaining();
        if(preFunded) {
            assertEquals(FixedScalar.whole(20).minus(purchase.payment().amount()),remainingBudget);
            assertEquals(precondition.inventory().economics().availableToReserve(purchase.payment().payerId()),
                    held.inventory().economics().availableToReserve(purchase.payment().payerId()),
                    "spending authorization moves into the exact payment hold without reserving cash twice");
            var cancelled=io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionFundingAuthority.cancelPurchase(held.inventory(),purchase);
            assertEquals(FixedScalar.whole(20),cancelled.economics().budgets().get(heldMission.financialBudgetId().orElseThrow()).remaining(),"a provably cancelled purchase returns money to this mission's envelope");
            assertEquals(held.inventory().economics().accounts(),cancelled.economics().accounts(),"cancellation does not invent a refund transfer");
        } else {
            assertEquals(FixedScalar.ZERO,remainingBudget);
            assertEquals(precondition.inventory().economics().availableToReserve(purchase.payment().payerId()).minus(purchase.payment().amount()),held.inventory().economics().availableToReserve(purchase.payment().payerId()));
        }
        assertEquals(held, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(held)));
        if (scenario==Scenario.WITHDRAWN || scenario==Scenario.RETAINED_BLOCKED) {
            withdrawDuringApproach(held,heldMission,transfer,tick,scenario==Scenario.RETAINED_BLOCKED); return;
        }
        if (interrupt) {
            held = pauseAndResume(held, mission.id(), transfer, tick);
            transfer = held.shipments().missions().get(mission.id()).replenishment().orElseThrow();
        }
        var schedules = new ArrayList<>(engine.checkpoint().schedules());
        schedules.removeIf(action -> group.members().stream().anyMatch(m -> m.actorId().equals(action.subject())) && action.kind().equals(ActorMovementProcess.PROGRESS));
        var review = TransportMissionProcess.progress(mission.id(), tick + 1);
        schedules.removeIf(action -> action.id().equals(review.id())); schedules.add(review);
        for (var resident : held.humanPopulation().residents().values()) {
            var need = ResidentNeedProcess.review(resident.id(), held.humanPopulation().nutrition(resident.id()).nextThresholdTick(held.bootstrap().ruleset().residentLife(),
                    resident.characteristics().effectiveMetabolismPermille(tick)));
            schedules.removeIf(action -> action.id().equals(need.id())); schedules.add(need);
        }
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), held, new SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        engine = FrontierEngines.createCanonicalStateAccess(configuration);
        var checkpoint = engine.checkpoint();
        engine = FrontierEngines.recoverCanonicalStateAccess(configuration,
                new RecoveryImage(configuration.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
        boolean received = false, cleared = false, ate = false;
        for (int turn = 0; turn < 2048; turn++) {
            var current = engine.canonicalState().state();
            if (!current.inventory().economics().reservations().containsKey(purchase.payment().id())) {
                received = true;
                if(preFunded)assertEquals(remainingBudget,current.inventory().economics().budgets().get(heldMission.financialBudgetId().orElseThrow()).remaining(),"actual receipt preserves all unused supply funds");
                assertFalse(current.inventory().fungibleResources().claims().containsKey(transfer.claimId()));
            }
            var movement = current.actorMovements().get(recipient);
            cleared |= movement != null && movement.context() instanceof ActorMovementContext.ResourceAccessExit;
            if (current.humanPopulation().nutrition(recipient).satietyUnits() >= current.bootstrap().ruleset().residentLife().eatBelowUnits()) { ate = true; break; }
            advance(engine, 1);
        }
        assertTrue(received); assertTrue(cleared, "the paid pickup must free the supplier without replacing GROUP/COURIER responsibility");
        assertTrue(ate, "foreign food must become ordinary portable nutrition");
    }
    private static void inaccessibleFood(FrontierWorldState state,TransportMission mission,long now) throws Exception {
        var actor=state.unitGroups().groups().get(mission.groupId()).members().getFirst().actorId();
        var origin=mission.supplies().orElseThrow().journey().returning().surfaces().get(16);
        state=state.withActorBody(actor,origin.standingBody());
        var supplier=state.companies().goodsTrade().participants().participants().get(mission.receiver().settlementId());
        var knowledge=TransportGroupMissionPort.knowledgeForEndpoints(state,mission.sender(),mission.receiver());
        var stations=new HashSet<SurfaceAnchor>();
        for(var id:GoodsParticipantStorage.containers(state,supplier,FoodCatalog.BREAD)) {
            var endpoint=GoodsParticipantStorage.endpoint(state,id);stations.add(endpoint.station());
            stations.addAll(ShipmentEndpointComposition.approachStations(state,endpoint,knowledge.forActor(state,actor)));
        }
        assertFalse(stations.contains(origin));
        var known=new LinkedHashMap<BlockPosition,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation>();
        var unknown=new LinkedHashMap<BlockPosition,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation>();
        for(var station:stations)for(int height=1;height<=5;height++) {
            var pos=station.support().offset(0,height,0);
            known.put(pos,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.known(
                    io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.SOLID));
            unknown.put(pos,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.unavailable(
                    io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.Kind.NOT_OBSERVED));
        }
        var blocked=new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(state.navigationGeometry().revision(),known)
                .apply(state,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved.OWNER);
        var unresolved=new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(state.navigationGeometry().revision(),unknown)
                .apply(state,io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved.OWNER);
        var api=io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.TravelDecision.CONTINUE_ITINERARY;
        try(var ignored=PedestrianRoutePlanning.bind((geometry,from,to)->{throw new AssertionError("movement permission started a route search");})) {
            assertEquals(api,io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.travelDecision(blocked,mission,now),
                    "stock with no usable access must not hold a caravan forever");
            assertEquals(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.TravelDecision.REST_FOR_LOCAL_SUPPLIES,
                    io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.travelDecision(unresolved,mission,now),
                    "unobserved geometry remains a real wait, not a false no-path result");
        }
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.select(blocked,mission,now).isEmpty());
    }

    /** Isolated ordinary movement and physical source-loss witness, not a native player action. */
    private static void withdrawDuringApproach(FrontierWorldState state,TransportMission mission,UnitResourceTransfer transfer,long now,boolean blocked) {
        var actor=transfer.actorId();var accepted=mission.supplies().orElseThrow().journey().returning().surfaces();
        var origin=accepted.get(Math.min(16,accepted.size()-1));
        state=state.withActorBody(actor,origin.standingBody());
        var movement=new ActorMovement(transfer.order(mission.id(),mission.revision()).movementOrder(),now,
                new ActorMovementContext.ExpeditionReplenishment(mission.id()),transfer.execution());
        state=ActorMovementProcess.reduceStarted(state,actor,new ActorMovementStarted(movement));
        var step=ActorMovementProcess.plan(state,ActorMovementProcess.progress(movement,now+1),now+1).stream()
                .map(ProposedEvent::payload).filter(ActorMovementColdAdvanced.class::isInstance).map(ActorMovementColdAdvanced.class::cast).findFirst().orElseThrow();
        state=ActorMovementProcess.reduceColdAdvanced(state,actor,step);
        var travel=state.actorMovements().get(actor).coldTravel().orElseThrow();
        assertTrue(travel.route().size()>2,"the cancellation fixture needs a real interval between departure and arrival");
        long atTick=travel.departedAtTick()+travel.ticksPerEdge();
        var actual=ActorMovementProcess.bodyAt(state,actor,atTick);assertNotEquals(origin.standingBody(),actual);
        var resources=state.inventory().fungibleResources();var quantities=new TreeMap<String,Integer>();
        resources.accounts().get(transfer.sourceAccountId()).lotQuantities().forEach((lot,q)->quantities.merge(resources.lots().get(lot).itemKind(),q,Integer::sum));
        var layout=new ArrayList<FungiblePhysicalObservation.Stack>();int slot=0;
        for(var entry:quantities.entrySet())for(int q=entry.getValue();q>0;q-=Math.min(64,q))
            layout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                    transfer.containerId(),state.inventory().availableSlots(transfer.containerId()).get(slot++))),entry.getKey(),Math.min(64,q)));
        var bound=resources.rebind(transfer.sourceAccountId(),7,FungiblePhysicalObservation.bind(resources,transfer.sourceAccountId(),7,layout));
        state=state.withInventory(state.inventory().withFungibleResources(bound));
        var binding=bound.bindings().values().stream().filter(b->b.accountId().equals(transfer.sourceAccountId())&&b.claimQuantities().containsKey(transfer.claimId())).findFirst().orElseThrow();
        if (blocked) {
            var fact=io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation.known(
                    io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell.SOLID);
            state=new io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved(state.navigationGeometry().revision(),
                    Map.of(transfer.station().support().offset(0,1,0),fact)).apply(state,
                    io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryObserved.OWNER);
            var events=TransportMissionProcess.plan(state,TransportMissionProcess.progress(mission.id(),atTick),atTick);
            var cancelled=events.stream().map(ProposedEvent::payload).filter(ExpeditionReplenishmentCancelled.class::isInstance)
                    .map(ExpeditionReplenishmentCancelled.class::cast).findFirst().orElseThrow();
            var codecs=FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(cancelled,codecs.decode(cancelled.type(),codecs.encode(cancelled)));
            var after=FrontierWorldRuntimeDefinition.reduce(state,new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:blocked-refill"),new TransactionId("transaction:blocked-refill"),state.bootstrap().worldId(),new Revision(1),
                    new SimInstant(atTick),mission.id(),CauseChain.root(new CommandId("command:blocked-refill")),cancelled));
            FrontierReferenceClosure.validateTransition(state,after,List.of());
            assertFalse(after.actorMovements().containsKey(actor));assertEquals(actual,after.actorLocations().get(actor).body());
            assertTrue(after.shipments().missions().get(mission.id()).replenishment().isEmpty());
            assertEquals(state.inventory().fungibleResources().lots(),after.inventory().fungibleResources().lots(),"cancellation creates or consumes no food");
            assertEquals(state.inventory().economics().accounts(),after.inventory().economics().accounts());
            assertEquals(FixedScalar.whole(20),after.inventory().economics().budgets().get(mission.financialBudgetId().orElseThrow()).remaining());
            assertEquals(after,new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
            var resumed=TransportMissionProcess.plan(after,TransportMissionProcess.progress(mission.id(),atTick+1),atTick+1);
            var replacement=resumed.stream().map(ProposedEvent::payload).filter(ExpeditionReplenishmentStarted.class::isInstance)
                    .map(ExpeditionReplenishmentStarted.class::cast).findFirst().orElseThrow();
            assertNotEquals(transfer.station(),replacement.transfer().station(),"an alternate service approach is preferable to abandoning usable food");
            var reselected=io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.start(
                    after,mission.id(),replacement.transfer(),atTick+1);
            assertEquals(state.inventory().economics().accounts(),reselected.inventory().economics().accounts());
            FrontierReferenceClosure.validateTransition(after,reselected,List.of());
            assertEquals(reselected,new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(reselected)));
            return;
        }
        var player=UUID.fromString("00000000-0000-0000-0000-000000000248");
        var observed=FungiblePhysicalHandoff.departToNew(bound,transfer.sourceAccountId(),7,binding,0,new SubjectId("custody:withdrawn-refill"),
                new ResourceCustody.Player(player),1,new PhysicalStackAddress.PlayerSlot(player,0)).forfeitAffectedClaims(bound);
        var after=FungibleResourceHandoffStateSupport.apply(state,observed,atTick);
        FrontierReferenceClosure.validateTransition(state,after,List.of());
        assertFalse(after.actorMovements().containsKey(actor));assertEquals(actual,after.actorLocations().get(actor).body());
        assertTrue(after.shipments().missions().get(mission.id()).replenishment().isEmpty());
        var budget=mission.financialBudgetId().orElseThrow();
        assertEquals(FixedScalar.whole(20),after.inventory().economics().budgets().get(budget).remaining());
        assertEquals(state.inventory().economics().accounts(),after.inventory().economics().accounts());
        assertFalse(after.inventory().economics().reservations().containsKey(mission.replenishmentPurchase().orElseThrow().payment().id()));
        assertEquals(after,new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        var released=FungibleClaimForfeitureStateSupport.released(state,observed);
        assertTrue(released.stream().anyMatch(r->r.actor().equals(actor)&&r.cancelledProgress().equals(Optional.of(ActorMovementProcess.progress(movement,now+1).id()))));
        var departure=assertInstanceOf(FungibleStockDepartureObserved.class,FungibleContainerPlayerEdit.classify(bound,
                transfer.sourceAccountId(),transfer.containerId(),transfer.sourceEconomicOwnerId(),7,player,UUID.randomUUID(),List.of(),
                SourceStockClaimLoss.admissibleClaims(state,transfer.sourceAccountId())));
        var removed=SourceStockClaimLoss.settle(state,departure,atTick);
        FrontierReferenceClosure.validateTransition(state,removed,List.of());
        assertFalse(removed.actorMovements().containsKey(actor));assertEquals(actual,removed.actorLocations().get(actor).body());
        assertEquals(FixedScalar.whole(20),removed.inventory().economics().budgets().get(budget).remaining());
    }

    private static FrontierWorldState pauseAndResume(FrontierWorldState held, SubjectId mission,
            UnitResourceTransfer transfer, long tick) {
        var passive = held.actorExecutions().next(transfer.actorId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, transfer.actorId());
        var paused = ActorExecutionComposition.LIFECYCLE.prepareBegin(held, passive, tick)
                .commit(held, FrontierWorldStateUpdate.begin());
        FrontierReferenceClosure.validateTransition(held, paused, List.of());
        paused = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(paused));
        assertEquals(held.inventory(), paused.inventory(), "pause must neither spend cash nor move reserved food");
        assertTrue(new ExpeditionSupplyServiceAccess().demands(paused, transfer.containerId()).stream()
                .noneMatch(d -> d.identity().actorId().equals(transfer.actorId())), "paused promise cannot reserve service access");
        var pausedBasis = paused;
        assertThrows(IllegalArgumentException.class, () ->
                io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.cold(
                        pausedBasis, mission, transfer.claimId(), tick));
        var successor = paused.actorExecutions().next(transfer.actorId(), transfer.execution().activityKind(),
                transfer.execution().activityOwnerId());
        var resumed = ActorExecutionComposition.LIFECYCLE.prepareResume(paused, transfer.execution(), successor,
                Optional.of(passive), tick).commit(paused, FrontierWorldStateUpdate.begin());
        FrontierReferenceClosure.validateTransition(paused, resumed, List.of());
        assertEquals(transfer.resumed(transfer.execution(), successor), resumed.shipments().missions().get(mission).replenishment().orElseThrow());
        assertEquals(held.inventory(), resumed.inventory());
        assertThrows(IllegalArgumentException.class, () -> transfer.resumed(successor, transfer.execution()));
        var foreign = new ActorExecutionId(transfer.actorId(), successor.activityKind(), new SubjectId("unit-group:foreign"), successor.generation());
        assertThrows(IllegalArgumentException.class, () -> transfer.resumed(transfer.execution(), foreign));
        // An issued physical effect is not a pausable promise, even if its actor
        // still has spare food. Build only that negative precondition here.
        var step = new ActorItemTransferStep(new ActorHotObservation(new ActorActuationId(
                new ActorBodyId(transfer.actorId(), 1), transfer.execution()), 1), List.of(
                new MaterialSourceSelection.Slice(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(transfer.containerId(), 0)), 1, 1, 1)), 1);
        var prepared = transfer.prepare(step);
        assertThrows(IllegalArgumentException.class, () -> prepared.resumed(transfer.execution(), successor));
        var pendingState = held.withChanges(FrontierWorldStateUpdate.begin().shipments(held.shipments()
                .replaceReplenishment(held.shipments().missions().get(mission), Optional.of(prepared))));
        assertEquals(Optional.of(mission), io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority
                .pendingOwnerForActor(pendingState, transfer.actorId()));
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.pendingForContainer(pendingState, transfer.containerId()));
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority
                .pendingOwnerForActor(held, transfer.actorId()).isEmpty(), "a pending successor cannot fence the immutable promise predecessor");
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.LIFECYCLE.prepareBegin(pendingState, passive, tick));
        var forgedPause = paused.withChanges(FrontierWorldStateUpdate.begin().shipments(paused.shipments()
                .replaceReplenishment(paused.shipments().missions().get(mission), Optional.of(prepared))));
        assertThrows(IllegalArgumentException.class, () ->
                io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.validate(
                        forgedPause, forgedPause.shipments().missions().get(mission)));
        return resumed;
    }
    private static void advance(FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection> engine, int budget) {
        var state = engine.canonicalState().state();
        var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
        var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(budget, 1024));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
    }
}
