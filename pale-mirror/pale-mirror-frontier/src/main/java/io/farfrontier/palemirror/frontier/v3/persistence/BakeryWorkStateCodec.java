package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.StationWorkPhysicalStep;
import io.farfrontier.palemirror.frontier.v3.model.BakeryWorkBlock;
import io.farfrontier.palemirror.frontier.v3.model.StationWorkState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.readString;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.writeString;

/** One closed, versioned bakery phase encoding shared by job snapshots and start events. */
final class BakeryWorkStateCodec {
    private BakeryWorkStateCodec() { }

    static void write(DataOutputStream output, Optional<StationWorkState> work) throws IOException {
        output.writeBoolean(work.isPresent());
        if (work.isEmpty()) return;
        StationWorkState value = work.orElseThrow();
        output.writeByte(value.phase().wireTag());
        output.writeByte(value.permission().wireTag());
        output.writeByte(value.outputRepresentation().wireTag());
        output.writeBoolean(value.maintenanceTarget().isPresent());
        if (value.maintenanceTarget().isPresent()) {
            writeString(output, value.maintenanceTarget().orElseThrow().itemId().value());
            writeString(output, value.maintenanceTarget().orElseThrow().sourceContainerId().value());
            writeString(output, value.maintenanceTarget().orElseThrow().itemKind());
        }
        writeString(output, value.stationId().value());
        writeString(output, value.recipeId().value());
        writeString(output, value.inputItemKind()); output.writeInt(value.inputQuantity());
        writeString(output, value.sourceAccountId().value());
        writeString(output, value.sourceContainerId().value());
        writeString(output, value.actorAccountId().value());
        writeString(output, value.stationAccountId().value());
        writeString(output, value.destinationAccountId().value());
        output.writeBoolean(value.pendingPhysicalStep().isPresent());
        if (value.pendingPhysicalStep().isPresent()) {
            StationWorkPhysicalStep step = value.pendingPhysicalStep().orElseThrow();
            output.writeByte(step.phase().wireTag());
            writeString(output, step.leaseId().value());
            output.writeByte(step.destinationSlot());
        }
        output.writeBoolean(value.block().isPresent());
        if (value.block().isPresent()) writeBlock(output, value.block().orElseThrow());
    }

    static Optional<StationWorkState> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        StationWorkState.Phase phase = StationWorkState.Phase.fromWireTag(input.readUnsignedByte());
        var permission = io.farfrontier.palemirror.frontier.v3.model.ResidentWorkKind.fromWireTag(input.readUnsignedByte());
        var outputRepresentation = io.farfrontier.palemirror.frontier.v3.model.StationWorkProduct.Representation.fromWireTag(input.readUnsignedByte());
        var maintenance = input.readBoolean() ? Optional.of(new StationWorkState.MaintenanceTarget(
                new SubjectId(readString(input)), new SubjectId(readString(input)), readString(input))) : Optional.<StationWorkState.MaintenanceTarget>empty();
        SubjectId station = new SubjectId(readString(input));
        var recipe = new io.farfrontier.palemirror.frontier.v3.model.ProductionRecipe.Id(readString(input));
        String inputKind = readString(input); int inputQuantity = input.readInt();
        SubjectId source = new SubjectId(readString(input));
        SubjectId sourceContainer = new SubjectId(readString(input));
        SubjectId actor = new SubjectId(readString(input));
        SubjectId stationAccount = new SubjectId(readString(input));
        SubjectId destination = new SubjectId(readString(input));
        Optional<StationWorkPhysicalStep> pending = input.readBoolean()
                ? Optional.of(new StationWorkPhysicalStep(StationWorkState.Phase.fromWireTag(input.readUnsignedByte()),
                new SceneLeaseId(readString(input)), input.readByte())) : Optional.empty();
        Optional<BakeryWorkBlock> block = input.readBoolean() ? Optional.of(readBlock(input)) : Optional.empty();
        return Optional.of(new StationWorkState(phase, permission, outputRepresentation, maintenance, station, recipe, inputKind, inputQuantity, source, sourceContainer, actor, stationAccount,
                destination, pending, block));
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
