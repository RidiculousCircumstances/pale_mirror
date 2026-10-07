package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.*;

/** Observes, but never applies, pending resource interactions before Vanilla removes either endpoint. */
final class FrontierV3ExpeditionTransferDeathResources {
    private static final String RECEIPT = "pmv3_expedition_retired_transfer_v1";
    private FrontierV3ExpeditionTransferDeathResources() { }
    private record Pending(SubjectId mission, SubjectId claim, ActorContainerItemOrder order, ActorItemTransferStep step) { }

    private static List<Pending> pending(FrontierWorldState state) {
        var result = new ArrayList<Pending>();
        for (var mission : state.shipments().missions().values().stream().sorted(Comparator.comparing(TransportMission::id)).toList()) {
            mission.supplies().ifPresent(load -> load.allocations().stream().filter(a -> a.pending().isPresent()).forEach(a ->
                    result.add(new Pending(mission.id(), a.claimId(), load.order(mission.id(), mission.sender(), a), a.pending().orElseThrow()))));
            mission.replenishment().filter(t -> t.pending().isPresent()).ifPresent(t -> result.add(new Pending(mission.id(),
                    t.claimId(), t.order(mission.id(), mission.revision()), t.pending().orElseThrow())));
        }
        return List.copyOf(result);
    }

    static FrontierV3ActorDeathResourceComposition.AfterFatality prepare(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, ActorBodyId id) {
        var state = runtime.decodedState().orElseThrow();
        var receipts = new ArrayList<ExpeditionTransferDeathObserved>();
        for (var retained : pending(state)) {
            var surface = state.inventory().surfaces().get(retained.order().containerEndpoint().containerId());
            if (!retained.step().observation().actuation().body().equals(id)
                    && !surface.location().equals(new ContainerLocation.Mobile(id.actorId()))) continue;
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
            if (!applied && !effect.before()) throw new IllegalStateException("prepared expedition death has ambiguous source/recipient preimages: " + retained.claim());
            var sourceLayout = FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(source.inventory(), state, source.containerId());
            var destination = attached != null ? FrontierV3ContainerSurfaceExecutor.observedFungibleSlots(attached.inventory(), state, attached.containerId())
                    : applied ? List.of(new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(
                            retained.order().actorId(), recipient, retained.order().actorSlot()), retained.order().portion().itemKind(), retained.order().portion().quantity()))
                            : List.<FungiblePhysicalObservation.Stack>of();
            var receipt = new ExpeditionTransferDeathObserved(retained.mission(), retained.claim(), id, retained.step(), applied, sourceLayout, destination);
            var bytes = FrontierWorldRuntimeDefinition.payloadCodecs().encode(receipt);
            // Persist at both original endpoints. At least the other endpoint survives this death;
            // recovery reuses this positive witness, never an empty body lookup or proximity.
            source.declaration().putByteArray(RECEIPT, bytes); source.inventory().setChanged();
            recipient.getPersistentData().putByteArray(RECEIPT, bytes);
            receipts.add(receipt);
        }
        return () -> receipts.forEach(receipt -> {
            if (!submit(runtime, receipt)) throw new IllegalStateException("retired expedition transfer receipt rejected: " + receipt.claimId());
        });
    }

    static boolean reconcileOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        for (var retained : pending(state)) {
            var holders = new ArrayList<net.minecraft.nbt.CompoundTag>();
            FrontierV3PhysicalContainer.loaded(level, state, retained.order().containerEndpoint().containerId())
                    .ifPresent(source -> holders.add(source.declaration()));
            var entity = level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), retained.order().actorId()));
            if (entity instanceof Mob recipient) holders.add(recipient.getPersistentData());
            for (var holder : holders) {
                if (!holder.contains(RECEIPT, net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)) continue;
                ExpeditionTransferDeathObserved receipt;
                try { receipt = (ExpeditionTransferDeathObserved) FrontierWorldRuntimeDefinition.payloadCodecs()
                        .decode("frontier.expedition_transfer_death_observed", holder.getByteArray(RECEIPT)); }
                catch (IllegalArgumentException invalid) { continue; }
                if (!receipt.missionId().equals(retained.mission()) || !receipt.claimId().equals(retained.claim())
                        || !receipt.step().equals(retained.step())
                        || state.actorLocations().get(receipt.body().actorId()).condition().status() != ActorLifeStatus.DEAD) continue;
                if (submit(runtime, receipt)) return true;
            }
        }
        return false;
    }
    private static boolean submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, ExpeditionTransferDeathObserved receipt) {
        return FrontierV3CommandSubmission.submit(runtime, receipt.type(), receipt.missionId().value(), receipt) instanceof CommandResult.Accepted;
    }
}
