package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable installed selector read before a complete saved aggregate is decoded. */
public record FrontierWorldSnapshotHeader(WorldId worldId, long seed, FrontierRuleset ruleset) {
    private static final int MAGIC = 0x4656334D;

    public FrontierWorldSnapshotHeader {
        Objects.requireNonNull(worldId, "snapshot world");
        Objects.requireNonNull(ruleset, "snapshot ruleset");
    }

    public static FrontierWorldSnapshotHeader read(byte[] encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("unknown Frontier v3 state magic");
            int version = input.readUnsignedByte();
            if (version < 41 || version > FrontierWorldStateCodec.VERSION) throw new IllegalArgumentException("unknown Frontier v3 state version");
            WorldId worldId = new WorldId(readString(input));
            long seed = input.readLong();
            FrontierRuleset ruleset = version >= 80 ? FrontierRulesets.require(readString(input), input.readInt(), readString(input))
                    : FrontierRulesets.legacyForSnapshotVersion(version);
            return new FrontierWorldSnapshotHeader(worldId, seed, ruleset);
        } catch (IOException error) {
            throw new IllegalArgumentException("truncated Frontier v3 state header", error);
        }
    }

    private static String readString(DataInputStream input) throws IOException {
        int size = input.readUnsignedShort();
        byte[] bytes = input.readNBytes(size);
        if (bytes.length != size) throw new IOException("truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
