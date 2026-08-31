package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic registry; payload kinds are explicit schema dependencies, never reflection. */
public final class PayloadCodecs {
    private final Map<String, PayloadCodec> byType;

    public PayloadCodecs(List<PayloadCodec> codecs) {
        Map<String, PayloadCodec> mutable = new LinkedHashMap<>();
        for (PayloadCodec codec : List.copyOf(codecs)) {
            Objects.requireNonNull(codec, "payload codec");
            if (mutable.putIfAbsent(codec.type(), codec) != null) {
                throw new IllegalArgumentException("duplicate payload codec: " + codec.type());
            }
        }
        byType = Map.copyOf(mutable);
    }

    public static PayloadCodecs merge(PayloadCodecs... registries) {
        Map<String, PayloadCodec> codecs = new LinkedHashMap<>();
        for (PayloadCodecs registry : registries) {
            Objects.requireNonNull(registry, "payload registry");
            for (PayloadCodec codec : registry.byType.values()) {
                if (codecs.putIfAbsent(codec.type(), codec) != null) {
                    throw new IllegalArgumentException("duplicate payload codec: " + codec.type());
                }
            }
        }
        return new PayloadCodecs(List.copyOf(codecs.values()));
    }

    public byte[] encode(FrontierPayload payload) {
        Objects.requireNonNull(payload, "payload");
        PayloadCodec codec = require(payload.type());
        byte[] encoded = Objects.requireNonNull(codec.encode(payload), "encoded payload");
        return encoded.clone();
    }

    public FrontierPayload decode(String type, byte[] encoded) {
        return Objects.requireNonNull(require(type).decode(encoded.clone()), "decoded payload");
    }

    /** Stable type inventory used by the closed process-composition gate. */
    public java.util.Set<String> types() { return byType.keySet(); }

    private PayloadCodec require(String type) {
        PayloadCodec codec = byType.get(Objects.requireNonNull(type, "payload type"));
        if (codec == null) {
            throw new IllegalArgumentException("unknown payload type: " + type);
        }
        return codec;
    }
}
