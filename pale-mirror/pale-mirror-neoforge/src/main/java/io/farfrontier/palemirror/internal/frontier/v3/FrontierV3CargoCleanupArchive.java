package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Immutable physical witnesses synced before cleanup; never cargo custody or spawn authority. */
final class FrontierV3CargoCleanupArchive {
    private static final int FORMAT = 1, MAX_BYTES = 1_048_576;
    private final Path directory;
    private final WorldId world;

    FrontierV3CargoCleanupArchive(Path directory, WorldId world) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.world = Objects.requireNonNull(world);
    }

    static FrontierV3CargoCleanupArchive at(ServerLevel level, WorldId world) {
        String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(world.value().getBytes(StandardCharsets.UTF_8));
        return new FrontierV3CargoCleanupArchive(level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("pale-mirror-cargo-cleanup").resolve(suffix), world);
    }

    Optional<FrontierV3CargoDeparture> observation(UUID entity) throws IOException {
        return read(target(entity), entity);
    }

    private Optional<FrontierV3CargoDeparture> read(Path file, UUID entity) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) {
            throw new IOException("invalid cargo cleanup witness file");
        }
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            CompoundTag tag = NbtIo.read(input, NbtAccounter.create(MAX_BYTES));
            if (input.read() != -1 || !tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT
                    || !tag.getString("world").equals(world.value()) || !tag.contains("observation", Tag.TAG_COMPOUND)) {
                throw new IOException("incompatible cargo cleanup witness");
            }
            var receipt = FrontierV3CargoDeparture.load(tag.getCompound("observation"));
            if (!receipt.entityId().equals(entity)) throw new IOException("cargo cleanup witness identity mismatch");
            return Optional.of(receipt);
        } catch (RuntimeException invalid) {
            throw new IOException("invalid cargo cleanup witness", invalid);
        }
    }

    /** Returning successfully means both complete file contents and directory entry are synced. */
    void retain(FrontierV3CargoDeparture receipt) throws IOException {
        publish(receipt, false);
    }

    /** The still-current DRAINING owner may refresh preparation after a rejected release. */
    void prepareRelease(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state,
                        io.farfrontier.palemirror.frontier.v3.model.SceneLease lease,
                        FrontierV3CargoDeparture receipt) throws IOException {
        if (!world.equals(state.bootstrap().worldId()) || !world.equals(lease.worldId())
                || !lease.equals(state.sceneLeases().get(lease.id()))
                || lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.DRAINING
                || state.fencedRecovery().cargoRetirements().pending().containsKey(receipt.entityId())
                || !FrontierV3CargoCarrierExecutor.id(lease).equals(receipt.entityId())
                || !lease.id().equals(receipt.leaseId()) || lease.revision() != receipt.sceneRevision()
                || !io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors.logistics(lease).cargoId().equals(receipt.cargoId())
                || FrontierV3CargoCarrierAuthority.currentEpoch(state.fencedRecovery(), lease).orElse(-1L) != receipt.authorityEpoch()) {
            throw new IOException("cargo cleanup preparation lacks exact current draining authority");
        }
        publish(receipt, true);
    }

    private void publish(FrontierV3CargoDeparture receipt, boolean preparation) throws IOException {
        if (preparation && read(saved(receipt.entityId()), receipt.entityId()).isPresent()) {
            throw new IOException("saved terminal witness cannot become live preparation");
        }
        var previous = observation(receipt.entityId());
        if (previous.isPresent()) {
            if (previous.get().equals(receipt)) { sync(directory); return; }
            var prior = previous.get();
            if (!preparation || !prior.leaseId().equals(receipt.leaseId()) || !prior.cargoId().equals(receipt.cargoId())
                    || prior.sceneRevision() != receipt.sceneRevision() || prior.authorityEpoch() > receipt.authorityEpoch()
                    || read(saved(receipt.entityId()), receipt.entityId()).isPresent()) {
                throw new IOException("contradictory cargo cleanup witness");
            }
        }
        createDurableDirectory(directory);
        if (previous.isEmpty()) try (var entries = Files.list(directory)) {
            if (entries.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .limit(CargoProjectionRetirements.MAX_PENDING).count() >= CargoProjectionRetirements.MAX_PENDING) {
                throw new IOException("cargo cleanup witness capacity exhausted");
            }
        }
        var tag = new CompoundTag();
        tag.putInt("format", FORMAT); tag.putString("world", world.value()); tag.put("observation", receipt.save());
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        if (bytes.size() > MAX_BYTES) throw new IOException("cargo cleanup witness exceeds size limit");
        Path staging = directory.resolve(receipt.entityId() + ".pending");
        if (Files.isSymbolicLink(staging)) throw new IOException("cargo cleanup staging is a symbolic link");
        try {
            try (var channel = FileChannel.open(staging, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (previous.isPresent()) Files.move(staging, target(receipt.entityId()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.createLink(target(receipt.entityId()), staging);
            sync(directory);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    private Path target(UUID entity) { return directory.resolve(Objects.requireNonNull(entity) + ".nbt"); }

    private Path saved(UUID entity) { return directory.resolve(Objects.requireNonNull(entity) + ".saved"); }

    /** Called only after the actual entity save + sync, before canonical acknowledgement. */
    void markSaved(FrontierV3CargoDeparture expected) throws IOException {
        var retained = observation(expected.entityId()).orElseThrow(() -> new IOException("missing cleanup witness"));
        if (!retained.equals(expected)) throw new IOException("cleanup witness changed before save confirmation");
        var previous = read(saved(expected.entityId()), expected.entityId());
        if (previous.isPresent()) {
            if (!previous.get().equals(expected)) throw new IOException("contradictory saved cleanup witness");
        } else {
            Files.createLink(saved(expected.entityId()), target(expected.entityId()));
        }
        sync(directory);
    }

    /** Bounded startup inventory; markers alone never authorize another physical mutation. */
    List<FrontierV3CargoDeparture> savedWitnesses() throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        var result = new ArrayList<FrontierV3CargoDeparture>();
        try (var paths = Files.newDirectoryStream(directory, "*.saved")) {
            for (Path path : paths) {
                if (result.size() >= CargoProjectionRetirements.MAX_PENDING) throw new IOException("saved cleanup capacity exceeded");
                String name = path.getFileName().toString();
                UUID id;
                try { id = UUID.fromString(name.substring(0, name.length() - ".saved".length())); }
                catch (IllegalArgumentException invalid) { throw new IOException("invalid saved cleanup identity", invalid); }
                if (!path.equals(saved(id))) throw new IOException("noncanonical saved cleanup identity");
                result.add(read(path, id).orElseThrow(() -> new IOException("saved cleanup witness disappeared")));
            }
        }
        return List.copyOf(result);
    }

    /** Only after exact canonical CargoCleanupSaved has completed its durable WAL commit. */
    void forgetAcknowledged(FrontierV3CargoDeparture expected) throws IOException {
        var retained = observation(expected.entityId());
        var confirmation = read(saved(expected.entityId()), expected.entityId());
        if (retained.isPresent() && !retained.get().equals(expected)
                || confirmation.isPresent() && !confirmation.get().equals(expected)) {
            throw new IOException("cargo cleanup witness changed before compaction");
        }
        if (retained.isEmpty() && confirmation.isEmpty()) return;
        Files.deleteIfExists(target(expected.entityId()));
        // Persist unlink before dropping the recoverable marker.
        sync(directory);
        Files.deleteIfExists(saved(expected.entityId()));
        sync(directory);
    }

    static void createDurableDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("cargo cleanup directory is not a directory");
            // A prior attempt may have created this directory but failed while syncing its parent.
            if (path.getParent() != null) sync(path.getParent());
            return;
        }
        createDurableDirectory(path.getParent());
        Files.createDirectory(path);
        sync(path.getParent());
    }

    static void sync(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
}
