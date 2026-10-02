package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec.*;

/** Exact replica/custody snapshot grammar; extraction preserves current wire order and tags. */
final class PhysicalReplicaCustodyStateCodec {
    private PhysicalReplicaCustodyStateCodec() { }

    static void write(DataOutputStream output, PhysicalReplicaCustodyState state) throws IOException {
        writeCount(output, state.replicas().size());
        for (PhysicalReplicaRecord replica : state.replicas().values().stream().sorted(Comparator.comparing(PhysicalReplicaRecord::objectId)).toList()) {
            writeString(output, replica.objectId().value()); writeString(output, replica.semanticKind());
            output.writeLong(replica.emittedCanonicalRevision()); output.writeLong(replica.observedCanonicalRevision()); output.writeLong(replica.replicaRevision());
            writeString(output, replica.fingerprint()); writeString(output, replica.provenance()); output.writeByte(replica.state().wireTag());
            output.writeBoolean(replica.observedFingerprint().isPresent());
            if (replica.observedFingerprint().isPresent()) { writeString(output, replica.observedFingerprint().orElseThrow()); writeString(output, replica.observedProvenance().orElseThrow());
                output.writeByte(replica.conflictReason().orElseThrow().wireTag()); }
        }
        writeCount(output, state.custodyByScope().size());
        for (PhysicalCustodyLease lease : state.custodyByScope().values().stream().sorted(Comparator.comparing(PhysicalCustodyLease::scopeId)).toList()) {
            writeString(output, lease.scopeId().value()); writeString(output, lease.objectId().value()); writeString(output, lease.providerId().value());
            output.writeLong(lease.authorityEpoch()); output.writeLong(lease.expectedCanonicalRevision()); output.writeLong(lease.expectedReplicaRevision());
            output.writeByte(lease.status().wireTag()); output.writeBoolean(lease.unresolvedReason() != null);
            if (lease.unresolvedReason() != null) output.writeByte(lease.unresolvedReason().wireTag());
        }
        writeCount(output, state.diagnostics().size());
        for (Map.Entry<SubjectId, DiagnosticTuple> entry : state.diagnostics().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            writeString(output, entry.getKey().value()); writeDiagnosticTuple(output, entry.getValue());
        } }
    static PhysicalReplicaCustodyState read(DataInputStream input) throws IOException {
        Map<SubjectId, PhysicalReplicaRecord> replicas = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId object = new SubjectId(readString(input)); String semanticKind = readString(input); long emitted = input.readLong(); long observed = input.readLong(); long replicaRevision = input.readLong();
            String fingerprint = readString(input); String provenance = readString(input); int state = input.readUnsignedByte();
            PhysicalReplicaState lifecycle = replicaState(state);
            Optional<String> observedFingerprint = Optional.empty(), observedProvenance = Optional.empty(); Optional<PhysicalReplicaConflictReason> conflictReason = Optional.empty();
            if (input.readBoolean()) { observedFingerprint = Optional.of(readString(input)); observedProvenance = Optional.of(readString(input)); conflictReason = Optional.of(replicaConflictReason(input.readUnsignedByte())); }
            if (replicas.put(object, new PhysicalReplicaRecord(object, semanticKind, emitted, observed, replicaRevision, fingerprint, provenance, lifecycle,
                    observedFingerprint, observedProvenance, conflictReason)) != null) {
                throw new IllegalArgumentException("duplicate physical replica identity");
            } }
        Map<SubjectId, PhysicalCustodyLease> leases = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId scope = new SubjectId(readString(input)); SubjectId object = new SubjectId(readString(input)); SubjectId provider = new SubjectId(readString(input));
            long epoch = input.readLong(); long canonicalRevision = input.readLong(); long replicaRevision = input.readLong();
            PhysicalCustodyLeaseStatus status = custodyStatus(input.readUnsignedByte());
            PhysicalCustodyUnresolvedReason reason = input.readBoolean() ? unresolvedReason(input.readUnsignedByte()) : null;
            if (leases.put(scope, new PhysicalCustodyLease(scope, object, provider, epoch, canonicalRevision, replicaRevision, status, reason)) != null) {
                throw new IllegalArgumentException("duplicate physical custody scope");
            } }
        Map<SubjectId, DiagnosticTuple> diagnostics = new LinkedHashMap<>();
        for (int index = 0, count = readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(readString(input));
            if (diagnostics.put(id, readDiagnosticTuple(input)) != null) throw new IllegalArgumentException("duplicate replica custody diagnostic identity");
        }
        return new PhysicalReplicaCustodyState(replicas, leases, diagnostics); }
    private static PhysicalReplicaState replicaState(int tag) { return switch (tag) { case 1 -> PhysicalReplicaState.EXPECTED; case 2 -> PhysicalReplicaState.OBSERVED_CURRENT; case 3 -> PhysicalReplicaState.CONFLICT;
        default -> throw new IllegalArgumentException("unknown physical replica lifecycle tag"); }; }
    private static PhysicalCustodyLeaseStatus custodyStatus(int tag) { return switch (tag) { case 1 -> PhysicalCustodyLeaseStatus.ACQUIRED; case 2 -> PhysicalCustodyLeaseStatus.CHECKPOINTED;
        case 3 -> PhysicalCustodyLeaseStatus.UNRESOLVED; case 4 -> PhysicalCustodyLeaseStatus.RELEASED; case 5 -> PhysicalCustodyLeaseStatus.PREPARING;
        default -> throw new IllegalArgumentException("unknown physical custody lifecycle tag"); }; }
    private static PhysicalCustodyUnresolvedReason unresolvedReason(int tag) { return switch (tag) { case 1 -> PhysicalCustodyUnresolvedReason.OBSERVATION_MISMATCH; case 2 -> PhysicalCustodyUnresolvedReason.RESTART_AMBIGUITY;
        case 3 -> PhysicalCustodyUnresolvedReason.PROVIDER_LOST; default -> throw new IllegalArgumentException("unknown physical custody conflict tag"); }; }
    private static PhysicalReplicaConflictReason replicaConflictReason(int tag) { return switch (tag) { case 1 -> PhysicalReplicaConflictReason.FINGERPRINT_MISMATCH;
        case 2 -> PhysicalReplicaConflictReason.PROVENANCE_MISMATCH; case 3 -> PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH;
        default -> throw new IllegalArgumentException("unknown physical replica conflict tag"); }; }
}
