package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.List;

final class ShipmentPayloadCodecs {
    private ShipmentPayloadCodecs() { }
    static PayloadCodecs create() {
        return new PayloadCodecs(List.of(
                codec("frontier.shipment_cargo_disposition_observed", (out, p) -> {
                    var v = (ShipmentCargoDispositionObserved) p;
                    out.writeUTF(v.shipmentId().value()); out.writeLong(v.expectedRevision());
                    out.writeUTF(v.identity().body().actorId().value()); out.writeLong(v.identity().body().physicalEpoch());
                    ActorExecutionStateCodec.writeId(out, v.identity().execution());
                    out.writeByte(switch (v.outcome()) { case WORLD_DROP -> 1; case MISSING_BEFORE_LOOT -> 2; });
                    if (v.worldCarrier().isPresent()) out.writeUTF(v.worldCarrier().orElseThrow().toString());
                }, in -> {
                    var id = new SubjectId(in.readUTF()); long revision = in.readLong();
                    var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
                    var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, ActorExecutionStateCodec.readId(in));
                    var outcome = switch (in.readUnsignedByte()) {
                        case 1 -> ShipmentCargoDispositionObserved.Outcome.WORLD_DROP;
                        case 2 -> ShipmentCargoDispositionObserved.Outcome.MISSING_BEFORE_LOOT;
                        default -> throw new IllegalArgumentException("unknown shipment cargo disposition tag");
                    };
                    return new ShipmentCargoDispositionObserved(id, revision, identity, outcome, outcome == ShipmentCargoDispositionObserved.Outcome.WORLD_DROP
                            ? java.util.Optional.of(java.util.UUID.fromString(in.readUTF())) : java.util.Optional.empty());
                }),
                codec("frontier.shipment_receipt_acknowledged", (out, p) -> {
                    var value = (ShipmentReceiptAcknowledged) p; out.writeUTF(value.shipmentId().value());
                    out.writeLong(value.expectedRevision()); out.writeUTF(value.receiptId().value());
                }, in -> new ShipmentReceiptAcknowledged(new SubjectId(in.readUTF()), in.readLong(), new SubjectId(in.readUTF()))),
                codec("frontier.shipment_dispatch_requested", (out, p) -> {
                    var value = (ShipmentDispatchRequested) p; out.writeUTF(value.senderId().value());
                    out.writeInt(value.senderKind().wireTag()); ShipmentStateCodec.writeShipment(out, value.shipment());
                }, in -> new ShipmentDispatchRequested(new SubjectId(in.readUTF()),
                        FrontierWireTags.require(EconomicOwnerKind.class, in.readInt()), ShipmentStateCodec.readShipment(in))),
                codec("frontier.shipment_hand_custody_observed", (out, p) -> {
                    var value = (ShipmentHandCustodyObserved) p;
                    out.writeUTF(value.shipmentId().value()); out.writeUTF(value.identity().body().actorId().value());
                    out.writeLong(value.identity().body().physicalEpoch()); ActorExecutionStateCodec.writeId(out, value.identity().execution());
                    out.writeByte(switch (value.boundary()) { case MATERIALIZED -> 0; case SAVED_DEPARTURE -> 1; });
                    PhysicalObservationStackCodec.writeStacks(out, List.of(value.hand()));
                }, in -> {
                    var id = new SubjectId(in.readUTF());
                    var body = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(new SubjectId(in.readUTF()), in.readLong());
                    var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(body, ActorExecutionStateCodec.readId(in));
                    var boundary = switch (in.readUnsignedByte()) {
                        case 0 -> ShipmentHandCustodyObserved.Boundary.MATERIALIZED;
                        case 1 -> ShipmentHandCustodyObserved.Boundary.SAVED_DEPARTURE;
                        default -> throw new IllegalArgumentException("unknown shipment hand boundary");
                    };
                    var hands = PhysicalObservationStackCodec.readStacks(in);
                    if (hands.size() != 1) throw new IllegalArgumentException("shipment hand boundary requires one stack");
                    return new ShipmentHandCustodyObserved(id, identity, boundary, hands.getFirst());
                }),
                codec("frontier.shipment_hot_prepared", (out, p) -> {
                    var value = (ShipmentHotPrepared) p; out.writeUTF(value.shipmentId().value()); ShipmentPhysicalStepCodec.write(out, value.step());
                }, in -> new ShipmentHotPrepared(new SubjectId(in.readUTF()), ShipmentPhysicalStepCodec.read(in))),
                codec("frontier.shipment_hot_transferred", (out, p) -> {
                    var value = (ShipmentHotTransferred) p; out.writeUTF(value.shipmentId().value()); ShipmentPhysicalStepCodec.write(out, value.step());
                    PhysicalObservationStackCodec.writeStacks(out, value.remainingSource()); PhysicalObservationStackCodec.writeStacks(out, value.destination());
                }, in -> new ShipmentHotTransferred(new SubjectId(in.readUTF()), ShipmentPhysicalStepCodec.read(in),
                        PhysicalObservationStackCodec.readStacks(in), PhysicalObservationStackCodec.readStacks(in))),
                codec("frontier.shipment_dispatched", (out, p) -> ShipmentStateCodec.writeShipment(out, ((ShipmentDispatched) p).shipment()), in -> new ShipmentDispatched(ShipmentStateCodec.readShipment(in))),
                codec("frontier.shipment_cold_transferred", (out, p) -> {
                    var step = (ShipmentColdTransferred) p; out.writeUTF(step.shipmentId().value()); out.writeLong(step.expectedRevision()); ShipmentStateCodec.status(out, step.expectedStatus());
                }, in -> new ShipmentColdTransferred(new SubjectId(in.readUTF()), in.readLong(), ShipmentStateCodec.status(in))),
                codec("frontier.shipment_retired", (out, p) -> { var step = (ShipmentRetired) p; out.writeUTF(step.shipmentId().value()); out.writeLong(step.expectedRevision()); },
                        in -> new ShipmentRetired(new SubjectId(in.readUTF()), in.readLong()))));
    }
    private interface Writer { void write(DataOutputStream out, FrontierPayload p) throws IOException; }
    private interface Reader { FrontierPayload read(DataInputStream in) throws IOException; }
    private static PayloadCodec codec(String type, Writer writer, Reader reader) {
        return new PayloadCodec() {
            public String type() { return type; }
            public byte[] encode(FrontierPayload p) {
                try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { writer.write(out, p); } return bytes.toByteArray(); }
                catch (IOException e) { throw new IllegalStateException("in-memory shipment encode failed", e); }
            }
            public FrontierPayload decode(byte[] bytes) {
                try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    FrontierPayload p = reader.read(in); if (in.available() != 0) throw new IllegalArgumentException("trailing shipment payload bytes"); return p;
                } catch (IOException e) { throw new IllegalArgumentException("truncated shipment payload", e); }
            }
        };
    }
}
