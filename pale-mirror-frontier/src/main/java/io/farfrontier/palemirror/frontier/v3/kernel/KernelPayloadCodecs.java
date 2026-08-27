package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Built-in payload codecs required by the v3 kernel itself. Domain payloads are supplied separately. */
public final class KernelPayloadCodecs {
    private KernelPayloadCodecs() {}

    public static PayloadCodecs scheduleEffects() {
        return new PayloadCodecs(List.of(new Created(), new Cancelled(), new Rescheduled(), new Consumed()));
    }

    private abstract static class EffectCodec implements PayloadCodec {
        final byte[] encodeAction(ScheduledAction action) {
            return KernelCodec.encodeScheduledAction(action);
        }

        final ScheduledAction decodeAction(byte[] bytes) {
            return KernelCodec.decodeScheduledAction(bytes);
        }

        final byte[] bytes(Writer writer) {
            try {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(result);
                writer.write(output);
                output.flush();
                return result.toByteArray();
            } catch (IOException error) { throw new IllegalStateException("kernel payload encoding failed", error); }
        }

        final DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
        @FunctionalInterface interface Writer { void write(DataOutputStream output) throws IOException; }
    }

    private static final class Created extends EffectCodec {
        @Override public String type() { return "kernel.schedule_created"; }
        @Override public byte[] encode(FrontierPayload payload) {
            return encodeAction(((ScheduleEffect.Created) payload).action());
        }
        @Override public FrontierPayload decode(byte[] bytes) { return new ScheduleEffect.Created(decodeAction(bytes)); }
    }

    private static final class Cancelled extends EffectCodec {
        @Override public String type() { return "kernel.schedule_cancelled"; }
        @Override public byte[] encode(FrontierPayload payload) { return ((ScheduleEffect.Cancelled) payload).scheduleId().value().getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public FrontierPayload decode(byte[] bytes) { return new ScheduleEffect.Cancelled(new ScheduleId(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))); }
    }

    private static final class Consumed extends EffectCodec {
        @Override public String type() { return "kernel.schedule_consumed"; }
        @Override public byte[] encode(FrontierPayload payload) { return ((ScheduleEffect.Consumed) payload).scheduleId().value().getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public FrontierPayload decode(byte[] bytes) { return new ScheduleEffect.Consumed(new ScheduleId(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))); }
    }

    private static final class Rescheduled extends EffectCodec {
        @Override public String type() { return "kernel.schedule_rescheduled"; }
        @Override public byte[] encode(FrontierPayload payload) {
            ScheduleEffect.Rescheduled effect = (ScheduleEffect.Rescheduled) payload;
            return bytes(output -> { output.writeUTF(effect.scheduleId().value()); byte[] action = encodeAction(effect.replacement()); output.writeInt(action.length); output.write(action); });
        }
        @Override public FrontierPayload decode(byte[] bytes) {
            try {
                DataInputStream input = input(bytes);
                ScheduleId id = new ScheduleId(input.readUTF());
                int length = input.readInt();
                if (length < 0 || length > 1_048_576) throw new IllegalArgumentException("invalid replacement length");
                byte[] action = input.readNBytes(length);
                if (action.length != length || input.available() != 0) throw new IllegalArgumentException("truncated replacement action");
                return new ScheduleEffect.Rescheduled(id, decodeAction(action));
            } catch (IOException error) { throw new IllegalArgumentException("truncated rescheduled payload", error); }
        }
    }
}
