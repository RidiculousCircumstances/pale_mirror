package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete causal path with actual material consumption, station work, travel and custody. */
class GuardDutyTest {
    private static FrontierWorldState initial() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:peaceful-guards"), 41,
                FrontierRulesets.installed("frontier-v3-simulation-baseline-r31")));
        var home=state.bootstrap().settlements().getFirst().id();
        var staffing=SettlementStaffingComposition.change(state,home,1);
        return staffing.isPresent()?SettlementStaffingComposition.reduce(state,home,staffing.orElseThrow()):state;
    }
    @Test void emptyGuardPlacesCreateFiniteMaterialDemandWithoutGenesisSwordsOrWear() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst().id();
        assertTrue(state.inventory().items().values().stream().noneMatch(item -> item.itemKind().equals("minecraft:iron_sword")));
        var deficit = EquipmentStockProjection.deficits(state, home).stream().filter(d -> d.demand().pool().equals("guard")).findFirst().orElseThrow();
        assertEquals(SettlementWorkPolicy.permissions(state,home).workers(ResidentWorkKind.GUARD).size(), deficit.missingUnits()); assertEquals(0, deficit.missingWear());
        assertTrue(deficit.demand().workWear().isEmpty());
        var order = EquipmentProductionPlanning.planned(state, home, 300).stream().filter(p -> p.request().pool().equals("guard")).findFirst().orElseThrow();
        assertTrue(order.operations() >= 1 && order.operations() <= 2, "unfunded future inputs may start with one obtainable sword");
        assertEquals("minecraft:iron_sword", order.request().recipe().product().itemKind());
        assertTrue(EquipmentRenewalForecast.read(state, home, 300).values().stream().allMatch(q -> q >= 0));
    }
    @Test void forgeMakesTwoSwordsResidentsCollectThemAndRecoveryPreservesExactlyTwoAppointments() {
        var state = armed(); var home = state.bootstrap().settlements().getFirst().id();
        assertEquals(2, GuardEquipment.in(state, home).size());
        for (var duty : GuardEquipment.in(state, home)) {
            assertEquals(GuardDuty.Phase.ON_DUTY, duty.phase()); assertTrue(GuardEquipment.held(state, duty));
            assertEquals(HumanAssignmentKind.GUARD, HumanAssignmentProjection.compile(state).assignment(duty.execution().actorId()).kind());
            assertEquals("Guard", ResidentPresentation.from(state, duty.execution().actorId()).role());
            assertTrue(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(state, duty.execution()).ready());
        }
        assertEquals(Math.max(0,SettlementWorkPolicy.permissions(state,home).workers(ResidentWorkKind.GUARD).size()-2),
                EquipmentStockProjection.deficits(state,home).stream().filter(d->d.demand().pool().equals("guard")).mapToInt(EquipmentStockProjection.Deficit::missingUnits).sum());
        assertEquals(0,state.inventory().fungibleResources().totalQuantity(home,"minecraft:iron_ingot"));
        var unchanged = state.inventory();
        state = apply(state, GuardWorkProcess.review(state, GuardContinuation.review(home, 6000), 6000), 6000);
        assertSame(unchanged, state.inventory()); assertEquals(2, GuardEquipment.in(state, home).size());
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void actualKernelRunsEquipmentApproachIssueAndExitWithoutManuallyDrivingMovement() {
        var state = armed(); var duty = state.strategicPlans().guards().values().iterator().next();
        var sword = state.inventory().items().get(duty.weapon());
        state = state.withInventory(state.inventory().moveObservedItem(sword.id(), sword.custody(),
                new InventoryCustody.ContainerSlot(duty.source().containerId(), state.inventory().firstFreeSlot(duty.source().containerId()).orElseThrow())));
        duty = duty.acquiring(sword.id(), duty.source());
        state = state.withStrategicPlans(state.strategicPlans().withGuard(duty));
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var engine = FrontierEngines.createCanonicalStateAccess(new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(6000),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(GuardContinuation.progress(duty.id(), 6001), ResidentActivityProcess.review(duty.execution().actorId(), 20000)),
                base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter()));
        for (long tick = 6001; tick < 20000; tick += 20) {
            var result = engine.advanceTo(new SimInstant(tick), new WorkBudget(1000, 1000));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().toString());
            var current = engine.canonicalState().state();
            if (current.strategicPlans().guards().get(duty.id()).phase() == GuardDuty.Phase.ON_DUTY
                    && !current.actorMovements().containsKey(duty.execution().actorId())) break;
        }
        var completed = engine.canonicalState().state();
        var ready = completed.strategicPlans().guards().get(duty.id());
        assertEquals(GuardDuty.Phase.ON_DUTY, ready.phase(), "the real queue must execute both approach and post-issue clearance: " + engine.checkpoint().schedules()
                + " movement=" + completed.actorMovements().get(duty.execution().actorId())
                + " actor=" + completed.actorLocations().get(duty.execution().actorId())
                + " access=" + ServiceAccessCoordinator.available(completed, GuardAuthority.service(ready)));
        assertTrue(GuardEquipment.held(completed, ready));
        assertFalse(completed.actorMovements().containsKey(duty.execution().actorId()));
        assertTrue(ServiceAccessCoordinator.boundary(completed, duty.source().containerId())
                .cleared(completed.actorLocations().get(duty.execution().actorId()).body()));
    }
    @Test void lostWeaponLowersReadinessWithoutInventingAReplacementAndStaleExecutionFails() {
        var state = armed(); var duty = state.strategicPlans().guards().values().iterator().next();
        var item = state.inventory().items().get(duty.weapon());
        state = state.withInventory(state.inventory().moveObservedItem(item.id(), item.custody(), new InventoryCustody.Player(new UUID(1, 2))));
        assertFalse(GuardEquipment.held(state, duty));
        assertEquals(SettlementWorkPolicy.permissions(state,duty.home()).workers(ResidentWorkKind.GUARD).size()-1, EquipmentStockProjection.deficits(state,
            duty.home()).stream().filter(d -> d.demand().pool().equals("guard")).findFirst().orElseThrow().missingUnits());
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6000), 6000), 6000);
        assertEquals(item.withCustody(new InventoryCustody.Player(new UUID(1, 2))), state.inventory().items().get(item.id()));
        var finalState = state;
        assertThrows(IllegalArgumentException.class, () -> GuardAuthority.require(finalState, new ActorExecutionId(duty.execution().actorId(), ActorActivityKind.GUARD,
                duty.id(), duty.execution().generation() + 1)));
        assertThrows(IllegalArgumentException.class, () -> finalState.withStrategicPlans(finalState.strategicPlans().withoutGuard(duty.id())));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void withdrawnUnarmedAppointmentReleasesTheResidentButCannotAbandonACarriedSword() {
        var state=armed();var duty=state.strategicPlans().guards().values().iterator().next();
        var permissions=SettlementWorkPolicy.permissions(state,duty.home()).withoutResident(duty.execution().actorId());
        state=state.withStrategicPlans(state.strategicPlans().withWorkPermissions(duty.home(),permissions));
        var stillArmed=state;
        var ended=new GuardDutyEvent.Changed(duty.id(),duty.revision(),GuardDutyEvent.Change.ENDED,Optional.empty());
        assertThrows(IllegalArgumentException.class,()->GuardDutyTransitions.apply(stillArmed,duty.id(),ended,21001));
        var item=state.inventory().items().get(duty.weapon());
        state=state.withInventory(state.inventory().moveObservedItem(item.id(),item.custody(),new InventoryCustody.Player(new UUID(23,17))));
        var acquiring=duty.acquiring(item.id(),duty.source());
        state=state.withStrategicPlans(state.strategicPlans().withGuard(acquiring));
        state=apply(state,GuardWorkProcess.progress(state,GuardContinuation.progress(duty.id(),21001),21001),21001);
        assertFalse(state.strategicPlans().guards().containsKey(duty.id()));
        assertTrue(state.actorExecutions().actors().get(duty.execution().actorId()).current().isEmpty());
        assertEquals(new InventoryCustody.Player(new UUID(23,17)),state.inventory().items().get(item.id()).custody());
        assertEquals(state,new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void guardEatsFromPocketAndResumesTheSameAppointmentWithTheSameSword() {
        var state = armed(); var duty = state.strategicPlans().guards().values().iterator().next(); var actor = duty.execution().actorId();
        var sword = state.inventory().items().get(duty.weapon());
        state=ActorBodyAuthority.demand(state,actor);
        var body=ActorBodyAuthority.current(state,actor);state=ActorBodyAuthority.running(state,body);
        var origin=state.actorLocations().get(actor).supportingSurface();
        var maneuver=new io.farfrontier.palemirror.frontier.v3.model.combat.MeleeManeuver(new ActorActuationId(body,duty.execution()),duty.weapon(),
            new io.farfrontier.palemirror.frontier.v3.model.combat.CombatTarget(io.farfrontier.palemirror.frontier.v3.model.combat.CombatTarget.Kind.PLAYER,new UUID(42,17),"pale_mirror:frontier_graybox"),
            io.farfrontier.palemirror.frontier.v3.model.combat.MeleeManeuver.Kind.BACKSTEP,26001,origin,origin.offset(1,0,0),26000,26004,26020,0,26060,26040,0);
        state=state.withChanges(FrontierWorldStateUpdate.begin().combat(state.combat().maneuver(maneuver)));
        state=ActorBodyAuthority.released(state,body);
        var lot = new SubjectId("lot:guard-food"); var pocket = new SubjectId("custody:guard-food");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, duty.home(), FoodCatalog.BREAD, 3,
                "fixture:guard-food", List.of()), new CustodyAccount(pocket, new ResourceCustody.Actor(actor), Map.of(lot, 3), Map.of(), Optional.of(new ActorItemSlot.Pocket(0))));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .humanPopulation(state.humanPopulation().accrueHunger(actor, 27000)));
        var meal = ResidentMealProcess.selectSourceAtYield(state, actor, 27000).orElseThrow();
        state = ResidentMealProcess.reduceStarted(state, actor, meal);
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        assertTrue(state.actorExecutions().retainsSuspended(actor, ActorActivityKind.GUARD, duty.id()));
        var deathBasis = ActorBodyAuthority.demand(state, actor);
        var deathBody = ActorBodyAuthority.current(deathBasis, actor);
        deathBasis = ActorBodyAuthority.running(deathBasis, deathBody);
        var location = deathBasis.actorLocations().get(actor);
        var eating = deathBasis.actorExecutions().actors().get(actor).current();
        var died = ActorBodyAuthority.died(deathBasis, new ActorBodyDied(deathBody, location.body(),
                location.condition().health(), Optional.empty(), eating, "fixture:guard-dies-during-meal"),
                FrontierActorDeathConsequences.INSTANCE, 27001);
        died = apply(died, GuardWorkProcess.progress(died, GuardContinuation.progress(duty.id(), 27002), 27002), 27002);
        assertFalse(died.strategicPlans().guards().containsKey(duty.id()));
        assertFalse(died.actorExecutions().retainsSuspended(actor, ActorActivityKind.GUARD, duty.id()));
        assertEquals(died, codec.decode(codec.encode(died)));
        long mealTick = 27001;
        for (int transition = 0; transition < 32 && state.humanPopulation().meals().containsKey(actor); transition++) {
            var remaining = state.humanPopulation().meals().get(actor);
            mealTick = Math.max(mealTick, remaining.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(mealTick));
            var step = ResidentMealProcess.planColdStep(state, actor, mealTick);
            if (step.isPresent()) state = ResidentMealProcess.reduceColdStep(state, actor, step.orElseThrow());
            mealTick++;
        }
        assertFalse(state.humanPopulation().meals().containsKey(actor));
        long resumeTick = Math.max(28000, mealTick);
        state = apply(state, ResidentActivityProcess.plan(state, ResidentActivityProcess.review(actor, resumeTick)), resumeTick);
        assertEquals(sword, state.inventory().items().get(duty.weapon()));
        var resumed = state.strategicPlans().guards().get(duty.id());
        assertEquals(duty.weapon(), resumed.weapon()); assertTrue(resumed.execution().generation() > duty.execution().generation());
        assertTrue(state.actorExecutions().owns(actor, ActorActivityKind.GUARD, duty.id()));
        assertEquals(maneuver,state.combat().maneuvers().get(duty.id()),"completed attempt remains original history, never relabelled as the resumed body's actuation");
        assertTrue(maneuver.retainedBy(resumed.execution(),resumed.weapon()));
        assertFalse(new io.farfrontier.palemirror.frontier.v3.model.combat.MeleeManeuver(maneuver.actor(),maneuver.weapon(),maneuver.target(),maneuver.kind(),maneuver.attemptOrdinal(),
            maneuver.origin(),maneuver.station(),26000,26004,0,0,26060,26040,0).retainedBy(resumed.execution(),resumed.weapon()),"an open attempt cannot cross execution generations");
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    @Test void wornSwordReturnsThroughStorageThenReleasesItsRepairReservation() {
        var state = armed(); var duty = state.strategicPlans().guards().values().iterator().next();
        var sword = state.inventory().items().get(duty.weapon());
        state = state.withInventory(state.inventory().changeCondition(sword, new ItemCondition(250, 249)));
        for (long tick = 6000; tick < 20000 && state.strategicPlans().guards().containsKey(duty.id()); tick += 100) {
            state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), tick), tick), tick);
            state = move(state, duty.execution().actorId(), tick);
            state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        }
        assertFalse(state.strategicPlans().guards().containsKey(duty.id()), "returned equipment must not remain permanently reserved");
        assertInstanceOf(InventoryCustody.ContainerSlot.class, state.inventory().items().get(duty.weapon()).custody());
        assertFalse(WorkEquipmentClaimComposition.reserved(state).contains(duty.weapon()));
        int expected=SettlementWorkPolicy.permissions(state,duty.home()).workers(ResidentWorkKind.GUARD).size()-1;
        assertTrue(EquipmentStockProjection.deficits(state, duty.home()).stream().anyMatch(d -> d.demand().pool().equals("guard") && d.missingUnits() == expected));
    }
    @Test void preparedTransferRecoversAndPreventsMealOrBodyReleaseBeforeItsExactReceipt() {
        var state = armed(); var previous = state.strategicPlans().guards().values().iterator().next(); var actor = previous.execution().actorId();
        var item = state.inventory().items().get(previous.weapon());
        var slot = new InventoryCustody.ContainerSlot(previous.source().containerId(), state.inventory().firstFreeSlot(previous.source().containerId()).orElseThrow());
        state = state.withInventory(state.inventory().moveObservedItem(item.id(), item.custody(), slot));
        var duty = previous.acquiring(item.id(), previous.source());
        // A player's sword in a bread-only store is existing stock, not a new deposit admission.
        var inventory = state.inventory(); var container = inventory.containers().get(slot.containerId());
        var policy = container.storage().orElseThrow();
        var breadOnly = new ResourceStoragePolicy(policy.endpoint(), new ResourceStorageFilter(false,
                Set.of(FoodCatalog.BREAD), Map.of()), policy.priority(), policy.role());
        state = state.withInventory(new ExactInventory(ImmutableLookupMap.withEntry(inventory.containers(), container.id(),
                container.withStorage(breadOnly)), inventory.items(), inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
        assertTrue(WorkEquipmentStorage.sourceAvailable(state, state.inventory().items().get(item.id()), slot.containerId()));
        assertFalse(WorkEquipmentStorage.available(state, item, duty.source()), "the return must still respect receiving filters");
        state = state.withStrategicPlans(state.strategicPlans().withGuard(duty));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(ImmutableLookupMap.withEntry(state.actorLocations(), actor,
                state.actorLocations().get(actor).withBody(duty.source().station().standingBody()))));
        var intent = GuardEquipmentStateSupport.issue(state, duty, 6000);
        state = apply(state, List.of(new ProposedEvent(duty.home(), new PhysicalIntentPrepared(intent))), 6000);
        assertTrue(GuardAuthority.pending(state, duty));
        assertFalse(ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).checkpoint(state, duty.execution()).ready());
        var finalState = state;
        assertThrows(IllegalArgumentException.class, () -> ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD).validateAmbientRelease(finalState, duty.execution()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        var requested = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        var command = command(state, requested, 6001);
        var plan = accepted(FrontierWorldProcessCatalog.planCommand("physical-observation", state, command));
        state = apply(state, FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("physical-observation", plan.events()), 6001);
        intent = state.physicalIntents().get(intent.id());
        var receipt = new EquipmentIssueObservation(new PhysicalObservationId("observation:guard-issued"), intent.id(), duty.id(), actor, item.id(), slot);
        requested = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        plan = accepted(FrontierWorldProcessCatalog.planCommand("physical-observation", state, command(state, requested, 6002)));
        state = apply(state, FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("physical-observation", plan.events()), 6002);
        assertFalse(GuardAuthority.pending(state, duty)); assertTrue(GuardEquipment.held(state, duty));
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6003), 6003), 6003);
        assertEquals(GuardDuty.Phase.ON_DUTY, state.strategicPlans().guards().get(duty.id()).phase());
    }
    @Test void equipmentApproachUsesThreeDimensionalGeometryRejectsDamageAndWithdrawsLostSource() {
        var state = armed(); var previous = state.strategicPlans().guards().values().iterator().next(); var actor = previous.execution().actorId();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), previous.home());
        var ground = DeclaredRouteFixtures.openStreet(state, home);
        var id = new SubjectId("container:guard-raised-contact");
        var contact = KnownPedestrianGround.forFrontier(state).at(ground.x() + 1, ground.z());
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, contact, Optional.empty(), contact);
        var record = ContainerRecord.storage(id, home.id(), 27, io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind.CHEST, new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE));
        var block = ground.support().offset(0, 1, 0);
        state = new ResourceStorageDeclared(record, block).apply(state, home.id());
        var sword = state.inventory().items().get(previous.weapon());
        state = state.withInventory(state.inventory().moveObservedItem(sword.id(), sword.custody(), new InventoryCustody.ContainerSlot(id, 0)));
        var duty = previous.acquiring(sword.id(), endpoint);
        state = state.withStrategicPlans(state.strategicPlans().withGuard(duty)).withActorBody(actor, new SurfaceAnchor(block).standingBody());
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6000), 6000), 6000);
        var movement = state.actorMovements().get(actor); assertNotNull(movement);
        var provider = new GuardMovementProvider(); var route = provider.route(state, movement, new SurfaceAnchor(block));
        assertEquals(new SurfaceAnchor(block), route.getFirst()); assertEquals(contact, route.getLast());
        provider.requireRoute(state, movement, route);
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(block.offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:guard-blocked-departure"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> provider.requireRoute(damaged, movement, route));
        var blockedSource = state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.CONFLICT));
        ServiceAccessCoordinator.requireDeclared(blockedSource, GuardAuthority.service(duty));
        assertFalse(ServiceAccessCoordinator.available(blockedSource, GuardAuthority.service(duty)));
        var stored = state.inventory().items().get(sword.id()); var player = new InventoryCustody.Player(new UUID(3, 4));
        state = state.withInventory(state.inventory().moveObservedItem(stored.id(), stored.custody(), player));
        ServiceAccessCoordinator.requireDeclared(state, GuardAuthority.service(duty));
        assertFalse(ServiceAccessCoordinator.available(state, GuardAuthority.service(duty)), "a lost source is a retained wait until its owner withdraws the order");
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6001), 6001), 6001);
        assertFalse(state.actorMovements().containsKey(actor)); assertEquals(duty, state.strategicPlans().guards().get(duty.id()));
        assertEquals(player, state.inventory().items().get(sword.id()).custody()); assertFalse(GuardEquipment.held(state, duty));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void deathDuringEquipmentApproachRetiresTheOrderAndAppointmentWithoutAwardingTheSword() {
        var state = armed(); var previous = state.strategicPlans().guards().values().iterator().next();
        var actor = previous.execution().actorId(); var item = state.inventory().items().get(previous.weapon());
        var slot = new InventoryCustody.ContainerSlot(previous.source().containerId(), state.inventory().firstFreeSlot(previous.source().containerId()).orElseThrow());
        state = state.withInventory(state.inventory().moveObservedItem(item.id(), item.custody(), slot));
        var duty = previous.acquiring(item.id(), previous.source());
        state = state.withStrategicPlans(state.strategicPlans().withGuard(duty));
        var away = duty.source().station().support().offset(5, 0, 0);
        state = state.withActorBody(actor, new SurfaceAnchor(away).standingBody());
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6000), 6000), 6000);
        assertNotNull(state.actorMovements().get(actor));
        var capability = ActorExecutionComposition.CAPABILITIES.require(ActorActivityKind.GUARD);
        var wrongGoal = new AmbientActorLease(actor, state.actorLocations().get(actor).body(), new SimInstant(6000), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.GUARD, duty.source().station().standingBody());
        assertFalse(capability.permitsAmbientMotion(state, duty.execution(), wrongGoal));
        state = ActorBodyAuthority.demand(state, actor); var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body); var location = state.actorLocations().get(actor);
        state = ActorBodyAuthority.died(state, new ActorBodyDied(body, location.body(), location.condition().health(),
                Optional.empty(), Optional.of(duty.execution()), "fixture:guard-death"), FrontierActorDeathConsequences.INSTANCE, 6001);
        assertFalse(state.actorMovements().containsKey(actor));
        state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), 6002), 6002), 6002);
        assertFalse(state.strategicPlans().guards().containsKey(duty.id()));
        assertEquals(slot, state.inventory().items().get(item.id()).custody());
        assertFalse(WorkEquipmentClaimComposition.reserved(state).contains(item.id()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
    @Test void pendingEquipmentRouteKeepsItsDependencyAndAppointsOnlyAfterARealResult() throws Exception {
        var state = armed(); var home = state.bootstrap().settlements().getFirst().id();
        for (var duty : GuardEquipment.in(state, home)) {
            var item = state.inventory().items().get(duty.weapon());
            var inventory = state.inventory().moveObservedItem(item.id(), item.custody(),
                    new InventoryCustody.ContainerSlot(duty.source().containerId(), state.inventory().firstFreeSlot(duty.source().containerId()).orElseThrow()));
            state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                    .actorExecutions(state.actorExecutions().finish(duty.execution()))
                    .strategicPlans(state.strategicPlans().withoutGuard(duty.id())));
        }
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        try (var ignored = PedestrianRoutePlanning.bind((geometry, start, target) -> ready.get()
                ? PedestrianRoutePlanning.calculate(geometry, start, target)
                : new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 0L, 0, "test:route-pending"))) {
            var basis = state;
            var pending = PedestrianRoutePlanning.observe(() -> GuardWorkProcess.review(basis, GuardContinuation.review(home, 6000), 6000));
            assertFalse(pending.requests().isEmpty(), "waiting must retain the actual route dependency");
            assertTrue(pending.result().stream().noneMatch(event -> event.payload() instanceof GuardDutyEvent.Appointed));
            assertSame(basis.inventory(), state.inventory());
            ready.set(true);
            var completed = GuardWorkProcess.review(state, GuardContinuation.review(home, 6001), 6001);
            state = apply(state, completed, 6001);
            assertEquals(2, GuardEquipment.in(state, home).size());
        }
    }
    private static CommandPlan.Accepted accepted(CommandPlan plan) {
        assertInstanceOf(CommandPlan.Accepted.class, plan, plan.toString()); return (CommandPlan.Accepted) plan;
    }
    private static FrontierCommand command(FrontierWorldState state, FrontierPayload payload, long tick) {
        var id = new CommandId("command:guard-test");
        return new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(), Revision.ZERO, new SimInstant(tick),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload);
    }
    private static FrontierWorldState move(FrontierWorldState state, SubjectId actor, long tick) {
        var movement = state.actorMovements().get(actor);
        if (movement == null) return state;
        long now = Math.max(tick + 1, movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(tick + 1));
        return apply(state, FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(), state,
                ActorMovementProcess.progress(movement, now), new SimInstant(now)), now);
    }
    static FrontierWorldState armed() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        var depot = state.inventory().containers().get(FrontierWorldState.depotId(home.id()));
        state = AutomaticStationProductionTest.supply(state, depot, "minecraft:iron_ingot", 4, "guard-metal");
        state = AutomaticStationProductionTest.supply(state, depot, "minecraft:stick", 2, "guard-handles");
        var facility = home.structures().stream().filter(value -> value.kind() == StructureKind.FORGE).findFirst().orElseThrow();
        var station = ProductionFacilityLayout.station(facility);
        state = AutomaticStationProductionTest.supply(state, state.inventory().containers().get(station.containerId()), ContainerSectionId.FUEL,
                "minecraft:coal", 8, "guard-fuel");
        var recipe = state.bootstrap().ruleset().production().require(new ProductionRecipe.Id("pale_mirror:make_iron_sword"));
        long tick = 300;
        for (int n = 0; n < 2; n++) {
            var objective = new StrategicObjective(new SubjectId("objective:guard-sword-" + n), home.id(),
                    StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
            var task = new StrategicTask(new SubjectId("task:guard-sword-" + n), objective.id(), home.id(), StrategicTaskKind.PRODUCE_AT_STATION,
                    Optional.empty(), StrategicOperationSpecifications.requirements(objective.kind()), List.of(), StrategicTaskStatus.ACTIVE);
            state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
            var worker = state.humanPopulation().resident(SettlementWorkPolicy.permissions(state, home.id()).workers(ResidentWorkKind.PRODUCTION).iterator().next());
            var bundle = ProductionMaterialSupply.select(state, home.id(), station, recipe, 1).orElseThrow();
            var job = StationWorkOrders.propose(state, task, facility, worker, ResidentWorkKind.PRODUCTION, recipe, bundle, Optional.empty(), ProductionRights.publicService(home.id()));
            state = ProductionProcess.reduceStarted(state, home.id(), new ProductionStarted(job, job.consumedItemId(), Optional.of(StationWorkAdmissionNavigation.propose(state, job))));
            for (int step = 0; state.productionJobs().containsKey(job.id()) && step < 800; step++, tick += 20) {
                var current = state.productionJobs().get(job.id()); tick = BakeryTestClock.due(state, current, tick);
                var burn = ProductionStationEnergy.next(state, station.id(), tick);
                if (burn.isPresent()) state = ProductionStationEnergy.apply(state, station.id(), new ProductionStationFuelChanged(
                        ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn.orElseThrow(), List.of(), tick));
                state = BakeryTestClock.arrive(state, current, tick);
                var events = ProductionProcess.planCompletion(state, ProductionProcess.complete(current, tick));
                var stepEvent = events.stream().map(ProposedEvent::payload).filter(BakeryColdStep.class::isInstance).map(BakeryColdStep.class::cast).findFirst();
                if (stepEvent.isPresent()) state = ProductionProcess.reduceBakeryColdStep(state, home.id(), stepEvent.orElseThrow());
            }
            assertFalse(state.productionJobs().containsKey(job.id()), "sword must be produced and physically delivered");
        }
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:iron_ingot"));
        assertEquals(0, state.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:stick"));
        tick = 5000;
        var staffing = SettlementStaffingComposition.change(state, home.id(), tick);
        if (staffing.isPresent()) state = SettlementStaffingComposition.reduce(state, home.id(), staffing.orElseThrow());
        state = apply(state, GuardWorkProcess.review(state, GuardContinuation.review(home.id(), tick), tick), tick);
        assertEquals(2, GuardEquipment.in(state, home.id()).size(), "staffing must authorize existing residents");
        for (int step = 0; step < 100; step++, tick += 100) {
            for (var duty : GuardEquipment.in(state, home.id())) {
                state = apply(state, GuardWorkProcess.progress(state, GuardContinuation.progress(duty.id(), tick), tick), tick);
                var movement = state.actorMovements().get(duty.execution().actorId());
                if (movement != null) {
                    long now = Math.max(tick + 1, movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(tick + 1));
                    state = apply(state, FrontierWorldProcessCatalog.planScheduled(FrontierWorldRuntimeDefinition.processRegistry(), state,
                            ActorMovementProcess.progress(movement, now), new SimInstant(now)), now);
                }
            }
            if (GuardEquipment.in(state, home.id()).stream().allMatch(d -> d.phase() == GuardDuty.Phase.ON_DUTY)
                    && state.actorMovements().isEmpty()) return state;
        }
        throw new AssertionError("guard equipment/clearance failed to finish");
    }
    private static FrontierWorldState apply(FrontierWorldState state, List<ProposedEvent> events, long tick) {
        var before = state;
        var registry = FrontierWorldRuntimeDefinition.processRegistry();
        var schedules = new LinkedHashMap<ScheduleId, ScheduledAction>();
        for (var event : events) if (event.payload() instanceof ScheduleEffect effect) {
            FrontierWorldStateTransitionValidator.INSTANCE.validateScheduleEffect(state, event.subject(), effect);
            switch (effect) {
                case ScheduleEffect.Created created -> schedules.put(created.action().id(), created.action());
                case ScheduleEffect.Rescheduled rescheduled -> { schedules.remove(rescheduled.scheduleId()); schedules.put(rescheduled.replacement().id(), rescheduled.replacement()); }
                case ScheduleEffect.ReconsiderationRequested requested -> schedules.put(requested.action().id(), requested.action());
                case ScheduleEffect.Cancelled cancelled -> schedules.remove(cancelled.scheduleId());
                case ScheduleEffect.Consumed consumed -> schedules.remove(consumed.scheduleId());
            }
        } else {
            var owner = registry.requireReducedEventOwner(event.payload().type());
            state = FrontierWorldProcessCatalog.reduce(owner, state, new FrontierEvent(1, new EventId("event:guard-test"), new TransactionId("transaction:guard-test"),
                    state.bootstrap().worldId(), new Revision(1), new SimInstant(tick), event.subject(), CauseChain.root(new CommandId("command:guard-test")), event.payload()));
        }
        FrontierWorldStateTransitionValidator.INSTANCE.validateTransition(before, state);
        FrontierReferenceClosure.validateTransition(before, state, List.copyOf(schedules.values()));
        return state;
    }
}
