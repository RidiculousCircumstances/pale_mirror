package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Optional;

final class BakeryHotDeliveryAbortedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.bakery_hot_delivery_aborted"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (BakeryHotDeliveryAborted) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeSubject(output, value.jobId());
            FrontierWorldStateCodec.writeString(output, value.leaseId().value());
            output.writeByte(value.destinationSlot()); output.writeLong(value.sourceEpoch());
            PhysicalObservationStackCodec.writeStacks(output, List.of(value.unchangedHand()));
            output.writeBoolean(value.exactItemId().isPresent());
            if (value.exactItemId().isPresent()) FrontierWorldPayloadCodecs.writeSubject(output, value.exactItemId().orElseThrow());
            FrontierWorldPayloadCodecs.writeSubject(output, value.occupiedDestination().scopeId());
            FrontierWorldStateCodec.writeString(output, value.occupiedDestination().observedKind());
            output.writeInt(value.occupiedDestination().observedCount());
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
            var job = FrontierWorldPayloadCodecs.readSubject(input).value();
            var lease = new SceneLeaseId(FrontierWorldStateCodec.readString(input));
            int slot = input.readUnsignedByte(); long epoch = input.readLong();
            var hand = PhysicalObservationStackCodec.readStacks(input);
            if (hand.size() != 1) throw new IllegalArgumentException("delivery cancellation needs exactly one hand");
            var exact = input.readBoolean() ? Optional.of(FrontierWorldPayloadCodecs.readSubject(input).value()) : Optional.<io.farfrontier.palemirror.frontier.v3.api.SubjectId>empty();
            var scope = FrontierWorldPayloadCodecs.readSubject(input).value();
            var kind = FrontierWorldStateCodec.readString(input); int count = input.readInt();
            return new BakeryHotDeliveryAborted(job, lease, slot, epoch, hand.getFirst(), exact,
                    new BakeryWorkBlock(BakeryWorkBlock.Reason.DESTINATION_OCCUPIED, scope, slot, kind, count));
        });
    }
}
