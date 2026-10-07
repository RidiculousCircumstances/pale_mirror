package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class UnitInventoryPayloadCodecs {
    private UnitInventoryPayloadCodecs() { }
    static PayloadCodecs create() { return new PayloadCodecs(List.of(new PayloadCodec() {
        @Override public String type() { return "frontier.unit_inventory_disposition_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var receipt = (UnitInventoryDispositionObserved) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                UnitGroupStateCodec.id(out, receipt.body().actorId()); out.writeLong(receipt.body().physicalEpoch());
                UnitGroupStateCodec.id(out, receipt.accountId()); out.writeLong(receipt.sourceEpoch());
                out.writeByte(switch (receipt.outcome()) { case WORLD_DROP -> 1; case MISSING_BEFORE_LOOT -> 2; });
                out.writeBoolean(receipt.worldCarrier().isPresent());
                if (receipt.worldCarrier().isPresent()) {
                    out.writeLong(receipt.worldCarrier().orElseThrow().getMostSignificantBits());
                    out.writeLong(receipt.worldCarrier().orElseThrow().getLeastSignificantBits());
                }
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new UnitInventoryDispositionObserved(
                    new ActorBodyId(UnitGroupStateCodec.id(in), in.readLong()), UnitGroupStateCodec.id(in), in.readLong(),
                    switch (in.readUnsignedByte()) {
                        case 1 -> UnitInventoryDispositionObserved.Outcome.WORLD_DROP;
                        case 2 -> UnitInventoryDispositionObserved.Outcome.MISSING_BEFORE_LOOT;
                        default -> throw new IllegalArgumentException("unknown inventory disposition tag");
                    }, in.readBoolean() ? Optional.of(new UUID(in.readLong(), in.readLong())) : Optional.empty()));
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.unit_inventory_bound_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var receipt = (UnitInventoryBoundObserved) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                UnitGroupStateCodec.id(out, receipt.body().actorId()); out.writeLong(receipt.body().physicalEpoch());
                UnitGroupStateCodec.id(out, receipt.accountId()); PhysicalObservationStackCodec.writeStacks(out, List.of(receipt.stack()));
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var body = new ActorBodyId(UnitGroupStateCodec.id(in), in.readLong()); var account = UnitGroupStateCodec.id(in);
                var stacks = PhysicalObservationStackCodec.readStacks(in);
                if (stacks.size() != 1) throw new IllegalArgumentException("personal binding requires one observed stack");
                return new UnitInventoryBoundObserved(body, account, stacks.getFirst());
            });
        }
    })); }
}
