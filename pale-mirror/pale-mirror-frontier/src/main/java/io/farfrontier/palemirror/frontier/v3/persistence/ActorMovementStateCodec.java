package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.ServicePointId;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema-owned durable actor orders; no pathfinder or live entity state is serialized. */
final class ActorMovementStateCodec {
    private ActorMovementStateCodec() { }
    private static void writePoint(DataOutputStream output, ServicePointId point) throws IOException {
        output.writeByte(point.kind().wireTag());
        FrontierWorldStateCodec.writeString(output, point.settlementId().value());
        FrontierWorldStateCodec.writeString(output, point.containerId().value());
    }
    private static ServicePointId readPoint(DataInputStream input) throws IOException {
        return new ServicePointId(ServicePointId.Kind.fromWireTag(input.readUnsignedByte()),
                new SubjectId(FrontierWorldStateCodec.readString(input)), new SubjectId(FrontierWorldStateCodec.readString(input)));
    }

    static void write(DataOutputStream output, Map<SubjectId, ActorMovement> movements) throws IOException {
        FrontierWorldStateCodec.writeCount(output, movements.size());
        for (ActorMovement movement : movements.values().stream()
                .sorted(Comparator.comparing(value -> value.order().actorId())).toList()) {
            MovementOrder order = movement.order();
            MovementOrderCodec.write(output, order);
            output.writeLong(movement.issuedAtTick());
            ActorExecutionStateCodec.writeId(output, movement.executionId());
            switch (movement.context()) {
                case ActorMovementContext.ForestryLeg leg -> {
                    output.writeByte(9); FrontierWorldStateCodec.writeString(output, leg.jobId().value()); output.writeLong(leg.jobRevision());
                }
                case ActorMovementContext.DefenderEquipmentReturn leg -> {
                    output.writeByte(8); FrontierWorldStateCodec.writeString(output, leg.assaultId().value());
                    FrontierWorldStateCodec.writeString(output, leg.transferId().value());
                }
                case ActorMovementContext.ExtractionLeg leg -> {
                    output.writeByte(7); FrontierWorldStateCodec.writeString(output, leg.jobId().value());
                    output.writeLong(leg.jobRevision());
                }
                case ActorMovementContext.ResourceAccessExit exit -> {
                    output.writeByte(6);
                    writePoint(output, exit.point());
                    FrontierWorldStateCodec.writeString(output, exit.executionOwnerId().value());
                }
                case ActorMovementContext.ServiceExit exit -> {
                    output.writeByte(0); // stable ServiceExit provider tag, never inferred from an ID
                    writePoint(output, exit.point());
                }
                case ActorMovementContext.ShipmentLeg leg -> {
                    output.writeByte(1); // stable declared Shipment provider, never an ID-prefix classifier
                    FrontierWorldStateCodec.writeString(output, leg.shipmentId().value());
                    output.writeLong(leg.shipmentRevision());
                }
                case ActorMovementContext.GroupLeg leg -> {
                    output.writeByte(2); FrontierWorldStateCodec.writeString(output, leg.groupId().value()); output.writeLong(leg.groupRevision());
                }
                case ActorMovementContext.ExpeditionSupply supply -> {
                    output.writeByte(3); FrontierWorldStateCodec.writeString(output, supply.missionId().value());
                    FrontierWorldStateCodec.writeString(output, supply.claimId().value());
                }
                case ActorMovementContext.ExpeditionAssembly assembly -> {
                    output.writeByte(4); FrontierWorldStateCodec.writeString(output, assembly.missionId().value());
                }
                case ActorMovementContext.ExpeditionReplenishment refill -> {
                    output.writeByte(5); FrontierWorldStateCodec.writeString(output, refill.missionId().value());
                }
            }
            output.writeBoolean(movement.coldTravel().isPresent());
            if (movement.coldTravel().isPresent()) {
                TimedKnownRoute travel = movement.coldTravel().orElseThrow();
                output.writeLong(travel.departedAtTick());
                output.writeLong(travel.ticksPerEdge());
                output.writeLong(travel.authorityEpoch());
                FrontierWorldStateCodec.writeCount(output, travel.route().size());
                for (SurfaceAnchor surface : travel.route())
                    FrontierWorldStateCodec.writePosition(output, surface.support());
            }
        }
    }

    static Map<SubjectId, ActorMovement> read(DataInputStream input) throws IOException {
        int count = FrontierWorldStateCodec.readCount(input);
        if (count > 4_096) throw new IllegalArgumentException("actor movement count exceeds retention bound");
        Map<SubjectId, ActorMovement> movements = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            MovementOrder order = MovementOrderCodec.read(input);
            SubjectId actor = order.actorId();
            long issuedAt = input.readLong();
            var executionId = ActorExecutionStateCodec.readId(input);
            ActorMovementContext context = switch (input.readUnsignedByte()) {
                case 9 -> new ActorMovementContext.ForestryLeg(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
                case 8 -> new ActorMovementContext.DefenderEquipmentReturn(new SubjectId(FrontierWorldStateCodec.readString(input)),
                        new io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId(FrontierWorldStateCodec.readString(input)));
                case 7 -> new ActorMovementContext.ExtractionLeg(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
                case 0 -> new ActorMovementContext.ServiceExit(readPoint(input));
                case 1 -> new ActorMovementContext.ShipmentLeg(
                        new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
                case 2 -> new ActorMovementContext.GroupLeg(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
                case 3 -> new ActorMovementContext.ExpeditionSupply(new SubjectId(FrontierWorldStateCodec.readString(input)),
                        new SubjectId(FrontierWorldStateCodec.readString(input)));
                case 4 -> new ActorMovementContext.ExpeditionAssembly(new SubjectId(FrontierWorldStateCodec.readString(input)));
                case 5 -> new ActorMovementContext.ExpeditionReplenishment(new SubjectId(FrontierWorldStateCodec.readString(input)));
                case 6 -> new ActorMovementContext.ResourceAccessExit(readPoint(input), new SubjectId(FrontierWorldStateCodec.readString(input)));
                default -> throw new IllegalArgumentException("unknown actor movement provider tag");
            };
            ActorMovement movement = new ActorMovement(order, issuedAt, context, executionId);
            if (input.readBoolean()) {
                long departedAt = input.readLong(), ticksPerEdge = input.readLong(), epoch = input.readLong();
                int length = FrontierWorldStateCodec.readCount(input);
                if (length < 1 || length > TimedKnownRoute.MAX_SURFACES)
                    throw new IllegalArgumentException("actor movement route length is invalid");
                List<SurfaceAnchor> route = new ArrayList<>(length);
                for (int surface = 0; surface < length; surface++)
                    route.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input)));
                movement = movement.withColdTravel(new TimedKnownRoute(
                        ActorMovement.segmentOrder(order, route.getLast()), route, departedAt, ticksPerEdge, epoch));
            }
            if (movements.putIfAbsent(actor, movement) != null)
                throw new IllegalArgumentException("duplicate actor movement identity");
        }
        return Map.copyOf(movements);
    }
}
