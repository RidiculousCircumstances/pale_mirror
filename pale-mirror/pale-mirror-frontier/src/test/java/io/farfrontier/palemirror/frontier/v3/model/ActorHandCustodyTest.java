package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.FrontierSceneContinuationPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActorHandCustodyTest {
    @Test void ordinarySceneReleaseCannotDiscardABoundFarmerOffhand() {
        ResourceSiteHarvestProcessTest.HotHarvest hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        FrontierWorldState draining = hot.state().transitionSceneLease(hot.lease().id(), SceneLeaseStatus.DRAINING);
        SceneLease lease = draining.sceneLeases().get(hot.lease().id());
        assertDoesNotThrow(() -> FrontierSceneLeaseStateSupport.requireNoBoundActorHand(draining, lease));
        SubjectId actor = hot.job().workerId();
        SubjectId accountId = new SubjectId("custody:bound-farmer-release");
        SubjectId lotId = new SubjectId("lot:bound-farmer-release");
        ResourceLot lot = new ResourceLot(lotId, new SubjectId("settlement:1"), "minecraft:wheat", 1,
                "field:bound-farmer-release", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Actor(actor), Map.of(lotId, 1), Map.of());
        FungibleResourceLedger issued = draining.inventory().fungibleResources().issue(lot, account);
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:bound-farmer-release"), accountId,
                new PhysicalStackAddress.ActorHand(actor, lease.members().getFirst().entityId()), 1L,
                "minecraft:wheat", Map.of(lotId, 1), Map.of());
        FrontierWorldState bound = draining.withInventory(draining.inventory().withFungibleResources(
                issued.rebind(accountId, 1L, List.of(binding))));
        SceneLeaseReleased release = new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(actor,
                lease.memberBody(bound.actorLocations(), actor), bound.actorLocations().get(actor).condition().health())));

        assertThrows(IllegalArgumentException.class, () -> FrontierSceneContinuationPlanner.releaseEvents(
                bound, lease, 22_301L, release), "planner must reject before journaling an untransferred hand");
        assertThrows(IllegalArgumentException.class, () -> bound.releaseSceneLease(lease.id(), release.members()),
                "replay/reducer must reject the same dangling physical hand");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void worldAcceptsOnlyItsNamedActorsDeterministicHandBody(boolean pocket) {
        ResourceSiteHarvestProcessTest.HotHarvest hot = ResourceSiteHarvestProcessTest.hotHarvestAfterColdSteps(0);
        FrontierWorldState world = hot.state();
        SubjectId actor = hot.job().workerId();
        SubjectId accountId = new SubjectId("custody:actor-hand-test");
        SubjectId lotId = new SubjectId("lot:actor-hand-test");
        ResourceLot lot = new ResourceLot(lotId, world.bootstrap().settlements().getFirst().id(),
                "minecraft:wheat", 1, "field:actor-hand-test", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Actor(actor), Map.of(lotId, 1), Map.of());
        FungibleResourceLedger stock = world.inventory().fungibleResources().issue(lot, account);
        UUID correctBody = SceneLease.deterministicEntityId(world.bootstrap().worldId(), actor);
        PhysicalStackBinding correct = new PhysicalStackBinding(new SubjectId("binding:actor-hand-test"), accountId,
                pocket ? new PhysicalStackAddress.ActorPocket(actor, correctBody, 3)
                        : new PhysicalStackAddress.ActorHand(actor, correctBody), 1L, "minecraft:wheat", Map.of(lotId, 1), Map.of());
        FrontierWorldState accepted = world.withInventory(world.inventory().withFungibleResources(
                stock.rebind(accountId, 1L, List.of(correct))));
        assertEquals(correct, accepted.inventory().fungibleResources().bindings().get(correct.id()));
        FrontierWorldState recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(accepted));
        assertEquals(correct, recovered.inventory().fungibleResources().bindings().get(correct.id()));

        ResourceSiteHarvestProcessTest.ColdHarvest cold = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        SceneLease notYetPhysical = ResourceSiteHarvestProcessTest.newHarvestLease(cold.state(), cold.site(), cold.job(),
                "hand-prepared-negative");
        FrontierWorldState prepared = cold.state().prepareSceneLease(notYetPhysical);
        assertThrows(IllegalArgumentException.class, () -> prepared.withInventory(
                prepared.inventory().withFungibleResources(stock.rebind(accountId, 1L, List.of(correct)))),
                "a PREPARED permission is not evidence of a physical offhand");

        FrontierWorldState noPhysicalOwner = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:actor-hand-no-owner"), 57L));
        SubjectId idleActor = noPhysicalOwner.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId idleAccountId = new SubjectId("custody:actor-hand-no-owner");
        SubjectId idleLotId = new SubjectId("lot:actor-hand-no-owner");
        ResourceLot idleLot = new ResourceLot(idleLotId, noPhysicalOwner.bootstrap().settlements().getFirst().id(),
                "minecraft:wheat", 1, "field:actor-hand-no-owner", List.of());
        CustodyAccount idleAccount = new CustodyAccount(idleAccountId, new ResourceCustody.Actor(idleActor),
                Map.of(idleLotId, 1), Map.of());
        FungibleResourceLedger idleStock = noPhysicalOwner.inventory().fungibleResources().issue(idleLot, idleAccount);
        PhysicalStackBinding phantom = new PhysicalStackBinding(new SubjectId("binding:actor-hand-no-owner"), idleAccountId,
                new PhysicalStackAddress.ActorHand(idleActor,
                        SceneLease.deterministicEntityId(noPhysicalOwner.bootstrap().worldId(), idleActor)),
                1L, "minecraft:wheat", Map.of(idleLotId, 1), Map.of());
        assertThrows(IllegalArgumentException.class, () -> noPhysicalOwner.withInventory(
                noPhysicalOwner.inventory().withFungibleResources(idleStock.rebind(idleAccountId, 1L, List.of(phantom)))));

        PhysicalStackBinding wrongBody = new PhysicalStackBinding(correct.id(), accountId,
                new PhysicalStackAddress.ActorHand(actor, UUID.fromString("00000000-0000-0000-0000-000000000149")),
                1L, "minecraft:wheat", Map.of(lotId, 1), Map.of());
        assertThrows(IllegalArgumentException.class, () -> world.withInventory(world.inventory().withFungibleResources(
                stock.rebind(accountId, 1L, List.of(wrongBody)))));

        SubjectId absent = new SubjectId("resident:absent-actor");
        CustodyAccount absentAccount = new CustodyAccount(accountId, new ResourceCustody.Actor(absent), Map.of(lotId, 1), Map.of());
        assertThrows(IllegalArgumentException.class, () -> world.withInventory(world.inventory().withFungibleResources(
                world.inventory().fungibleResources().issue(lot, absentAccount))));
    }
}
