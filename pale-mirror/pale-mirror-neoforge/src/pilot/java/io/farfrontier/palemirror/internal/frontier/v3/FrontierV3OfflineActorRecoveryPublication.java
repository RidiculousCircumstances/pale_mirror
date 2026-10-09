package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import net.minecraft.nbt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;

/** Offline-only publication. Caller MUST hold the scanned world's session lock throughout. */
final class FrontierV3OfflineActorRecoveryPublication {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private FrontierV3OfflineActorRecoveryPublication() { }
    enum Boundary { RECEIPT_DURABLE, CUSTODY_DURABLE, CANONICAL_DURABLE }
    @FunctionalInterface interface BoundaryObserver { void reached(Boundary boundary) throws IOException; }

    /**
     * The immutable receipt is also the original SavedData backup and replay plan.
     * Publish custody first: a crash may fence the old PREPARED lease, never release
     * it without durable custody. Retry requires identical saved-entity input hashes.
     * No time advancement, direct canonical file editing, or fallback replacement.
     */
    static void publish(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierV3OfflineActorAbsence.Proof absence, SubjectId actor,
                        Path ledgerFile, Path receiptFile) throws IOException {
        publish(runtime, absence, actor, ledgerFile, receiptFile, boundary -> { });
    }

    static void publish(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierV3OfflineActorAbsence.Proof absence, SubjectId actor,
                        Path ledgerFile, Path receiptFile, BoundaryObserver observer) throws IOException {
        checkedPath(absence.world(), ledgerFile);
        checkedPath(absence.world(), receiptFile);
        if (ledgerFile.equals(receiptFile)) throw new IOException("receipt cannot replace the carrier ledger");
        var codec = new FrontierWorldStateCodec();
        var checkpoint = runtime.checkpointImage().orElseThrow();
        CompoundTag receipt;
        FrontierV3OfflineActorRecoveryPlan plan;
        if (Files.exists(receiptFile, LinkOption.NOFOLLOW_LINKS)) {
            receipt = decode(read(receiptFile));
            if (receipt.getInt("format") != 1 || !receipt.getString("worldPath").equals(absence.world().toString())
                    || !receipt.getString("worldId").equals(checkpoint.worldId().value())
                    || !receipt.getString("actor").equals(actor.value())
                    || !receipt.hasUUID("uuid") || !receipt.getUUID("uuid").equals(absence.actorUuid())
                    || !receipt.getString("ledgerPath").equals(absence.world().relativize(ledgerFile).toString())
                    || !proofTag(absence).equals(receipt.get("absence")))
                throw new IOException("offline recovery receipt does not match the locked world inputs");
            require(receipt, "beforeState", Tag.TAG_BYTE_ARRAY);
            require(receipt, "beforeLedger", Tag.TAG_BYTE_ARRAY);
            require(receipt, "afterLedger", Tag.TAG_BYTE_ARRAY);
            require(receipt, "revision", Tag.TAG_LONG);
            require(receipt, "instant", Tag.TAG_LONG);
            var before = codec.decode(receipt.getByteArray("beforeState"));
            plan = FrontierV3OfflineActorRecoveryPlan.create(before, actor, absence,
                    ledger(decode(receipt.getByteArray("beforeLedger"))));
        } else {
            byte[] original = readLedgerImage(ledgerFile);
            var root = decode(original);
            var carriers = ledger(root);
            var before = runtime.decodedState().orElseThrow();
            plan = FrontierV3OfflineActorRecoveryPlan.create(before, actor, absence, carriers);
            var carrier = plan.recoveryCarrier();
            if (!carriers.fence(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision()))
                throw new IOException("offline recovery carrier rejected");
            var afterRoot = root.copy();
            afterRoot.put("data", carriers.save(new CompoundTag(), null));
            receipt = new CompoundTag();
            receipt.putInt("format", 1);
            receipt.putString("worldPath", absence.world().toString());
            receipt.putString("worldId", checkpoint.worldId().value());
            receipt.putString("actor", actor.value()); receipt.putUUID("uuid", absence.actorUuid());
            receipt.putString("ledgerPath", absence.world().relativize(ledgerFile).toString());
            receipt.put("absence", proofTag(absence));
            receipt.putLong("revision", checkpoint.revision().value());
            receipt.putLong("instant", checkpoint.instant().ticks());
            receipt.putByteArray("beforeState", codec.encode(before));
            receipt.putByteArray("beforeLedger", original);
            receipt.putByteArray("afterLedger", encode(afterRoot));
            atomicWrite(receiptFile, encode(receipt));
        }
        observer.reached(Boundary.RECEIPT_DURABLE);
        var current = runtime.decodedState().orElseThrow();
        long delta = current.equals(plan.before()) ? 0 : current.equals(plan.draining()) ? 1
                : current.equals(plan.closed()) ? 2 : -1;
        if (delta < 0 || checkpoint.revision().value() != Math.addExact(receipt.getLong("revision"), delta)
                || checkpoint.instant().ticks() != receipt.getLong("instant"))
            throw new IOException("offline recovery canonical head advanced or diverged");
        byte[] existing = readLedgerImage(ledgerFile), original = receipt.getByteArray("beforeLedger"), after = receipt.getByteArray("afterLedger");
        // Validate the receipt's proposed data independently before publishing it.
        var nextLedger = ledger(decode(after));
        var carrier = plan.recoveryCarrier();
        var expectedRoot = decode(original);
        var expectedLedger = ledger(expectedRoot);
        if (!expectedLedger.fence(carrier.identity(), carrier.physicalRevision(), carrier.ambientRevision())
                || !expectedLedger.save(new CompoundTag(), null).equals(nextLedger.save(new CompoundTag(), null)))
            throw new IOException("offline recovery receipt changes unrelated custody");
        expectedRoot.put("data", expectedLedger.save(new CompoundTag(), null));
        if (!expectedRoot.equals(decode(after))) throw new IOException("offline recovery receipt changes SavedData metadata");
        if (Arrays.equals(existing, original)) expectedLedger.save(ledgerFile.toFile(), null);
        else if (!Arrays.equals(existing, after)) throw new IOException("offline carrier ledger changed since recovery preparation");
        observer.reached(Boundary.CUSTODY_DURABLE);
        plan.apply(runtime, nextLedger);
        // Registered commands retain their ordinary durable WAL. Do not checkpoint
        // here: that method also compacts WAL and would remove original evidence.
        if (!runtime.decodedState().orElseThrow().equals(plan.closed()))
            throw new IOException("offline recovery failed to retain the closed state");
        observer.reached(Boundary.CANONICAL_DURABLE);
    }

