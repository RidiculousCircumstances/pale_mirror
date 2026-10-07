package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.List;

final class ExpeditionSupplyPayloadCodecs {
    private ExpeditionSupplyPayloadCodecs() { }
    static PayloadCodecs create() {
        return new PayloadCodecs(List.of(codec("frontier.expedition_supply_cold_loaded", (out, p) -> {
            var v = (ExpeditionSupplyColdLoaded)p; UnitGroupStateCodec.id(out, v.missionId()); UnitGroupStateCodec.id(out, v.claimId());
        }, in -> new ExpeditionSupplyColdLoaded(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in))),
        codec("frontier.expedition_supply_hot_prepared", (out, p) -> {
            var v = (ExpeditionSupplyHotPrepared)p; UnitGroupStateCodec.id(out, v.missionId()); UnitGroupStateCodec.id(out, v.claimId());
            ActorItemTransferStepCodec.write(out, v.step());
        }, in -> new ExpeditionSupplyHotPrepared(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), ActorItemTransferStepCodec.read(in))),
        codec("frontier.expedition_supply_hot_loaded", (out, p) -> {
            var v = (ExpeditionSupplyHotLoaded)p; UnitGroupStateCodec.id(out, v.missionId()); UnitGroupStateCodec.id(out, v.claimId());
            ActorItemTransferStepCodec.write(out, v.step()); PhysicalObservationStackCodec.writeStacks(out, v.remainingSource());
            PhysicalObservationStackCodec.writeStacks(out, v.destination());
        }, in -> new ExpeditionSupplyHotLoaded(UnitGroupStateCodec.id(in), UnitGroupStateCodec.id(in), ActorItemTransferStepCodec.read(in),
                PhysicalObservationStackCodec.readStacks(in), PhysicalObservationStackCodec.readStacks(in))),
        codec("frontier.expedition_supply_replanned", (out, p) -> {
            var v = (ExpeditionSupplyReplanned)p; UnitGroupStateCodec.id(out, v.missionId()); out.writeLong(v.expectedRevision());
            ExpeditionSupplyLoadCodec.write(out, v.replacement());
        }, in -> new ExpeditionSupplyReplanned(UnitGroupStateCodec.id(in), in.readLong(), ExpeditionSupplyLoadCodec.read(in)))));
    }
    private interface Writer { void write(DataOutputStream out, FrontierPayload p) throws IOException; }
    private interface Reader { FrontierPayload read(DataInputStream in) throws IOException; }
    private static PayloadCodec codec(String type, Writer writer, Reader reader) {
        return new PayloadCodec() {
            public String type() { return type; }
            public byte[] encode(FrontierPayload p) {
                try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { writer.write(out, p); } return bytes.toByteArray(); }
                catch (IOException failure) { throw new IllegalStateException("inventory supply encode failed", failure); }
            }
            public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    var value = reader.read(in); if (in.available() != 0) throw new IllegalArgumentException("trailing inventory supply bytes"); return value;
                } catch (IOException failure) { throw new IllegalArgumentException("truncated inventory supply payload", failure); }
            }
        };
    }
}
