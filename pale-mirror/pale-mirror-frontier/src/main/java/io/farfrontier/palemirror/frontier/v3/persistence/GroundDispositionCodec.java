package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** Explicit ground outcome; legacy UUID-only carrier bytes are not this schema. */
final class GroundDispositionCodec {
    private GroundDispositionCodec() { }
    static void write(DataOutputStream out, Optional<ResourceCustody.Ground> ground) throws IOException {
        out.writeByte(2); out.writeBoolean(ground.isPresent());
        if (ground.isEmpty()) return;
        var value = ground.orElseThrow();
        out.writeLong(value.entityId().getMostSignificantBits()); out.writeLong(value.entityId().getLeastSignificantBits());
        FrontierWorldStateCodec.writePosition(out, value.position());
    }
    static Optional<ResourceCustody.Ground> read(DataInputStream in) throws IOException {
        if (in.readUnsignedByte() != 2) throw new IllegalArgumentException("unsupported ground disposition format");
        return in.readBoolean() ? Optional.of(new ResourceCustody.Ground(new UUID(in.readLong(), in.readLong()),
                FrontierWorldStateCodec.readPosition(in))) : Optional.empty();
    }
}
