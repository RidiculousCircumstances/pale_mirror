package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringRecoveryTeam;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceStatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Stable snapshot state for bounded exact in-place route repair. */
final class RouteMaintenanceStateCodec {
    private RouteMaintenanceStateCodec() { }

    static void write(DataOutputStream output, Map<SubjectId, RouteMaintenance> maintenances) throws IOException {
        if (maintenances.size() > RouteMaintenanceStateSupport.MAX_MAINTENANCE) {
            throw new IllegalArgumentException("route maintenance count is out of bounds");
        }
        output.writeByte(maintenances.size());
        for (RouteMaintenance maintenance : maintenances.values().stream().sorted(java.util.Comparator.comparing(RouteMaintenance::id)).toList()) {
            FrontierWorldStateCodec.writeString(output, maintenance.id().value());
            FrontierWorldStateCodec.writeString(output, maintenance.settlementId().value());
            FrontierWorldStateCodec.writePosition(output, maintenance.repairCell());
            output.writeByte(maintenance.semanticPart().wireTag());
            output.writeByte(maintenance.status().wireTag());
            output.writeBoolean(maintenance.cargoId().isPresent());
            if (maintenance.cargoId().isPresent()) FrontierWorldStateCodec.writeString(output, maintenance.cargoId().orElseThrow().value());
            RouteConstructionStateCodec.writeTeam(output, maintenance.team());
            output.writeBoolean(maintenance.assembly().isPresent());
            if (maintenance.assembly().isPresent()) RouteConstructionStateCodec.writeAssembly(output, maintenance.assembly().orElseThrow());
        }
    }

    static Map<SubjectId, RouteMaintenance> read(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count > RouteMaintenanceStateSupport.MAX_MAINTENANCE) throw new IllegalArgumentException("route maintenance count is out of bounds");
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            SubjectId id = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId settlementId = new SubjectId(FrontierWorldStateCodec.readString(input));
            BlockPosition repairCell = FrontierWorldStateCodec.readPosition(input);
            GrayboxSemanticPart part = FrontierWireTags.require(GrayboxSemanticPart.class, input.readUnsignedByte());
            RouteMaintenanceStatus status = FrontierWireTags.require(RouteMaintenanceStatus.class, input.readUnsignedByte());
            Optional<SubjectId> cargo = input.readBoolean() ? Optional.of(new SubjectId(FrontierWorldStateCodec.readString(input))) : Optional.empty();
            EngineeringRecoveryTeam team = RouteConstructionStateCodec.readTeam(input);
            Optional<EngineeringWorkAssembly> assembly = input.readBoolean() ? Optional.of(RouteConstructionStateCodec.readAssembly(input)) : Optional.empty();
            RouteMaintenance maintenance = new RouteMaintenance(id, settlementId, repairCell, part, status, cargo, team, assembly);
            if (maintenances.put(id, maintenance) != null) throw new IllegalArgumentException("duplicate route maintenance id");
        }
        return Map.copyOf(maintenances);
    }
}
