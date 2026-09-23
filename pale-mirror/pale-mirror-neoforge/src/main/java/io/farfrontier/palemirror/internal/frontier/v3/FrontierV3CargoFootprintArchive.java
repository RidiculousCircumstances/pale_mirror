package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.CargoProjectionRetirements;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread durable monotone evidence. It owns no entity, item or canonical state. */
final class FrontierV3CargoFootprintArchive {
    private static final int MAX_BYTES = 1_048_576;
    private final Path directory;
    private final WorldId world;

    FrontierV3CargoFootprintArchive(Path directory, WorldId world) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.world = Objects.requireNonNull(world);
    }

    static FrontierV3CargoFootprintArchive at(ServerLevel level, WorldId world) {
        String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(world.value().getBytes(StandardCharsets.UTF_8));
        return new FrontierV3CargoFootprintArchive(level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("pale-mirror-cargo-footprints").resolve(suffix), world);
    }

    Optional<FrontierV3CargoRetirementFootprint> read(UUID entity, long epoch) throws IOException {
        Path file = target(entity, epoch);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        var value = readFile(file);
        if (!entity.equals(value.entity()) || epoch != value.authorityEpoch()) throw new IOException("foreign cargo footprint attempt");
        return Optional.of(value);
    }

    private FrontierV3CargoRetirementFootprint readFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES) {
            throw new IOException("invalid cargo footprint file");
        }
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            var value = FrontierV3CargoRetirementFootprint.load(NbtIo.read(input, NbtAccounter.create(MAX_BYTES)));
            if (input.read() != -1 || !world.equals(value.world())
                    || !file.equals(target(value.entity(), value.authorityEpoch()))
                    && !file.equals(savedTarget(value))) {
                throw new IOException("foreign or trailing cargo footprint data");
            }
            return value;
        } catch (RuntimeException invalid) {
            throw new IOException("malformed cargo footprint", invalid);
        }
    }

    void retain(FrontierV3CargoRetirementFootprint next) throws IOException {
        if (!world.equals(next.world())) throw new IOException("foreign cargo footprint world");
        var previous = read(next.entity(), next.authorityEpoch());
        if (previous.isPresent() && !next.extendsEvidence(previous.get())) throw new IOException("cargo footprint regression or identity change");
        FrontierV3CargoCleanupArchive.createDurableDirectory(directory);
        if (previous.filter(next::equals).isPresent()) {
            FrontierV3CargoCleanupArchive.sync(directory); return;
        }
        // A new observed column invalidates the previous complete-save receipt. Remove
        // that receipt durably before publishing extended evidence, never overwrite it.
        Path priorMarker = savedTarget(next);
        if (Files.exists(priorMarker, LinkOption.NOFOLLOW_LINKS)) {
            if (previous.isEmpty() || !readFile(priorMarker).equals(previous.get())) {
                throw new IOException("cargo footprint save marker disagrees with retained evidence");
            }
            Files.delete(priorMarker);
            FrontierV3CargoCleanupArchive.sync(directory);
        }
        if (previous.isEmpty()) {
            try (var files = Files.newDirectoryStream(directory, "*.nbt")) {
                int count = 0;
                for (var ignored : files) if (++count >= CargoProjectionRetirements.MAX_PENDING) throw new IOException("cargo footprint capacity exhausted");
            }
        }
        var bytes = new ByteArrayOutputStream(); NbtIo.write(next.save(), new DataOutputStream(bytes));
        if (bytes.size() > MAX_BYTES) throw new IOException("cargo footprint exceeds bounded encoding");
        Path staging = directory.resolve(stem(next.entity(), next.authorityEpoch()) + ".pending");
        if (Files.isSymbolicLink(staging)) throw new IOException("cargo footprint staging is a symlink");
        try {
            try (var channel = FileChannel.open(staging, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(staging, target(next.entity(), next.authorityEpoch()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            FrontierV3CargoCleanupArchive.sync(directory);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /** Keeps old attempts visible; neither an epoch maximum nor current position is coverage. */
    List<FrontierV3CargoRetirementFootprint> inventory() throws IOException {
        return inventory("*.nbt");
    }

    List<FrontierV3CargoRetirementFootprint> savedInventory() throws IOException {
        return inventory("*.saved");
    }

    private List<FrontierV3CargoRetirementFootprint> inventory(String pattern) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        var entries = new ArrayList<FrontierV3CargoRetirementFootprint>();
        try (var files = Files.newDirectoryStream(directory, pattern)) {
            for (Path file : files) {
                if (entries.size() >= CargoProjectionRetirements.MAX_PENDING) throw new IOException("cargo footprint capacity exceeded");
                entries.add(readFile(file));
            }
        }
        entries.sort(Comparator.comparing(FrontierV3CargoRetirementFootprint::entity)
                .thenComparingLong(FrontierV3CargoRetirementFootprint::authorityEpoch));
        return List.copyOf(entries);
    }

    /** After actual entity write+sync, before durable canonical acknowledgement. */
    void markSaved(FrontierV3CargoRetirementFootprint expected) throws IOException {
        var retained = read(expected.entity(), expected.authorityEpoch()).orElseThrow(() -> new IOException("missing cargo footprint"));
        if (!retained.equals(expected)) throw new IOException("cargo footprint changed before save marker");
        Path marker = savedTarget(expected);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (!readFile(marker).equals(expected)) throw new IOException("contradictory cargo footprint save marker");
        } else Files.createLink(marker, target(expected.entity(), expected.authorityEpoch()));
        FrontierV3CargoCleanupArchive.sync(directory);
    }

    /** Caller owes durable canonical acknowledgement. A stale snapshot cannot delete new evidence. */
    void forgetExact(FrontierV3CargoRetirementFootprint expected) throws IOException {
        if (!world.equals(expected.world())) throw new IOException("foreign cargo footprint cleanup");
        var retained = read(expected.entity(), expected.authorityEpoch());
        if (retained.isPresent() && !retained.get().equals(expected)) throw new IOException("cargo footprint changed before cleanup");
        Path marker = savedTarget(expected);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && !readFile(marker).equals(expected)) throw new IOException("cargo footprint marker changed before cleanup");
        Files.deleteIfExists(target(expected.entity(), expected.authorityEpoch()));
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) FrontierV3CargoCleanupArchive.sync(directory);
        Files.deleteIfExists(marker);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) FrontierV3CargoCleanupArchive.sync(directory);
    }

    private Path savedTarget(FrontierV3CargoRetirementFootprint footprint) {
        return directory.resolve(stem(footprint.entity(), footprint.authorityEpoch()) + ".saved");
    }
    private Path target(UUID entity, long epoch) { return directory.resolve(stem(entity, epoch) + ".nbt"); }
    private static String stem(UUID entity, long epoch) {
        if (epoch < 1) throw new IllegalArgumentException("invalid cargo footprint epoch");
        return Objects.requireNonNull(entity) + "-" + epoch;
    }
}
