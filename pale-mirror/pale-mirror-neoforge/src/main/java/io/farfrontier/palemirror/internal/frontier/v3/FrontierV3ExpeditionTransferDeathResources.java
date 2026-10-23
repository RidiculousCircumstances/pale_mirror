package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Observes, but never applies, pending resource interactions before Vanilla removes either endpoint. */
final class FrontierV3ExpeditionTransferDeathResources {
    private FrontierV3ExpeditionTransferDeathResources() { }
    private record Pending(SubjectId mission, SubjectId claim, ActorContainerItemOrder order, ActorItemTransferStep step,
            FrontierV3OperationReceipt.Protocol protocol, FrontierPayload prepared) { }

    private static List<Pending> pending(FrontierWorldState state) {
        var result = new ArrayList<Pending>();
        for (var mission : state.shipments().missions().values().stream().sorted(Comparator.comparing(TransportMission::id)).toList()) {
            mission.supplies().ifPresent(load -> load.allocations().stream().filter(a -> a.pending().isPresent()).forEach(a ->
                    result.add(new Pending(mission.id(), a.claimId(), load.order(mission.id(), a), a.pending().orElseThrow(),
                            FrontierV3OperationReceipt.Protocol.EXPEDITION_SUPPLY,
                            new ExpeditionSupplyHotPrepared(mission.id(), a.claimId(), a.pending().orElseThrow())))));
            mission.replenishment().filter(t -> t.pending().isPresent()).ifPresent(t -> result.add(new Pending(mission.id(),
                    t.claimId(), t.order(mission.id(), mission.revision()), t.pending().orElseThrow(),
                    FrontierV3OperationReceipt.Protocol.EXPEDITION_REPLENISHMENT,
                    new ExpeditionReplenishmentHotPrepared(mission.id(), t.claimId(), t.pending().orElseThrow()))));
        }
        return List.copyOf(result);
    }
    static boolean settled(FrontierWorldState state, ActorBodyId id) {
        return pending(state).stream().noneMatch(retained -> retained.step().observation().actuation().body().equals(id)
                || java.util.Optional.ofNullable(state.inventory().surfaces().get(retained.order().containerEndpoint().containerId()))
                    .filter(surface -> surface.location().equals(new ContainerLocation.Mobile(id.actorId()))).isPresent());
    }

    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var effects = new ArrayList<FrontierV3OperationAftermath>();
        for (var retained : pending(state)) {
            var surface = state.inventory().surfaces().get(retained.order().containerEndpoint().containerId());
            if (!retained.step().observation().actuation().body().equals(id)
                    && (surface == null || !surface.location().equals(new ContainerLocation.Mobile(id.actorId())))) continue;
            var source = FrontierV3PhysicalContainer.loaded(level, state, retained.order().containerEndpoint().containerId()).orElse(null);
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), retained.order().actorId()));
            var attached = retained.order().actorSlot() instanceof ActorItemSlot.AttachedStorage storage
                    ? FrontierV3PhysicalContainer.loaded(level, state, storage.containerId()).orElse(null) : null;
            if (source == null || !(entity instanceof Mob recipient)
                    || retained.order().actorSlot() instanceof ActorItemSlot.AttachedStorage && attached == null)
                throw new IllegalStateException("prepared expedition death lacks its indexed physical endpoints: " + retained.claim());
            var effect = new FrontierV3ActorItemTransfer.FungibleStep(retained.order(), source, attached, recipient,
                    recipient.getUUID(), retained.step().source(), retained.step().destinationSlot(), retained.step().destinationBefore());
            boolean applied = effect.after();
            if (!applied && !effect.before()) {
                unresolved(retained, id, "changed-source-or-destination"); continue;
            }
            var sourceLayout = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(source.inventory(), state, source.containerId());
            var destination = attached != null ? FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(attached.inventory(), state, attached.containerId())
                    : applied ? List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                            retained.order().actorId(), recipient, retained.order().actorSlot()), retained.order().portion().itemKind(), retained.order().portion().quantity()))
                            : List.<FungiblePhysicalObservation.Stack>of();
            var receipt = new ExpeditionTransferDeathObserved(retained.mission(), retained.claim(), id, retained.step(), applied, sourceLayout, destination);
            var ports = List.of(source.persistence().get(), attached == null
                    ? FrontierV3OperationEntityParticipant.current(recipient, retained.step().observation().actuation().body().physicalEpoch())
                    : attached.persistence().get());
            var declaration = FrontierV3MinecraftOperation.declaration(retained.protocol(), retained.mission(), retained.prepared(), ports);
            var captured = FrontierV3OperationAftermath.capture(level, declaration, ports, receipt, applied,
                    applied ? effect::after : effect::before);
            if (captured.isEmpty()) unresolved(retained, id, "native-outcome-or-unused-permission-unavailable");
            else effects.add(captured.orElseThrow());
        }
        return FrontierV3ActorDeathResourceComposition.retained(effects, () -> { });
    }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        for (var retained : pending(state)) {
            var operations = FrontierV3OperationLedger.get(level);
            var operation = operations.find(retained.protocol(), retained.mission(),
                    FrontierV3OperationConfirmation.fingerprint(retained.prepared())).orElse(null);
            if (operation == null) continue;
            var command = operations.ownerCommand(operation.ticket()).orElse(null);
            if (command == null || !(command.consequence() instanceof ExpeditionTransferDeathObserved receipt)) continue;
            if (!receipt.missionId().equals(retained.mission()) || !receipt.claimId().equals(retained.claim())
                    || !receipt.step().equals(retained.step())) throw new IllegalStateException("retired expedition has a foreign native consequence");
            ActorBodyAuthority.requireRetiredDeath(state, receipt.body());
            if (FrontierV3OperationConfirmation.submitRecovery(level, runtime, operation.ticket(), receipt, Optional.empty())) return true;
        }
        return false;
    }
    private static void unresolved(Pending pending, ActorBodyId body, String reason) {
        io.farfrontier.palemirror.PaleMirrorMod.LOGGER.warn(
                "PMV3_EXPEDITION_DEATH_UNRESOLVED mission={} claim={} body={} protocol={} reason={}; retained pending transfer, automatic replay refused",
                pending.mission(), pending.claim(), body, pending.protocol(), reason);
    }
}
