package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Physical adapter for a mission's retained generic container-to-person instruction. */
final class FrontierV3ExpeditionReplenishmentExecutor {
    private FrontierV3ExpeditionReplenishmentExecutor() { }
    static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                            FrontierWorldState state, TransportMission mission) {
        var transfer = mission.replenishment().orElseThrow();
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), transfer.actorId()));
        if (!(entity instanceof Mob actor) || !actor.isAlive() || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, actor)
                || !FrontierV3SurfaceObservation.at(actor, transfer.station()) || state.actorMovements().containsKey(transfer.actorId())) return false;
        var lease = state.ambientLeases().get(transfer.actorId());
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT) return false;
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, actor, transfer.execution(), runtime::decodedState); }
        catch (IllegalArgumentException stale) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, actor) || !actuation.current(actor)) return false;
        state = runtime.decodedState().orElseThrow();
        if (!mission.equals(state.shipments().missions().get(mission.id()))) return false;
        var source = FrontierV3PhysicalContainer.loaded(level, state, transfer.containerId()).orElse(null);
        if (source == null || ReferenceContainerCustody.blocksCanonicalUse(state, transfer.containerId())
                || !ReferenceContainerCustody.hasLiveCustody(state, transfer.containerId())) return false;
        var order = transfer.order(mission.id(), mission.revision());
        var pending = transfer.pending().orElse(null);
        if (pending == null && (!ReferenceContainerCustody.hasOperationalCustody(state, transfer.containerId())
                || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.accessAvailable(state, mission, transfer))) return false;
        var preparation = pending == null ? MaterialSourcePreparation.review(state, order) : null;
        if (preparation != null && preparation.status() != MaterialSourcePreparation.Status.READY) return false;
        var slices = pending == null ? preparation.requireReady() : pending.source();
        var step = pending == null ? new ActorItemTransferStep(new ActorHotObservation(actuation.id(), lease.revision()), slices,
                actuation.id().body().physicalEpoch()) : pending;
        var effect = new FrontierV3ActorItemTransfer.FungibleStep(order, source, null, actor, actor.getUUID(), slices, -1, 0);
        if (pending == null) {
            if (!ReferenceContainerCustody.hasOperationalCustody(state, transfer.containerId()) || !effect.before()
                    || !io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionReplenishmentAuthority.accessAvailable(state, mission, transfer)) return false;
            return submit(level, runtime, mission, new ExpeditionReplenishmentHotPrepared(mission.id(), transfer.claimId(), step));
        }
        if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != lease.revision())
            throw new IllegalStateException("prepared expedition replenishment lost exact actuation " + transfer.claimId());
        if (!effect.after() && (!effect.before() || !effect.apply() || !effect.after()))
            throw new IllegalStateException("expedition replenishment matches neither physical preimage nor receipt " + transfer.claimId());
        var remainder = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(source.inventory(), state, source.containerId());
        var destination = List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(transfer.actorId(), actor, transfer.slot()), transfer.itemKind(), transfer.quantity()));
        boolean accepted = submit(level, runtime, mission, new ExpeditionReplenishmentHotLoaded(mission.id(), transfer.claimId(), step, remainder, destination));
        if (accepted) {
            if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, source.containerId(), source))
                throw new IllegalStateException("replenishment lacks next actual replica boundary " + transfer.claimId());
            FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), transfer.actorId(), actor);
        }
        return accepted;
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, TransportMission mission, FrontierPayload payload) {
        var result = FrontierV3CommandSubmission.submit(runtime, payload.type(), mission.id().value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "expedition:" + mission.id().value(), payload.type(), mission.id(), result);
        return result instanceof CommandResult.Accepted;
    }
}
