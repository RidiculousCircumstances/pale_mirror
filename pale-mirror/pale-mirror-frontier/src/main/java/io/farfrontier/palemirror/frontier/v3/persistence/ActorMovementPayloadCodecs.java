package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementHotObserved;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementInterrupted;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementStarted;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdRequested;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdRouteStarted;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementPositionSnapshot;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorPositionView;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import java.util.List;
import java.util.Optional;

/** Stable wire payloads for the shared actor movement owner. */
final class ActorMovementPayloadCodecs {
    private ActorMovementPayloadCodecs() { }

    static PayloadCodecs create() { return new PayloadCodecs(List.of(coldAdvanced(), coldRequested(), coldRouteStarted(), hotObserved(), interrupted(), started())); }

    private static void writePositions(java.io.DataOutputStream out, MovementPositionSnapshot value) throws java.io.IOException {
        out.writeLong(value.atTick()); out.writeInt(value.hotPoints().size());
        for (var point : value.hotPoints()) {
            FrontierWorldPayloadCodecs.writeSubject(out, point.body().actorId()); out.writeLong(point.body().physicalEpoch());
            out.writeDouble(point.point().x()); out.writeDouble(point.point().y()); out.writeDouble(point.point().z());
        }
    }
    private static MovementPositionSnapshot readPositions(java.io.DataInputStream in) throws java.io.IOException {
        long at = in.readLong(); int count = in.readInt();
        if (count < 0 || count > MovementPositionSnapshot.MAX_POINTS) throw new IllegalArgumentException("unbounded movement positions");
        var points = new java.util.ArrayList<MovementPositionSnapshot.HotPoint>();
        for (int index = 0; index < count; index++) points.add(new MovementPositionSnapshot.HotPoint(
                new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong()),
                new ActorPositionView.TravelPoint(in.readDouble(), in.readDouble(), in.readDouble())));
        return new MovementPositionSnapshot(at, points);
    }
    private static PayloadCodec coldRequested() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_cold_requested"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var value = (ActorMovementColdRequested) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorExecutionStateCodec.writeId(out, value.execution()); out.writeLong(value.goalRevision()); writePositions(out, value.positions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorMovementColdRequested(
                    ActorExecutionStateCodec.readId(in), in.readLong(), readPositions(in)));
        }
    }; }
    private static PayloadCodec coldRouteStarted() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_cold_route_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var value = (ActorMovementColdRouteStarted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                byte[] advance = coldAdvanced().encode(value.advance());
                out.writeInt(advance.length); out.write(advance); writePositions(out, value.positions());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                int length = in.readInt();
                if (length < 0 || length > 262_144) throw new IllegalArgumentException("unbounded observed movement route");
                byte[] advance = in.readNBytes(length);
                if (advance.length != length) throw new IllegalArgumentException("truncated observed movement route");
                return new ActorMovementColdRouteStarted((ActorMovementColdAdvanced) coldAdvanced().decode(advance), readPositions(in));
            });
        }
    }; }

    private static PayloadCodec started() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_started"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ActorMovementStarted value = (ActorMovementStarted) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> ActorMovementStateCodec.write(out,
                    java.util.Map.of(value.movement().order().actorId(), value.movement())));
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var values = ActorMovementStateCodec.read(in);
                if (values.size() != 1) throw new IllegalArgumentException("movement start needs exactly one declared order");
                return new ActorMovementStarted(values.values().iterator().next());
            });
        }
    }; }

    private static PayloadCodec interrupted() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_interrupted"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorMovementInterrupted value = (ActorMovementInterrupted) payload;
                FrontierWorldPayloadCodecs.writeSubject(out, value.actorId());
                out.writeLong(value.goalRevision()); out.writeLong(value.atTick());
                FrontierWorldPayloadCodecs.writePosition(out, value.retainedBody().supportingSurface().support());
                ActorExecutionStateCodec.writeId(out, value.executionId());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorMovementInterrupted(
                    FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(), in.readLong(),
                    BodyPosition.above(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(in))), ActorExecutionStateCodec.readId(in)));
        }
    }; }

    private static PayloadCodec coldAdvanced() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_cold_advanced"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorMovementColdAdvanced value = (ActorMovementColdAdvanced) payload;
                FrontierWorldPayloadCodecs.writeSubject(out, value.actorId());
                out.writeLong(value.goalRevision()); out.writeLong(value.atTick());
                out.writeBoolean(value.arrivedSurface().isPresent());
                if (value.arrivedSurface().isPresent()) FrontierWorldPayloadCodecs.writePosition(out,
                        value.arrivedSurface().orElseThrow().support());
                ActorExecutionStateCodec.writeId(out, value.executionId());
                PedestrianRouteReceiptCodec.write(out, value.plannedRoute());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
                long revision = in.readLong(); long atTick = in.readLong();
                Optional<SurfaceAnchor> arrived = in.readBoolean()
                        ? Optional.of(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(in)))
                        : Optional.empty();
                var execution = ActorExecutionStateCodec.readId(in);
                return new ActorMovementColdAdvanced(actor, revision, atTick, arrived, execution, PedestrianRouteReceiptCodec.read(in));
            });
        }
    }; }

    private static PayloadCodec hotObserved() { return new PayloadCodec() {
        @Override public String type() { return "frontier.actor_movement_hot_observed"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                ActorMovementHotObserved value = (ActorMovementHotObserved) payload;
                FrontierWorldPayloadCodecs.writeSubject(out, value.actorId());
                out.writeLong(value.goalRevision()); out.writeLong(value.ambientRevision());
                FrontierWorldPayloadCodecs.writePosition(out, value.observedBody().supportingSurface().support());
                ActorHotObservationCodec.write(out, value.observation());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorMovementHotObserved(
                    FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong(), in.readLong(),
                    BodyPosition.above(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(in))), ActorHotObservationCodec.read(in)));
        }
    }; }
}
