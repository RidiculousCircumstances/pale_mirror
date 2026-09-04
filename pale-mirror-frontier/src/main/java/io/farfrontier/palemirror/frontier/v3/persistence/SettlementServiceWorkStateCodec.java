package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceTarget;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWork;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkKind;
import io.farfrontier.palemirror.frontier.v3.model.SettlementServiceWorkPhase;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable snapshot representation of bounded resident-owned settlement service work. */
final class SettlementServiceWorkStateCodec {
    private SettlementServiceWorkStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, SettlementServiceWork> values) throws IOException {
        FrontierWorldStateCodec.writeCount(output, values.size());
        for (SettlementServiceWork work : values.values().stream().sorted(java.util.Comparator.comparing(SettlementServiceWork::id)).toList()) {
            writeOne(output, work);
        }
    }

    static Map<SubjectId, SettlementServiceWork> read(DataInputStream input) throws IOException {
        Map<SubjectId, SettlementServiceWork> values = new LinkedHashMap<>();
        for (int index = 0, count = FrontierWorldStateCodec.readCount(input); index < count; index++) {
            SettlementServiceWork work = readOne(input);
            if (values.put(work.id(), work) != null) throw new IllegalArgumentException("duplicate settlement service-work id");
        }
        return Map.copyOf(values);
    }

    static void writeOne(DataOutputStream output, SettlementServiceWork work) throws IOException {
        FrontierWorldStateCodec.writeString(output, work.id().value());
        output.writeByte(work.kind().wireTag());
        FrontierWorldStateCodec.writeString(output, work.settlementId().value());
        FrontierWorldStateCodec.writeString(output, work.workerId().value());
        FrontierWorldStateCodec.writeString(output, work.facilityId().value());
        FrontierWorldStateCodec.writeString(output, work.inputSource().containerId().value());
        output.writeByte(work.inputSource().slot());
        FrontierWorldStateCodec.writePosition(output, work.inputStation().support());
        FrontierWorldStateCodec.writePosition(output, work.workStation().support());
        FrontierWorldStateCodec.writeString(output, work.inputItemId().value());
        writeTarget(output, work.target());
        FrontierWorldStateCodec.writeString(output, work.inputIssueIntentId().value());
        FrontierWorldStateCodec.writeString(output, work.endpointIntentId().value());
        TraversalTopologyStateCodec.write(output, work.inputTraversal());
        output.writeShort(work.inputTraversalCursor());
        TraversalTopologyStateCodec.write(output, work.workTraversal());
        output.writeShort(work.workTraversalCursor());
        output.writeByte(work.phase().wireTag());
        output.writeByte(work.completedWorkTicks());
    }

    static SettlementServiceWork readOne(DataInputStream input) throws IOException {
        SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
        SettlementServiceWorkKind kind = SettlementServiceWorkKind.fromWireTag(input.readUnsignedByte());
        SubjectId settlement = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId worker = new SubjectId(FrontierWorldStateCodec.readString(input));
        SubjectId facility = new SubjectId(FrontierWorldStateCodec.readString(input));
        io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot source = new io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot(
                new SubjectId(FrontierWorldStateCodec.readString(input)), input.readUnsignedByte());
        SurfaceAnchor inputStation = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input));
        SurfaceAnchor workStation = new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input));
        SubjectId item = new SubjectId(FrontierWorldStateCodec.readString(input));
        SettlementServiceTarget target = readTarget(input);
        PhysicalIntentId inputIssueIntent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        PhysicalIntentId endpointIntent = new PhysicalIntentId(FrontierWorldStateCodec.readString(input));
        TraversalTopology inputTraversal = TraversalTopologyStateCodec.read(input);
        int inputCursor = input.readUnsignedShort();
        TraversalTopology workTraversal = TraversalTopologyStateCodec.read(input);
        int workCursor = input.readUnsignedShort();
        SettlementServiceWorkPhase phase = SettlementServiceWorkPhase.fromWireTag(input.readUnsignedByte());
        return new SettlementServiceWork(id, kind, settlement, worker, facility, source, inputStation, workStation,
                item, target, inputIssueIntent, endpointIntent, inputTraversal, inputCursor, workTraversal, workCursor, phase, input.readUnsignedByte());
    }

    private static void writeTarget(DataOutputStream output, SettlementServiceTarget target) throws IOException {
        if (target instanceof SettlementServiceTarget.StructureCell structure) {
            output.writeByte(1); FrontierWorldStateCodec.writeString(output, structure.structureId().value());
            FrontierWorldStateCodec.writePosition(output, structure.position());
            return;
        }
        SettlementServiceTarget.Infection infection = (SettlementServiceTarget.Infection) target;
        output.writeByte(2); output.writeInt(infection.cell().x()); output.writeInt(infection.cell().z());
    }

    private static SettlementServiceTarget readTarget(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
            case 1 -> new SettlementServiceTarget.StructureCell(new SubjectId(FrontierWorldStateCodec.readString(input)), FrontierWorldStateCodec.readPosition(input));
            case 2 -> new SettlementServiceTarget.Infection(new InfectionCell(input.readInt(), input.readInt()));
            default -> throw new IllegalArgumentException("unknown settlement service-work target tag");
        };
    }
}
