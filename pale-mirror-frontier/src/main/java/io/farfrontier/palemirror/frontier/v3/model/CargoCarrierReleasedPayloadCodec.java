package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

import java.util.UUID;

/** Versioned durable encoding for the atomic HOT-cargo release boundary. */
final class CargoCarrierReleasedPayloadCodec implements PayloadCodec {
    @Override public String type() { return "frontier.cargo_carrier_released"; }

    @Override public byte[] encode(FrontierPayload payload) {
        CargoCarrierReleased released = (CargoCarrierReleased) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeString(output, released.leaseId().value());
            FrontierWorldPayloadCodecs.writeSubject(output, released.cargoId());
            FrontierWorldPayloadCodecs.writeString(output, released.carrierId().toString());
            FrontierWorldPayloadCodecs.writeString(output, released.observerPlayerId().toString());
        });
    }

    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> new CargoCarrierReleased(
                new SceneLeaseId(FrontierWorldPayloadCodecs.readString(input)), FrontierWorldPayloadCodecs.readSubject(input).value(),
                UUID.fromString(FrontierWorldPayloadCodecs.readString(input)), UUID.fromString(FrontierWorldPayloadCodecs.readString(input))));
    }
}
