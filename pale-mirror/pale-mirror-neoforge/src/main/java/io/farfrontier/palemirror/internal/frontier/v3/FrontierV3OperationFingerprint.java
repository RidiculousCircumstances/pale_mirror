package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Stable typed identity of a prepared native witness. Compound insertion order is
 * not semantic identity; list order and numeric types are. This is not a serializer. */
final class FrontierV3OperationFingerprint {
    private FrontierV3OperationFingerprint() { }
    static String of(CompoundTag prepared) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                write(prepared, output);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void write(Tag value, DataOutputStream output) throws IOException {
        output.writeByte(value.getId());
        if (value instanceof CompoundTag compound) {
            var keys = new TreeSet<>(compound.getAllKeys()); output.writeInt(keys.size());
            for (var key : keys) { output.writeUTF(key); write(Objects.requireNonNull(compound.get(key)), output); }
        } else if (value instanceof ListTag list) {
            output.writeInt(list.size());
            for (var item : list) write(item, output);
        } else value.write(output);
    }
}
