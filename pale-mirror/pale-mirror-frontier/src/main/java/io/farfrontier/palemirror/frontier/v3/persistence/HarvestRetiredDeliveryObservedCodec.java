package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

final class HarvestRetiredDeliveryObservedCodec implements PayloadCodec {
    @Override public String type() { return "frontier.harvest_retired_delivery_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        var value = (HarvestRetiredDeliveryObserved) payload;
        return FrontierWorldPayloadCodecs.encodeProduction(out -> {
            out.writeUTF(value.identity().body().actorId().value()); out.writeLong(value.identity().body().physicalEpoch());
            ActorExecutionStateCodec.writeId(out, value.identity().execution()); out.writeUTF(value.siteId().value());
            out.writeInt(value.deliveredBefore()); out.writeInt(value.quantity()); out.writeBoolean(value.applied().isPresent());
            if (value.applied().isPresent()) {
                var receipt = value.applied().orElseThrow();
                out.writeUTF(receipt.id().value()); out.writeUTF(receipt.intentId().value());
                ResourceSiteHarvestDeliveryObservationCodec.write(out, receipt);
            }
        });
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
            var body = new ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
            var identity = new ActorActuationId(body, ActorExecutionStateCodec.readId(in));
            var site = new SubjectId(in.readUTF()); int before = in.readInt(), quantity = in.readInt();
            Optional<ResourceSiteHarvestDeliveryObservation> applied = in.readBoolean() ? Optional.of(
                    ResourceSiteHarvestDeliveryObservationCodec.read(in, new PhysicalObservationId(in.readUTF()),
                        new PhysicalIntentId(in.readUTF()))) : Optional.empty();
            return new HarvestRetiredDeliveryObserved(identity, site, before, quantity, applied);
        });
    }
}
