package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse;
import java.io.*;
import java.util.*;

/** Exact forest continuation and prepared consequences. No source/owner rediscovery on hydration. */
final class ForestryWorkCodec {
    private ForestryWorkCodec() { }
    static void write(DataOutputStream out, ForestryWork job) throws IOException {
        id(out, job.id()); id(out, job.home()); ActorExecutionStateCodec.writeId(out, job.execution()); id(out, job.toolId());
        ShipmentStateCodec.endpoint(out, job.toolSource()); ShipmentStateCodec.endpoint(out, job.storageTarget());
        out.writeUTF(job.outputKind()); id(out, job.carriedAccount()); id(out, job.outputLot()); out.writeLong(job.cargoPart());
        out.writeLong(job.revision()); out.writeByte(job.phase().wireTag()); out.writeBoolean(job.target().isPresent());
        if (job.target().isPresent()) writeTarget(out, job.target().orElseThrow());
        WorkStateCodec.writeProgress(out, job.labour()); out.writeBoolean(job.felling().isPresent());
        if (job.felling().isPresent()) writeFelling(out, job.felling().orElseThrow());
        out.writeInt(job.appliedEffects()); out.writeBoolean(job.pending().isPresent());
        if (job.pending().isPresent()) writeStep(out, job.pending().orElseThrow());
    }
    static ForestryWork read(DataInputStream in) throws IOException {
        var id = id(in); var home = id(in); var execution = ActorExecutionStateCodec.readId(in); var tool = id(in);
        var source = ShipmentStateCodec.endpoint(in); var storage = ShipmentStateCodec.endpoint(in); String kind = in.readUTF();
        var account = id(in); var lot = id(in); long part = in.readLong(), revision = in.readLong();
        var phase = ForestryWork.Phase.decode(in.readUnsignedByte());
        var target = in.readBoolean() ? Optional.of(readTarget(in)) : Optional.<ForestryWork.Target>empty();
        var labour = WorkStateCodec.readProgress(in);
        var felling = in.readBoolean() ? Optional.of(readFelling(in)) : Optional.<TreeFelling>empty(); int applied = in.readInt();
        var pending = in.readBoolean() ? Optional.of(readStep(in)) : Optional.<ForestryPhysicalStep>empty();
        return new ForestryWork(id, home, execution, tool, source, storage, kind, account, lot, part,
                revision, phase, target, labour, felling, applied, pending);
    }
    static void reference(DataOutputStream out, TreeReference tree) throws IOException {
        out.writeLong(tree.id().value().getMostSignificantBits()); out.writeLong(tree.id().value().getLeastSignificantBits());
        out.writeLong(tree.generation()); out.writeInt(tree.region().x()); out.writeInt(tree.region().z());
    }
    static TreeReference reference(DataInputStream in) throws IOException {
        return new TreeReference(new TreeId(new UUID(in.readLong(), in.readLong())), in.readLong(), new ForestRegion(in.readInt(), in.readInt()));
    }
    static void writeTarget(DataOutputStream out, ForestryWork.Target target) throws IOException {
        reference(out, target.tree()); out.writeLong(target.sourceRevision()); FrontierWorldStateCodec.writePosition(out, target.station().support());
        out.writeInt(target.expectedLogs());
    }
    static ForestryWork.Target readTarget(DataInputStream in) throws IOException {
        return new ForestryWork.Target(reference(in), in.readLong(), new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)), in.readInt());
    }
    static void writeFelling(DataOutputStream out, TreeFelling felling) throws IOException {
        reference(out, felling.tree()); out.writeLong(felling.sourceRevision()); var batch = felling.batch();
        out.writeUTF(batch.operationId()); ActorExecutionStateCodec.writeId(out, batch.execution());
        FrontierWorldStateCodec.writePosition(out, batch.workStation().support()); FrontierWorldStateCodec.writePosition(out, batch.contact());
        out.writeInt(batch.effects().size()); for (var effect : batch.effects()) BlockExtractionStateCodec.write(out, effect);
    }
    static TreeFelling readFelling(DataInputStream in) throws IOException {
        var tree = reference(in); long revision = in.readLong(); String operation = in.readUTF();
        var execution = ActorExecutionStateCodec.readId(in); var station = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in));
        var contact = FrontierWorldStateCodec.readPosition(in); int count = in.readInt();
        if (count < 1 || count > BlockExtractionBatch.MAX_BLOCKS) throw new IllegalArgumentException("unbounded felling consequence batch");
        var effects = new ArrayList<BlockExtraction>(); for (int i = 0; i < count; i++) effects.add(BlockExtractionStateCodec.read(in));
        return new TreeFelling(tree, revision, new BlockExtractionBatch(operation, execution, station, contact, effects));
    }
    static void writeStep(DataOutputStream out, ForestryPhysicalStep step) throws IOException {
        switch (step) {
            case ForestryPhysicalStep.Equipment equipment -> { out.writeByte(1); WorkEquipmentStepCodec.write(out, equipment.transfer()); }
            case ForestryPhysicalStep.Cargo cargo -> { out.writeByte(2); ActorItemTransferStepCodec.write(out, cargo.transfer()); }
            case ForestryPhysicalStep.Fell fell -> {
                out.writeByte(3); ActorHotObservationCodec.write(out, fell.observation()); BlockExtractionStateCodec.write(out, fell.effect());
                out.writeInt(fell.child()); out.writeInt(fell.carriedBefore()); out.writeLong(fell.sourceEpoch());
                ExactItemStateCodec.write(out, fell.toolUse().before()); out.writeInt(fell.toolUse().wear());
            }
        }
    }
    static ForestryPhysicalStep readStep(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ForestryPhysicalStep.Equipment(WorkEquipmentStepCodec.read(in));
            case 2 -> new ForestryPhysicalStep.Cargo(ActorItemTransferStepCodec.read(in));
            case 3 -> new ForestryPhysicalStep.Fell(ActorHotObservationCodec.read(in), BlockExtractionStateCodec.read(in),
                    in.readInt(), in.readInt(), in.readLong(), new ToolUse(ExactItemStateCodec.read(in), in.readInt()));
            default -> throw new IllegalArgumentException("unknown forestry physical effect tag");
        };
    }
    private static void id(DataOutputStream out, SubjectId id) throws IOException { out.writeUTF(id.value()); }
    private static SubjectId id(DataInputStream in) throws IOException { return new SubjectId(in.readUTF()); }
}
