package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Isolated WAL codecs for the persisted ambient HOT/COLD execution boundary. */
final class AmbientLeasePayloadCodecs {
    private AmbientLeasePayloadCodecs() { }
    static PayloadCodec prepared() { return new PreparedCodec(); }
    static PayloadCodec transition() { return new TransitionCodec(); }
    static PayloadCodec released() { return new ReleasedCodec(); }

    private static final class PreparedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.ambient_lease_prepared"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> writeLease(output, ((AmbientLeasePrepared) payload).lease())); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new AmbientLeasePrepared(readLease(input))); }
    }
    private static final class TransitionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.ambient_lease_transition"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> { AmbientLeaseTransition transition = (AmbientLeaseTransition) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, transition.actorId()); output.writeByte(transition.status().wireTag()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            var actor = FrontierWorldPayloadCodecs.readSubject(input); int status = input.readUnsignedByte();
            if (status >= AmbientLeaseStatus.values().length) throw new IllegalArgumentException("unknown ambient lease status");
            return new AmbientLeaseTransition(actor.value(), FrontierWireTags.require(AmbientLeaseStatus.class, status));
        }); }
    }
    private static final class ReleasedCodec implements PayloadCodec {
        @Override public String type() { return "frontier.ambient_lease_released"; }
        @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> { AmbientLeaseReleased release = (AmbientLeaseReleased) payload;
            FrontierWorldPayloadCodecs.writeSubject(output, release.actorId()); FrontierWorldPayloadCodecs.writePosition(output, release.position()); output.writeLong(release.health().raw()); }); }
        @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new AmbientLeaseReleased(
                FrontierWorldPayloadCodecs.readSubject(input).value(), FrontierWorldPayloadCodecs.readPosition(input), new FixedScalar(input.readLong()))); }
    }
    private static void writeLease(DataOutputStream output, AmbientActorLease lease) throws IOException {
        FrontierWorldPayloadCodecs.writeSubject(output, lease.actorId()); FrontierWorldPayloadCodecs.writePosition(output, lease.handoffPosition()); output.writeLong(lease.handoffInstant().ticks());
        output.writeLong(lease.revision()); output.writeByte(lease.status().wireTag()); output.writeByte(lease.goal().wireTag()); FrontierWorldPayloadCodecs.writePosition(output, lease.goalPosition());
    }
    private static AmbientActorLease readLease(DataInputStream input) throws IOException {
        var actor = FrontierWorldPayloadCodecs.readSubject(input); BlockPosition handoff = FrontierWorldPayloadCodecs.readPosition(input);
        long instant = input.readLong(); long revision = input.readLong(); int status = input.readUnsignedByte(); int goal = input.readUnsignedByte();
        BlockPosition goalPosition = FrontierWorldPayloadCodecs.readPosition(input);
        if (status >= AmbientLeaseStatus.values().length || goal >= AmbientGoalKind.values().length) throw new IllegalArgumentException("unknown ambient lease value");
        return new AmbientActorLease(actor.value(), handoff, new SimInstant(instant), revision, FrontierWireTags.require(AmbientLeaseStatus.class, status), FrontierWireTags.require(AmbientGoalKind.class, goal), goalPosition);
    }
}
