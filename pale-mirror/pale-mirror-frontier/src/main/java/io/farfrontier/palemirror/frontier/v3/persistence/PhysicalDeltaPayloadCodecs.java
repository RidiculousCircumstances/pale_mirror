package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDelta;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaKind;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaObserved;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltasObserved;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stable codecs for one bounded physical observation and its atomic survival cascade. */
final class PhysicalDeltaPayloadCodecs {
    private PhysicalDeltaPayloadCodecs() { }

    static PayloadCodec single() { return new SingleCodec(); }
    static PayloadCodec batch() { return new BatchCodec(); }

    private static final class SingleCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_delta_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(output -> write(output, ((PhysicalDeltaObserved) payload).delta()));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new PhysicalDeltaObserved(read(input)));
        }
    }

    private static final class BatchCodec implements PayloadCodec {
        @Override public String type() { return "frontier.physical_deltas_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            PhysicalDeltasObserved observed = (PhysicalDeltasObserved) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                output.writeByte(observed.deltas().size());
                for (PhysicalDelta delta : observed.deltas()) write(output, delta);
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                int count = input.readUnsignedByte();
                if (count < 1 || count > PhysicalDeltasObserved.MAX_ATOMIC_DELTAS) {
                    throw new IllegalArgumentException("physical delta observation count is out of bounds");
                }
                List<PhysicalDelta> deltas = new ArrayList<>(count);
                for (int index = 0; index < count; index++) deltas.add(read(input));
                return new PhysicalDeltasObserved(deltas);
            });
        }
    }

    private static void write(DataOutputStream output, PhysicalDelta delta) throws IOException {
        FrontierWorldPayloadCodecs.writePosition(output, delta.position()); output.writeByte(delta.kind().wireTag());
        FrontierWorldPayloadCodecs.writeString(output, delta.cause()); output.writeBoolean(delta.ownerId().isPresent());
        if (delta.ownerId().isPresent()) FrontierWorldPayloadCodecs.writeSubject(output, delta.ownerId().orElseThrow());
        output.writeBoolean(delta.semanticPart().isPresent());
        if (delta.semanticPart().isPresent()) output.writeByte(delta.semanticPart().orElseThrow().wireTag());
    }

    private static PhysicalDelta read(DataInputStream input) throws IOException {
        BlockPosition position = FrontierWorldPayloadCodecs.readPosition(input); int kind = input.readUnsignedByte();
        String cause = FrontierWorldPayloadCodecs.readString(input);
        Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> owner = input.readBoolean()
                ? Optional.of(FrontierWorldPayloadCodecs.readSubject(input).value()) : Optional.empty();
        int part = input.readBoolean() ? input.readUnsignedByte() : -1;
        if (kind >= PhysicalDeltaKind.values().length || part >= GrayboxSemanticPart.values().length) {
            throw new IllegalArgumentException("unknown physical delta value");
        }
        Optional<GrayboxSemanticPart> semantic = part < 0 ? Optional.empty()
                : Optional.of(FrontierWireTags.require(GrayboxSemanticPart.class, part));
        return new PhysicalDelta(position, FrontierWireTags.require(PhysicalDeltaKind.class, kind), owner, semantic, cause);
    }
}
