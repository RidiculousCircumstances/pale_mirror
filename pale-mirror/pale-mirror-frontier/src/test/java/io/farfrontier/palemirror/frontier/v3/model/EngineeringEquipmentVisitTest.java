package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Connected modeled visits, not native worksite or player acceptance. */
class EngineeringEquipmentVisitTest {
    private static final SubjectId STORE = new SubjectId("container:engineering-single-station");

    @Test void bothOwnersIssueAndReturnFourToolsThroughOneOrdinaryWarehouseSequentially() throws Exception {
        for (boolean maintenance : List.of(false, true)) {
            var state = fixture(maintenance, 4);
            var crew = owner(state).engineeringTeam().orElseThrow().memberIds();
            assertEquals(1, ResourceStorageAccess.at(state, STORE).stations().size());
            var visited = new HashSet<SubjectId>();
            for (int index = 0; index < crew.size(); index++) {
                try { state = startVisit(state, PhysicalIntentKind.EQUIPMENT_ISSUE); }
                catch (KnownPedestrianNavigation.RouteUnavailable failure) {
                    throw new AssertionError("issue visit: maintenance=" + maintenance + ", index=" + index
                            + ", crew=" + ownerPositionsForDiagnostic(state, crew), failure);
                }
                var visit = owner(state).assembly().orElseThrow().equipmentVisit().orElseThrow();
                assertTrue(visited.add(visit.actorId()));
                assertEquals(STORE, visit.access().containerId());
                assertTrue(owner(state).assembly().orElseThrow().members().entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(visit.actorId())).allMatch(entry -> entry.getValue().corridor().size() == 1));
                state = arrive(state);
                var intent = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
                state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), intent);
                state = transitionEquipment(state, intent, PhysicalIntentStatus.RUNNING);
                var item = state.inventory().items().get(visit.itemId());
                state = transitionEquipmentWithReceipt(state, intent, new EquipmentIssueObservation(observation(intent), intent.id(),
                        owner(state).id(), visit.actorId(), visit.itemId(), (InventoryCustody.ContainerSlot) item.custody()));
                assertEquals(EngineeringDepotService.VisitProgress.CLEAR_REQUIRED, EngineeringDepotService.progress(state, owner(state)));
                assertFalse(EngineeringEquipmentProcess.mayCompact(state, state.physicalIntents().get(intent.id())),
                        "another owner's preparation cannot discard this visit's clearance receipt");
                state = arrive(startVisit(state, PhysicalIntentKind.EQUIPMENT_ISSUE));
                assertTrue(EngineeringEquipmentProcess.mayCompact(state, state.physicalIntents().get(intent.id())));
                assertTrue(EngineeringDepotService.clearedForCompletion(state, owner(state)));
                var point = ServiceBoundaryComposition.geometry(state, ServiceBoundaryComposition.declaration(state, STORE).identity());
                assertFalse(point.egressSurfaces().contains(state.actorLocations().get(visit.actorId()).supportingSurface()),
                        "a retained crew member must not park in the next visitor's immediate approach");
            }
            assertEquals(Set.copyOf(crew), visited);
            assertTrue(EngineeringToolCustody.ready(state, owner(state).engineeringTeam().orElseThrow()));
            // Explicit return-phase fixture setup; it is not evidence of construction completion.
            state = ready(state);
            visited.clear();
            for (int index = 0; index < crew.size(); index++) {
                state = arrive(startVisit(state, PhysicalIntentKind.EQUIPMENT_RETURN));
                var visit = owner(state).assembly().orElseThrow().equipmentVisit().orElseThrow();
                assertTrue(visited.add(visit.actorId()));
                var intent = EngineeringEquipmentProcess.returnOne(state, owner(state)).orElseThrow();
                state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), intent);
                state = transitionEquipment(state, intent, PhysicalIntentStatus.RUNNING);
                assertTrue(state.reservedContainerSlots(STORE).contains(intent.targetSlot().orElseThrow().slot()));
                assertNotEquals(intent.targetSlot().orElseThrow().slot(), state.firstFreeContainerSlot(STORE).orElseThrow());
                state = roundtrip(state);
                state = transitionEquipmentWithReceipt(state, intent, new EquipmentReturnObservation(observation(intent), intent.id(),
                        owner(state).id(), visit.actorId(), visit.itemId(), EquipmentServiceBoundary.targetSlot(intent)));
                assertFalse(EngineeringDepotService.clearedForCompletion(state, owner(state)), "receipt does not release physical access");
                state = arrive(startVisit(state, PhysicalIntentKind.EQUIPMENT_RETURN));
            }
            assertEquals(Set.copyOf(crew), visited);
            assertTrue(EngineeringEquipmentProcess.returnedOrLost(state, owner(state)));
            assertTrue(EngineeringDepotService.clearedForCompletion(state, owner(state)));
            assertEquals(state, roundtrip(state));
        }
    }

    @Test void selectedVisitReplayNeverSearchesAndMissingDeclarationFailsClosed() throws Exception {
        var state = fixture(false, 2);
        var selected = compile(state, PhysicalIntentKind.EQUIPMENT_ISSUE);
        var event = EngineeringExecutionEvents.constructionAssemblyStarted(state, owner(state).id(), selected);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        var before = roundtrip(state);
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("replay searched"); })) {
            var applied = RouteConstructionStateSupport.reduceAssemblyStarted(before, FrontierRouteNetwork.OWNER, event);
            assertEquals(selected, owner(applied).assembly().orElseThrow());
            assertEquals(applied, roundtrip(applied));
        }
        assertThrows(IllegalArgumentException.class, () -> new EngineeringWorkAssembly(selected.purpose(), selected.members()));
        var visit = selected.equipmentVisit().orElseThrow();
        var forged = new EngineeringEquipmentVisit(visit.actorId(), new SubjectId("item:absent-tool"), visit.access(), visit.operation(),
                visit.returnSlot(), visit.phase(), visit.requestedAtTick());
        var wrong = new EngineeringWorkAssembly(selected.purpose(), selected.members(), Optional.of(forged));
        assertThrows(IllegalArgumentException.class, () -> EngineeringDepotService.requireSuccessor(before, owner(before), wrong));
    }

    @Test void pendingUnknownAndSafetyDoNotBecomeNoPathOrTryAnotherWarehouse() throws Exception {
        for (var status : List.of(PedestrianRouteResult.Status.PLANNING, PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                PedestrianRouteResult.Status.SAFETY_LIMIT, PedestrianRouteResult.Status.NO_PATH)) {
            var state = fixture(false, 2);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> {
                calls.incrementAndGet(); return new PedestrianRouteResult(status, List.of(), 0, 0, "selected-equipment-test");
            })) {
                var failure = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class, () -> compile(state, PhysicalIntentKind.EQUIPMENT_ISSUE));
                assertEquals(status, failure.status());
                if (status != PedestrianRouteResult.Status.NO_PATH) assertEquals(1, calls.get());
            }
        }
    }

    @Test void sameOwnerHotSuccessorKeepsBodyLeaseAndExecutionWhilePreparedCannotRetarget() throws Exception {
        var state = arrive(startVisit(fixture(false, 2), PhysicalIntentKind.EQUIPMENT_ISSUE));
        var visit = owner(state).assembly().orElseThrow().equipmentVisit().orElseThrow();
        var intent = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
        state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), intent);
        state = transitionEquipment(state, intent, PhysicalIntentStatus.RUNNING);
        var item = state.inventory().items().get(visit.itemId());
        state = transitionEquipmentWithReceipt(state, intent, new EquipmentIssueObservation(observation(intent), intent.id(), owner(state).id(),
                visit.actorId(), visit.itemId(), (InventoryCustody.ContainerSlot) item.custody()));
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, visit.actorId(), new SimInstant(200L)));
        var prepared = state;
        assertFalse(EngineeringExecutionAuthority.mayStartJourney(prepared, owner(prepared)));
        var selected = compile(state, PhysicalIntentKind.EQUIPMENT_ISSUE);
        assertThrows(IllegalArgumentException.class, () -> EngineeringDepotService.requireSuccessor(prepared, owner(prepared), selected));
        state = ModeledActorBodyFacts.present(state, visit.actorId());
        state = AmbientLeaseStateProcess.transition(state, visit.actorId(), AmbientLeaseStatus.HOT);
        var lease = state.ambientLeases().get(visit.actorId());
        var bodies = state.fencedRecovery();
        var locations = state.actorLocations();
        var executions = state.actorExecutions();
        var moved = startVisit(state, PhysicalIntentKind.EQUIPMENT_ISSUE);
        assertEquals(bodies, moved.fencedRecovery());
        assertEquals(locations, moved.actorLocations());
        assertEquals(executions, moved.actorExecutions());
        assertEquals(lease.revision(), moved.ambientLeases().get(visit.actorId()).revision());
        assertEquals(lease.handoffBody(), moved.ambientLeases().get(visit.actorId()).handoffBody());
        assertEquals(owner(moved).assembly().orElseThrow().members().get(visit.actorId()).ambientTarget().standingBody(),
                moved.ambientLeases().get(visit.actorId()).goalBody());
        assertEquals(moved, roundtrip(moved));
    }

    @Test void sourceDepartureCancelsOnlyItsUnbegunTransferBeforeStockMutationAndReplaysWithoutSearch() throws Exception {
        for (boolean maintenance : List.of(false, true)) for (boolean destroyed : List.of(false, true)) {
            var state = arrive(startVisit(fixture(maintenance, 2), PhysicalIntentKind.EQUIPMENT_ISSUE));
            var visit = owner(state).assembly().orElseThrow().equipmentVisit().orElseThrow();
            var intent = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
            state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), intent);
            var item = state.inventory().items().get(visit.itemId());
            FrontierPayload departure = destroyed ? new ExactItemDestroyed(item.id(), item.custody(), "test:equipment-source-loss")
                    : new ExactItemCustodyChanged(item.id(), item.custody(), new InventoryCustody.Player(new UUID(0, 41)));
            var command = command(state, departure);
            var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
            var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                    definition.commandPlanner().plan(state, command));
            var cancelled = assertInstanceOf(PhysicalIntentTransition.class, accepted.events().getFirst().payload());
            assertEquals(PhysicalIntentStatus.CONFLICTED, cancelled.status());
            assertTrue(cancelled.retirementProof().isPresent());
            assertEquals(departure, accepted.events().getLast().payload());
            var before = roundtrip(state);
            var observation = accepted.events().getLast();
            assertThrows(IllegalArgumentException.class, () -> replay(before, observation));
            var restored = before;
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("source-loss replay searched"); })) {
                for (var event : accepted.events()) restored = replay(restored, event);
            }
            assertFalse(restored.physicalIntents().containsKey(intent.id()));
            var binding = FencedRecoveryPhysicalIntentSupport.bindingId(intent);
            assertFalse(restored.fencedRecovery().current().containsKey(binding));
            assertEquals(FencedRecoveryDisposition.CANCELLED_BEFORE_EFFECT, restored.fencedRecovery().tombstones().get(binding).disposition());
            assertTrue(restored.inventory().actorItems(visit.actorId()).stream().noneMatch(held -> held.id().equals(item.id())));
            assertEquals(EngineeringDepotService.VisitProgress.CLEAR_REQUIRED, EngineeringDepotService.progress(restored, owner(restored)));
            assertEquals(restored, roundtrip(restored));
        }
    }

    @Test void returnedStockCannotReuseAnEarlierVisitReceiptOrPhysicalIntentIdentity() throws Exception {
        var state = arrive(startVisit(fixture(false, 2), PhysicalIntentKind.EQUIPMENT_ISSUE));
        var visit = owner(state).assembly().orElseThrow().equipmentVisit().orElseThrow();
        var first = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
        state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), first);
        var source = (InventoryCustody.ContainerSlot) state.inventory().items().get(visit.itemId()).custody();
        state = transitionEquipment(state, first, PhysicalIntentStatus.RUNNING);
        state = transitionEquipmentWithReceipt(state, first, new EquipmentIssueObservation(observation(first), first.id(),
                owner(state).id(), visit.actorId(), visit.itemId(), source));
        state = arrive(startVisit(state, PhysicalIntentKind.EQUIPMENT_ISSUE));
        var returned = new ExactItemCustodyChanged(visit.itemId(), new InventoryCustody.Actor(visit.actorId()), source);
        var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                definition.commandPlanner().plan(state, command(state, returned)));
        for (var event : accepted.events()) state = replay(state, event);
        var next = EngineeringDepotService.compile(state, owner(state), EngineeringJourneyPurpose.MUSTER_DEPOT, PhysicalIntentKind.EQUIPMENT_ISSUE, 501L);
        state = RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                EngineeringExecutionEvents.constructionAssemblyStarted(state, owner(state).id(), next));
        assertEquals(visit.itemId(), next.equipmentVisit().orElseThrow().itemId());
        assertFalse(EngineeringDepotService.transferred(state, owner(state), next.equipmentVisit().orElseThrow()));
        assertTrue(EngineeringEquipmentProcess.mayCompact(state, state.physicalIntents().get(first.id())));
        state = arrive(state);
        var second = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
        assertNotEquals(first.id(), second.id());
        state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), second);
        assertTrue(state.physicalIntents().containsKey(second.id()));
        assertFalse(EngineeringDepotService.transferred(state, owner(state), next.equipmentVisit().orElseThrow()),
                "bounded retention of an old receipt cannot complete the newly prepared transfer");
        assertEquals(EngineeringDepotService.VisitProgress.TRANSFER_PENDING, EngineeringDepotService.progress(state, owner(state)));
        assertEquals(state, roundtrip(state));
    }

    private static FrontierWorldState transitionEquipmentWithReceipt(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation receipt) {
        var requested = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                definition.commandPlanner().plan(state, command(state, requested)));
        // This is domain-state replay; kernel schedule effects are owned by the engine,
        // not FrontierWorldState. This fixture does not claim scheduler execution.
        for (var event : accepted.events()) if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect))
            state = replay(state, event);
        return state;
    }

    @Test void begunAndUnknownEquipmentSourceDepartureRemainFencedForInspection() throws Exception {
        for (boolean unknown : List.of(false, true)) {
            var state = arrive(startVisit(fixture(false, 2), PhysicalIntentKind.EQUIPMENT_ISSUE));
            var intent = EngineeringEquipmentProcess.issueOne(state, owner(state)).orElseThrow();
            state = PhysicalIntentLifecycleFixture.prepare(state, owner(state).settlementId(), intent);
            state = transitionEquipment(state, intent, PhysicalIntentStatus.RUNNING);
            if (unknown) state = transitionEquipment(state, intent, PhysicalIntentStatus.UNKNOWN_AFTER_RESTART);
            var item = state.inventory().items().get(intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT));
            var loss = new ExactItemDestroyed(item.id(), item.custody(), "test:begun-equipment-source-loss");
            var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                    definition.commandPlanner().plan(state, command(state, loss)));
            assertTrue(ContainerPhysicalAuthorityComposition.pending(state, STORE));
            assertEquals(state, roundtrip(state));
        }
    }

    private static FrontierCommand command(FrontierWorldState state, FrontierPayload payload) {
        var id = new CommandId("command:equipment-source-departure");
        return new FrontierCommand(FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(), Revision.ZERO,
                new SimInstant(500), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload);
    }
    private static FrontierWorldState transitionEquipment(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentStatus status) {
        var requested = new PhysicalIntentTransition(intent.id(), status, Optional.empty());
        var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                definition.commandPlanner().plan(state, command(state, requested)));
        for (var event : accepted.events()) state = replay(state, event);
        return state;
    }
    private static FrontierWorldState replay(FrontierWorldState state, ProposedEvent proposed) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var payload = codecs.decode(proposed.payload().type(), codecs.encode(proposed.payload()));
        var id = new CommandId("command:equipment-source-replay");
        var event = new FrontierEvent(1, new EventId("event:equipment-source-replay"), new TransactionId("transaction:equipment-source-replay"),
                state.bootstrap().worldId(), new Revision(1), new SimInstant(500), proposed.subject(), CauseChain.root(id), payload);
        return FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L).reducer().apply(state, event);
    }
    private static EngineeringWorkAssembly compile(FrontierWorldState state, PhysicalIntentKind operation) {
        return EngineeringDepotService.compile(state, owner(state), operation == PhysicalIntentKind.EQUIPMENT_ISSUE
                ? EngineeringJourneyPurpose.MUSTER_DEPOT : EngineeringJourneyPurpose.RETURN_DEPOT, operation, 200L);
    }
    private static FrontierWorldState startVisit(FrontierWorldState state, PhysicalIntentKind operation) {
        var assembly = compile(state, operation);
        return switch (owner(state)) {
            case RouteConstruction value -> RouteConstructionStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                    EngineeringExecutionEvents.constructionAssemblyStarted(state, value.id(), assembly));
            case RouteMaintenance value -> RouteMaintenanceStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                    EngineeringExecutionEvents.maintenanceAssemblyStarted(state, value.id(), assembly));
        };
    }
    private static FrontierWorldState arrive(FrontierWorldState state) {
        for (int steps = 0; !owner(state).assembly().orElseThrow().complete(); steps++) {
            if (steps > 4096) throw new AssertionError("bounded selected visit did not arrive");
            var journey = owner(state).assembly().orElseThrow();
            var next = journey.advance(journey.nextSafeAdvance().orElseThrow());
            state = switch (owner(state)) {
                case RouteConstruction value -> RouteConstructionStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER,
                        EngineeringExecutionEvents.constructionAssemblyAdvanced(state, value.id(), next));
                case RouteMaintenance value -> RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER,
                        EngineeringExecutionEvents.maintenanceAssemblyAdvanced(state, value.id(), next));
            };
        }
        return state;
    }
    private static FrontierWorldState ready(FrontierWorldState state) {
        return switch (owner(state)) {
            case RouteConstruction value -> state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(Map.of(value.id(),
                    value.withConfirmedCells(value.workCells().size(), RouteConstructionStatus.READY))));
            case RouteMaintenance value -> state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(Map.of(value.id(), value.ready())));
        };
    }
    private static EngineeringWorkOrder owner(FrontierWorldState state) {
        return state.routeConstructions().isEmpty() ? state.routeMaintenances().values().iterator().next() : state.routeConstructions().values().iterator().next();
    }
    private static PhysicalObservationId observation(PhysicalIntent intent) {
        return new PhysicalObservationId("observation:" + intent.id().value().substring("intent:".length()));
    }
    private static Map<SubjectId, SurfaceAnchor> ownerPositionsForDiagnostic(FrontierWorldState state, List<SubjectId> crew) {
        var positions = new LinkedHashMap<SubjectId, SurfaceAnchor>();
        crew.forEach(actor -> positions.put(actor, state.actorLocations().get(actor).supportingSurface()));
        return positions;
    }
    private static FrontierWorldState roundtrip(FrontierWorldState state) throws Exception {
        var codec = new FrontierWorldStateCodec(); return codec.decode(codec.encode(state));
    }
    private static FrontierWorldState fixture(boolean maintenance, int members) {
        var base = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(new WorldId("frontier:equipment-visit-" + maintenance + "-" + members), 41L).initialState();
        var construction = base.routeConstructions().values().iterator().next();
        var state = FrontierWorldState.initial(base.bootstrap());
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), construction.settlementId());
        var actors = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.capability(HumanCapability.ENGINEERING) > 0).sorted(Comparator.comparing(ResidentProfile::id))
                .limit(members).map(ResidentProfile::id).toList();
        assertEquals(members, actors.size());
        if (maintenance) {
            var cell = state.routeTopology().settlementWaypoints(state.bootstrap(), settlement.id()).get(2);
            state = state.recordPhysicalDelta(new PhysicalDelta(cell, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                    Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)),
                    Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "player:equipment-fixture"));
            var id = new SubjectId("maintenance:single-warehouse");
            var work = new RouteMaintenance(id, settlement.id(), cell, GrayboxSemanticPart.ROUTE_SURFACE, RouteMaintenanceStatus.BUILDING,
                    Optional.empty(), EngineeringRecoveryTeam.forWorkOrder(id, settlement.id(), actors), Optional.empty());
            state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, EngineeringExecutionEvents.maintenanceStarted(state, work));
        } else {
            var work = new RouteConstruction(construction.id(), construction.settlementId(), construction.waypoints(), construction.workCells(),
                    0, RouteConstructionStatus.BUILDING, Optional.empty(), Optional.of(EngineeringRecoveryTeam.forWorkOrder(construction.id(), settlement.id(), actors)), Optional.empty());
            state = RouteConstructionStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, EngineeringExecutionEvents.constructionStarted(state, work));
        }
        var station = KnownPedestrianGround.forFrontier(state).at(settlement.anchor().x() + 20, settlement.anchor().z() + 20);
        var endpoint = new ShipmentEndpoint.Storage(settlement.id(), STORE, station, Optional.empty(), station);
        var container = new ContainerRecord(STORE, settlement.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 100, ResourceStorageRole.WAREHOUSE)));
        state = new ResourceStorageDeclared(container, station.support().offset(1, 1, 0)).apply(state, settlement.id());
        var inventory = state.inventory().withSurfaceStatus(STORE, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(STORE, ContainerSurfaceStatus.ACTIVE);
        for (int index = 0; index < members; index++) inventory = inventory.store(new ExactItemStack(new SubjectId("item:equipment-visit-tool-" + index),
                settlement.id(), EngineeringToolCustody.FIRST_GRAYBOX_TOOL, 1, new InventoryCustody.ContainerSlot(STORE, index)));
        return state.withInventory(inventory);
    }
}
