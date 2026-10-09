package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Offline-only proof-before-rearm publisher. It never edits the canonical WAL. */
final class FrontierV3OfflineFirstAdmissionRecoveryPublication {
    private FrontierV3OfflineFirstAdmissionRecoveryPublication() { }

    enum Boundary { RECEIPT_DURABLE, REARM_DURABLE }
    @FunctionalInterface interface BoundaryObserver { void reached(Boundary boundary) throws IOException; }

    static String publish(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3OfflineActorAbsence.Proof absence, SubjectId actor, Path ledgerFile,
            boolean apply) throws IOException {
        return publish(runtime, absence, actor, ledgerFile, apply, ignored -> { });
    }

    static String publish(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierV3OfflineActorAbsence.Proof absence, SubjectId actor, Path ledgerFile,
            boolean apply, BoundaryObserver observer) throws IOException {
        FrontierV3OfflineActorRecoveryPublication.checkedPath(absence.world(), ledgerFile);
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IOException("canonical checkpoint missing"));
        var state = runtime.decodedState().orElseThrow(() -> new IOException("canonical state missing"));
        if (!checkpoint.worldId().equals(state.bootstrap().worldId())) throw new IOException("canonical world identity changed");
        byte[] existing = FrontierV3OfflineActorRecoveryPublication.readLedgerImage(ledgerFile);
        CompoundTag root = FrontierV3OfflineActorRecoveryPublication.decode(existing);
        var ledger = FrontierV3OfflineActorRecoveryPublication.ledger(root);
        var first = ledger.firstAdmission(actor).orElseThrow(() -> new IOException("first-admission record missing"));

        if (first.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT) {
            String id = first.absenceReceipt().orElseThrow();
            Path receiptFile = receiptPath(ledgerFile, first.identity().entityId(), id);
            FrontierV3OfflineActorRecoveryPublication.checkedPath(absence.world(), receiptFile);
            var receipt = FrontierV3OfflineActorRecoveryPublication.decode(
                    FrontierV3OfflineActorRecoveryPublication.read(receiptFile));
            validateReceipt(receipt, absence, actor, ledgerFile, checkpoint, state, id);
            if (!Arrays.equals(existing, receipt.getByteArray("afterLedger")))
                throw new IOException("re-armed SavedData differs from its durable absence receipt");
            return "already re-armed actor=" + actor.value() + " proof=" + id;
        }

