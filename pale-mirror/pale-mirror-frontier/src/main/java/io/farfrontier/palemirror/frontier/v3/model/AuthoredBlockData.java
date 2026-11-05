package io.farfrontier.palemirror.frontier.v3.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** Explicit visual payload only. Native adapters validate a closed field schema before applying it. */
public record AuthoredBlockData(Kind kind, int dataVersion, String snbt) {
    public enum Kind {
        MATERIAL_TEXTURES(1), SIGN_TEXT(2), BANNER_PATTERNS(3);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Kind decode(int tag) {
            for (var kind : values()) if (kind.tag == tag) return kind;
            throw new IllegalArgumentException("unknown authored visual data kind");
        }
    }
    public AuthoredBlockData {
        Objects.requireNonNull(kind); Objects.requireNonNull(snbt);
        if (dataVersion < 1 || dataVersion > 100000 || snbt.isBlank() || snbt.getBytes(StandardCharsets.UTF_8).length > 16384 || !snbt.startsWith("{") || !snbt.endsWith("}"))
            throw new IllegalArgumentException("invalid bounded authored visual data");
    }
    public String encoded() { return Base64.getUrlEncoder().withoutPadding().encodeToString(snbt.getBytes(StandardCharsets.UTF_8)); }
    public static AuthoredBlockData decode(int kind, int version, String data) {
        if (data.length() > 32768) throw new IllegalArgumentException("unbounded visual payload");
        var result = new AuthoredBlockData(Kind.decode(kind), version, new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8));
        if (!result.encoded().equals(data)) throw new IllegalArgumentException("noncanonical visual payload encoding");
        return result;
    }
}
