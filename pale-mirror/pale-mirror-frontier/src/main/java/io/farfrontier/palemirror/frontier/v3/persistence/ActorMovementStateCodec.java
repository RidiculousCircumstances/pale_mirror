package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWireTags;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
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

    static void write(DataOutputStream output, Map<SubjectId, ActorMovement> movements) throws IOException {
        FrontierWorldStateCodec.writeCount(output, movements.size());
        for (ActorMovement movement : movements.values().stream()
                .sorted(Comparator.comparing(value -> value.order().actorId())).toList()) {
            MovementOrder order = movement.order();
            FrontierWorldStateCodec.writeString(output, order.ownerId().value());
            FrontierWorldStateCodec.writeString(output, order.actorId().value());
            output.writeLong(order.goalOrdinal());
            output.writeLong(order.goalRevision());
            output.writeByte(FrontierWireTags.tag(order.capability()));
            output.writeByte(FrontierWireTags.tag(order.arrivalPolicy()));
            FrontierWorldStateCodec.writeCount(output, order.legalStations().size());
            for (SurfaceAnchor station : order.legalStations())
                FrontierWorldStateCodec.writePosition(output, station.support());
            output.writeLong(movement.issuedAtTick());
            ActorExecutionStateCodec.writeId(output, movement.executionId());
            switch (movement.context()) {
                case ActorMovementContext.ServiceExit exit -> {
                    output.writeByte(0); // stable ServiceExit provider tag, never inferred from an ID
                    FrontierWorldStateCodec.writeString(output, exit.settlementId().value());
                    FrontierWorldStateCodec.writeString(output, exit.depotId().value());
                }
                case ActorMovementContext.ShipmentLeg leg -> {
                    output.writeByte(1); // stable declared Shipment provider, never an ID-prefix classifier
                    FrontierWorldStateCodec.writeString(output, leg.shipmentId().value());
                    output.writeLong(leg.shipmentRevision());
                }
                case ActorMovementContext.GroupLeg leg -> {
                    output.writeByte(2); FrontierWorldStateCodec.writeString(output, leg.groupId().value()); output.writeLong(leg.groupRevision());
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
            SubjectId owner = new SubjectId(FrontierWorldStateCodec.readString(input));
            SubjectId actor = new SubjectId(FrontierWorldStateCodec.readString(input));
            long ordinal = input.readLong(), revision = input.readLong();
            TraversalCapability capability = FrontierWireTags.require(TraversalCapability.class, input.readUnsignedByte());
            MovementOrder.ArrivalPolicy policy = FrontierWireTags.require(MovementOrder.ArrivalPolicy.class, input.readUnsignedByte());
            int stationsCount = FrontierWorldStateCodec.readCount(input);
            if (stationsCount < 1 || stationsCount > 8) throw new IllegalArgumentException("actor movement station count is invalid");
            List<SurfaceAnchor> stations = new ArrayList<>(stationsCount);
            for (int station = 0; station < stationsCount; station++)
                stations.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input)));
            MovementOrder order = new MovementOrder(owner, actor, ordinal, revision, stations, capability, policy);
            long issuedAt = input.readLong();
            var executionId = ActorExecutionStateCodec.readId(input);
            ActorMovementContext context = switch (input.readUnsignedByte()) {
                case 0 -> new ActorMovementContext.ServiceExit(
                        new SubjectId(FrontierWorldStateCodec.readString(input)),
                        new SubjectId(FrontierWorldStateCodec.readString(input)));
                case 1 -> new ActorMovementContext.ShipmentLeg(
                        new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
                case 2 -> new ActorMovementContext.GroupLeg(new SubjectId(FrontierWorldStateCodec.readString(input)), input.readLong());
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
