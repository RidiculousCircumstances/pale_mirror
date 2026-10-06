package io.farfrontier.palemirror.frontier.v3.persistence;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Snapshot and event grammar share the same explicit transport declarations. */
final class ShipmentStateCodec {
    private ShipmentStateCodec() { }
    static void write(DataOutputStream out, ShipmentState state) throws IOException {
        out.writeInt(state.shipments().size());
        for (Shipment value : state.shipments().values().stream().sorted(Comparator.comparing(Shipment::id)).toList()) writeShipment(out, value);
    }
    static ShipmentState read(DataInputStream in) throws IOException {
        int n = in.readInt(); if (n < 0 || n > ShipmentState.MAX_SHIPMENTS) throw new IllegalArgumentException("invalid shipment count");
        var result = new LinkedHashMap<SubjectId, Shipment>();
        for (int i = 0; i < n; i++) { Shipment value = readShipment(in); if (result.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("duplicate shipment ID"); }
        return new ShipmentState(result);
    }
    static void writeShipment(DataOutputStream out, Shipment value) throws IOException {
        id(out, value.id());
        switch (value.authorization().kind()) { case GOODS_CONTRACT_SHIPMENT -> out.writeByte(1); }
        id(out, value.authorization().claimId()); id(out, value.authorization().claimantId()); id(out, value.authorization().executorId());
        out.writeLong(value.authorization().authorizationRevision()); ActorExecutionStateCodec.writeId(out, value.execution());
        endpoint(out, value.sender()); endpoint(out, value.receiver()); id(out, value.sourceAccountId()); id(out, value.carriedAccountId()); id(out, value.receivingAccountId());
        out.writeUTF(value.itemKind()); out.writeInt(value.lotQuantities().size());
        for (var e : value.lotQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { id(out, e.getKey()); out.writeInt(e.getValue()); }
        status(out, value.status()); out.writeLong(value.revision());
        out.writeBoolean(value.pendingPhysicalStep().isPresent());
        if (value.pendingPhysicalStep().isPresent()) ShipmentPhysicalStepCodec.write(out, value.pendingPhysicalStep().orElseThrow());
        out.writeBoolean(value.reception().isPresent());
        if (value.reception().isPresent()) { id(out, value.reception().orElseThrow().id()); id(out, value.reception().orElseThrow().claimId()); lots(out, value.reception().orElseThrow().lotQuantities()); }
    }
    static Shipment readShipment(DataInputStream in) throws IOException {
        SubjectId shipment = id(in);
        var kind = switch (in.readUnsignedByte()) { case 1 -> ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT; default -> throw new IllegalArgumentException("unknown delegation provider tag"); };
        var grant = new ResourceClaimDelegation(kind, id(in), id(in), id(in), in.readLong());
        var execution = ActorExecutionStateCodec.readId(in); ShipmentEndpoint sender = endpoint(in), receiver = endpoint(in);
        SubjectId source = id(in), carried = id(in), receiving = id(in); String item = in.readUTF();
        int n = in.readInt(); if (n < 0 || n > 64) throw new IllegalArgumentException("invalid shipment lot count");
        var lots = new LinkedHashMap<SubjectId, Integer>();
        for (int i = 0; i < n; i++) if (lots.putIfAbsent(id(in), in.readInt()) != null) throw new IllegalArgumentException("duplicate shipment lot");
        var status = status(in); long revision = in.readLong();
        var pending = in.readBoolean() ? Optional.of(ShipmentPhysicalStepCodec.read(in)) : Optional.<ShipmentPhysicalStep>empty();
        var reception = in.readBoolean() ? Optional.of(new ShipmentReception(id(in), id(in), lots(in))) : Optional.<ShipmentReception>empty();
        return new Shipment(shipment, grant, execution, sender, receiver, source, carried, receiving, item, lots, status, revision, pending, reception);
    }
    static void lots(DataOutputStream out, Map<SubjectId, Integer> lots) throws IOException {
        out.writeInt(lots.size());
        for (var e : lots.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { id(out, e.getKey()); out.writeInt(e.getValue()); }
    }
    static Map<SubjectId, Integer> lots(DataInputStream in) throws IOException {
        int n = in.readInt(); if (n < 1 || n > 64) throw new IllegalArgumentException("invalid transported portion lot count");
        var lots = new LinkedHashMap<SubjectId, Integer>();
        for (int i = 0; i < n; i++) if (lots.putIfAbsent(id(in), in.readInt()) != null) throw new IllegalArgumentException("duplicate transported portion lot");
        return lots;
    }
    static void status(DataOutputStream out, Shipment.Status status) throws IOException {
        out.writeByte(switch (status) { case AWAITING_LOAD -> 1; case CARRYING -> 2; case DELIVERED -> 3; case ALLOCATION_WITHDRAWN -> 4; case CARGO_DISPOSED -> 5; });
    }
    static Shipment.Status status(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> Shipment.Status.AWAITING_LOAD; case 2 -> Shipment.Status.CARRYING;
            case 3 -> Shipment.Status.DELIVERED; case 4 -> Shipment.Status.ALLOCATION_WITHDRAWN;
            case 5 -> Shipment.Status.CARGO_DISPOSED;
            default -> throw new IllegalArgumentException("unknown shipment status tag");
        };
    }
    static void endpoint(DataOutputStream out, ShipmentEndpoint endpoint) throws IOException {
        switch (endpoint.kind()) { case SETTLEMENT_DEPOT -> out.writeByte(1); }
        id(out, endpoint.settlementId()); id(out, endpoint.facilityId()); id(out, endpoint.containerId());
        FrontierWorldStateCodec.writePosition(out, endpoint.station().support());
    }
    static ShipmentEndpoint endpoint(DataInputStream in) throws IOException {
        var kind = switch (in.readUnsignedByte()) { case 1 -> ShipmentEndpoint.Kind.SETTLEMENT_DEPOT; default -> throw new IllegalArgumentException("unknown shipment endpoint tag"); };
        return new ShipmentEndpoint(kind, id(in), id(in), id(in), new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)));
    }
    private static void id(DataOutputStream out, SubjectId id) throws IOException { out.writeUTF(id.value()); }
    private static SubjectId id(DataInputStream in) throws IOException { return new SubjectId(in.readUTF()); }
}
