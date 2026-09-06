package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

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
            if (version != FrontierWorldStateCodec.VERSION) throw new IllegalArgumentException("Frontier v3 header requires a fresh current-schema world");
            // The state codec prefixes every current-schema aggregate with the closed
            // Process/Scene descriptor inventory.  Header selection happens before complete
            // hydration, but it must consume and validate that exact field too: otherwise the
            // fingerprint is misread as a WorldId and restart deterministically quarantines a
            // perfectly valid snapshot.
            if (!FrontierDurationProcessDriverRegistry.inventoryFingerprint().equals(readString(input))) {
                throw new IllegalArgumentException("Frontier v3 header has an incompatible process/scene descriptor inventory");
            }
            WorldId worldId = new WorldId(readString(input));
            long seed = input.readLong();
            FrontierRuleset ruleset = FrontierRulesets.require(readString(input), input.readInt(), readString(input));
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
