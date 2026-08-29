package io.farfrontier.palemirror.internal.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded noncanonical contextual object card, sent only to a negotiated PM client. */
public record PlayerContextCardPayload(String title, List<String> lines, int durationTicks, int accentRgb)
        implements CustomPacketPayload {
    private static final int MAX_TITLE = 72;
    private static final int MAX_LINES = 2;
    private static final int MAX_LINE = 112;
    public static final Type<PlayerContextCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            PaleMirrorMod.MOD_ID, "player_context_card"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerContextCardPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.title, MAX_TITLE);
                buffer.writeVarInt(payload.lines.size());
                payload.lines.forEach(line -> buffer.writeUtf(line, MAX_LINE));
                buffer.writeVarInt(payload.durationTicks); buffer.writeInt(payload.accentRgb);
            }, buffer -> {
                String title = buffer.readUtf(MAX_TITLE);
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("context-card line count is invalid");
                List<String> lines = new ArrayList<>(count);
                for (int index = 0; index < count; index++) lines.add(buffer.readUtf(MAX_LINE));
                return new PlayerContextCardPayload(title, lines, buffer.readVarInt(), buffer.readInt());
            });

    public PlayerContextCardPayload {
        title = text(title, MAX_TITLE, "context-card title");
        lines = List.copyOf(Objects.requireNonNull(lines, "context-card lines"));
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("context-card may contain at most " + MAX_LINES + " detail lines");
        lines = lines.stream().map(value -> text(value, MAX_LINE, "context-card line")).toList();
        if (durationTicks < 20 || durationTicks > 200) throw new IllegalArgumentException("context-card duration must be 20..200 ticks");
    }

    private static String text(String value, int maximum, String label) {
        String result = Objects.requireNonNull(value, label);
        if (result.isBlank() || result.length() > maximum || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(label + " must be one line of 1.." + maximum + " characters");
        }
        return result;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
