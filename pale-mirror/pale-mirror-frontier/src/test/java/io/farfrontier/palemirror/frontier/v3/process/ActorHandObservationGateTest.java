package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ActorHandObservationGateTest {
    @Test void genericPhysicalCommandsCannotClaimReleaseOrTransferAnActorsHand() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:actor-hand-generic-gate"), 59L));
        SubjectId actor = initial.bootstrap().settlements().getFirst().residents().getFirst().id();
        initial = AmbientLeaseStateProcess.transition(AmbientLeaseStateProcess.prepare(initial,
                AmbientActorProcess.nextLease(initial, actor, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO)),
                actor, AmbientLeaseStatus.HOT);
        SubjectId accountId = new SubjectId("custody:generic-gate-actor");
        SubjectId lotId = new SubjectId("lot:generic-gate-wheat");
        ResourceLot wheat = new ResourceLot(lotId, initial.bootstrap().settlements().getFirst().id(),
                "minecraft:wheat", 1, "field:generic-gate", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Actor(actor), Map.of(lotId, 1), Map.of());
        PhysicalStackAddress.ActorHand hand = new PhysicalStackAddress.ActorHand(actor,
                SceneLease.deterministicEntityId(initial.bootstrap().worldId(), actor));
        FungibleResourceLedger stock = initial.inventory().fungibleResources().issue(wheat, account);
        PhysicalStackBinding binding = FungiblePhysicalObservation.bind(stock, accountId, 1L,
                List.of(new FungiblePhysicalObservation.Stack(hand, "minecraft:wheat", 1))).getFirst();
        FrontierWorldState state = initial.withInventory(initial.inventory().withFungibleResources(
                stock.rebind(accountId, 1L, List.of(binding))));

        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleLayout(state,
                new FungibleStackLayoutObserved(accountId, 1L,
                        List.of(new FungiblePhysicalObservation.Stack(hand, "minecraft:wheat", 1)))));
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldPhysicalObservationProcess.planFungibleBindingRelease(state,
                new FungibleStackBindingsReleased(accountId, 1L), 1L));
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000151");
        var attempted = FungiblePhysicalHandoff.departToNew(state.inventory().fungibleResources(), accountId, 1L,
                binding, 0, new SubjectId("custody:generic-gate-player"), new ResourceCustody.Player(player),
                1L, new PhysicalStackAddress.PlayerSlot(player, 0));
        assertInstanceOf(CommandPlan.Rejected.class,
                FrontierWorldPhysicalObservationProcess.planFungibleHandoff(state, attempted));
    }
}
