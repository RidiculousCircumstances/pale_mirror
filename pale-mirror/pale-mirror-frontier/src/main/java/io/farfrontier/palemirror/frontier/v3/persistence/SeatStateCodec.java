package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.io.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.persistence.CombatWire.*;

/** Explicit bounded seat identity/geometry/use grammar, independent of clinical beds. */
final class SeatStateCodec {
    static void place(DataOutputStream out, SeatPlace value) throws IOException {
        id(out, value.id().buildingId()); out.writeUTF(value.id().localId()); id(out, value.home());
        out.writeByte(value.kind().wireTag()); out.writeUTF(value.chairBlock());
        FrontierWorldStateCodec.writePosition(out, value.chair()); out.writeBoolean(value.table().isPresent());
        if (value.table().isPresent()) {
            out.writeUTF(value.table().orElseThrow().block());
            FrontierWorldStateCodec.writePosition(out, value.table().orElseThrow().position());
        }
        FrontierWorldStateCodec.writePosition(out, value.approach().support()); FrontierWorldStateCodec.writePosition(out, value.egress().support());
        out.writeUTF(value.facing().name());
    }
    static SeatPlace place(DataInputStream in) throws IOException {
        var id = new SeatPlace.Id(id(in), in.readUTF()); var home = id(in);
        var kind = BuildingSeat.Kind.fromWireTag(in.readUnsignedByte()); var chairBlock = in.readUTF();
        var chair = FrontierWorldStateCodec.readPosition(in);
        var table = in.readBoolean() ? Optional.of(new SeatTable(in.readUTF(), FrontierWorldStateCodec.readPosition(in)))
                : Optional.<SeatTable>empty();
        return new SeatPlace(id, home, kind, chairBlock, chair, table,
                new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)), new SurfaceAnchor(FrontierWorldStateCodec.readPosition(in)),
                FacilityFacing.valueOf(in.readUTF()));
    }
    static void use(DataOutputStream out, SeatUse value) throws IOException {
        ActorExecutionStateCodec.writeId(out, value.execution()); place(out, value.place()); out.writeByte(value.phase().wireTag());
        out.writeLong(value.revision()); out.writeLong(value.changedAt()); out.writeLong(value.carrierEpoch()); out.writeBoolean(value.pendingBody().isPresent());
        if (value.pendingBody().isPresent()) { actuation(out, value.pendingBody().orElseThrow()); out.writeByte(value.pendingAction().orElseThrow().wireTag()); out.writeByte(value.pendingMode().orElseThrow().wireTag()); }
        out.writeBoolean(value.pendingObservation().isPresent());
        if (value.pendingObservation().isPresent()) FrontierWorldPayloadCodecs.writeBody(out, value.pendingObservation().orElseThrow());
    }
    static SeatUse use(DataInputStream in) throws IOException {
        var execution = ActorExecutionStateCodec.readId(in); var place = place(in); var phase = SeatUse.Phase.fromWireTag(in.readUnsignedByte());
        long revision = in.readLong(), tick = in.readLong(), carrierEpoch = in.readLong(); boolean pending = in.readBoolean();
        var body = pending ? Optional.of(actuation(in)) : Optional.<ActorActuationId>empty();
        var action = pending ? Optional.of(SeatUse.Action.fromWireTag(in.readUnsignedByte())) : Optional.<SeatUse.Action>empty();
        var mode = pending ? Optional.of(SeatUse.ExecutionMode.fromWireTag(in.readUnsignedByte())) : Optional.<SeatUse.ExecutionMode>empty();
        var observation = in.readBoolean() ? Optional.of(FrontierWorldPayloadCodecs.readBody(in)) : Optional.<BodyPosition>empty();
        return new SeatUse(execution, place, phase, revision, tick, carrierEpoch, body, action, mode, observation);
    }
    static void write(DataOutputStream out, SeatState value) throws IOException {
        out.writeInt(value.uses().size());
        for (var use : value.uses().values().stream().sorted(Comparator.comparing(v -> v.execution().actorId())).toList()) use(out, use);
    }
    static SeatState read(DataInputStream in) throws IOException {
        var uses = new LinkedHashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, SeatUse>();
        int size = UnitGroupStateCodec.count(in, SimulationCapacity.MAX_RESIDENTS);
        for (int i = 0; i < size; i++) {
            var use = use(in);
            if (uses.putIfAbsent(use.execution().actorId(), use) != null) throw new IllegalArgumentException("duplicate seat actor");
        }
        return new SeatState(uses);
    }
    private SeatStateCodec() { }
}
