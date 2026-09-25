package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Offline maintenance only. Never packaged in the mod or usable against a locked live world. */
final class FrontierV3OfflineActorAbsence {
    private FrontierV3OfflineActorAbsence() { }
    record FileProof(String relativePath, String sha256) { }
    record Proof(Path world, UUID actorUuid, int entityChunks, List<FileProof> files) {
        Proof { files = List.copyOf(files); }
    }
    @FunctionalInterface interface LockedAction<T> { T apply(Proof proof) throws IOException; }

    /** The callback, including any later repair, runs while the Minecraft session lock is held. */
    static <T> T withProof(Path requestedWorld, UUID actorUuid, LockedAction<T> action) throws IOException {
        Path world = requestedWorld.toRealPath();
        Path lockPath = world.resolve("session.lock");
        if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(world.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("offline recovery requires an existing Minecraft world and session lock");
        try (var channel = FileChannel.open(lockPath, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("world is running or locked");
            List<Path> inventory;
            try (var paths = Files.walk(world)) {
                inventory = paths.limit(100_001).sorted().toList();
            }
            if (inventory.size() > 100_000) throw new IOException("offline world inventory exceeds bound");
            var proofs = new ArrayList<FileProof>(); int chunks = 0, entityRegions = 0;
            for (Path path : inventory) {
                if (Files.isSymbolicLink(path)) throw new IOException("offline world contains a symbolic link: " + path);
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString();
                boolean levelData = path.getParent().equals(world)
                        && (name.equals("level.dat") || name.equals("level.dat_old"));
                boolean playerData = path.getParent().getFileName().toString().equals("playerdata");
                if (levelData || playerData) {
                    if (playerData && !name.matches("[0-9a-fA-F-]{36}\\.dat(?:_old)?"))
                        throw new IOException("unsupported player-data member: " + path);
                    byte[] bytes = readBounded(path);
                    var root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(64L * 1024 * 1024));
                    if (contains(root, actorUuid)) throw new IOException("actor UUID still exists in saved player/world NBT: " + actorUuid);
                    proofs.add(new FileProof(world.relativize(path).toString(), hash(bytes)));
                    continue;
                }
                if (!path.getParent().getFileName().toString().equals("entities")) continue;
                if (!path.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca"))
                    throw new IOException("unread entity storage member: " + path);
                byte[] bytes = readBounded(path);
                chunks += scan(bytes, actorUuid);
                entityRegions++;
                proofs.add(new FileProof(world.relativize(path).toString(), hash(bytes)));
            }
            if (entityRegions == 0) throw new IOException("no entity storage inventory; absence unproven");
            return action.apply(new Proof(world, actorUuid, chunks, proofs));
        }
    }

    private static byte[] readBounded(Path path) throws IOException {
        if (Files.size(path) > 64L * 1024 * 1024) throw new IOException("offline NBT member exceeds read bound: " + path);
        return Files.readAllBytes(path);
    }

    static int scan(byte[] bytes, UUID target) throws IOException {
        if (bytes.length < 8192) throw new IOException("truncated region header");
        var buffer = ByteBuffer.wrap(bytes); int chunks = 0;
        var sectors = new BitSet(); sectors.set(0, 2);
        for (int slot = 0; slot < 1024; slot++) {
            int header = buffer.getInt(slot * 4), sector = header >>> 8, count = header & 255;
            if (header == 0) continue;
            long offset = (long) sector * 4096;
            if (sector < 2 || count == 0 || offset + 5 > bytes.length || sectors.nextSetBit(sector) >= 0
                    && sectors.nextSetBit(sector) < sector + count) throw new IOException("invalid or overlapping entity sector");
            sectors.set(sector, sector + count);
            int length = buffer.getInt((int) offset), compression = Byte.toUnsignedInt(bytes[(int) offset + 4]);
            if (length < 1 || length + 4L > count * 4096L || offset + 4L + length > bytes.length)
                throw new IOException("invalid entity record length");
            var raw = new ByteArrayInputStream(bytes, (int) offset + 5, length - 1);
            InputStream decoded = switch (compression) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                case 3 -> raw;
                default -> throw new IOException("unsupported/external entity compression: " + compression);
            };
            try (var input = new DataInputStream(decoded)) {
                var root = NbtIo.read(input, NbtAccounter.create(16L * 1024 * 1024));
                if (!(root.get("Entities") instanceof ListTag entities)
                        || !entities.isEmpty() && entities.getElementType() != Tag.TAG_COMPOUND)
                    throw new IOException("missing or invalid entity inventory");
                if (contains(root, target)) throw new IOException("actor UUID still exists in saved entities: " + target);
                if (input.read() != -1) throw new IOException("trailing data after entity NBT");
                chunks++;
            }
        }
        return chunks;
    }

    private static boolean contains(Tag tag, UUID target) {
        if (tag instanceof CompoundTag compound) {
            if (compound.hasUUID("UUID") && compound.getUUID("UUID").equals(target)) return true;
            for (String key : compound.getAllKeys()) if (contains(compound.get(key), target)) return true;
        } else if (tag instanceof ListTag list) for (Tag child : list) if (contains(child, target)) return true;
        return false;
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
