package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;

/** Exact before-removal authority. It never grants identity to a replacement block. */
record FrontierV3ContainerRemovalWitness(SubjectId container, SubjectId owner, BlockPosition position,
                                        long custodyEpoch, long recoveryEpoch, FrontierV3OperationReceipt operation) {
    FrontierV3ContainerRemovalWitness {
        Objects.requireNonNull(container); Objects.requireNonNull(owner); Objects.requireNonNull(position);
        if (custodyEpoch < 1 || recoveryEpoch < 1) throw new IllegalArgumentException("removal lacks exact authority epochs");
        Objects.requireNonNull(operation);
        var declaration = operation.declaration();
        String address = Long.toString(new net.minecraft.core.BlockPos(position.x(), position.y(), position.z()).asLong());
        if (declaration.protocol() != FrontierV3OperationReceipt.Protocol.CONTAINER_REMOVAL
                || !declaration.owner().equals(io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects.PHYSICAL_EXECUTOR)
                || !declaration.fingerprint().equals(FrontierV3OperationConfirmation.fingerprint(
                        new io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition(container,
                                io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.RETIRED)))
                || declaration.participants().size() != 1
                || declaration.participants().stream().anyMatch(p -> p.medium() != FrontierV3OperationReceipt.Medium.CHUNK_BLOCKS
                        || !p.identity().equals(address)))
            throw new IllegalArgumentException("removal lacks its exact terrain operation");
    }
    CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("container", container.value()); tag.putString("owner", owner.value());
        tag.putInt("x", position.x()); tag.putInt("y", position.y()); tag.putInt("z", position.z());
        tag.putLong("custodyEpoch", custodyEpoch); tag.putLong("recoveryEpoch", recoveryEpoch);
        tag.put("operation", FrontierV3OperationReceiptCodec.write(operation)); return tag;
    }
    static FrontierV3ContainerRemovalWitness read(CompoundTag tag) {
        return new FrontierV3ContainerRemovalWitness(new SubjectId(tag.getString("container")), new SubjectId(tag.getString("owner")),
                new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getLong("custodyEpoch"), tag.getLong("recoveryEpoch"),
                FrontierV3OperationReceiptCodec.read(tag.getCompound("operation")));
    }
    byte[] encode() {
        try { return FrontierV3JournalNbt.encode(save()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    static FrontierV3ContainerRemovalWitness decode(byte[] bytes) {
        try { return read(FrontierV3JournalNbt.decode(bytes)); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    FrontierV3ContainerRemovalWitness bind(FrontierV3OperationReceipt next) {
        if (!next.ticket().equals(operation.ticket()) || next.sequence() != operation.sequence()
                || !next.declaration().equals(operation.declaration()) || !next.applied().containsAll(operation.applied())
                || !next.saved().containsAll(operation.saved()) || operation.cancelled() && !next.cancelled()
                || operation.ownerClosed() && !next.ownerClosed())
            throw new IllegalArgumentException("container removal changed its retained operation");
        return new FrontierV3ContainerRemovalWitness(container, owner, position, custodyEpoch, recoveryEpoch, next);
    }
}