    static FrontierV3AmbientCarrierLedger ledger(CompoundTag root) throws IOException {
        require(root, "data", Tag.TAG_COMPOUND);
        return FrontierV3AmbientCarrierLedger.load(root.getCompound("data"), null);
    }
    /** Receipt comparison uses the complete logical image, never a stale checkpoint alone. */
    static byte[] readLedgerImage(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("carrier checkpoint missing");
        var root = new CompoundTag();
        root.put("data", FrontierV3AmbientCarrierLedger.readFile(path, null).save(new CompoundTag(), null));
        NbtUtils.addCurrentDataVersion(root);
        return encode(root);
    }
    static CompoundTag proofTag(FrontierV3OfflineActorAbsence.Proof proof) {
        var tag = new CompoundTag(); tag.putInt("chunks", proof.entityChunks());
        var files = new ListTag();
        for (var file : proof.files()) {
            var entry = new CompoundTag(); entry.putString("path", file.relativePath()); entry.putString("sha256", file.sha256()); files.add(entry);
        }
        tag.put("files", files); return tag;
    }
    private static void require(CompoundTag tag, String name, int type) throws IOException {
        if (!tag.contains(name, type)) throw new IOException("incomplete offline recovery receipt: " + name);
    }
    static void checkedPath(Path world, Path path) throws IOException {
        if (!path.isAbsolute() || !path.normalize().equals(path) || !path.startsWith(world)
                || !path.getParent().toRealPath().equals(path.getParent()) || Files.isSymbolicLink(path))
            throw new IOException("offline recovery path escapes the exact world: " + path);
    }
    static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES)
            throw new IOException("offline recovery file missing or exceeds bound: " + path);
        return Files.readAllBytes(path);
    }
    static CompoundTag decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("offline recovery data exceeds bound");
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_BYTES));
    }
    static byte[] encode(CompoundTag tag) throws IOException {
        var out = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag, out);
        if (out.size() > MAX_BYTES) throw new IOException("offline recovery data exceeds bound");
        return out.toByteArray();
    }
    static void atomicWrite(Path target, byte[] bytes) throws IOException {
        // Unique staging files are retained on failure; never delete an unknown old temp.
        Path staged = Files.createTempFile(target.getParent(), ".offline-recovery-", ".tmp");
        try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (var directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { directory.force(true); }
    }
}
