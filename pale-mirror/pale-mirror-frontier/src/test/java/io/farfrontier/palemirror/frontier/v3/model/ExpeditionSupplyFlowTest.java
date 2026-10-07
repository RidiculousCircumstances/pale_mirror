package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Registered dispatch, movement and custody must really load provisions before an outbound order. */
class ExpeditionSupplyFlowTest {
    @Test void actualFullPersonalInventoriesSelectAndReserveTheFiniteAnimalAndLoadItsRealCargoCustody() throws ReflectiveOperationException {
        var base = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-pack-load"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var initial = base.initialState(); var resources = initial.inventory().fungibleResources();
        for (var resident : initial.humanPopulation().residents().values()) {
            if (!resident.settlementId().equals(new SubjectId("settlement:1"))) continue;
            for (int slot = 0; slot < 9; slot++) {
                var lot = new SubjectId("lot:personal-test/" + resident.id().value().replace(':', '-') + "/" + slot);
                var account = new SubjectId("custody:personal-test/" + resident.id().value().replace(':', '-') + "/" + slot);
                resources = resources.issue(new ResourceLot(lot, resident.settlementId(), "minecraft:stone", 64, "fixture:occupied-personal-inventory", List.of()),
                        new CustodyAccount(account, new ResourceCustody.Actor(resident.id()), Map.of(lot, 64), Map.of(), Optional.of(UnitInventoryPresentation.slots().get(slot))));
            }
        }
        initial = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        config = completeNeedClocks(config);
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        SubjectId missionId = null; boolean departed = false, recovered = false;
        int initialBread = bread(initial);
        for (int boundary = 0; boundary < 2048; boundary++) {
            var state = engine.canonicalState().state();
            if (missionId == null && !state.shipments().missions().isEmpty()) {
                var mission = state.shipments().missions().values().iterator().next(); missionId = mission.id();
                assertTrue(mission.transportAssetId().isPresent(), "cargo plus provisions cannot fit the actual courier's pockets");
                var asset = state.transportFleet().require(mission.transportAssetId().orElseThrow());
                assertEquals(Optional.of(mission.id()), asset.missionId());
                var shipment = state.shipments().shipments().get(mission.shipmentIds().getFirst());
                assertEquals(asset.actorId(), shipment.execution().actorId());
                assertEquals(new ResourceCustody.Container(asset.containerId()), shipment.carriedCustody());
                assertFalse(HumanAssignmentProjection.compile(state).assignments().containsKey(asset.actorId()));
            }
            if (missionId != null) {
                var mission = state.shipments().missions().get(missionId);
                var shipment = state.shipments().shipments().get(mission.shipmentIds().getFirst());
                if (!recovered && shipment.status() == Shipment.Status.CARRYING) {
                    var checkpoint = engine.checkpoint();
                    var decoded = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
                    for (var component : FrontierWorldState.class.getRecordComponents()) assertTrue(Objects.equals(component.getAccessor().invoke(state),
                            component.getAccessor().invoke(decoded)), "checkpoint changed component " + component.getName());
                    engine = FrontierEngines.recoverCanonicalStateAccess(config, new RecoveryImage(config.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
                    assertEquals(shipment, engine.canonicalState().state().shipments().shipments().get(shipment.id()));
                    recovered = true;
                }
                if (mission.stage() == TransportMission.Stage.OUTBOUND) {
                    assertTrue(mission.supplies().orElseThrow().complete());
                    assertEquals(shipment.carriedCustody(), state.inventory().fungibleResources().accounts().get(shipment.carriedAccountId()).custody());
                    assertEquals(initialBread, bread(state), "loading only transfers actual stock");
                    assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, engine.checkpoint().instant().ticks()));
                    departed = true; break;
                }
            }
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(missionId); assertTrue(recovered); assertTrue(departed);
        // A traveller with an empty personal food pocket and real shared food is the next fixture precondition.
        // Stock is moved, not minted; its commercial neighbour remains allocated and must not be eaten.
        var travelling = engine.canonicalState().state(); var mission = travelling.shipments().missions().get(missionId);
        var group = travelling.unitGroups().groups().get(mission.groupId()); var asset = travelling.transportFleet().require(mission.transportAssetId().orElseThrow());
        verifyHumanLossForecast(travelling, mission, engine.checkpoint().instant().ticks());
        verifyAttachedCargoDeath(travelling, mission, engine.checkpoint().instant().ticks());
        var formationState = travelling;
        if (group.journey().isEmpty()) {
            var start = io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.start(travelling, group, 1).orElseThrow();
            formationState = io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.reduce(travelling, group.id(), start);
        }
        var formed = formationState.unitGroups().groups().get(group.id()); var route = formed.journey().orElseThrow().route();
        assertEquals(route.size() - 1, formed.journey().orElseThrow().cursor(), "regional travel must not stop every16 edges");
        assertTrue(route.size() > 30);
        var fast = formed.members().getFirst().actorId();
        var positions = new LinkedHashMap<>(formationState.actorLocations());
        for (var member : formed.members()) positions.put(member.actorId(), positions.get(member.actorId()).withBody(
                route.get(member.actorId().equals(fast) ? 25 : 0).standingBody()));
        var lagging = formationState.withChanges(FrontierWorldStateUpdate.begin().actorLocations(positions));
        long travelTick = engine.checkpoint().instant().ticks();
        assertFalse(io.farfrontier.palemirror.frontier.v3.model.group.GroupTravelCohesion.permits(lagging, formed, fast, route.get(26), travelTick),
                "no fast member may leave an obstructed companion behind");
        var slow = formed.members().getLast().actorId();
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.group.GroupTravelCohesion.permits(lagging, formed, slow, route.get(1), travelTick));
        var retainedPositions = lagging.actorLocations();
        io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView physicallyTogether = actor -> route.get(25).standingBody();
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.group.GroupTravelCohesion.permits(lagging, formed, fast,
                route.get(26), physicallyTogether), "current HOT neighbours must override stale durable checkpoints for cohesion");
        io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView physicallyBehind = actor -> route.get(0).standingBody();
        var hold = io.farfrontier.palemirror.frontier.v3.model.group.GroupTravelCohesion.assess(formationState, formed, fast,
                route.get(26), physicallyBehind);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPermission.Reason.GROUP_SPATIAL_STRETCH, hold.reason());
        assertTrue(hold.waitingFor().isPresent());
        assertNotEquals(fast, hold.waitingFor().orElseThrow());
        assertFalse(io.farfrontier.palemirror.frontier.v3.model.group.GroupTravelCohesion.permits(formationState, formed, fast,
                route.get(26), physicallyBehind), "physical lag must still hold the advancing member");
        assertSame(retainedPositions, lagging.actorLocations(), "the read-only gate cannot journal or change poses");
        var departureState = travelling;
        var donor = group.members().stream().filter(m -> departureState.humanPopulation().resident(m.actorId()) != null)
                .min(Comparator.comparingLong(m -> {
                    var a = departureState.actorLocations().get(m.actorId()).body(); var b = departureState.actorLocations().get(asset.actorId()).body();
                    return (long) (a.x() - b.x()) * (a.x() - b.x()) + (long) (a.z() - b.z()) * (a.z() - b.z());
                })).orElseThrow().actorId();
        var food = UnitInventory.select(travelling.inventory().fungibleResources(), donor, mission.sender().settlementId(), FoodCatalog.BREAD, 1).orElseThrow();
        var stocked = travelling.inventory().fungibleResources().accounts().get(food.accountId());
        var presentation = UnitInventoryPresentation.inventory(travelling, donor).get(stocked.id());
        var slot = presentation.slot(); var containerAccount = ReferenceContainerCustody.scopeId(asset.containerId());
        var donation = new ActorContainerItemOrder(mission.id(), donor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(stocked.id(), stocked.custody(), containerAccount,
                        new ResourceCustody.Container(asset.containerId()), Optional.empty(), FoodCatalog.BREAD, stocked.lotQuantities()),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(asset.containerId()), travelling.actorLocations().get(donor).supportingSurface(), slot, 0, 1);
        travelling = travelling.withInventory(ActorItemCustody.transferCold(travelling, donation));
        long refillAt = engine.checkpoint().instant().ticks(); var population = travelling.humanPopulation();
        var nutrition = new LinkedHashMap<>(population.nutrition());
        nutrition.put(donor, new ResidentNutrition(ResidentNutritionStatus.HUNGRY, travelling.bootstrap().ruleset().residentLife().eatBelowUnits() - 1, refillAt, 0));
        travelling = travelling.withHumanPopulation(new HumanPopulation(population.households(), population.residents(), population.birthJobs(),
                population.health(), population.quarantines(), population.migrations(), population.provisions(), nutrition, population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations()));
        var refill = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.select(travelling, mission, refillAt).orElseThrow();
        assertEquals(donor, refill.actorId());
        var holding = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.start(travelling, mission.id(), refill, refillAt);
        FrontierReferenceClosure.validateTransition(travelling, holding, List.of());
        assertTrue(new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(holding)).shipments().equals(holding.shipments()));
        int beforeMeal = bread(holding);
        var provisioned = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.cold(holding, mission.id(), refill.claimId());
        FrontierReferenceClosure.validateTransition(holding, provisioned, List.of());
        assertEquals(beforeMeal, bread(provisioned), "shared-to-person handoff is not consumption");
        assertTrue(provisioned.shipments().missions().get(missionId).replenishment().isEmpty());
        assertEquals(refill.quantity(), UnitInventory.available(provisioned.inventory().fungibleResources(), donor, mission.sender().settlementId(), FoodCatalog.BREAD));
        assertTrue(ResidentMealOpportunity.find(provisioned, donor, refillAt).orElseThrow().foodSource() instanceof ResidentFoodSource.Personal);
        var schedules = new ArrayList<>(engine.checkpoint().schedules());
        var review = io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.review(donor, refillAt + 1);
        schedules.removeIf(a -> a.id().equals(review.id()) || a.subject().equals(donor)
                && a.kind().equals(io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.REVIEW));
        schedules.add(review);
        schedules.add(io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.review(donor,
                provisioned.humanPopulation().nutrition(donor).nextThresholdTick(provisioned.bootstrap().ruleset().residentLife(),
                        provisioned.humanPopulation().resident(donor).characteristics().effectiveMetabolismPermille(refillAt))));
        var eatingConfig = new FrontierEngineConfiguration<>(base.worldId(), provisioned, new SimInstant(refillAt), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules,
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var eater = FrontierEngines.createCanonicalStateAccess(eatingConfig); boolean ate = false;
        for (int boundary = 0; boundary < 256; boundary++) {
            var state = eater.canonicalState().state();
            if (state.humanPopulation().nutrition(donor).satietyUnits() >= state.bootstrap().ruleset().residentLife().eatBelowUnits()) {
                assertEquals(beforeMeal - refill.quantity(), bread(state), "only the acquired personal portion is consumed"); ate = true; break;
            }
            var due = eater.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, a)).sorted().findFirst().orElseThrow();
            var result = eater.advanceTo(new SimInstant(Math.max(eater.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertTrue(ate, "shared food must actually become an ordinary portable meal, not a trip to the home depot");
        // Continue the registered mission, not a fabricated terminal reducer call. The same
        // finite animal must deliver, return and become available without losing personal stock.
        boolean returned = false, accepted = false, recoveredAfterMeal = false;
        for (int boundary = 0; boundary < 6000; boundary++) {
            var state = eater.canonicalState().state();
            var retainedMission = state.shipments().missions().get(missionId);
            if (retainedMission == null) { returned = true; break; }
            accepted |= retainedMission.shipmentIds().stream().map(state.shipments().shipments()::get).anyMatch(Shipment::terminal);
            if (!recoveredAfterMeal && state.actorMovements().values().stream().anyMatch(m -> m.coldTravel().isPresent())) {
                var saved = eater.checkpoint();
                eater = FrontierEngines.recoverCanonicalStateAccess(eatingConfig,
                        new RecoveryImage(eatingConfig.worldId(), Optional.of(new SnapshotRecord(saved, 0)), List.of()));
                assertEquals(state.transportFleet(), eater.canonicalState().state().transportFleet());
                recoveredAfterMeal = true;
            }
            var current = eater.canonicalState().state();
            var due = eater.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a)).sorted().findFirst().orElseThrow();
            var result = eater.advanceTo(new SimInstant(Math.max(eater.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            if (boundary % 64 == 0) eater.compact(eater.checkpoint().revision());
        }
        assertTrue(accepted); assertTrue(returned, "animal mission must complete the actual return and retirement");
        assertTrue(recoveredAfterMeal);
        var terminal = eater.canonicalState().state();
        assertTrue(terminal.transportFleet().require(asset.actorId()).missionId().isEmpty());
        assertEquals(ActorLifeStatus.ALIVE, terminal.actorLocations().get(asset.actorId()).condition().status());
        assertFalse(terminal.inventory().economics().budgets().containsKey(mission.financialBudgetId().orElseThrow()));
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> completeNeedClocks(
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base) {
        var schedules = new ArrayList<>(base.initialSchedules()); var state = base.initialState();
        for (var resident : state.humanPopulation().residents().values()) {
            var review = io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.review(resident.id(),
                    state.humanPopulation().nutrition(resident.id()).nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                            resident.characteristics().effectiveMetabolismPermille(base.initialInstant().ticks())));
            schedules.removeIf(action -> action.id().equals(review.id())); schedules.add(review);
        }
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }
    private static void verifyHumanLossForecast(FrontierWorldState initial, TransportMission mission, long tick) {
        var state = initial;
        for (var member : initial.unitGroups().groups().get(mission.groupId()).members()) {
            if (initial.humanPopulation().resident(member.actorId()) == null) continue;
            state = ActorBodyAuthority.demand(state, member.actorId());
            var body = ActorBodyAuthority.current(state, member.actorId());
            state = ActorBodyAuthority.running(state, body);
            var location = state.actorLocations().get(member.actorId());
            state = ActorBodyAuthority.died(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(body,
                    location.body(), location.condition().health(), Optional.of(location.body()),
                    state.actorExecutions().actors().get(member.actorId()).current(), "fixture:human-casualty"),
                    FrontierActorDeathConsequences.INSTANCE, tick);
        }
        var forecast = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyPlanning.walkingForecast(state,
                mission, state.unitGroups().groups().get(mission.groupId()), tick, mission.supplies().orElseThrow().foodKind()).orElseThrow();
        assertTrue(forecast.feasible()); assertEquals(0, forecast.foodItems());
        assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(state, mission, tick),
                "an actual surviving carrier must not wait for nonexistent eaters");
        assertEquals(initial.inventory().economics(), state.inventory().economics());
        assertEquals(bread(initial), bread(state), "casualty accounting cannot consume or invent provisions");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    /** Model-only loss branch starts from the same actually loaded mission. It does not
     * certify native Vanilla loot; the original registered roundtrip remains unchanged. */
    private static void verifyAttachedCargoDeath(FrontierWorldState initial, TransportMission mission, long tick) {
        var actor = mission.transportAssetId().orElseThrow(); var asset = initial.transportFleet().require(actor);
        var state = ActorBodyAuthority.demand(initial, actor);
        var incarnation = ActorBodyAuthority.current(state, actor); state = ActorBodyAuthority.running(state, incarnation);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, asset.containerId()).orElseThrow();
        var layout = new ArrayList<FungiblePhysicalObservation.Stack>();
        for (int slot = 0; slot < 15; slot++) {
            var image = ReferenceContainerCustody.expectedFungibleSlot(state, asset.containerId(), slot);
            if (image.isPresent()) layout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(asset.containerId(), slot)), image.orElseThrow().itemKind(), image.orElseThrow().quantity()));
        }
        var resources = state.inventory().fungibleResources();
        state = state.withInventory(state.inventory().withFungibleResources(resources.rebind(account.id(), 7,
                FungiblePhysicalObservation.bind(resources, account.id(), 7, layout))));
        var body = state.actorLocations().get(actor);
        var died = ActorBodyAuthority.died(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied(incarnation,
                body.body(), body.condition().health(), Optional.of(body.body()), state.actorExecutions().actors().get(actor).current(), "fixture:observed-fatality"),
                FrontierActorDeathConsequences.INSTANCE, tick);
        assertFalse(ReferenceContainerCustody.retiredEmptyAttachment(died, asset.containerId()), "body loss cannot erase its remaining cargo");
        var binding = died.inventory().fungibleResources().bindings().values().stream().filter(b -> b.accountId().equals(account.id())
                && !b.claimQuantities().isEmpty()).findFirst().orElseThrow();
        var carrier = UUID.fromString("00000000-0000-0000-0000-00000000d1ed");
        var handoff = FungiblePhysicalHandoff.departToNew(died.inventory().fungibleResources(), account.id(), 7, binding, 0,
                new SubjectId("custody:fallen-pack-cargo"), new ResourceCustody.WorldCarrier(carrier), 1,
                new PhysicalStackAddress.WorldEntity(carrier)).forfeitAffectedClaims(died.inventory().fungibleResources());
        assertTrue(FungibleClaimForfeitureStateSupport.supports(died, handoff));
        var lost = FungibleClaimForfeitureStateSupport.apply(died, handoff);
        FrontierReferenceClosure.validateTransition(died, lost, List.of());
        assertEquals(bread(initial), bread(lost), "death changes custody and contract feasibility, not total stock");
        assertEquals(new ResourceCustody.WorldCarrier(carrier), lost.inventory().fungibleResources().accounts().get(handoff.destinationAccount().id()).custody());
        assertTrue(mission.shipmentIds().stream().map(lost.shipments().shipments()::get).allMatch(Shipment::terminal));
        assertTrue(lost.inventory().economics().reservations().values().stream().noneMatch(r -> r.payeeId().equals(mission.sender().settlementId())), "failed cargo cannot be paid as delivered");
        assertEquals(lost, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(lost)));
        var base = FrontierWorldRuntimeDefinition.configuration(lost.bootstrap().worldId(), lost.bootstrap().seed(), lost.bootstrap().ruleset());
        var schedules = new ArrayList<ScheduledAction>();
        schedules.add(io.farfrontier.palemirror.frontier.v3.process.UnitGroupProcess.progress(mission.groupId(), tick + 1));
        schedules.add(io.farfrontier.palemirror.frontier.v3.process.TransportMissionProcess.progress(mission.id(), tick + 1));
        mission.shipmentIds().forEach(shipment -> schedules.add(
                io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment, tick + 1)));
        for (var resident : lost.humanPopulation().residents().values()) {
            schedules.add(io.farfrontier.palemirror.frontier.v3.process.ResidentActivityProcess.review(resident.id(), tick + 48_000));
            schedules.add(io.farfrontier.palemirror.frontier.v3.process.ResidentNeedProcess.review(resident.id(),
                    lost.humanPopulation().nutrition(resident.id()).nextThresholdTick(lost.bootstrap().ruleset().residentLife(), resident.characteristics().effectiveMetabolismPermille(tick))));
        }
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), lost, new SimInstant(tick), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
        var survivors = FrontierEngines.createCanonicalStateAccess(configuration); boolean recalled = false;
        for (int turn = 0; turn < 512; turn++) {
            var current = survivors.canonicalState().state();
            if (!current.shipments().missions().containsKey(mission.id())) { recalled = true; break; }
            var due = survivors.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action)).sorted().findFirst().orElseThrow();
            var result = survivors.advanceTo(new SimInstant(Math.max(survivors.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(32, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertTrue(recalled, "survivors must cancel the outward journey and actually return after cargo loss");
        assertEquals(ActorLifeStatus.DEAD, survivors.canonicalState().state().actorLocations().get(actor).condition().status());
        assertEquals(initial.transportFleet().assets().size(), survivors.canonicalState().state().transportFleet().assets().size());
        assertTrue(survivors.canonicalState().state().transportFleet().require(actor).missionId().isEmpty());
    }
    @Test void observedSourceRemovalWithdrawsPromisesAtomicallyWithoutFoodDuplicationOrPayment() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-source-change"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        for (int boundary = 0; boundary < 256 && engine.canonicalState().state().shipments().missions().isEmpty(); boundary++) {
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        var state = engine.canonicalState().state();
        var mission = state.shipments().missions().values().iterator().next();
        var allocation = mission.supplies().orElseThrow().next().orElseThrow();
        var resources = state.inventory().fungibleResources();
        var quantities = new TreeMap<String, Integer>();
        resources.accounts().get(allocation.sourceAccountId()).lotQuantities().forEach((lot, q) -> quantities.merge(resources.lots().get(lot).itemKind(), q, Integer::sum));
        var layout = new ArrayList<FungiblePhysicalObservation.Stack>(); int slot = 0;
        for (var entry : quantities.entrySet()) for (int q = entry.getValue(); q > 0; q -= Math.min(64, q))
            layout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(mission.sender().containerId(), slot++)), entry.getKey(), Math.min(64, q)));
        var bound = resources.rebind(allocation.sourceAccountId(), 7, FungiblePhysicalObservation.bind(resources, allocation.sourceAccountId(), 7, layout));
        state = state.withInventory(state.inventory().withFungibleResources(bound));
        var binding = bound.bindings().values().stream().filter(b -> b.accountId().equals(allocation.sourceAccountId())
                && b.claimQuantities().containsKey(allocation.claimId())).findFirst().orElseThrow();
        var player = UUID.fromString("00000000-0000-0000-0000-000000000232");
        var observation = FungiblePhysicalHandoff.departToNew(bound, allocation.sourceAccountId(), 7, binding, binding.quantity() - 1,
                new SubjectId("custody:expedition-player-removal"), new ResourceCustody.Player(player), 1,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitAffectedClaims(bound);
        var balances = state.inventory().economics().accounts(); int beforeBread = bread(state);
        assertTrue(FungibleClaimForfeitureStateSupport.supports(state, observation));
        var after = FungibleClaimForfeitureStateSupport.apply(state, observation);
        FrontierReferenceClosure.validateTransition(state, after, List.of());
        assertEquals(beforeBread, bread(after));
        assertEquals(balances, after.inventory().economics().accounts());
        var load = after.shipments().missions().get(mission.id()).supplies().orElseThrow();
        assertTrue(load.needsReplan()); assertFalse(load.complete());
        assertTrue(observation.forfeitedClaimIds().stream().noneMatch(after.inventory().fungibleResources().claims()::containsKey));
        assertFalse(after.inventory().fungibleResources().accounts().containsKey(allocation.destinationAccountId()));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        // The removed source lot also held merchandise: no cargo survives to justify departure.
        assertTrue(mission.shipmentIds().stream().map(after.shipments().shipments()::get).allMatch(Shipment::terminal));
        var returning = io.farfrontier.palemirror.frontier.v3.process.TransportMissionProcess.advance(after, mission.id(),
                new TransportMissionAdvanced(mission.id(), mission.revision(), TransportMission.Stage.RETURNING), engine.checkpoint().instant().ticks());
        assertEquals(beforeBread, bread(returning));
        assertTrue(returning.shipments().missions().get(mission.id()).supplies().isEmpty());
        assertTrue(returning.inventory().economics().budgets().containsKey(mission.financialBudgetId().orElseThrow()),
                "return still owns its finite budget; loading abort does not fabricate completed return");
        assertEquals(returning, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(returning)));
    }
    @Test void actualLoadingConservesStockAndResumesItsRetainedObligationAfterCheckpoint() {
        var config = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:expedition-load"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        SubjectId missionId = null; boolean recovered = false, departed = false;
        Set<SubjectId> declaredClaims = Set.of();
        int initialBread = bread(config.initialState());
        for (int boundary = 0; boundary < 2048; boundary++) {
            var state = engine.canonicalState().state();
            if (missionId == null && !state.shipments().missions().isEmpty()) {
                var mission = state.shipments().missions().values().iterator().next(); missionId = mission.id();
                var supplies = mission.supplies().orElseThrow();
                assertFalse(supplies.complete(), "initial stock is still at the depot, not in participant pockets");
                declaredClaims = supplies.allocations().stream().map(a -> a.claimId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
                assertFalse(declaredClaims.isEmpty());
                assertTrue(declaredClaims.stream().allMatch(state.inventory().fungibleResources().claims()::containsKey));
            }
            if (missionId != null) {
                var mission = state.shipments().missions().get(missionId); var supplies = mission.supplies().orElseThrow();
                if (!recovered && supplies.allocations().stream().anyMatch(a -> a.loaded()) && !supplies.complete()) {
                    var checkpoint = engine.checkpoint(); var codec = new FrontierWorldStateCodec();
                    assertEquals(state, codec.decode(codec.encode(state)));
                    engine = FrontierEngines.recoverCanonicalStateAccess(config,
                            new RecoveryImage(config.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
                    assertEquals(supplies, engine.canonicalState().state().shipments().missions().get(missionId).supplies().orElseThrow());
                    recovered = true;
                }
                if (mission.stage() == TransportMission.Stage.OUTBOUND) {
                    assertTrue(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(
                            state, mission, engine.checkpoint().instant().ticks()));
                    assertFalse(io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionSupplyAuthority.loadedForDeparture(
                            state, mission, engine.checkpoint().instant().ticks() + 100_000L),
                            "a completed old load receipt is not a fresh nutrition/route forecast");
                    assertTrue(supplies.complete());
                    assertTrue(declaredClaims.stream().noneMatch(state.inventory().fungibleResources().claims()::containsKey));
                    for (var entry : supplies.foodTargets().entrySet()) assertTrue(UnitInventory.accounts(state.inventory().fungibleResources(), entry.getKey())
                            .stream().filter(a -> a.claimQuantities().isEmpty()).mapToInt(a -> state.inventory().fungibleResources()
                                    .unclaimedQuantity(a.id(), mission.sender().settlementId(), supplies.foodKind())).sum() >= entry.getValue());
                    assertEquals(initialBread, bread(state), "loading changes custody, never creates or consumes stock");
                    departed = true; break;
                }
            }
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(a -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, a))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(missionId, "accepted contract must dispatch its feasible provisioned group");
        assertTrue(recovered, "checkpoint splits actual provisioning receipts, not a fabricated loaded fixture");
        assertTrue(departed, "members must load, clear the depot and gather before departure");
    }
    private static int bread(FrontierWorldState state) {
        return state.inventory().fungibleResources().accounts().values().stream().mapToInt(account -> account.lotQuantities().entrySet().stream()
                .filter(entry -> state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind().equals(FoodCatalog.BREAD))
                .mapToInt(Map.Entry::getValue).sum()).sum();
    }
}
