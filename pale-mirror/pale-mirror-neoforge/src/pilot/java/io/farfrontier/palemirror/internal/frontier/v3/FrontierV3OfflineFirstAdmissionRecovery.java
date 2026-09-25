package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.runtime.*;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

/** Stopped-world repair of a first-body crash window; never packaged in the production mod. */
public final class FrontierV3OfflineFirstAdmissionRecovery {
    private FrontierV3OfflineFirstAdmissionRecovery() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 6 || !(args[0].equals("inspect") || args[0].equals("apply")))
            throw new IllegalArgumentException("inspect|apply WORLD_PATH EXPECTED_SEED EXPECTED_HEAD ACTOR EXPECTED_AMBIENT_LEASE_REVISION");
        System.out.println(recover(Path.of(args[1]), Long.parseLong(args[2]), Long.parseLong(args[3]),
                new SubjectId(args[4]), Long.parseLong(args[5]), args[0].equals("apply")));
    }

    static String recover(Path world, long expectedSeed, long expectedHead, SubjectId actor,
                          long expectedLease, boolean apply) throws IOException {
        if (expectedHead < 0L || expectedLease < 1L) throw new IllegalArgumentException("invalid expected recovery revision");
        var worldId = FrontierV3PhysicalWorld.WORLD_ID;
        return FrontierV3OfflineActorAbsence.withProof(world, SceneLease.deterministicEntityId(worldId, actor), proof -> {
            var level = NbtIo.readCompressed(proof.world().resolve("level.dat"), NbtAccounter.create(64L * 1024 * 1024));
            var settings = level.getCompound("Data").getCompound("WorldGenSettings");
            if (!settings.contains("seed", Tag.TAG_LONG) || settings.getLong("seed") != expectedSeed)
                throw new IOException("Minecraft world seed does not match the first-body recovery target");
            var store = new FrontierFileStore(proof.world(), FrontierWorldRuntimeDefinition.payloadCodecs());
            var image = store.recover(worldId);
            if (image.checkpoint().isEmpty()) throw new IOException("first-body recovery requires an existing canonical snapshot");
            var configuration = FrontierWorldRecoveryConfiguration.select(worldId, expectedSeed,
                    image.checkpoint().map(value -> value.checkpoint()));
            var runtime = FrontierV3ServerRuntime.startRecovered(configuration, store, image, Integer.MAX_VALUE);
            var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IOException("canonical world recovery failed"));
            var state = runtime.decodedState().orElseThrow();
            var lease = state.ambientLeases().get(actor);
            if (checkpoint.revision().value() != expectedHead || lease == null || lease.revision() != expectedLease)
                throw new IOException("canonical head or actor ambient lease changed");
            String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8));
            var dimension = FrontierV3PhysicalWorld.DIMENSION.location();
            Path data = proof.world().resolve("dimensions").resolve(dimension.getNamespace()).resolve(dimension.getPath()).resolve("data");
            Path ledger = data.resolve("pale_mirror_frontier_v3_ambient_carriers_" + suffix + ".dat");
            // A stopped-world inspection is read-only. Applying writes only the receipt and
            // one atomic SavedData image while the same global entity scan lock is retained.
            return FrontierV3OfflineFirstAdmissionRecoveryPublication.publish(runtime, proof, actor, ledger, apply)
                    + " head=" + expectedHead + " lease=" + expectedLease + " scannedChunks=" + proof.entityChunks();
        });
    }
}
