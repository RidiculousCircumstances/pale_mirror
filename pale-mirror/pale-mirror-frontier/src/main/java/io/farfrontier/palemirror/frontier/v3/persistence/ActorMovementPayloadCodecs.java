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

import java.util.List;
import java.util.Optional;

/** Stable wire payloads for the shared actor movement owner. */
final class ActorMovementPayloadCodecs {
    private ActorMovementPayloadCodecs() { }

    static PayloadCodecs create() { return new PayloadCodecs(List.of(coldAdvanced(), hotObserved(), interrupted(), started())); }

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
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var actor = FrontierWorldPayloadCodecs.readSubject(in).value();
                long revision = in.readLong(); long atTick = in.readLong();
                Optional<SurfaceAnchor> arrived = in.readBoolean()
                        ? Optional.of(new SurfaceAnchor(FrontierWorldPayloadCodecs.readPosition(in)))
                        : Optional.empty();
                return new ActorMovementColdAdvanced(actor, revision, atTick, arrived, ActorExecutionStateCodec.readId(in));
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
