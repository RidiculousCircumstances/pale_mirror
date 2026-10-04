package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodec;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.List;

final class ActorBodyPayloadCodecs {
    private ActorBodyPayloadCodecs() { }
    static PayloadCodecs create() { return new PayloadCodecs(List.of(new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_released"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var released = (ActorBodyReleased) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, released.body().actorId());
                out.writeLong(released.body().physicalEpoch());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorBodyReleased(
                    new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong())));
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_unloaded"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var unloaded = (ActorBodyUnloaded) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, unloaded.body().actorId()); out.writeLong(unloaded.body().physicalEpoch());
                FrontierWorldPayloadCodecs.writeBody(out, unloaded.expectedBody()); out.writeLong(unloaded.expectedHealth().raw());
                FrontierWorldPayloadCodecs.writeBody(out, unloaded.observedBody()); out.writeLong(unloaded.observedHealth().raw());
                out.writeBoolean(unloaded.expectedExecution().isPresent());
                if (unloaded.expectedExecution().isPresent()) ActorExecutionStateCodec.writeId(out, unloaded.expectedExecution().orElseThrow());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var body = new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong());
                var expected = FrontierWorldPayloadCodecs.readBody(in); var health = new FixedScalar(in.readLong());
                var observed = FrontierWorldPayloadCodecs.readBody(in); var observedHealth = new FixedScalar(in.readLong());
                var execution = in.readBoolean() ? java.util.Optional.of(ActorExecutionStateCodec.readId(in))
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
                return new ActorBodyUnloaded(body, expected, health, observed, observedHealth, execution);
            });
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_present"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var present = (ActorBodyPresent) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, present.body().actorId()); out.writeLong(present.body().physicalEpoch());
                FrontierWorldPayloadCodecs.writeBody(out, present.expectedBody()); out.writeLong(present.expectedHealth().raw());
                FrontierWorldPayloadCodecs.writeBody(out, present.observedBody()); out.writeLong(present.observedHealth().raw());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> new ActorBodyPresent(
                    new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong()),
                    FrontierWorldPayloadCodecs.readBody(in), new FixedScalar(in.readLong()),
                    FrontierWorldPayloadCodecs.readBody(in), new FixedScalar(in.readLong())));
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_inspected"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var inspected = (ActorBodyInspected) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, inspected.body().actorId()); out.writeLong(inspected.body().physicalEpoch());
                out.writeByte(inspected.source().wireTag());
                FrontierWorldPayloadCodecs.writeBody(out, inspected.expectedBody()); out.writeLong(inspected.expectedHealth().raw());
                FrontierWorldPayloadCodecs.writeBody(out, inspected.observedBody()); out.writeLong(inspected.observedHealth().raw());
                out.writeBoolean(inspected.expectedExecution().isPresent());
                if (inspected.expectedExecution().isPresent()) ActorExecutionStateCodec.writeId(out, inspected.expectedExecution().orElseThrow());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var body = new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong());
                var source = ActorBodyInspected.Source.fromWireTag(in.readUnsignedByte());
                var expected = FrontierWorldPayloadCodecs.readBody(in); var health = new FixedScalar(in.readLong());
                var observed = FrontierWorldPayloadCodecs.readBody(in); var observedHealth = new FixedScalar(in.readLong());
                var execution = in.readBoolean() ? java.util.Optional.of(ActorExecutionStateCodec.readId(in))
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
                return new ActorBodyInspected(body, source, expected, health, observed, observedHealth, execution);
            });
        }
    }, new PayloadCodec() {
        @Override public String type() { return "frontier.actor_body_died"; }
        @Override public byte[] encode(FrontierPayload payload) {
            var died = (ActorBodyDied) payload;
            return FrontierWorldPayloadCodecs.encodeProduction(out -> {
                FrontierWorldPayloadCodecs.writeSubject(out, died.body().actorId()); out.writeLong(died.body().physicalEpoch());
                FrontierWorldPayloadCodecs.writeBody(out, died.expectedBody()); out.writeLong(died.expectedHealth().raw());
                out.writeBoolean(died.observedBody().isPresent());
                if (died.observedBody().isPresent()) FrontierWorldPayloadCodecs.writeBody(out, died.observedBody().orElseThrow());
                out.writeBoolean(died.expectedExecution().isPresent());
                if (died.expectedExecution().isPresent()) ActorExecutionStateCodec.writeId(out, died.expectedExecution().orElseThrow());
                FrontierWorldPayloadCodecs.writeString(out, died.cause());
            });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            return FrontierWorldPayloadCodecs.decodeProduction(bytes, in -> {
                var body = new ActorBodyId(FrontierWorldPayloadCodecs.readSubject(in).value(), in.readLong());
                var expected = FrontierWorldPayloadCodecs.readBody(in); var health = new FixedScalar(in.readLong());
                var observed = in.readBoolean() ? java.util.Optional.of(FrontierWorldPayloadCodecs.readBody(in))
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.BodyPosition>empty();
                var execution = in.readBoolean() ? java.util.Optional.of(ActorExecutionStateCodec.readId(in))
                        : java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty();
                return new ActorBodyDied(body, expected, health, observed, execution, FrontierWorldPayloadCodecs.readString(in));
            });
        }
    })); }
}
