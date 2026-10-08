package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Loaded provisioning executes the same generic actor/container interaction as ordinary work. */
final class FrontierV3ExpeditionSupplyExecutor {
    private FrontierV3ExpeditionSupplyExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        for (var mission : state.shipments().missions().values().stream().filter(m -> m.replenishment().isPresent())
                .sorted(Comparator.comparing(TransportMission::id)).toList())
            if (FrontierV3ExpeditionReplenishmentExecutor.progress(level, runtime, state, mission)) return;
        for (var mission : state.shipments().missions().values().stream().filter(m -> m.stage() == TransportMission.Stage.LOADING
                && m.supplies().filter(load -> !load.complete()).isPresent()).sorted(Comparator.comparing(TransportMission::id)).toList())
            if (progress(level, runtime, state, mission)) return;
    }
    private static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, TransportMission mission) {
        var load = mission.supplies().orElseThrow(); var a = load.next().orElseThrow(); var order = load.order(mission.id(), mission.sender(), a);
        var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), a.actorId()));
        if (!(entity instanceof Mob worker) || !worker.isAlive()
                || !FrontierV3ActorBodyController.recognizesRecordedBody(level, state, worker)
                || state.actorMovements().containsKey(a.actorId()) || !FrontierV3SurfaceObservation.at(worker, order.station())) return false;
        var execution = ExpeditionSupplyAuthority.execution(state, mission, a); var lease = state.ambientLeases().get(a.actorId());
        if (execution.isEmpty() || lease == null || lease.status() != AmbientLeaseStatus.HOT) return false;
        FrontierV3ActorActuation actuation;
        try { actuation = FrontierV3ActorActuation.capture(state, worker, execution.orElseThrow(), runtime::decodedState); }
        catch (IllegalArgumentException stale) { return false; }
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, worker) || !actuation.current(worker)) return false;
        state = runtime.decodedState().orElseThrow();
        if (!mission.equals(state.shipments().missions().get(mission.id()))) return false;
        var surface = state.inventory().surfaces().get(mission.sender().containerId());
        if (surface == null) return false;
        var physical = FrontierV3PhysicalContainer.loaded(level, state, mission.sender().containerId()).orElse(null);
        var attached = a.slot() instanceof ActorItemSlot.AttachedStorage storage
                ? FrontierV3PhysicalContainer.loaded(level, state, storage.containerId()).orElse(null) : null;
        if (a.slot() instanceof ActorItemSlot.AttachedStorage && (attached == null
                || ReferenceContainerCustody.blocksCanonicalUse(state, attached.containerId())
                || !ReferenceContainerCustody.hasLiveCustody(state, attached.containerId()))) return false;
        var chest = physical == null ? null : physical.inventory();
        if (chest == null || ReferenceContainerCustody.blocksCanonicalUse(state, mission.sender().containerId())
                || !ReferenceContainerCustody.hasLiveCustody(state, mission.sender().containerId())) return false;
        var pending = a.pending().orElse(null);
        if (pending == null && (!ReferenceContainerCustody.hasOperationalCustody(state, mission.sender().containerId())
                || attached != null && !ReferenceContainerCustody.hasOperationalCustody(state, attached.containerId())
                || !ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, a)))) return false;
        try {
            var preparation = pending == null ? MaterialSourcePreparation.review(state, order) : null;
            if (preparation != null && preparation.status() != MaterialSourcePreparation.Status.READY) return false;
            var source = pending == null ? preparation.requireReady() : pending.source();
            var destination = attached == null || pending != null ? null : ExpeditionSupplyAuthority.destination(state, load, a).orElse(null);
            if (attached != null && pending == null && destination == null) return false;
            var step = pending == null ? new ActorItemTransferStep(new ActorHotObservation(actuation.id(), lease.revision()),
                    source, ExpeditionSupplyAuthority.destinationEpoch(state, a, actuation.id().body().physicalEpoch()),
                    destination == null ? -1 : destination.slot(), destination == null ? 0 : destination.before()) : pending;
            var transfer = new FrontierV3ActorItemTransfer.FungibleStep(order, physical, attached, worker, worker.getUUID(), source, step.destinationSlot(), step.destinationBefore());
            if (pending == null) {
                if (!ReferenceContainerCustody.hasOperationalCustody(state, mission.sender().containerId())
                        || !ServiceAccessCoordinator.available(state, ExpeditionSupplyServiceAccess.identity(mission, a)) || !transfer.before()) return false;
                return submit(level, runtime, mission, new ExpeditionSupplyHotPrepared(mission.id(), a.claimId(), step));
            }
            if (!step.observation().actuation().equals(actuation.id()) || step.observation().scopeRevision() != lease.revision())
                throw new IllegalArgumentException("prepared supply effect lost its body/scope authority");
            if (!transfer.after() && (!transfer.before() || !transfer.apply() || !transfer.after()))
                throw new IllegalArgumentException("supply effect matches neither unapplied nor applied physical preimage");
            var layout = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(chest, state, mission.sender().containerId());
            var destinationLayout = attached == null ? List.of(new FungiblePhysicalObservation.Stack(
                    FrontierV3ActorResourceSlots.address(a.actorId(), worker, a.slot()), load.foodKind(), a.quantity()))
                    : FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(attached.inventory(), state, attached.containerId());
            boolean accepted = submit(level, runtime, mission, new ExpeditionSupplyHotLoaded(mission.id(), a.claimId(), step, layout, destinationLayout));
            if (accepted) {
                if (!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, mission.sender().containerId(), physical)
                        || attached != null && !FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedContainerMutation(runtime, attached.containerId(), attached))
                    throw new IllegalStateException("supply transfer lacks its next container replica boundary");
                if (attached == null) FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), a.actorId(), worker);
            }
            return accepted;
        } catch (IllegalArgumentException conflict) {
            org.slf4j.LoggerFactory.getLogger(FrontierV3ExpeditionSupplyExecutor.class).warn(
                    "PMV3 supply interaction conflict: mission={} claim={} actor={} pending={} reason={}", mission.id(), a.claimId(), a.actorId(), a.pending(), conflict.getMessage());
            return FrontierV3ContainerSurfaceExecutor.reportConflict(runtime, mission.sender().containerId());
        }
    }
    private static boolean submit(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, TransportMission mission, FrontierPayload payload) {
        var result = FrontierV3CommandSubmission.submit(runtime, payload.type(), mission.id().value(), payload);
        FrontierV3DiagnosticTrace.record(level.getServer(), "expedition:" + mission.id().value(), payload.type(), mission.id(), result);
        return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }
}
