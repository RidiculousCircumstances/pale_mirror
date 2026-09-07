package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Stable WAL payload codecs for the pure replica/custody transition vocabulary. */
final class PhysicalReplicaCustodyPayloadCodecs {
    private PhysicalReplicaCustodyPayloadCodecs() { }
    static List<PayloadCodec> codecs() { return List.of(new Declared(), new Observed(), new Acquired(), new Checkpointed(), new Unresolved(), new Released()); }
    private abstract static class Base implements PayloadCodec {
        final byte[] encodeBytes(Writer writer) { return FrontierWorldPayloadCodecs.encodeProduction(writer::write); }
        final FrontierPayload decodeBytes(byte[] bytes, Reader reader) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, reader::read); }
        final void subject(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
        final io.farfrontier.palemirror.frontier.v3.api.SubjectId subject(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
        @FunctionalInterface interface Writer { void write(DataOutputStream output) throws IOException; }
        @FunctionalInterface interface Reader { FrontierPayload read(DataInputStream input) throws IOException; }
    }
    private static final class Declared extends Base {
        @Override public String type() { return "frontier.physical_replica_declared"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> writeReplica(output, ((ReplicaDeclared) payload).replica())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes, input -> new ReplicaDeclared(readReplica(input))); }
    }
    private static final class Observed extends Base {
        @Override public String type() { return "frontier.physical_replica_observed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> {
            ReplicaObserved value = (ReplicaObserved) payload; subject(output, value.objectId()); output.writeLong(value.expectedCanonicalRevision()); output.writeLong(value.expectedReplicaRevision());
            FrontierWorldPayloadCodecs.writeString(output, value.fingerprint()); FrontierWorldPayloadCodecs.writeString(output, value.provenance()); output.writeLong(value.observedCanonicalRevision());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes, input -> new ReplicaObserved(
                subject(input), input.readLong(), input.readLong(), FrontierWorldPayloadCodecs.readString(input), FrontierWorldPayloadCodecs.readString(input), input.readLong())); }
    }
    private static final class Acquired extends Base {
        @Override public String type() { return "frontier.physical_custody_acquired"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> writeLease(output, ((CustodyAcquired) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes, input -> new CustodyAcquired(readLease(input))); }
    }
    private static final class Checkpointed extends Base {
        @Override public String type() { return "frontier.physical_custody_checkpointed"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> writeFence(output, (CustodyCheckpointed) payload)); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes, input -> {
            Fence fence = readFence(input); return new CustodyCheckpointed(fence.scope(), fence.epoch(), fence.canonical(), fence.replica());
        }); }
    }
    private static final class Unresolved extends Base {
        @Override public String type() { return "frontier.physical_custody_unresolved"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> {
            CustodyUnresolved value = (CustodyUnresolved) payload;
            subject(output, value.scopeId()); output.writeLong(value.expectedEpoch());
            output.writeLong(value.expectedCanonicalRevision()); output.writeLong(value.expectedReplicaRevision());
            output.writeByte(value.reason().wireTag());
        }); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes,
                input -> new CustodyUnresolved(subject(input), input.readLong(), input.readLong(), input.readLong(), readReason(input.readUnsignedByte()))); }
    }
    private static final class Released extends Base {
        @Override public String type() { return "frontier.physical_custody_released"; }
        @Override public byte[] encode(FrontierPayload payload) { return encodeBytes(output -> writeFence(output, (CustodyReleased) payload)); }
        @Override public FrontierPayload decode(byte[] bytes) { return decodeBytes(bytes, input -> {
            Fence fence = readFence(input); return new CustodyReleased(fence.scope(), fence.epoch(), fence.canonical(), fence.replica());
        }); }
    }
    private static void writeReplica(DataOutputStream output, PhysicalReplicaRecord value) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, value.objectId()); FrontierWorldPayloadCodecs.writeString(output, value.semanticKind());
        output.writeLong(value.emittedCanonicalRevision()); output.writeLong(value.observedCanonicalRevision()); output.writeLong(value.replicaRevision());
        FrontierWorldPayloadCodecs.writeString(output, value.fingerprint()); FrontierWorldPayloadCodecs.writeString(output, value.provenance()); output.writeByte(value.state().wireTag());
    }
    private static PhysicalReplicaRecord readReplica(DataInputStream input) throws IOException {
        var object = FrontierWorldPayloadCodecs.readSubject(input).value(); String kind = FrontierWorldPayloadCodecs.readString(input);
        long emitted = input.readLong(); long observed = input.readLong(); long replicaRevision = input.readLong(); String fingerprint = FrontierWorldPayloadCodecs.readString(input); String provenance = FrontierWorldPayloadCodecs.readString(input);
        return new PhysicalReplicaRecord(object, kind, emitted, observed, replicaRevision, fingerprint, provenance, readReplicaState(input.readUnsignedByte()));
    }
    private static void writeLease(DataOutputStream output, PhysicalCustodyLease lease) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, lease.scopeId()); FrontierWorldPayloadCodecs.writeSubject(output, lease.objectId()); FrontierWorldPayloadCodecs.writeSubject(output, lease.providerId());
        output.writeLong(lease.authorityEpoch()); output.writeLong(lease.expectedCanonicalRevision()); output.writeLong(lease.expectedReplicaRevision());
        output.writeByte(lease.status().wireTag()); output.writeBoolean(lease.unresolvedReason() != null);
        if (lease.unresolvedReason() != null) output.writeByte(lease.unresolvedReason().wireTag());
    }
    private static PhysicalCustodyLease readLease(DataInputStream input) throws IOException {
        var scope = FrontierWorldPayloadCodecs.readSubject(input).value(); var object = FrontierWorldPayloadCodecs.readSubject(input).value();
        var provider = FrontierWorldPayloadCodecs.readSubject(input).value();
        long epoch = input.readLong(); long canonical = input.readLong(); long replica = input.readLong(); PhysicalCustodyLeaseStatus status = readStatus(input.readUnsignedByte());
        PhysicalCustodyUnresolvedReason reason = input.readBoolean() ? readReason(input.readUnsignedByte()) : null;
        return new PhysicalCustodyLease(scope, object, provider, epoch, canonical, replica, status, reason);
    }
    private static void writeFence(DataOutputStream output, CustodyCheckpointed value) throws IOException { writeFence(output, value.scopeId(), value.expectedEpoch(), value.expectedCanonicalRevision(), value.expectedReplicaRevision()); }
    private static void writeFence(DataOutputStream output, CustodyReleased value) throws IOException { writeFence(output, value.scopeId(), value.expectedEpoch(), value.expectedCanonicalRevision(), value.expectedReplicaRevision()); }
    private static void writeFence(DataOutputStream output, io.farfrontier.palemirror.frontier.v3.api.SubjectId scope, long epoch, long canonical, long replica) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, scope); output.writeLong(epoch); output.writeLong(canonical); output.writeLong(replica);
    }
    private static Fence readFence(DataInputStream input) throws IOException {
        return new Fence(FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(), input.readLong(), input.readLong());
    }
    private record Fence(io.farfrontier.palemirror.frontier.v3.api.SubjectId scope, long epoch, long canonical, long replica) { }
    private static PhysicalReplicaState readReplicaState(int tag) { return switch (tag) {
        case 1 -> PhysicalReplicaState.EXPECTED; case 2 -> PhysicalReplicaState.OBSERVED_CURRENT; case 3 -> PhysicalReplicaState.CONFLICT;
        default -> throw new IllegalArgumentException("unknown replica lifecycle tag");
    }; }
    private static PhysicalCustodyLeaseStatus readStatus(int tag) { return switch (tag) {
        case 1 -> PhysicalCustodyLeaseStatus.ACQUIRED; case 2 -> PhysicalCustodyLeaseStatus.CHECKPOINTED;
        case 3 -> PhysicalCustodyLeaseStatus.UNRESOLVED; case 4 -> PhysicalCustodyLeaseStatus.RELEASED;
        default -> throw new IllegalArgumentException("unknown custody lifecycle tag");
    }; }
    private static PhysicalCustodyUnresolvedReason readReason(int tag) { return switch (tag) {
        case 1 -> PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH; case 2 -> PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY;
        case 3 -> PhysicalCustodyUnresolvedReason.PROVIDER_LOST; default -> throw new IllegalArgumentException("unknown custody reason tag");
    }; }
}
