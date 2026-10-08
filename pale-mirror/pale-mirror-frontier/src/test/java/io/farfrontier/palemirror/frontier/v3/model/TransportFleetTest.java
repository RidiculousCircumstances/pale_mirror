package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class TransportFleetTest {
    @Test void mobileStorageUsesTheRegisteredProjectionLeaseAndRetainsCargoAcrossRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:mobile-storage-custody"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
        var asset = state.transportFleet().assets().values().iterator().next();
        var account = new SubjectId("custody:pack-test-goods");
        var lot = new ResourceLot(new SubjectId("lot:pack-test-goods"), asset.homeSettlementId(),
                "minecraft:wheat", 70, "test", java.util.List.of());
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(asset.containerId()), java.util.Map.of(lot.id(), 70), java.util.Map.of()))));
        var execution = state.actorExecutions().next(asset.actorId(),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, asset.actorId());
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorExecutions(state.actorExecutions().begin(execution, 0)));
        assertTrue(ActorSpatialCourtesy.assess(state, execution).ready());
        assertTrue(ReferenceContainerCustody.isReferenceContainer(state, asset.containerId()));
        assertEquals("container.mobile-storage", ReferenceContainerCustody.semanticKind(state, asset.containerId()));
        assertEquals(MaterialContainerImage.Layout.BULK, ReferenceContainerCustody.layout(state, asset.containerId()));
        assertEquals(64, ReferenceContainerCustody.expectedFungibleSlot(state, asset.containerId(), 0).orElseThrow().quantity());
        assertEquals(6, ReferenceContainerCustody.expectedFungibleSlot(state, asset.containerId(), 1).orElseThrow().quantity());
        var base = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.configuration(
                state.bootstrap().worldId(), state.bootstrap().seed(), state.bootstrap().ruleset());
        var engine = io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines.create(
                new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                        state.bootstrap().worldId(), state, SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(),
                        base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(),
                        java.util.List.of(), base.transactionCommitter()));
        var before = state;
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "mobile-prepare",
                new PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared(asset.containerId(), 1, 0, "", "")));
        var prepared = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(before.actorLocations(), prepared.actorLocations());
        assertEquals(before.inventory().fungibleResources(), prepared.inventory().fungibleResources());
        assertEquals(ContainerSurfaceStatus.PREPARED, prepared.inventory().surfaces().get(asset.containerId()).status());
        assertTrue(ReferenceContainerCustody.hasLiveCustody(prepared, asset.containerId()));
        assertFalse(ReferenceContainerCustody.hasOperationalCustody(prepared, asset.containerId()));
        assertEquals(java.util.Optional.of(asset.containerId()), ActorInventoryInteractionFences.pendingOwner(prepared, asset.actorId()));
        assertTrue(ActorInventoryInteractionFences.pending(prepared, asset.actorId()));
        var courtesy = ActorSpatialCourtesy.assess(prepared, execution);
        assertFalse(courtesy.ready(), "local avoidance must honor a separately owned mobile inventory operation");
        assertEquals(asset.containerId(), courtesy.waiting().orElseThrow().dependencyOwner());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION,
                courtesy.waiting().orElseThrow().reason());
        var expected = prepared.replicaCustody().replicas().get(asset.containerId());
        // Model receipt only, not a claim about native donkey inventory or body admission.
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "mobile-confirm",
                new PhysicalReplicaCustodyPayloads.ProjectionCustodyConfirmed(ReferenceContainerCustody.scopeId(asset.containerId()),
                        1, expected.emittedCanonicalRevision(), expected.replicaRevision(), expected.fingerprint(), expected.provenance())));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "mobile-active",
                new ContainerSurfaceTransition(asset.containerId(), ContainerSurfaceStatus.ACTIVE)));
        var confirmed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(ReferenceContainerCustody.hasOperationalCustody(confirmed, asset.containerId()));
        assertFalse(ActorInventoryInteractionFences.pending(confirmed, asset.actorId()),
                "observed mobile storage may move with its body; a normal inventory lease is not a movement lock");
        assertTrue(ActorSpatialCourtesy.assess(confirmed, execution).ready());
        assertEquals(before.inventory().fungibleResources(), confirmed.inventory().fungibleResources());
        var checkpoint = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "mobile-double-prepare",
                new PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared(asset.containerId(), 2, 0, "", "")));
        assertArrayEquals(checkpoint.canonicalState(), engine.checkpoint().canonicalState());
        var actors = new LinkedHashMap<>(before.actorLocations()); var body = actors.get(asset.actorId());
        actors.put(asset.actorId(), new ActorLocation(body.body(), ActorCondition.dead(), body.kind()));
        var retired = ActorExecutionComposition.LIFECYCLE.retire(before, asset.actorId(), execution.activityKind(), asset.actorId());
        var lost = before.withChanges(FrontierWorldStateUpdate.begin().actorExecutions(retired).actorLocations(actors));
        assertTrue(ReferenceContainerCustody.blocksCanonicalUse(lost, asset.containerId()));
        assertThrows(IllegalArgumentException.class, () -> ReferenceProjectionStateSupport.prepare(lost,
                new PhysicalReplicaCustodyPayloads.ReferenceProjectionPrepared(asset.containerId(), 1, 0, "", ""), 1));
    }

    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine,
                                        String suffix, FrontierPayload payload) {
        var checkpoint = engine.checkpoint(); var id = new CommandId("command:" + suffix);
        return engine.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), payload));
    }

    @Test void finiteBootstrapMobileLocationAndLossSurviveRecoveryWithoutReplacement() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:finite-pack-animals"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"));
        var state = FrontierWorldState.initial(bootstrap);
        assertEquals(12, state.transportFleet().assets().size());
        assertEquals(12, state.transportFleet().assets().values().stream().map(a -> a.homeSettlementId()).distinct().count());
        var asset = state.transportFleet().assets().values().iterator().next();
        assertEquals(ActorKind.PACK_ANIMAL, state.actorLocations().get(asset.actorId()).kind());
        var surface = state.inventory().surfaces().get(asset.containerId());
        assertFalse(surface.fixed()); assertEquals(15, state.inventory().containers().get(asset.containerId()).slotCount());
        assertThrows(IllegalStateException.class, surface::position);
        assertEquals(asset.homeStation().support().offset(0, 1, 0), surface.position(state));
        var actors = new LinkedHashMap<>(state.actorLocations());
        var previous = actors.get(asset.actorId());
        actors.put(asset.actorId(), new ActorLocation(previous.body(), ActorCondition.dead(), previous.kind()));
        var lost = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(lost));
        assertEquals(lost, recovered);
        assertEquals(12, recovered.transportFleet().assets().size());
        assertEquals(ActorLifeStatus.DEAD, recovered.actorLocations().get(asset.actorId()).condition().status());
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.demand(recovered, asset.actorId()));
        var orphan = new LinkedHashMap<>(state.inventory().surfaces());
        orphan.put(asset.containerId(), new ContainerSurface(asset.containerId(),
                new ContainerLocation.Mobile(new SubjectId("actor:foreign-pack")), surface.status()));
        var malformed = new ExactInventory(state.inventory().containers(), state.inventory().items(), state.inventory().cargo(),
                state.inventory().playerItems(), state.inventory().worldCarrierItems(), state.inventory().conflicts(), orphan,
                state.inventory().economics(), state.inventory().fungibleResources());
        assertThrows(IllegalArgumentException.class, () -> state.withInventory(malformed));
    }
    @Test void exactReservationCannotBeDoubleBookedOrReleasedByAnotherMission() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:pack-reservation"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")));
        var fleet = state.transportFleet(); var asset = fleet.assets().values().iterator().next();
        var mission = new SubjectId("mission:first"); var other = new SubjectId("mission:other");
        var reserved = fleet.reserve(asset.actorId(), mission);
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(asset.actorId(), other));
        assertThrows(IllegalArgumentException.class, () -> reserved.release(asset.actorId(), other));
        assertEquals(fleet, reserved.release(asset.actorId(), mission));
        assertThrows(IllegalArgumentException.class, () -> state.withChanges(FrontierWorldStateUpdate.begin().transportFleet(reserved)));
    }
}
