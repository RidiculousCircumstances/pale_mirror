package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPayloads.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** WAL codecs intentionally share the snapshot's exact binding grammar. */
final class FencedRecoveryPayloadCodecs {
    private FencedRecoveryPayloadCodecs() { }
    static List<PayloadCodec> codecs() {
        return List.of(new PreparedCodec(), new FenceCodec("frontier.fenced_recovery_running", Running.class),
                new FenceCodec("frontier.fenced_recovery_observed", Observed.class),
                new FenceCodec("frontier.fenced_recovery_confirmed", Confirmed.class),
                new FenceCodec("frontier.fenced_recovery_revoked_to_cold", RevokedToCold.class),
                new AmbiguousCodec(), new FenceCodec("frontier.fenced_recovery_abandoned", Abandoned.class), new CargoCleanupSavedCodec());
    }
    private abstract static class Base implements PayloadCodec {
        final byte[] bytes(Writer writer) { return FrontierWorldPayloadCodecs.encodeProduction(writer::write); }
        final FrontierPayload payload(byte[] bytes, Reader reader) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, reader::read); }
        final void id(DataOutputStream output, SubjectId id) throws IOException { FrontierWorldPayloadCodecs.writeSubject(output, id); }
        final SubjectId id(DataInputStream input) throws IOException { return FrontierWorldPayloadCodecs.readSubject(input).value(); }
        @FunctionalInterface interface Writer { void write(DataOutputStream output) throws IOException; }
        @FunctionalInterface interface Reader { FrontierPayload read(DataInputStream input) throws IOException; }
    }
    private static final class PreparedCodec extends Base {
        @Override public String type() { return "frontier.fenced_recovery_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return bytes(output -> writeBinding(output, ((Prepared) payload).binding())); }
        @Override public FrontierPayload decode(byte[] bytes) { return payload(bytes, input -> new Prepared(readBinding(input))); }
    }
    private static final class CargoCleanupSavedCodec extends Base {
        @Override public String type() { return "frontier.cargo_cleanup_saved"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return bytes(output -> FencedRecoveryStateCodec.writeRetirement(output, ((CargoCleanupSaved) payload).retirement()));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return payload(bytes, input -> new CargoCleanupSaved(FencedRecoveryStateCodec.readRetirement(input)));
        }
    }
    private static final class FenceCodec extends Base {
        private final String type; private final Class<?> kind;
        FenceCodec(String type, Class<?> kind) { this.type = type; this.kind = kind; }
        @Override public String type() { return type; }
        @Override public byte[] encode(FrontierPayload payload) { return bytes(output -> { SubjectId id; long epoch;
            if (payload instanceof Running value) { id = value.bindingId(); epoch = value.expectedEpoch(); }
            else if (payload instanceof Observed value) { id = value.bindingId(); epoch = value.expectedEpoch(); }
            else if (payload instanceof Confirmed value) { id = value.bindingId(); epoch = value.expectedEpoch(); }
            else if (payload instanceof RevokedToCold value) { id = value.bindingId(); epoch = value.expectedEpoch(); }
            else if (payload instanceof Abandoned value) { id = value.bindingId(); epoch = value.expectedEpoch(); }
            else throw new IllegalArgumentException("wrong fenced recovery fence payload"); id(output, id); output.writeLong(epoch); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return payload(bytes, input -> { SubjectId id = id(input); long epoch = input.readLong();
            if (kind == Running.class) return new Running(id, epoch); if (kind == Observed.class) return new Observed(id, epoch);
            if (kind == Confirmed.class) return new Confirmed(id, epoch); if (kind == RevokedToCold.class) return new RevokedToCold(id, epoch); return new Abandoned(id, epoch); }); }
    }
    private static final class AmbiguousCodec extends Base {
        @Override public String type() { return "frontier.fenced_recovery_ambiguous"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return bytes(output -> {
                Ambiguous value = (Ambiguous) payload;
                id(output, value.bindingId()); output.writeLong(value.expectedEpoch());
                FrontierWorldPayloadCodecs.writeString(output, value.reason()); output.writeByte(value.action().wireTag());
                FrontierWorldPayloadCodecs.writeDiagnosticTuple(output, value.diagnostic());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return payload(bytes, input -> new Ambiguous(id(input), input.readLong(), FrontierWorldPayloadCodecs.readString(input),
                    disposition(input.readUnsignedByte()), FrontierWorldPayloadCodecs.readDiagnosticTuple(input)));
        }
    }
    private static void writeBinding(DataOutputStream output, FencedRecoveryBinding value) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, value.bindingId()); output.writeByte(value.asset().wireTag());
        FrontierWorldPayloadCodecs.writeSubject(output, value.ownerId()); output.writeLong(value.ownerRevision());
        output.writeLong(value.authorityEpoch()); output.writeByte(value.phase().wireTag());
        output.writeBoolean(value.reversibleCheckpoint()); output.writeByte(value.recoveryAttempts());
        output.writeByte(value.nextAction().wireTag()); FrontierWorldPayloadCodecs.writeString(output, value.reason());
    }
    private static FencedRecoveryBinding readBinding(DataInputStream input) throws IOException {
        return new FencedRecoveryBinding(FrontierWorldPayloadCodecs.readSubject(input).value(), asset(input.readUnsignedByte()),
                FrontierWorldPayloadCodecs.readSubject(input).value(), input.readLong(), input.readLong(),
                phase(input.readUnsignedByte()), input.readBoolean(), input.readUnsignedByte(),
                disposition(input.readUnsignedByte()), FrontierWorldPayloadCodecs.readString(input));
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
