package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.KernelPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;

import java.nio.ByteBuffer;
import java.util.List;

/** Complete payload registry for the currently installed v3 world processes. */
public final class FrontierWorldPayloadCodecs {
    private FrontierWorldPayloadCodecs() { }
    public static PayloadCodecs create() {
        return PayloadCodecs.merge(KernelPayloadCodecs.scheduleEffects(), new PayloadCodecs(List.of(new InfectionCodec())));
    }
    private static final class InfectionCodec implements PayloadCodec {
        @Override public String type() { return "frontier.infection_changed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            InfectionChanged changed = (InfectionChanged) payload;
            return ByteBuffer.allocate(16).putInt(changed.cell().x()).putInt(changed.cell().z()).putLong(changed.intensity().value().raw()).array();
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("malformed infection change payload");
            ByteBuffer input = ByteBuffer.wrap(bytes);
            return new InfectionChanged(new InfectionCell(input.getInt(), input.getInt()), new FixedRatio(new FixedScalar(input.getLong())));
        }
    }
}
