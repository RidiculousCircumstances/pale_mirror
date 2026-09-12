package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact current authority and stale-binding rejection facts for fresh F0.5 worlds. */
final class FencedRecoveryStateCodec {
    private FencedRecoveryStateCodec() { }

    static void write(DataOutputStream output, FencedRecoveryState state) throws IOException {
        FrontierWorldStateCodec.writeCount(output, state.current().size());
        for (FencedRecoveryBinding binding : state.current().values().stream().sorted(Comparator.comparing(FencedRecoveryBinding::bindingId)).toList()) {
            writeBinding(output, binding);
        }
        FrontierWorldStateCodec.writeCount(output, state.tombstones().size());
        for (FencedRecoveryTombstone tombstone : state.tombstones().values().stream().sorted(Comparator.comparing(FencedRecoveryTombstone::bindingId)).toList()) {
            FrontierWorldStateCodec.writeString(output, tombstone.bindingId().value()); output.writeByte(tombstone.asset().wireTag());
            FrontierWorldStateCodec.writeString(output, tombstone.ownerId().value()); output.writeLong(tombstone.ownerRevision()); output.writeLong(tombstone.retiredEpoch());
            output.writeByte(tombstone.disposition().wireTag()); FrontierWorldStateCodec.writeString(output, tombstone.reason());
        }
    }

    static FencedRecoveryState read(DataInputStream input) throws IOException {
        Map<SubjectId, FencedRecoveryBinding> current = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            FencedRecoveryBinding binding = readBinding(input);
            if (current.put(binding.bindingId(), binding) != null) throw new IllegalArgumentException("duplicate fenced recovery binding");
        }
        Map<SubjectId, FencedRecoveryTombstone> tombstones = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input)); FencedRecoveryAsset asset = asset(input.readUnsignedByte());
            SubjectId owner = new SubjectId(FrontierWorldStateCodec.readString(input)); long revision = input.readLong(); long epoch = input.readLong();
            FencedRecoveryTombstone tombstone = new FencedRecoveryTombstone(id, asset, owner, revision, epoch,
                    disposition(input.readUnsignedByte()), FrontierWorldStateCodec.readString(input));
            if (tombstones.put(id, tombstone) != null) throw new IllegalArgumentException("duplicate fenced recovery tombstone");
        }
        return new FencedRecoveryState(current, tombstones);
    }

    private static void writeBinding(DataOutputStream output, FencedRecoveryBinding binding) throws IOException {
        FrontierWorldStateCodec.writeString(output, binding.bindingId().value()); output.writeByte(binding.asset().wireTag());
        FrontierWorldStateCodec.writeString(output, binding.ownerId().value()); output.writeLong(binding.ownerRevision()); output.writeLong(binding.authorityEpoch());
        output.writeByte(binding.phase().wireTag()); output.writeBoolean(binding.reversibleCheckpoint()); output.writeByte(binding.recoveryAttempts());
        output.writeByte(binding.nextAction().wireTag()); FrontierWorldStateCodec.writeString(output, binding.reason());
    }
    private static FencedRecoveryBinding readBinding(DataInputStream input) throws IOException {
        return new FencedRecoveryBinding(new SubjectId(FrontierWorldStateCodec.readString(input)), asset(input.readUnsignedByte()),
                new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong(), input.readLong(), phase(input.readUnsignedByte()),
                input.readBoolean(), input.readUnsignedByte(), disposition(input.readUnsignedByte()), FrontierWorldStateCodec.readString(input));
    }
    private static FencedRecoveryAsset asset(int tag) {
        return switch (tag) {
            case 1 -> FencedRecoveryAsset.BODY;
            case 2 -> FencedRecoveryAsset.CARGO;
            case 3 -> FencedRecoveryAsset.CONTAINER;
            case 4 -> FencedRecoveryAsset.EFFECT;
            default -> throw new IllegalArgumentException("unknown fenced recovery asset");
        };
    }
    private static FencedRecoveryPhase phase(int tag) {
        return switch (tag) {
            case 1 -> FencedRecoveryPhase.PREPARED;
            case 2 -> FencedRecoveryPhase.RUNNING;
            case 3 -> FencedRecoveryPhase.OBSERVED;
            case 4 -> FencedRecoveryPhase.CONFIRMED;
            case 5 -> FencedRecoveryPhase.AMBIGUOUS;
            default -> throw new IllegalArgumentException("unknown fenced recovery phase");
        };
    }
    private static FencedRecoveryDisposition disposition(int tag) {
        return switch (tag) {
            case 1 -> FencedRecoveryDisposition.RECLAIM;
            case 2 -> FencedRecoveryDisposition.RESUME_COLD;
            case 3 -> FencedRecoveryDisposition.INSPECT;
            case 4 -> FencedRecoveryDisposition.RETRY;
            case 5 -> FencedRecoveryDisposition.REPAIR;
            case 6 -> FencedRecoveryDisposition.ABANDON;
            case 7 -> FencedRecoveryDisposition.REJECT_STALE;
            default -> throw new IllegalArgumentException("unknown fenced recovery disposition");
        };
    }
}
