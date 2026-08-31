package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.util.UUID;

/** Versioned durable encoding for the atomic HOT-cargo release boundary. */
public final class CargoCarrierReleasedPayloadCodec implements PayloadCodec {
    private static final int OPTIONAL_OBSERVER_FORMAT = 2;

    @Override public String type() { return "frontier.cargo_carrier_released"; }

    @Override public byte[] encode(FrontierPayload payload) {
        CargoCarrierReleased released = (CargoCarrierReleased) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            output.writeByte(OPTIONAL_OBSERVER_FORMAT);
            FrontierWorldPayloadCodecs.writeString(output, released.leaseId().value());
            FrontierWorldPayloadCodecs.writeSubject(output, released.cargoId());
            FrontierWorldPayloadCodecs.writeString(output, released.carrierId().toString());
            output.writeBoolean(released.observerPlayerId().isPresent());
            if (released.observerPlayerId().isPresent()) FrontierWorldPayloadCodecs.writeString(output, released.observerPlayerId().orElseThrow().toString());
        });
    }

    @Override public FrontierPayload decode(byte[] bytes) {
        // Format 1 began with a bounded string length, whose first byte is always zero for a
        // valid scene-lease ID. Its retained WAL tail remains recoverable across this v3 change.
        if (bytes.length > 0 && Byte.toUnsignedInt(bytes[0]) == OPTIONAL_OBSERVER_FORMAT) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                if (input.readUnsignedByte() != OPTIONAL_OBSERVER_FORMAT) throw new IllegalArgumentException("unknown cargo carrier release payload format");
                return new CargoCarrierReleased(new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readSubject(input).value(),
                        UUID.fromString(FrontierWorldPayloadCodecs.readString(input)), input.readBoolean()
                        ? java.util.Optional.of(UUID.fromString(FrontierWorldPayloadCodecs.readString(input))) : java.util.Optional.empty());
            });
        }
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new CargoCarrierReleased(
                new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readSubject(input).value(),
                UUID.fromString(FrontierWorldPayloadCodecs.readString(input)), java.util.Optional.of(UUID.fromString(FrontierWorldPayloadCodecs.readString(input)))));
    }
}
