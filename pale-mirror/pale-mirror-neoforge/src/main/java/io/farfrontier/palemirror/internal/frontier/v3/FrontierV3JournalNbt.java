package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.io.*;
import java.util.Arrays;

/** NBT compounds are unordered typed maps; byte ordering is not record identity. */
final class FrontierV3JournalNbt {
    private FrontierV3JournalNbt() { }
    static byte[] encode(CompoundTag value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(value, output); }
        return bytes.toByteArray();
    }
    static CompoundTag decode(byte[] bytes) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var tag = NbtIo.read(input, NbtAccounter.create(16L * 1024 * 1024));
            if (input.read() != -1) throw new IOException("trailing physical row payload");
            return tag;
        }
    }
    static boolean same(byte[] left, byte[] right) throws IOException {
        return Arrays.equals(left, right) || left != null && right != null && decode(left).equals(decode(right));
    }
}
