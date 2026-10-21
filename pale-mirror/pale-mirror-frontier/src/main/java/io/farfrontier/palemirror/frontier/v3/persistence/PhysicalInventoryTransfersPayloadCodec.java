package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.*;

final class PhysicalInventoryTransfersPayloadCodec implements PayloadCodec {
    private static final int MAX_BYTES = 1_048_576;
    private final PayloadCodec handoff;
    PhysicalInventoryTransfersPayloadCodec(PayloadCodec handoff) { this.handoff = Objects.requireNonNull(handoff); }
    @Override public String type() { return "frontier.physical_inventory_transfers_observed"; }
    @Override public byte[] encode(FrontierPayload payload) {
        byte[] encoded = encodeProduction(out -> {
            var batch = (PhysicalInventoryTransfersObserved) payload; out.writeByte(1); out.writeByte(batch.resources().size());
            for (var resource : batch.resources()) { byte[] bytes = handoff.encode(resource); out.writeInt(bytes.length); out.write(bytes); }
            out.writeByte(batch.exactItems().size());
            for (var item : batch.exactItems()) { writeSubject(out, item.itemId()); writeCustody(out, item.from()); writeCustody(out, item.to()); }
        });
        if (encoded.length > MAX_BYTES) throw new IllegalArgumentException("unbounded physical transfer batch encoding");
        return encoded;
    }
    @Override public FrontierPayload decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("unbounded physical transfer batch encoding");
        return decodeProduction(bytes, in -> {
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("unsupported physical transfer batch format");
            int count = in.readUnsignedByte(); if (count > 64) throw new IllegalArgumentException("unbounded resource transfer batch");
            var resources = new ArrayList<FungibleResourceHandoffObserved>();
            for (int i = 0; i < count; i++) {
                int size = in.readInt(); if (size < 1 || size > in.available()) throw new IllegalArgumentException("invalid embedded handoff size");
                resources.add((FungibleResourceHandoffObserved) handoff.decode(in.readNBytes(size)));
            }
            int exact = in.readUnsignedByte(); if (exact + count > 64) throw new IllegalArgumentException("unbounded exact transfer batch");
            var items = new ArrayList<ExactItemCustodyChanged>();
            for (int i = 0; i < exact; i++) items.add(new ExactItemCustodyChanged(readSubject(in).value(), readCustody(in), readCustody(in)));
            return new PhysicalInventoryTransfersObserved(resources, items);
        });
    }
}