        FrontierV3OfflineFirstAdmissionRecoveryPlan.create(state, actor, absence, ledger);
        CompoundTag receipt = new CompoundTag();
        receipt.putInt("format", 1);
        receipt.putString("worldPath", absence.world().toString());
        receipt.putString("worldId", checkpoint.worldId().value());
        receipt.putString("actor", actor.value());
        receipt.putUUID("uuid", first.identity().entityId());
        receipt.putString("ledgerPath", absence.world().relativize(ledgerFile).toString());
        receipt.putLong("revision", checkpoint.revision().value());
        receipt.putLong("instant", checkpoint.instant().ticks());
        receipt.putLong("leaseRevision", state.ambientLeases().get(actor).revision());
        receipt.putByteArray("beforeState", new FrontierWorldStateCodec().encode(state));
        receipt.put("absence", FrontierV3OfflineActorRecoveryPublication.proofTag(absence));
        receipt.putByteArray("beforeLedger", existing);
        String id = digest(receipt);
        rejectCompetingReceipt(ledgerFile, first.identity().entityId(), existing, id, absence);
        receipt.putString("proofId", id);
        if (!ledger.rearmFirstAdmissionAfterAbsence(first, id))
            throw new IOException("exact first-admission re-arm was rejected");
        var after = root.copy();
        after.put("data", ledger.save(new CompoundTag(), null));
        receipt.putByteArray("afterLedger", FrontierV3OfflineActorRecoveryPublication.encode(after));
        Path receiptFile = receiptPath(ledgerFile, first.identity().entityId(), id);
        FrontierV3OfflineActorRecoveryPublication.checkedPath(absence.world(), receiptFile);
        if (Files.exists(receiptFile, LinkOption.NOFOLLOW_LINKS)) {
            var retained = FrontierV3OfflineActorRecoveryPublication.decode(
                    FrontierV3OfflineActorRecoveryPublication.read(receiptFile));
            validateReceipt(retained, absence, actor, ledgerFile, checkpoint, state, id);
            if (!Arrays.equals(existing, retained.getByteArray("beforeLedger")))
                throw new IOException("existing first-admission receipt has another predecessor");
            receipt = retained;
        }
        if (!apply) {
            FrontierV3OfflineActorRecoveryPublication.encode(receipt);
            return "inspected actor=" + actor.value() + " proof=" + id + " (no writes)";
        }
        if (!Files.exists(receiptFile, LinkOption.NOFOLLOW_LINKS))
            FrontierV3OfflineActorRecoveryPublication.atomicWrite(receiptFile,
                    FrontierV3OfflineActorRecoveryPublication.encode(receipt));
        observer.reached(Boundary.RECEIPT_DURABLE);
        byte[] current = FrontierV3OfflineActorRecoveryPublication.readLedgerImage(ledgerFile);
        if (Arrays.equals(current, existing))
            ledger.save(ledgerFile.toFile(), null);
        else if (!Arrays.equals(current, receipt.getByteArray("afterLedger")))
            throw new IOException("carrier ledger changed after absence receipt publication");
        observer.reached(Boundary.REARM_DURABLE);
        return "re-armed actor=" + actor.value() + " proof=" + id;
    }

    private static Path receiptPath(Path ledgerFile, java.util.UUID actorId, String id) {
        return ledgerFile.getParent().resolve("offline-first-admission-" + actorId + "-" + id + ".dat");
    }

    /** A durable receipt fixes one predecessor and proof, even before SavedData publication. */
    private static void rejectCompetingReceipt(Path ledgerFile, java.util.UUID actorId,
            byte[] currentLedger, String proposedId, FrontierV3OfflineActorAbsence.Proof absence) throws IOException {
        String prefix = "offline-first-admission-" + actorId + "-";
        java.util.List<Path> receipts;
        try (var paths = Files.list(ledgerFile.getParent())) {
            receipts = paths.filter(path -> path.getFileName().toString().startsWith(prefix))
                    .limit(4_097).sorted().toList();
        }
        if (receipts.size() > 4_096) throw new IOException("first-admission recovery receipt inventory exceeds bound");
        for (Path path : receipts) {
            FrontierV3OfflineActorRecoveryPublication.checkedPath(absence.world(), path);
            if (!path.getFileName().toString().matches(java.util.regex.Pattern.quote(prefix) + "[0-9a-f]{64}\\.dat"))
                throw new IOException("invalid first-admission recovery receipt name");
            var retained = FrontierV3OfflineActorRecoveryPublication.decode(
                    FrontierV3OfflineActorRecoveryPublication.read(path));
            if (!retained.contains("beforeLedger", Tag.TAG_BYTE_ARRAY))
                throw new IOException("incomplete first-admission recovery receipt inventory");
            if (Arrays.equals(currentLedger, retained.getByteArray("beforeLedger"))
                    && (!path.equals(receiptPath(ledgerFile, actorId, proposedId))
                        || !FrontierV3OfflineActorRecoveryPublication.proofTag(absence).equals(retained.get("absence"))))
                throw new IOException("first-admission absence proof or canonical head changed after receipt publication");
        }
    }

    private static void validateReceipt(CompoundTag receipt, FrontierV3OfflineActorAbsence.Proof absence,
            SubjectId actor, Path ledgerFile, io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint,
            FrontierWorldState state, String id) throws IOException {
        if (!receipt.contains("format", Tag.TAG_INT) || receipt.getInt("format") != 1
                || !receipt.getString("worldPath").equals(absence.world().toString())
                || !receipt.getString("worldId").equals(checkpoint.worldId().value())
                || !receipt.getString("actor").equals(actor.value())
                || !receipt.hasUUID("uuid") || !receipt.getUUID("uuid").equals(absence.actorUuid())
                || !receipt.getString("ledgerPath").equals(absence.world().relativize(ledgerFile).toString())
                || !receipt.contains("revision", Tag.TAG_LONG) || receipt.getLong("revision") != checkpoint.revision().value()
                || !receipt.contains("instant", Tag.TAG_LONG) || receipt.getLong("instant") != checkpoint.instant().ticks()
                || !receipt.contains("leaseRevision", Tag.TAG_LONG)
                || state.ambientLeases().get(actor) == null
                || receipt.getLong("leaseRevision") != state.ambientLeases().get(actor).revision()
                || !receipt.contains("beforeState", Tag.TAG_BYTE_ARRAY)
                || !FrontierV3OfflineActorRecoveryPublication.proofTag(absence).equals(receipt.get("absence"))
                || !receipt.contains("beforeLedger", Tag.TAG_BYTE_ARRAY)
                || !receipt.contains("afterLedger", Tag.TAG_BYTE_ARRAY)
                || !receipt.getString("proofId").equals(id))
            throw new IOException("first-admission receipt differs from the locked world and canonical head");
        try {
            if (!new FrontierWorldStateCodec().decode(receipt.getByteArray("beforeState")).equals(state))
                throw new IOException("canonical state changed after first-admission absence receipt");
        } catch (IllegalArgumentException invalid) {
            throw new IOException("first-admission receipt contains invalid canonical state", invalid);
        }
        if (!digest(receipt).equals(id)) throw new IOException("first-admission receipt proof ID is invalid");
        var before = FrontierV3OfflineActorRecoveryPublication.ledger(
                FrontierV3OfflineActorRecoveryPublication.decode(receipt.getByteArray("beforeLedger")));
        try { FrontierV3OfflineFirstAdmissionRecoveryPlan.create(state, actor, absence, before); }
        catch (IllegalArgumentException invalid) { throw new IOException("receipt predecessor lost its exact canonical owner", invalid); }
        var expected = before.firstAdmission(actor).orElseThrow(() -> new IOException("receipt predecessor missing"));
        if (expected.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING
                || !before.rearmFirstAdmissionAfterAbsence(expected, id))
            throw new IOException("receipt predecessor cannot be re-armed");
        var expectedRoot = FrontierV3OfflineActorRecoveryPublication.decode(receipt.getByteArray("beforeLedger"));
        expectedRoot.put("data", before.save(new CompoundTag(), null));
        if (!expectedRoot.equals(FrontierV3OfflineActorRecoveryPublication.decode(receipt.getByteArray("afterLedger"))))
            throw new IOException("receipt proposes unrelated carrier-ledger mutation");
    }

    /** Fixed-field encoding: NBT compound iteration/serialization order is not a hash contract. */
    private static String digest(CompoundTag receiptSeed) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            putString(hash, "pm-first-admission-absence-v1");
            putString(hash, receiptSeed.getString("worldPath"));
            putString(hash, receiptSeed.getString("worldId"));
            putString(hash, receiptSeed.getString("actor"));
            putString(hash, receiptSeed.getUUID("uuid").toString());
            putString(hash, receiptSeed.getString("ledgerPath"));
            putLong(hash, receiptSeed.getLong("revision"));
            putLong(hash, receiptSeed.getLong("instant"));
            putLong(hash, receiptSeed.getLong("leaseRevision"));
            putBytes(hash, receiptSeed.getByteArray("beforeLedger"));
            var absence = receiptSeed.getCompound("absence");
            putLong(hash, absence.getInt("chunks"));
            var files = absence.getList("files", Tag.TAG_COMPOUND);
            putLong(hash, files.size());
            for (Tag raw : files) {
                var file = (CompoundTag) raw;
                putString(hash, file.getString("path"));
                putString(hash, file.getString("sha256"));
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void putString(MessageDigest hash, String value) {
        putBytes(hash, value.getBytes(StandardCharsets.UTF_8));
    }
    private static void putBytes(MessageDigest hash, byte[] value) {
        hash.update(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
        hash.update(value);
    }
    private static void putLong(MessageDigest hash, long value) {
        hash.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
