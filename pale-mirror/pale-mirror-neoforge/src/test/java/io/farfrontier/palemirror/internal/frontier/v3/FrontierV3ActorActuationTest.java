package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorActuationTest {
    @Test void retainedCommandCannotAcquireSuccessorExecutionOrNewBodyEpochAtTheDeferredTick() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actuation-fence"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var source = new AtomicReference<>(Optional.of(state));
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L, body.physicalEpoch());
        var old = new FrontierV3ActorActuation(new ActorActuationId(body, execution), source::get);
        assertTrue(old.current(declaration));
        var successor = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, 1L)
                .commit(state, FrontierWorldStateUpdate.begin());
        source.set(Optional.of(state));
        var next = new FrontierV3ActorActuation(new ActorActuationId(body, successor), source::get);
        assertFalse(old.current(declaration), "cached path/STOP cannot be blessed by a fresh current-execution lookup");
        assertTrue(next.current(declaration), "activity replacement preserves the same physical incarnation");
        var codec = new FrontierWorldStateCodec();
        source.set(Optional.of(codec.decode(codec.encode(state))));
        assertFalse(old.current(declaration));
        assertTrue(next.current(declaration));
        state = ActorBodyAuthority.released(state, body);
        source.set(Optional.of(state));
        assertFalse(next.current(declaration), "unloaded representation cannot retain actuator permission");
        state = ActorBodyAuthority.demand(state, actor);
        var replacement = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, replacement);
        source.set(Optional.of(state));
        assertFalse(next.current(declaration), "same UUID at a new body epoch is not yesterday's actuator");
        source.set(Optional.empty());
        assertFalse(next.current(declaration), "stopped or quarantined runtime grants no motion");
    }

    @Test void bodyDeclarationCannotSubstituteUuidKindRepresentationOrEpochForExactAuthority() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actuation-tuple"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var source = new AtomicReference<>(Optional.of(state));
        var permit = new FrontierV3ActorActuation(new ActorActuationId(body, execution), source::get);
        var valid = FrontierV3ActorCarrierComposition.fromCanonical(state, actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 1L, body.physicalEpoch());
        assertTrue(permit.current(valid));
        assertFalse(permit.current(valid.inactiveCarrier()));
        assertFalse(permit.current(new FrontierV3ActorCarrierComposition.Declaration(actor, ActorKind.BIOFORM,
                valid.owner(), valid.entityId(), valid.representation(), valid.authorityRevision(), valid.epoch())));
        assertFalse(permit.current(new FrontierV3ActorCarrierComposition.Declaration(actor, valid.kind(),
                valid.owner(), new UUID(0L, 1L), valid.representation(), valid.authorityRevision(), valid.epoch())));
        assertFalse(permit.current(valid.liveBody(valid.owner(), valid.authorityRevision(), valid.epoch() + 1L)));
    }
}
