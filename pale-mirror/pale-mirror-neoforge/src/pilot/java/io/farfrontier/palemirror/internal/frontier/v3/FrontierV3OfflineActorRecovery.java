package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.runtime.*;
import net.minecraft.nbt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;

/** Explicit offline maintenance entry, available only on the pilot classpath. */
public final class FrontierV3OfflineActorRecovery {
    private FrontierV3OfflineActorRecovery() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 6 || !(args[0].equals("inspect") || args[0].equals("apply")))
            throw new IllegalArgumentException("inspect|apply WORLD_PATH EXPECTED_SEED EXPECTED_INITIAL_HEAD ACTOR EXPECTED_LEASE_REVISION");
        System.out.println(recover(Path.of(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]),
                new SubjectId(args[4]), Long.parseLong(args[5]), args[0].equals("apply")));
    }

    static String recover(Path world, long expectedSeed, long expectedHead, SubjectId actor,
                          long expectedLease, boolean apply) throws IOException {
        if (expectedHead < 0 || expectedLease < 1) throw new IllegalArgumentException("invalid expected recovery revision");
        var worldId = FrontierV3PhysicalWorld.WORLD_ID;
        var uuid = SceneLease.deterministicEntityId(worldId, actor);
        return FrontierV3OfflineActorAbsence.withProof(world, uuid, proof -> {
            var level = NbtIo.readCompressed(proof.world().resolve("level.dat"), NbtAccounter.create(64L * 1024 * 1024));
            var settings = level.getCompound("Data").getCompound("WorldGenSettings");
            if (!settings.contains("seed", Tag.TAG_LONG) || settings.getLong("seed") != expectedSeed)
                throw new IOException("Minecraft world seed does not match the recovery target");
            var store = new FrontierFileStore(proof.world(), FrontierWorldRuntimeDefinition.payloadCodecs());
            var image = store.recover(worldId);
            if (image.checkpoint().isEmpty()) throw new IOException("offline recovery requires an existing canonical snapshot");
            var configuration = FrontierWorldRecoveryConfiguration.select(worldId, expectedSeed,
                    image.checkpoint().map(value -> value.checkpoint()));
            var runtime = FrontierV3ServerRuntime.startRecovered(configuration, store, image, Integer.MAX_VALUE);
            var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IOException("canonical world recovery failed"));
            var state = runtime.decodedState().orElseThrow();
            var lease = state.ambientLeases().get(actor);
            if (lease == null || lease.revision() != expectedLease)
                throw new IOException("actor lease generation changed");
            String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8));
            var dimension = FrontierV3PhysicalWorld.DIMENSION.location();
            Path data = proof.world().resolve("dimensions").resolve(dimension.getNamespace()).resolve(dimension.getPath()).resolve("data");
            Path ledger = data.resolve("pale_mirror_frontier_v3_ambient_carriers_" + suffix + ".dat");
            Path receipt = data.resolve("offline-actor-recovery-" + uuid + "-" + expectedLease + ".dat");
            if (Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) {
                var saved = NbtIo.readCompressed(receipt, NbtAccounter.create(64L * 1024 * 1024));
                if (!saved.contains("revision", Tag.TAG_LONG) || saved.getLong("revision") != expectedHead)
                    throw new IOException("requested recovery head differs from the retained receipt");
            } else {
                if (checkpoint.revision().value() != expectedHead) throw new IOException("canonical head changed before recovery");
                var root = NbtIo.readCompressed(ledger, NbtAccounter.create(64L * 1024 * 1024));
                if (!root.contains("data", Tag.TAG_COMPOUND)) throw new IOException("missing carrier SavedData");
                FrontierV3OfflineActorRecoveryPlan.create(state, actor, proof,
                        FrontierV3AmbientCarrierLedger.load(root.getCompound("data"), null));
            }
            if (apply) FrontierV3OfflineActorRecoveryPublication.publish(runtime, proof, actor, ledger, receipt);
            // Do not call shutdown: it checkpoints/compacts the original WAL. No thread,
            // server or open store handle was started; submitted commands are already durable.
            return (apply ? "applied" : "inspected (no writes)") + " actor=" + actor.value()
                    + " head=" + runtime.checkpointImage().orElseThrow().revision().value()
                    + " lease=" + runtime.decodedState().orElseThrow().ambientLeases().get(actor).status()
                    + " scannedChunks=" + proof.entityChunks();
        });
    }
}
