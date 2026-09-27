package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.BakeryPhysicalStep;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkBlock;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.readString;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.writeString;

/** One closed, versioned bakery phase encoding shared by job snapshots and start events. */
final class BakeryWorkStateCodec {
    private BakeryWorkStateCodec() { }

    static void write(DataOutputStream output, Optional<BakeryWorkState> work) throws IOException {
        output.writeBoolean(work.isPresent());
        if (work.isEmpty()) return;
        BakeryWorkState value = work.orElseThrow();
        output.writeByte(value.phase().wireTag());
        writeString(output, value.stationId().value());
        writeString(output, value.sourceAccountId().value());
        writeString(output, value.actorAccountId().value());
        writeString(output, value.stationAccountId().value());
        writeString(output, value.destinationAccountId().value());
        output.writeByte(value.completedWorkTicks());
        output.writeBoolean(value.pendingPhysicalStep().isPresent());
        if (value.pendingPhysicalStep().isPresent()) {
            BakeryPhysicalStep step = value.pendingPhysicalStep().orElseThrow();
            output.writeByte(step.phase().wireTag());
            writeString(output, step.leaseId().value());
            output.writeByte(step.destinationSlot());
        }
        output.writeBoolean(value.block().isPresent());
        if (value.block().isPresent()) writeBlock(output, value.block().orElseThrow());
    }

    static Optional<BakeryWorkState> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        BakeryWorkState.Phase phase = BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte());
        SubjectId station = new SubjectId(readString(input));
        SubjectId source = new SubjectId(readString(input));
        SubjectId actor = new SubjectId(readString(input));
        SubjectId stationAccount = new SubjectId(readString(input));
        SubjectId destination = new SubjectId(readString(input));
        int completed = input.readUnsignedByte();
        Optional<BakeryPhysicalStep> pending = input.readBoolean()
                ? Optional.of(new BakeryPhysicalStep(BakeryWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                new SceneLeaseId(readString(input)), input.readByte())) : Optional.empty();
        Optional<BakeryWorkBlock> block = input.readBoolean() ? Optional.of(readBlock(input)) : Optional.empty();
        return Optional.of(new BakeryWorkState(phase, station, source, actor, stationAccount,
                destination, completed, pending, block));
    }

    static void writeBlock(DataOutputStream output, BakeryWorkBlock block) throws IOException {
        output.writeByte(block.reason().wireTag());
        writeString(output, block.scopeId().value());
        output.writeByte(block.slot());
        writeString(output, block.observedKind());
        output.writeByte(block.observedCount());
    }

    static BakeryWorkBlock readBlock(DataInputStream input) throws IOException {
        return new BakeryWorkBlock(BakeryWorkBlock.Reason.fromWireTag(input.readUnsignedByte()),
                new SubjectId(readString(input)), input.readByte(), readString(input), input.readUnsignedByte());
    }
}
