package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientPendingAdmissionsTest {
    @Test void bodyHistoryEndsJoinBridgeWithoutASceneButNotForAnUnprovedOrStaleBody() {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:join-bridge"), 41L));
        var actor = initial.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = ActorBodyAuthority.demand(initial, actor);
        var body = ActorBodyAuthority.current(state, actor); state = ActorBodyAuthority.running(state, body);
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3AmbientActorExecutor.entityId(state, actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, body.physicalEpoch());
        var binding = FrontierV3ActorOwnerBinding.body(declaration);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(state, binding, ledger),
                "missing lifetime history never produces join permission");
        FrontierV3ActorAdoptionFixture.establishPhysicalHistory(ledger, declaration);
        assertTrue(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(state, binding, ledger),
                "a body is not required to belong to an activity scene");
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(initial, binding, ledger));
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(state,
                FrontierV3ActorOwnerBinding.body(declaration.liveBody(declaration.owner(), 0L, body.physicalEpoch() + 1L)), ledger));
        var location = state.actorLocations().get(actor);
        var departed = new FrontierV3ActorBodyDeparture(declaration.inactiveCarrier(), 1L,
                new SceneMemberPosition(actor, location.body(), location.condition().health()),
                location.body(), location.condition().health(), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(1L, ledger.beginBodyResidence(declaration));
        assertTrue(ledger.recordBodyDeparture(departed));
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(state, binding, ledger),
                "the join bridge cannot erase an unresolved physical departure");
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedBodyOwns(state, binding, restored));
        assertTrue(restored.bodyDeparture(actor).isPresent());
    }
}
