package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;
import java.util.function.Supplier;

/** Fixed-storage owner of an actual block replacement, before palette mutation/onRemove.
 * Slot drops keep their own custody owner; an emptying call cannot retire a provider. */
public final class FrontierV3ContainerBlockOperation {
    private FrontierV3ContainerBlockOperation() { }
    public static BlockState replace(LevelChunk chunk, BlockPos position, BlockState replacement, Supplier<BlockState> mutation) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return mutation.get();
        var runtime = FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return mutation.get();
        return replace(level, runtime, chunk, position, replacement, mutation);
    }
    static BlockState replace(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            LevelChunk chunk, BlockPos position, BlockState replacement, Supplier<BlockState> mutation) {
        var before = chunk.getBlockState(position);
        // Same-block property updates (including double-chest shape) keep the native provider.
        if (before.is(replacement.getBlock())) return mutation.get();
        var entity = chunk.getBlockEntities().get(position);
        if (!(entity instanceof Container inventory)) return mutation.get();
        var state = runtime.decodedState().orElseThrow();
        var physical = FrontierV3PhysicalContainer.observedFixed(level, state, inventory).orElse(null);
        if (physical == null || !ReferenceContainerCustody.isReferenceContainer(state, physical.containerId())) return mutation.get();
        var id = physical.containerId(); var record = state.inventory().containers().get(id);
        var surface = state.inventory().surfaces().get(id);
        var lease = state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(id));
        var recovery = state.fencedRecovery().current().get(FencedRecoveryContainerSupport.bindingId(record));
        if (surface.status() != ContainerSurfaceStatus.ACTIVE || lease == null || recovery == null
                || !ReferenceContainerCustody.hasOperationalCustody(state, id)
                || FrontierV3ContainerEffectFence.pending(level, state, id))
            throw new IllegalStateException("container removal lacks an uncontested current provider: " + id);
        var port = FrontierV3OperationChunks.current(level, position);
        var intent = new CompoundTag(); intent.putString("container", id.value());
        intent.putLong("custodyEpoch", lease.authorityEpoch()); intent.putLong("recoveryEpoch", recovery.authorityEpoch());
        intent.putLong("providerIncarnation", physical.persistence().get().identity().incarnation());
        intent.put("before", NbtUtils.writeBlockState(before)); intent.put("after", NbtUtils.writeBlockState(replacement));
        var consequence = new ContainerSurfaceTransition(id, ContainerSurfaceStatus.RETIRED);
        var declaration = new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.CONTAINER_REMOVAL,
                FrontierExecutionSubjects.PHYSICAL_EXECUTOR, id.value() + "/" + recovery.authorityEpoch() + "/" + FrontierV3OperationFingerprint.of(intent),
                FrontierV3OperationConfirmation.fingerprint(consequence), Set.of(port.identity()));
        var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (!operations.canPrepare(declaration) || !journal.canPrepareRemoval(id))
            throw new IllegalStateException("container removal cannot proceed under journal backpressure: " + id);
        var operation = operations.prepare(declaration);
        var witness = new FrontierV3ContainerRemovalWitness(id, record.ownerId(), surface.position(),
                lease.authorityEpoch(), recovery.authorityEpoch(), operation);
        // The common journal is a self-contained outbox before the family index is
        // published. A crash between these independent writes cannot lose the owner.
        operations.retainOwnerIntent(operation.ticket(), new FrontierV3OperationLedger.OwnerCommand(
                new io.farfrontier.palemirror.frontier.v3.api.CommandId("physical-operation:" + operation.ticket()), consequence,
                FrontierV3OperationLedger.OwnerCommand.Closure.EXPLICIT_OWNER, witness.encode()));
        operations.persist(level);
        journal.prepareRemoval(witness); journal.persist(level);
        var returned = new BlockState[1];
        var result = FrontierV3MinecraftOperation.apply(level, declaration, List.of(port),
                () -> chunk.getBlockState(position).equals(before) && chunk.getBlockEntities().get(position) == entity,
                () -> { returned[0] = mutation.get(); return returned[0] != null; },
                () -> chunk.getBlockState(position).equals(replacement) && chunk.getBlockEntities().get(position) != entity);
        if (result.status() == FrontierV3MinecraftOperation.Status.WAITING_DURABILITY
                && operations.sealUnattemptedCancellation(operation.ticket())) {
            operations.persist(level);
            FrontierV3ContainerRemovalExecutor.cancel(level, witness);
            return null; // Vanilla retry; no block, inventory or canonical surface changed.
        }
        if (!result.complete())
            throw new IllegalStateException("container removal awaits exact native outcome: " + result.status() + " container=" + id);
        return returned[0];
    }
}
