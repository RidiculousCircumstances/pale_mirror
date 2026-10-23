package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;

/** Field adapter for the shared inventory and durable Minecraft operation protocol. No independent tick loop. */
final class FrontierV3FieldEquipmentExecutor {
    static Optional<FrontierV3OperationAftermath> deathEffect(ServerLevel level, FrontierWorldState state,
            ResourceSiteHarvestJob job, net.minecraft.world.entity.Mob body) {
        var step = job.equipment().pending().orElse(null);
        if (step == null || !(body instanceof Villager worker)) return Optional.empty();
        var physical = FrontierV3PhysicalContainer.loaded(level, state, step.slot().containerId()).orElse(null);
        if (physical == null) return Optional.empty();
        var order = FieldEquipment.order(state, job, Optional.of(step.slot()));
        var adapter = new FrontierV3EquipmentItemTransfer(physical.inventory(), worker, step.item(), step.slot(), order.direction());
        var participants = List.of(physical.persistence().get(),
                FrontierV3OperationEntityParticipant.current(worker, step.observation().actuation().body().physicalEpoch()));
        var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FIELD_EQUIPMENT,
                job.id(), new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(),
                        FieldEquipmentChanged.Action.PREPARE, Optional.of(step)), participants);
        boolean applied = adapter.after();
        if (!applied && !adapter.before()) return Optional.empty();
        var consequence = new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(), applied
                ? FieldEquipmentChanged.Action.RETIRED_APPLIED : FieldEquipmentChanged.Action.RETIRED_UNAPPLIED, Optional.of(step));
        return FrontierV3OperationAftermath.capture(level, declaration, participants, consequence, applied,
                applied ? adapter::after : adapter::before);
    }
    static boolean progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ResourceSiteHarvestJob job, Villager worker) {
        var execution = state.actorExecutions().current(ActorActivityKind.FIELD_HARVEST).get(job.workerId());
        if (execution == null || !execution.activityOwnerId().equals(job.id())) return false;
        if (FieldEquipment.needsReturnStorage(state, job)) {
            try { FieldEquipment.selectReturn(state, job).ifPresent(event ->
                    FrontierV3CommandSubmission.submit(runtime, "field-equipment-return-storage", job.id().value(), event)); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) { }
            return true;
        }
        if (!FieldEquipment.transferring(job)) return false;
        var step = job.equipment().pending().orElse(null);
        var endpoint = FieldEquipment.endpoint(state, job);
        if (step == null && (!FrontierV3SemanticMovement.arrived(level, worker, endpoint.station())
                || !state.actorLocations().get(job.workerId()).supportingSurface().equals(endpoint.station()))) return false;
        var physical = FrontierV3PhysicalContainer.loaded(level, state, endpoint.containerId()).orElse(null);
        if (physical == null) return true;
        var actuation = FrontierV3ActorActuation.capture(state, worker, execution, runtime::decodedState);
        if (!actuation.current(worker)) return true;
        if (step == null) {
            if (!HarvestServiceAccess.available(state, job) || !ReferenceContainerCustody.hasOperationalCustody(state, endpoint.containerId())
                    || !FieldEquipment.transferAvailable(state, job)
                    || FrontierV3ContainerEffectFence.pending(level, state, endpoint.containerId())) return true;
            var order = FieldEquipment.order(state, job, Optional.empty());
            var item = ((ActorContainerItemOrder.Portion.Exact) order.portion()).item();
            var adapter = new FrontierV3EquipmentItemTransfer(physical.inventory(), worker, item, order.exactSlot(), order.direction());
            if (!adapter.before()) return true;
            long epoch = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(endpoint.containerId())).authorityEpoch();
            step = new WorkEquipmentStep(new ActorHotObservation(actuation.id(), job.storageRevision()), item, order.exactSlot(), epoch);
            FrontierV3CommandSubmission.submit(runtime, "field-equipment-prepare", job.id().value(),
                    new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(), FieldEquipmentChanged.Action.PREPARE, Optional.of(step)));
            return true;
        }
        if (!step.observation().actuation().equals(actuation.id())) return true;
        var order = FieldEquipment.order(state, job, Optional.of(step.slot()));
        var adapter = new FrontierV3EquipmentItemTransfer(physical.inventory(), worker, step.item(), step.slot(), order.direction());
        var prepare = new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(), FieldEquipmentChanged.Action.PREPARE, Optional.of(step));
        var participants = List.of(physical.persistence().get(),
                FrontierV3OperationEntityParticipant.current(worker, actuation.id().body().physicalEpoch()));
        var declaration = FrontierV3MinecraftOperation.declaration(FrontierV3OperationReceipt.Protocol.FIELD_EQUIPMENT,
                job.id(), prepare, participants);
        var result = FrontierV3MinecraftOperation.apply(level, declaration, participants, adapter::before, adapter::apply, adapter::after);
        if (result.complete()) FrontierV3OperationConfirmation.submit(level, runtime, result.ticket(),
                new FieldEquipmentChanged(job.siteId(), job.id(), job.equipment().stage(), FieldEquipmentChanged.Action.CONFIRM, Optional.of(step)),
                () -> FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), job.workerId(), worker));
        return true;
    }
    private FrontierV3FieldEquipmentExecutor() { }
}
