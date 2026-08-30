package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;

/** Wire codecs owned by the exact resident-health and settlement-quarantine boundary. */
final class HumanHealthPayloadCodecs {
    private HumanHealthPayloadCodecs() { }

    static PayloadCodec residentTransition() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.resident_health_transition"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                ResidentHealthTransition transition = (ResidentHealthTransition) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, transition.residentId()); output.writeByte(transition.status().ordinal()); output.writeLong(transition.atTick());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                var resident = FrontierWorldPayloadCodecs.readSubject(input).value(); int status = input.readUnsignedByte();
                if (status >= ResidentHealthStatus.values().length) throw new IllegalArgumentException("unknown resident health status");
                return new ResidentHealthTransition(resident, ResidentHealthStatus.values()[status], input.readLong());
            }); }
        };
    }

    static PayloadCodec quarantineTransition() {
        return new PayloadCodec() {
            @Override public String type() { return "frontier.settlement_quarantine_transition"; }
            @Override public byte[] encode(FrontierPayload payload) { return FrontierWorldPayloadCodecs.encodeProduction(output -> {
                SettlementQuarantineTransition transition = (SettlementQuarantineTransition) payload;
                FrontierWorldPayloadCodecs.writeSubject(output, transition.settlementId()); output.writeByte(transition.status().ordinal()); output.writeLong(transition.atTick());
            }); }
            @Override public FrontierPayload decode(byte[] bytes) { return FrontierWorldPayloadCodecs.decodeProduction(bytes, input -> {
                var settlement = FrontierWorldPayloadCodecs.readSubject(input).value(); int status = input.readUnsignedByte();
                if (status >= SettlementQuarantineStatus.values().length) throw new IllegalArgumentException("unknown settlement quarantine status");
                return new SettlementQuarantineTransition(settlement, SettlementQuarantineStatus.values()[status], input.readLong());
            }); }
        };
    }
}
