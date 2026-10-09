package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.io.*;
import java.util.*;

/** Snapshot and event use one grammar for the exact mining continuation and non-replayable intent. */
final class ExtractionWorkCodec {
    private ExtractionWorkCodec() { }
    static void write(DataOutputStream out, ExtractionWork job) throws IOException {
        id(out, job.id()); id(out, job.siteId()); ActorExecutionStateCodec.writeId(out, job.execution()); id(out, job.toolId());
        id(out, job.toolReturnSlot().containerId()); out.writeInt(job.toolReturnSlot().slot()); out.writeUTF(job.outputKind());
        id(out, job.carriedAccountId()); id(out, job.outputLotId()); out.writeLong(job.batch());
        out.writeByte(job.phase().wireTag()); out.writeLong(job.revision()); out.writeBoolean(job.target().isPresent());
        if (job.target().isPresent()) {
            var target = job.target().orElseThrow(); out.writeByte(target.key().family().wireTag());
            id(out, target.key().owner()); out.writeLong(target.key().cell()); out.writeLong(target.revision());
        }
        WorkStateCodec.writeProgress(out, job.labour()); out.writeBoolean(job.pending().isPresent());
        if (job.pending().isPresent()) writeStep(out, job.pending().orElseThrow());
    }
    static ExtractionWork read(DataInputStream in) throws IOException {
        var id = id(in); var site = id(in); var execution = ActorExecutionStateCodec.readId(in); var tool = id(in);
        var slot = new InventoryCustody.ContainerSlot(id(in), in.readInt()); var kind = in.readUTF();
        var account = id(in); var lot = id(in); long batch = in.readLong();
        var phase = ExtractionWork.Phase.decode(in.readUnsignedByte()); long revision = in.readLong();
        var target = in.readBoolean() ? Optional.of(new ExtractionTarget(new CellMutationKey(
                CellMutationKey.OwnerFamily.decode(in.readUnsignedByte()), id(in), in.readLong()), in.readLong())) : Optional.<ExtractionTarget>empty();
        var labour = WorkStateCodec.readProgress(in);
        var pending = in.readBoolean() ? Optional.of(readStep(in)) : Optional.<ExtractionPhysicalStep>empty();
        return new ExtractionWork(id, site, execution, tool, slot, kind, account, lot, batch, phase, revision, target, labour, pending);
    }
    static void writeStep(DataOutputStream out, ExtractionPhysicalStep step) throws IOException {
        switch (step) {
            case ExtractionPhysicalStep.Equipment equipment -> {
                out.writeByte(1); ActorHotObservationCodec.write(out, equipment.observation());
                id(out, equipment.slot().containerId()); out.writeInt(equipment.slot().slot()); out.writeLong(equipment.containerEpoch());
            }
            case ExtractionPhysicalStep.BlockWork block -> {
                out.writeByte(2); ActorHotObservationCodec.write(out, block.observation());
                var effect = block.extraction(); out.writeUTF(effect.operationId()); ActorExecutionStateCodec.writeId(out, effect.execution());
                FrontierWorldStateCodec.writePosition(out, effect.target()); ExtractionSiteStateCodec.writeDefinition(out, effect.definition());
                out.writeByte(effect.output().size());
                for (var output : effect.output()) { out.writeUTF(output.itemKind()); out.writeByte(output.quantity()); }
                out.writeByte(block.carriedBefore());
                out.writeLong(block.sourceEpoch());
            }
            case ExtractionPhysicalStep.Cargo cargo -> {
                out.writeByte(3); ActorItemTransferStepCodec.write(out, cargo.transfer());
            }
        }
    }
    static ExtractionPhysicalStep readStep(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ExtractionPhysicalStep.Equipment(ActorHotObservationCodec.read(in),
                    new InventoryCustody.ContainerSlot(id(in), in.readInt()), in.readLong());
            case 2 -> {
                var observation = ActorHotObservationCodec.read(in); String operation = in.readUTF();
                var execution = ActorExecutionStateCodec.readId(in); var position = FrontierWorldStateCodec.readPosition(in);
                var definition = ExtractionSiteStateCodec.readDefinition(in); int count = in.readUnsignedByte();
                if (count < 1 || count > BlockExtraction.MAX_OUTPUTS) throw new IllegalArgumentException("invalid retained extraction outputs");
                var outputs = new ArrayList<BlockExtraction.Output>();
                for (int index = 0; index < count; index++) outputs.add(new BlockExtraction.Output(in.readUTF(), in.readUnsignedByte()));
                yield new ExtractionPhysicalStep.BlockWork(observation, new BlockExtraction(operation, execution, position, definition, outputs), in.readUnsignedByte(), in.readLong());
            }
            case 3 -> new ExtractionPhysicalStep.Cargo(ActorItemTransferStepCodec.read(in));
            default -> throw new IllegalArgumentException("unknown extraction physical operation tag");
        };
    }
    private static void id(DataOutputStream out, SubjectId id) throws IOException { out.writeUTF(id.value()); }
    private static SubjectId id(DataInputStream in) throws IOException { return new SubjectId(in.readUTF()); }
}
