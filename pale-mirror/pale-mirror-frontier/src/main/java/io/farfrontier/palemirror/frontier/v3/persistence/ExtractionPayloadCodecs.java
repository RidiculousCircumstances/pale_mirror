package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.List;

final class ExtractionPayloadCodecs {
    private ExtractionPayloadCodecs() { }
    static PayloadCodecs create() {
        return new PayloadCodecs(List.of(codec("frontier.extraction_work_started", (out, payload) -> {
            var value = (ExtractionWorkStarted) payload;
            ExtractionWorkCodec.write(out, value.work()); out.writeLong(value.expectedOrdinal());
        }, in -> new ExtractionWorkStarted(ExtractionWorkCodec.read(in), in.readLong())),
        codec("frontier.extraction_work_progressed", (out, payload) -> {
            var value = (ExtractionWorkProgressed) payload;
            out.writeUTF(value.jobId().value()); out.writeLong(value.expectedRevision()); out.writeByte(value.operation().wireTag());
        }, in -> new ExtractionWorkProgressed(new SubjectId(in.readUTF()), in.readLong(), ExtractionWorkProgressed.Operation.decode(in.readUnsignedByte()))),
        codec("frontier.extraction_source_boundary", (out, payload) -> {
            var value = (ExtractionSourceBoundary) payload; out.writeUTF(value.region().siteId().value());
            out.writeInt(value.region().chunkX()); out.writeInt(value.region().chunkZ()); out.writeByte(value.operation().wireTag());
            out.writeLong(value.expectedEpoch()); out.writeLong(value.expectedReplicaRevision()); out.writeUTF(value.fingerprint());
        }, in -> new ExtractionSourceBoundary(new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRegion(
                new SubjectId(in.readUTF()), in.readInt(), in.readInt()), ExtractionSourceBoundary.Operation.decode(in.readUnsignedByte()),
                in.readLong(), in.readLong(), in.readUTF())),
        codec("frontier.extraction_geometry_changed", (out, payload) -> {
            var value = (ExtractionGeometryChanged) payload; var cell = value.predecessor();
            out.writeInt(cell.key().family().wireTag()); out.writeUTF(cell.key().owner().value());
            out.writeByte(cell.key().role().wireTag()); out.writeLong(cell.key().cell());
            FrontierWorldStateCodec.writePosition(out, cell.position()); out.writeLong(cell.revision());
            ExtractionSiteStateCodec.writeBlock(out, cell.block()); ExtractionSiteStateCodec.writeBlock(out, value.actual());
        }, in -> new ExtractionGeometryChanged(new WorksiteBlock(new WorksiteBlock.Key(CellMutationKey.OwnerFamily.decode(in.readInt()),
                new SubjectId(in.readUTF()), WorksiteBlock.Role.decode(in.readUnsignedByte()), in.readLong()),
                FrontierWorldStateCodec.readPosition(in), in.readLong(), ExtractionSiteStateCodec.readBlock(in)), ExtractionSiteStateCodec.readBlock(in))),
        codec("frontier.extraction_source_changed", (out, payload) -> {
            var value = (ExtractionSourceChanged) payload;
            out.writeInt(value.target().key().family().wireTag()); out.writeUTF(value.target().key().owner().value());
            out.writeLong(value.target().key().cell()); out.writeLong(value.target().revision());
            ExtractionSiteStateCodec.writeBlock(out, value.actual()); out.writeLong(value.sourceEpoch()); out.writeLong(value.sourceReplicaRevision());
            out.writeBoolean(value.unapplied().isPresent());
            if (value.unapplied().isPresent()) {
                var abort = value.unapplied().orElseThrow(); out.writeUTF(abort.jobId().value());
                ExtractionWorkCodec.writeStep(out, abort.step()); PhysicalObservationStackCodec.writeStacks(out, abort.unchangedHand());
            }
        }, in -> {
            var target = new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionTarget(
                    new CellMutationKey(CellMutationKey.OwnerFamily.decode(in.readInt()), new SubjectId(in.readUTF()), in.readLong()), in.readLong());
            var actual = ExtractionSiteStateCodec.readBlock(in); long epoch = in.readLong(), replica = in.readLong();
            var abort = java.util.Optional.<ExtractionSourceChanged.UnappliedEffect>empty();
            if (in.readBoolean()) {
                var job = new SubjectId(in.readUTF()); var step = ExtractionWorkCodec.readStep(in);
                if (!(step instanceof io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep.BlockWork block))
                    throw new IllegalArgumentException("external source abort requires a nominal block effect");
                abort = java.util.Optional.of(new ExtractionSourceChanged.UnappliedEffect(job, block, PhysicalObservationStackCodec.readStacks(in)));
            }
            return new ExtractionSourceChanged(target, actual, epoch, replica, abort);
        }),
        codec("frontier.extraction_hot_prepared", (out, payload) -> {
            var value = (ExtractionHotPrepared) payload; out.writeUTF(value.jobId().value()); ExtractionWorkCodec.writeStep(out, value.step());
        }, in -> new ExtractionHotPrepared(new SubjectId(in.readUTF()), ExtractionWorkCodec.readStep(in))),
        codec("frontier.extraction_hot_observed", (out, payload) -> {
            var value = (ExtractionHotObserved) payload; out.writeUTF(value.jobId().value()); ExtractionWorkCodec.writeStep(out, value.step());
            PhysicalObservationStackCodec.writeStacks(out, value.remainingSource()); PhysicalObservationStackCodec.writeStacks(out, value.destination());
            out.writeBoolean(value.externalSuccessor().isPresent());
            if (value.externalSuccessor().isPresent()) ExtractionSiteStateCodec.writeBlock(out, value.externalSuccessor().orElseThrow());
        }, in -> new ExtractionHotObserved(new SubjectId(in.readUTF()), ExtractionWorkCodec.readStep(in),
                PhysicalObservationStackCodec.readStacks(in), PhysicalObservationStackCodec.readStacks(in),
                in.readBoolean() ? java.util.Optional.of(ExtractionSiteStateCodec.readBlock(in)) : java.util.Optional.empty())),
        codec("frontier.extraction_hand_custody_observed", (out, payload) -> {
            var value = (ExtractionHandCustodyObserved) payload; out.writeUTF(value.jobId().value());
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeByte(value.boundary().wireTag());
            PhysicalObservationStackCodec.writeStacks(out, List.of(value.stack()));
        }, in -> {
            var id = new SubjectId(in.readUTF());
            var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, ActorExecutionStateCodec.readId(in));
            var boundary = ExtractionHandCustodyObserved.Boundary.decode(in.readUnsignedByte()); var stacks = PhysicalObservationStackCodec.readStacks(in);
            if (stacks.size() != 1) throw new IllegalArgumentException("mining hand boundary needs its single observed stack");
            return new ExtractionHandCustodyObserved(id, identity, boundary, stacks.getFirst());
        })));
    }
    private interface Writer { void write(DataOutputStream out, FrontierPayload payload) throws IOException; }
    private interface Reader { FrontierPayload read(DataInputStream in) throws IOException; }
    private static PayloadCodec codec(String type, Writer writer, Reader reader) {
        return new PayloadCodec() {
            public String type() { return type; }
            public byte[] encode(FrontierPayload payload) {
                try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { writer.write(out, payload); } return bytes.toByteArray(); }
                catch (IOException failure) { throw new IllegalStateException("in-memory extraction encode failed", failure); }
            }
            public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    var payload = reader.read(in);
                    if (in.available() != 0) throw new IllegalArgumentException("trailing extraction payload bytes");
                    return payload;
                } catch (IOException failure) { throw new IllegalArgumentException("truncated extraction payload", failure); }
            }
        };
    }
}
